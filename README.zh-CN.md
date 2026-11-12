# FirefoxV CN

[English](README.md) · **简体中文** · [فارسی](README.fa.md) · [Русский](README.ru.md) · [Español](README.es.md)

这是 [Niloofar VPN](https://github.com/FossilizedProgrammer/niloofar_vpn) 的简体
中文分支，换了应用名、重签了密钥，包名为 `com.ffvcn.client`。它本身是一个调用
Firefox VPN 引擎（位于 `../firefox-vpn-go`）的 Android 客户端，那个 Go 引擎被交叉
编译成 `libffvpn.so`，从 `nativeLibraryDir` 里直接 exec 启动，并指向一个免费的
Firefox 出口。

相对上游的改动：简体中文界面（`values-zh-rCN`）、节点列表里的中文国家名、独立的
发布签名密钥、新包名。完整清单和原版代码的安全审计见 `二开说明.md` 与
`安全审计报告.md`。

## 它是怎么连上的

- **VPN 模式（默认）。** 应用会打开一个 TUN 网卡，由 `libc2tun.so`（tun2socks）把
  手机上的每个数据包转成一条到引擎的 SOCKS5 连接，再从选定的 Firefox 出口送出。对
  手机来说这就是一个普通的系统级 VPN：所有应用都被覆盖，任何地方都不用填代理设置。
- **代理模式。** 不建立系统 VPN，也没有授权弹窗：只跑引擎自己的本地 SOCKS（1080）
  和 HTTP（1081）监听，供你手动指向它们的应用使用。

### 为什么给其他设备共享走的是代理端口

`VpnService` 只能承载建立隧道的这台设备上各应用的流量。Android 不允许应用把一台
通过热点连进来的笔记本或第二部手机也塞进自己的 VPN，所以「共享」只是把引擎的监听
绑到 `0.0.0.0`，由对方设备把 `<手机IP>:1080`（SOCKS）或 `<手机IP>:1081`（HTTP）
设成代理。流量依然是从这台手机的同一条隧道出去的；只有交接给别的设备那一步用的是
代理，因为 Android 只提供了这一种交接方式。所以它是 VPN，而不是「只是个代理」——
代理模式是你可以另外选的独立选项。

## 功能

- 一键连接：应用先拉取出口列表，逐个探测，拨通第一个真正能跑流量的最快节点，然后
  才显示「已连接」。
- **VPN 模式** —— 打开 TUN 网卡，`libc2tun.so`（tun2socks）把设备流量转发进引擎的
  SOCKS5 监听。
- **代理模式** —— 不建系统 VPN、不弹授权框：只跑本地 SOCKS/HTTP 监听，给那些你自己
  指定了代理的应用用。
- **局域网共享** —— 监听绑到 `0.0.0.0`，同一 Wi-Fi 或热点下的其他设备就能用
  `SOCKS <IP>:1080` 或 `HTTP <IP>:1081`。
- 保活：每分钟探一次本地监听，一旦不再响应就自动重建会话，而不是让一个已经死掉的
  隧道继续挂着。
- 在应用内用 Mozilla 自己的页面登录：邮箱、密码、两步验证码、确认邮件以及 Mozilla
  的边缘校验全部由 Mozilla 的页面处理，会话一直保留到 Mozilla 确认。
- 可保存多个账户并一键切换，每个账户单独显示额度，另附一份会打开 Mozilla 注册页的
  建号指引。
- 实时吞吐/延迟读数，以及应用内的日志卡片：显示日志大小、可复制到剪贴板、可清空；
  记录只留在内存里，既不展示也不写入磁盘。
- 英语/波斯语/俄语/西班牙语/**简体中文**界面，应用内可切换语言，图标是一枚砂岩玫瑰
  窗花。

## 构建

环境要求：JDK 17+（本仓库用的是 21）、带 build-tools 35 和 NDK 26.3.11579264 的
Android SDK，以及用来编译引擎的 Go。

```sh
./tools/build-apks.sh
```

它会执行 `assembleRelease`，并把拆分出来的 APK 复制到 `dist/`。同一套构建也能在
GitHub 上跑：`.github/workflows/build-apks.yml` 会在每次 push、pull request 和打
tag 时构建。push 到 `main` 会刷新 **Latest build** 这个滚动 release，打 tag 则生成
一个带版本号的正式 release，所以 Releases 里的 APK 永远不会比 `main` 旧；每次运行
还会把 APK 作为 `FirefoxVCN-apks` 产物挂到这次 run 上。二进制文件不进仓库。

| 文件 | ABI |
| --- | --- |
| `dist/FirefoxVCN-1.1.1-armeabi-v7a.apk` | 32 位 ARM |
| `dist/FirefoxVCN-1.1.1-arm64-v8a.apk` | 64 位 ARM |
| `dist/FirefoxVCN-1.1.1-universal.apk` | 两者通吃 |

发布版用 `signing/firefoxv-cn.keystore` 里那把固定密钥签名（PKCS12，别名
`firefoxv-cn`，密码 `ffvpncn`），所以本地或 CI 出的新包都能直接覆盖安装**这个应用**
的旧包。包名是 `com.ffvcn.client`，因此它和原版 Niloofar VPN 可以并存，不会互相
覆盖。以后如果换了别的密钥重新构建，装之前要先卸载一次，否则签名对不上。

### 依赖与网络

`settings.gradle.kts` 优先走腾讯和 Myket 的 Maven 镜像，因为从这台机器访问
`dl.google.com` 不可靠。用户级的 `~/.gradle/gradle.properties` 里配了一个 Java 用
不了的 SOCKS 代理，所以构建脚本会传 `-DsocksProxyHost=` 把它关掉，同时清掉环境变量
里的代理设置。

### 重新编译引擎

```sh
./tools/ffvpn/build-android.sh
```

脚本会把 `../firefox-vpn-go` 复制到一个临时目录，打上 `tools/ffvpn/` 里的补丁
（v2 quick-stretch 盐、会话令牌缓存、`--list-countries --json`、TLS 握手超时、
图形验证码作答），再用 NDK clang 为两个 ABI 交叉编译，开启 cgo 以便走 Android 自己
的域名解析。

## 目录结构

```
app/src/main/java/com/ffvcn/client/
  MainActivity.kt            界面宿主、VPN 授权、通知权限
  SignInActivity.kt          Firefox 账户表单，转发引擎的交互提示
  WebSignInActivity.kt       在 WebView 里打开 Mozilla 自己的登录页
  AppLocale.kt               应用内语言切换
  ui/                        Compose 界面与主题
  vpn/FirefoxVpnService.kt   前台服务、TUN 建立、保活、共享
  vpn/FirefoxEngine.kt       引擎进程、探测、流量验证
  vpn/FxaLogin.kt            账户登录，令牌缓存供后续连接使用
  vpn/ServerList.kt          出口列表解析、探测与排序
  vpn/CountryNames.kt        出口国家名的中文显示名
  vpn/Logs.kt                保存在内存里的日志，设置页可复制或清空
  vpn/Store.kt, State.kt     偏好设置与可观察的界面状态
  vpn/NetInfo.kt             共享时显示的局域网地址
app/src/main/java/com/multicore/vpn/tunnel/
  Tun2Socks.kt               指向 libc2tun 的 JNI 桥（包名由 .so 固定）
```

## 已知限制

- 共享出去的设备走的是代理端口，不是隧道：Android 的 VPN 不覆盖热点的客户端，所以
  热点上的设备必须手动把代理设成 `<手机IP>:1080`（SOCKS）或 `<手机IP>:1081`
  （HTTP）。参见「为什么给其他设备共享走的是代理端口」。
- 切换模式或共享开关要等到下次连接才生效；正在跑的会话不会自动重启。
- 代理模式没有断网保护（kill switch），也没有分应用路由。
