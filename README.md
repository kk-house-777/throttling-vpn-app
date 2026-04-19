# Throttling VPN App

An Android VPN app that simulates various network conditions (2G to 3G/HSPA+) by throttling traffic for specific apps. Built for learning low-level networking concepts.

## Architecture

```
Target App → [TUN interface] → [Throttle Layer (Go)] → [tun2socks/gVisor] → Internet
```

- **VpnService** captures traffic from selected apps via TUN interface
- **tun2socks** (Go, gVisor TCP/IP stack) handles L3↔L4 packet forwarding
- **Token bucket** algorithm controls throughput between TUN and tun2socks
- **Unix socketpair** bridges the throttle layer and tun2socks engine

## Modules

| Module | Description |
|---|---|
| `app/` | VPN throttling app — controls VPN, speed settings, target app management |
| `test-app/` | Test target app — measures download/upload speed and latency via httpbin.org |
| `go/tun2sockslib/` | Go wrapper around tun2socks v2 engine with token bucket throttling |

## Features

- **Speed control**: 50 kbps (2G) to 5 Mbps (HSPA+) with logarithmic slider
- **Three modes**: Block (blackhole), Throttle (rate-limited), Unlimited (passthrough)
- **Per-app targeting**: Select which apps are affected via application ID
- **Real-time speed change**: Adjust speed without restarting VPN
- **Persistent app list**: Target apps saved with DataStore

## Building

### Prerequisites

- Android Studio with SDK 36+
- Go 1.26+ (`go version`)
- gomobile (`go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init`)
- Android NDK r29+ and `ANDROID_NDK_HOME` environment variable set

### Build the Go library

```bash
cd go/tun2sockslib
gomobile bind -target=android -androidapi 26 -o ../../app/libs/tun2socks.aar .
```

### Build the Android apps

```bash
./gradlew assembleDebug
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb install test-app/build/outputs/apk/debug/test-app-debug.apk
```

## Version Code Scheme

### Play ストア (universal APK)

`build.gradle.kts` の `versionCode` がそのまま使われる。

### F-Droid (ABI split)

`versionCode * 10 + abiCode` の suffix 方式。

| ABI | abiCode | 例 (versionCode=2) |
|-----|---------|-------------------|
| x86 | 1 | 21 |
| armeabi-v7a | 2 | 22 |
| x86_64 | 3 | 23 |
| arm64-v8a | 4 | 24 |

### バージョンアップ時の手順

1. `app/build.gradle.kts` の `versionCode` をインクリメント (例: 2 → 3)
2. Play は自動で `3` になる
3. F-Droid は自動で `31, 32, 33, 34` になる（VercodeOperation で算出）
4. F-Droid metadata (`fdroiddata/metadata/okoge.house.throttling_app.fdroid.yml`) に新ビルドエントリを追加し、commit hash を更新する
