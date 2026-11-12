package com.ffvcn.client.vpn

import com.ffvcn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One published build, as the release feed describes it. */
data class Release(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?
)

enum class UpdateState { Idle, Checking, Latest, Available, Failed }

/**
 * Version check against the project's own GitHub releases.
 *
 * Only public release feeds are read — no account, no token, and nothing about
 * the device leaves the phone. A build is only offered when its tag is newer
 * than [BuildConfig.VERSION_NAME], so a fork that forgets to bump the version
 * is not nagged forever. Drafts and pre-releases are skipped on purpose: they
 * are scratch builds, not something to push at people who just want the app to
 * work.
 *
 * "The feed answered and has no numbered release" is reported as [UpdateState.Latest],
 * never as a failure: a repo whose only release is the rolling pre-release used
 * to be indistinguishable from an unreachable network, which made every manual
 * check claim GitHub was down.
 */
object Updates {
    /** The repository's API feed; the app id is the source of truth for the name. */
    private const val FEED = "https://api.github.com/repos/zixiwu/ffvcn/releases?per_page=20"

    /**
     * Fallback served from github.com rather than api.github.com. The API host is
     * the one that gets rate-limited or blocked most often, and the atom feed
     * still carries the tags, so a check degrades to "version known, open the
     * page yourself" instead of failing outright.
     */
    private const val ATOM = "https://github.com/zixiwu/ffvcn/releases.atom"
    private const val TAG_PAGE = "https://github.com/zixiwu/ffvcn/releases/tag/"

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 12_000

    private val _state = MutableStateFlow(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _release = MutableStateFlow<Release?>(null)
    val release: StateFlow<Release?> = _release.asStateFlow()

    /** Short technical cause for the last failure, shown next to the message. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Runs once per process, so opening the app does not hammer the API. */
    suspend fun auto() {
        if (_state.value != UpdateState.Idle) return
        check()
        // A background check that fails has nothing to say: leave the settings
        // row on its neutral hint instead of reporting a network error nobody
        // asked about. The manual button still reports failures.
        if (_state.value == UpdateState.Failed) _state.value = UpdateState.Idle
    }

    /** Blocking check; safe to call from the UI, it switches to IO itself. */
    suspend fun check() {
        _state.value = UpdateState.Checking
        _error.value = null
        val fetched = withContext(Dispatchers.IO) { runCatching { fetch() } }
        if (fetched.isFailure) {
            _error.value = describe(fetched.exceptionOrNull())
            _state.value = UpdateState.Failed
            return
        }
        val newest = fetched.getOrNull()
        if (newest == null) {
            // Reachable, but nothing numbered has been published yet. That is
            // "up to date", not "cannot reach GitHub".
            _release.value = null
            _state.value = UpdateState.Latest
            return
        }
        if (compare(newest.version, BuildConfig.VERSION_NAME) > 0) {
            _release.value = newest
            _state.value = UpdateState.Available
        } else {
            _release.value = null
            _state.value = UpdateState.Latest
        }
    }

    /** API first, atom feed second; null means "no numbered release yet". */
    private fun fetch(): Release? {
        val api = runCatching { fetchApi() }
        if (api.isSuccess) return api.getOrNull()
        val atom = runCatching { fetchAtom() }
        if (atom.isSuccess) return atom.getOrNull()
        throw api.exceptionOrNull() ?: atom.exceptionOrNull() ?: IOException("network")
    }

    private fun fetchApi(): Release? {
        val connection = open(FEED)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return pick(JSONArray(body))
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** Reads tags from the atom feed; no assets there, so [Release.apkUrl] is null. */
    private fun fetchAtom(): Release? {
        val connection = open(ATOM)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val xml = connection.inputStream.bufferedReader().use { it.readText() }
            var bestTag: String? = null
            var bestVersion: String? = null
            Regex("""/releases/tag/([^"<>\s]+)""").findAll(xml).forEach { match ->
                val tag = match.groupValues[1]
                val version = normalize(tag)
                // Only dotted numbers compare, so a branch or scratch tag in the
                // feed cannot win the "newest" contest by accident.
                if (version.isEmpty() || version.any { !it.isDigit() && it != '.' }) {
                    return@forEach
                }
                if (bestVersion == null || compare(version, bestVersion) > 0) {
                    bestVersion = version
                    bestTag = tag
                }
            }
            val tag = bestTag ?: return null
            val version = bestVersion ?: return null
            return Release(version = version, notes = "", pageUrl = TAG_PAGE + tag, apkUrl = null)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            // GitHub rejects requests without a user agent.
            setRequestProperty("User-Agent", "FFVCN/" + BuildConfig.VERSION_NAME)
        }

    /** The newest non-draft, non-prerelease entry, or null when there is none. */
    private fun pick(array: JSONArray): Release? {
        var best: Release? = null
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optBoolean("draft") || item.optBoolean("prerelease")) continue
            val version = normalize(item.optString("tag_name"))
            if (version.isEmpty()) continue
            val entry = Release(
                version = version,
                notes = item.optString("body").trim(),
                pageUrl = item.optString("html_url"),
                apkUrl = apkOf(item.optJSONArray("assets"))
            )
            if (best == null || compare(entry.version, best.version) > 0) best = entry
        }
        return best
    }

    /** First APK attached to the release, if the author uploaded one. */
    private fun apkOf(assets: JSONArray?): String? {
        if (assets == null) return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").ifBlank { null }
            }
        }
        return null
    }

    /** A short, human-readable cause; the UI shows it in parentheses. */
    private fun describe(t: Throwable?): String = when (t) {
        null -> "network"
        is java.net.SocketTimeoutException -> "timeout"
        is java.net.UnknownHostException -> "DNS"
        is javax.net.ssl.SSLException -> "TLS"
        else -> t.message?.take(48)?.takeIf { it.isNotBlank() } ?: "network"
    }

    /** "v1.2.0-beta.1" becomes "1.2.0", so tags and versionName compare alike. */
    private fun normalize(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+').trim()

    /** Semver-ish compare: 1.10.0 beats 1.9.9, and missing parts count as zero. */
    private fun compare(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    private fun parts(version: String): List<Int> =
        normalize(version).split('.', ' ')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }
}
