package com.ffvcn.client.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.ffvcn.client.AppLocale
import com.ffvcn.client.BuildConfig
import com.ffvcn.client.R
import com.ffvcn.client.WebSignInActivity
import com.ffvcn.client.vpn.CountryNames
import com.ffvcn.client.vpn.Mode
import com.ffvcn.client.vpn.LogLine
import com.ffvcn.client.vpn.Logs
import com.ffvcn.client.vpn.NetInfo
import com.ffvcn.client.vpn.Phase
import com.ffvcn.client.vpn.Server
import com.ffvcn.client.vpn.ServerList
import com.ffvcn.client.vpn.Store
import com.ffvcn.client.vpn.UpdateState
import com.ffvcn.client.vpn.Updates
import com.ffvcn.client.vpn.VpnState
import com.ffvcn.client.vpn.VpnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    ui: VpnUi,
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf(Store.accounts(context)) }
    var language by remember { mutableStateOf(AppLocale.current(context)) }
    var country by remember { mutableStateOf(Store.country(context)) }
    var exits by remember { mutableStateOf<List<Server>>(emptyList()) }
    val countries = remember(exits) {
        exits.distinctBy { it.country }
            .map { it.country to CountryNames.display(it.country, it.countryName) }
            .sortedWith(
                compareBy({ CountryNames.rank(it.first) }, { it.second.lowercase() })
            )
    }

    LaunchedEffect(Unit) {
        val cached = withContext(Dispatchers.IO) {
            ServerList.readCache(Store.cacheFile(context))
        }
        if (cached.isNotEmpty()) exits = cached
        val binary = Store.binary(context)
        if (binary.exists()) {
            val fresh = withContext(Dispatchers.IO) {
                runCatching {
                    ServerList.fetch(binary, Store.cacheFile(context)) { false }
                }.getOrDefault(emptyList())
            }
            if (fresh.isNotEmpty()) exits = fresh
        }
    }

    // Validates a token with the engine: it proves the session against the
    // auth server and caches it in one go (`--session-token-file`).
    fun verify(session: String, mail: String) {
        scope.launch {
            busy = true
            error = null
            var quota = ""
            // The page reports the session as soon as the password is accepted,
            // before any code it still asks for; Mozilla then has to confirm it.
            var problem: String? = null
            var attempt = 0
            while (attempt < VERIFY_ATTEMPTS) {
                attempt++
                problem = withContext(Dispatchers.IO) {
                    val binary = Store.binary(context)
                    if (!binary.exists()) {
                        context.getString(R.string.error_engine_missing)
                    } else {
                        ServerList.verifyToken(
                            binary = binary,
                            workDir = Store.workDir(context),
                            token = session
                        ) { line ->
                            Regex("""Bandwidth:\s*(.+)$""")
                                .find(line)?.groupValues?.get(1)?.trim()
                                ?.let { quota = it }
                        }
                    }
                }
                if (problem == null || !looksUnconfirmed(problem.orEmpty())) break
                delay(VERIFY_RETRY_MS)
            }
            busy = false
            if (problem == null) {
                if (mail.isNotBlank()) Store.saveAccount(context, mail.trim(), session, quota)
                accounts = Store.accounts(context)
                Logs.add("account", "saved ${mail.trim()}")
                onSignedIn()
            } else {
                Logs.add("account", "verify failed: ${problem.take(160)}")
                error = context.getString(R.string.sign_in_failed) + "\n" + problem
            }
        }
    }

    val browser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val session = data.getStringExtra("token") ?: return@rememberLauncherForActivityResult
        val mail = data.getStringExtra("email")?.trim() ?: return@rememberLauncherForActivityResult
        verify(session, mail)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.language_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.language_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppLocale.choices.forEach { (tag, label) ->
                        ChoiceRow(
                            label = if (tag == AppLocale.SYSTEM) {
                                stringResource(R.string.language_system)
                            } else {
                                label
                            },
                            selected = language == tag
                        ) {
                            language = tag
                            (context as? Activity)?.let { AppLocale.apply(it, tag) }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.server_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.server_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // A dropdown instead of one row per country: the exit list
                    // can run to dozens of regions, which buried every section
                    // below it. The choice is stored, so it survives a restart.
                    RegionPicker(
                        selected = country,
                        options = listOf("" to stringResource(R.string.server_auto)) + countries,
                        modifier = Modifier.padding(top = 6.dp)
                    ) { picked ->
                        country = picked
                        Store.setCountry(context, picked)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.mode_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeOption(
                            title = R.string.mode_vpn,
                            subtitle = R.string.mode_vpn_desc,
                            selected = ui.mode == Mode.Vpn,
                            modifier = Modifier.weight(1f)
                        ) {
                            Store.setMode(context, Mode.Vpn)
                            VpnState.update { it.copy(mode = Mode.Vpn) }
                        }
                        ModeOption(
                            title = R.string.mode_proxy,
                            subtitle = R.string.mode_proxy_desc,
                            selected = ui.mode == Mode.Proxy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Store.setMode(context, Mode.Proxy)
                            VpnState.update { it.copy(mode = Mode.Proxy) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.share_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.share_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ui.share,
                            onCheckedChange = { enabled ->
                                Store.setShare(context, enabled)
                                VpnState.update { it.copy(share = enabled) }
                            }
                        )
                    }
                    if (ui.share) {
                        val address = NetInfo.shareAddress()
                        Text(
                            text = if (address != null) {
                                stringResource(R.string.share_ready, address) + " · " +
                                    stringResource(R.string.share_ports, "1080", "1081")
                            } else {
                                stringResource(R.string.share_no_network)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (ui.phase != Phase.Off) {
                        Text(
                            text = stringResource(R.string.settings_next_connect),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.account_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = stringResource(
                            if (accounts.isEmpty()) R.string.account_none else R.string.account_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    accounts.forEach { saved ->
                        AccountRow(
                            email = saved.email,
                            quota = saved.quota,
                            active = saved.email.equals(ui.email, ignoreCase = true) && ui.signedIn,
                            enabled = !busy,
                            onUse = {
                                Store.activateAccount(context, saved.email)
                                error = null
                                accounts = Store.accounts(context)
                                onSignedIn()
                            },
                            onRemove = {
                                Store.removeAccount(context, saved.email)
                                error = null
                                accounts = Store.accounts(context)
                                if (Store.accounts(context).isEmpty()) onSignedOut() else onSignedIn()
                            }
                        )
                    }

                    if (accounts.isNotEmpty() && ui.phase != Phase.Off) {
                        Text(
                            text = stringResource(R.string.account_switch_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            error = null
                            browser.launch(Intent(context, WebSignInActivity::class.java))
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.size(10.dp))
                        }
                        Text(
                            stringResource(
                                when {
                                    busy -> R.string.sign_in_checking
                                    accounts.isEmpty() -> R.string.sign_in_with_firefox
                                    else -> R.string.account_add
                                }
                            )
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.log_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.log_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val lines by Logs.lines.collectAsState()
                    Text(
                        text = if (lines.isEmpty()) {
                            stringResource(R.string.log_empty)
                        } else {
                            stringResource(R.string.log_size, lines.size, logSize(lines))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                copyLog(context)
                                Toast.makeText(context, R.string.log_copied, Toast.LENGTH_SHORT).show()
                            },
                            enabled = lines.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.log_copy))
                        }
                        TextButton(
                            onClick = { Logs.clear() },
                            enabled = lines.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.log_clear))
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.about_core),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.about_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UpdateRow()
                }
            }
        }
    }

}

/**
 * Version check against the project's GitHub releases. The app only reads the
 * public release feed; downloads open the release page in a browser, so the
 * app never installs anything by itself.
 */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updates.state.collectAsState()
    val release by Updates.release.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (state) {
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    UpdateState.Latest -> stringResource(R.string.update_latest)
                    UpdateState.Available ->
                        stringResource(R.string.update_available, release?.version.orEmpty())
                    UpdateState.Failed -> stringResource(R.string.update_failed)
                    UpdateState.Idle -> stringResource(R.string.update_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state == UpdateState.Available) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { scope.launch { Updates.check() } },
                enabled = state != UpdateState.Checking
            ) {
                Text(stringResource(R.string.check_update))
            }
        }
        if (state == UpdateState.Available && release != null) {
            Button(
                onClick = { openPage(context, release?.pageUrl.orEmpty()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.update_download))
            }
        }
    }
}

