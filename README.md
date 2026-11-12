# FFVCN

**English** · [简体中文](README.zh-CN.md) · [فارسی](README.fa.md) · [Русский](README.ru.md) · [Español](README.es.md)

A Chinese-localised fork of [Niloofar VPN](https://github.com/FossilizedProgrammer/niloofar_vpn),
rebranded and re-signed as `com.ffvcn.client`. It is an Android client for the
Firefox VPN engine in `../firefox-vpn-go`. The Go engine is cross-compiled into
`libffvpn.so`, exec'd from `nativeLibraryDir`, and pointed at a free Firefox
exit.

Changes against upstream: a Simplified Chinese UI (`values-zh-rCN`), Chinese
country names for the exit picker, a dedicated release keystore, and the new
package id. See `二开说明.md` and `安全审计报告.md` for the full list and the
audit of the original code.

## How it connects

- **VPN mode (default).** The app opens a TUN interface and `libc2tun.so`
  (tun2socks) turns every packet from this phone into a SOCKS5 connection to
  the engine and out through the chosen Firefox exit. From the phone's side
  this is an ordinary system VPN: every app is covered and no proxy setting has
  to be entered anywhere.
- **Proxy mode.** No system VPN, no consent dialog: only the engine's local
  SOCKS (1080) and HTTP (1081) listeners run, for the apps you point at them
  yourself.

### Why sharing with other devices uses the proxy ports

`VpnService` only carries the traffic of apps running on the device that
created the tunnel. Android gives an app no way to route a tethered laptop or
a second phone through its VPN, so Share binds the engine's listeners to
`0.0.0.0` and the other device sets `<phone-ip>:1080` (SOCKS) or
`<phone-ip>:1081` (HTTP) as its proxy. The traffic still leaves the phone
through the same tunnel; only the hand-off to that device is a proxy, because
that is the only hand-off Android allows. So this is a VPN, not "only a
proxy" — proxy mode is a separate option you can pick instead.

## Features

- One-tap connect: the app fetches the exit list, probes every edge, dials the
  fastest one that actually carries traffic, and only then reports "Connected".
- **VPN mode** — a TUN interface is opened and `libc2tun.so` (tun2socks)
  forwards the device's traffic into the engine's SOCKS5 listener.
- **Proxy mode** — no system VPN, no consent dialog: only the local SOCKS/HTTP
  listeners run, for apps that you point at the proxy yourself.
- **Share on network** — the listeners bind to `0.0.0.0` so other devices on the
  same Wi-Fi/hotspot can use `SOCKS <ip>:1080` or `HTTP <ip>:1081`.
- Keep-alive: the local listener is probed every minute and a session that
  stops answering is rebuilt automatically instead of leaving a dead tunnel up.
- Sign-in on Mozilla's own page inside the app: email, password, two-factor
  code, the confirmation email and Mozilla's edge challenge are all handled by
  Mozilla's page, and the session is kept until Mozilla confirms it.
- Several accounts saved on the device with a one-tap switch, per-account
  quota, and a create-account guide that opens Mozilla's sign-up page.
- Live throughput/latency readout and an in-app log card: it shows the log's
  size, copies it to the clipboard, or clears it — the lines stay in memory and
  are never shown or written to disk.
- English/Persian/Russian/Spanish/Chinese UI with an in-app language picker, and
  a sandstone rosette launcher icon.
- Region picker as a dropdown: all 33 curated regions are always listed, the ones
  with a live exit on top in pinyin order and the rest sunk to the bottom as
  "No exits"; the choice is remembered, a fresh install starts on Automatic.
- Quick settings tile (**FFV开关**): connect or disconnect from the shade, with
  the tile following the live state and handing back to the app when consent or
  an account is still missing.
- Update check against this fork's GitHub releases
  ([zixiwu/ffvcn](https://github.com/zixiwu/ffvcn/releases)): one silent check
  per launch, a pill on the home screen when something newer exists, and a manual
  button in Settings. Downloads open the release page — the app never installs
  anything by itself.

## Build

Requirements: JDK 17+ (21 is used here), Android SDK with build-tools 35 and
NDK 26.3.11579264, Go for the engine.

```sh
./tools/build-apks.sh
```

This runs `assembleRelease` and copies the splits into `dist/`. The same build
runs on GitHub: `.github/workflows/build-apks.yml` builds every push, pull
request and tag. Every non-PR build publishes a numbered release tagged
`v<versionName>`, which is moved to the commit just built if it already exists,
so Releases never point at older code and re-running a version replaces its
APKs. Each run also attaches them as the `FirefoxVCN-apks` artifact. Binaries
never live in the repository.

| File | ABI |
| --- | --- |
| `dist/FirefoxVCN-1.1.2-armeabi-v7a.apk` | 32-bit ARM |
| `dist/FirefoxVCN-1.1.2-arm64-v8a.apk` | 64-bit ARM |
| `dist/FirefoxVCN-1.1.2-universal.apk` | both |

Release builds are signed with the fixed key in `signing/firefoxv-cn.keystore`
(PKCS12, alias `firefoxv-cn`, password `ffvpncn`), so every build — local or CI
— installs over the previous one of *this* app. The package id is
`com.ffvcn.client`, so it installs alongside the original Niloofar VPN instead
of replacing it. If you ever rebuild with a different key, uninstall once before
installing: the signatures will not match.

### Dependencies and network

`settings.gradle.kts` resolves through the Tencent and Myket Maven mirrors
first, because `dl.google.com` is unreliable from this machine. The user-level
`~/.gradle/gradle.properties` sets a SOCKS proxy that Java cannot use, so the
build script passes `-DsocksProxyHost=` to turn it off and also clears the
environment proxy variables.

### Rebuilding the engine

```sh
./tools/ffvpn/build-android.sh
```

The script copies `../firefox-vpn-go` to a scratch directory, applies the
patches in `tools/ffvpn/` (v2 quick-stretch salt, cached session token,
`--list-countries --json`, TLS handshake timeout, image-answer prompt), and
cross-compiles for both ABIs with the NDK clang, using cgo so Android's
resolver is used.

## Structure

```
app/src/main/java/com/ffvcn/client/
  MainActivity.kt            UI host, VPN consent, notifications permission
  SignInActivity.kt          Firefox Account form, relays the engine's prompts
  WebSignInActivity.kt       Mozilla's own sign-in page in a WebView
  AppLocale.kt               in-app language override
  ui/                        Compose screens and theme
  vpn/FirefoxVpnService.kt   foreground service, TUN setup, keep-alive, share
  vpn/FirefoxEngine.kt       engine process, probing, traffic check
  vpn/FxaLogin.kt            account sign-in, token cached for later connects
  vpn/ServerList.kt          exit list parsing, probing and ranking
  vpn/Logs.kt                in-memory log the settings card copies or clears
  vpn/Store.kt, State.kt     preferences and observable UI state
  vpn/NetInfo.kt             LAN address shown when sharing
app/src/main/java/com/multicore/vpn/tunnel/
  Tun2Socks.kt               JNI bridge to libc2tun (package fixed by the .so)
```

## Limitations

- Devices sharing the connection go through the proxy ports, not the tunnel:
  Android's VPN does not cover tethered clients, so a device on your hotspot
  must set the proxy to `<phone-ip>:1080` (SOCKS) or `<phone-ip>:1081` (HTTP)
  manually. See "Why sharing with other devices uses the proxy ports".
- Changing mode or the share switch takes effect on the next connect; the
  running session is not restarted automatically.
- Proxy mode has no kill switch and no per-app routing.