/** Opens a web page outside the app; a device without a browser just no-ops. */
private fun openPage(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** The log's size, in the units a file size reads in. */
private fun logSize(lines: List<LogLine>): String {
    val bytes = lines.sumOf { it.toString().toByteArray().size + 1 }
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/** Puts the whole log on the clipboard, for pasting into a message. */
private fun copyLog(context: Context) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.log_title), Logs.text()))
}

/**
 * The region picker: a full-width field that opens the list in a menu, so
 * dozens of exits no longer push the rest of the settings off the screen. The
 * empty code is "automatic", which is what a fresh install starts on; the
 * choice is stored, so it is still there after a restart.
 */
@Composable
private fun RegionPicker(
    selected: String,
    options: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val current = options.firstOrNull { it.first.equals(selected, ignoreCase = true) }?.second
        ?: selected.ifEmpty { options.first().second }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    text = stringResource(R.string.server_pick_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (code, name) ->
                val active = code.equals(selected, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            color = if (active) accent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** One row of a settings picker; the check marks the active choice. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text(text = "\u2713", color = accent, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * One saved account: tapping the row makes it the account the engine uses,
 * and Remove drops it from the device.
 */
@Composable
private fun AccountRow(
    email: String,
    quota: String,
    active: Boolean,
    enabled: Boolean,
    onUse: () -> Unit,
    onRemove: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (active) accent.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !active, onClick = onUse)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (active) accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (active) {
                    Text(
                        text = stringResource(R.string.account_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
            }
            if (quota.isNotBlank()) {
                Text(
                    text = stringResource(R.string.quota_label) + ": " + quota,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = stringResource(R.string.account_remove),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.clickable(enabled = enabled, onClick = onRemove)
            )
        }
    }
}

/** A selectable mode card; the border and wash carry the selection. */
@Composable
private fun ModeOption(
    title: Int,
    subtitle: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Whether the engine refused the session only because Mozilla has not confirmed
 * it yet: the sign-in page reports the session right after the password, before
 * the code or the emailed link it still asks for.
 */
private fun looksUnconfirmed(problem: String): Boolean =
    problem.contains("errno 138") ||
        problem.contains("errno 201") ||
        problem.contains("unconfirmed", ignoreCase = true) ||
        problem.contains("unverified", ignoreCase = true) ||
        problem.contains("not verified", ignoreCase = true)

/** How long a session that is waiting for confirmation is retried. */
private const val VERIFY_ATTEMPTS = 40
private const val VERIFY_RETRY_MS = 4_000L
