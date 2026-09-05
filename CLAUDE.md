# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android VPN app that rate-limits network traffic for selected apps using a token bucket algorithm. Simulates 2G–3G network conditions (50 kbps – 5 Mbps) for testing app behavior under poor connectivity.

## Modules

- **app/** — Main VPN throttling app (`okoge.house.throttling_app`)
- **test-app/** — Network speed measurement utility (`okoge.house.throttling_app.testapp`)
- **go/tun2sockslib/** — Go library: tun2socks wrapper with token bucket throttling, compiled to AAR via gomobile

## Build Commands

```bash
# Build debug APKs
./gradlew assembleDebug

# Build specific module
./gradlew :app:assembleDebug
./gradlew :test-app:assembleDebug

# Run tests
./gradlew test
./gradlew :app:testDebugUnitTest

# Rebuild Go AAR (requires gomobile + Android NDK)
cd go/tun2sockslib
gomobile bind -target=android -androidapi 26 -o ../../app/libs/tun2socks.aar .
```

## Architecture

**Data flow:** Target App → TUN interface → Go throttle layer (token bucket) → tun2socks/gVisor → Internet

Key components:
- **ThrottleVpnService** — Android VpnService that creates TUN interface, passes FD to Go layer. Handles start/stop/setSpeed intents. Stops automatically via `onTaskRemoved` when app is killed.
- **Go tun2sockslib** — Creates Unix socketpair between TUN and tun2socks engine. Runs bidirectional relay goroutines with `golang.org/x/time/rate.Limiter` (token bucket) on each direction. Exposed functions: `Start(fd, speedKbps)`, `SetSpeed(speedKbps)`, `Stop()`.
- **MainActivity** — Compose UI with Navigation 3. Handles VPN + notification permission flow (Android 13+ POST_NOTIFICATIONS). Speed slider uses logarithmic mapping.
- **TargetAppRepository** — DataStore Preferences for persisting target app list.

**Speed units:** UI displays kbps. Go layer expects KB/s. Conversion: `kbpsToKBps() = kbps / 8`. Speed modes: Block (< 0), Unlimited (0), Throttle (> 0).

**AAR build:** `app/libs/tun2socks.aar` is gitignored, not checked in — it's rebuilt from Go source on every CI run (see `.github/workflows/unit-test.yml`) rather than vendored, since this repo also ships an `fdroid` flavor and F-Droid's build process expects reproducible builds from source rather than opaque prebuilt binaries. For local development, run the `gomobile bind` command above once; `app/libs/tun2socks.aar` must exist before any Gradle task that builds the `app` module will succeed.

## Tech Stack

- Kotlin 2.3.20, Compose, Material 3, Navigation 3
- Target/Compile SDK 37, Min SDK 26, Java 11
- Go 1.25.0 + gomobile (tun2socks v2.6.0, gVisor TCP/IP stack)
- AGP 9.4.0 / Gradle 9.7.1, version catalog at `gradle/libs.versions.toml`
- Compose は BOM を使わず各ライブラリのバージョンを version catalog に直接ピン留めしている。
  BOM だと解決後のバージョンが catalog から見えず、API レベル要求の踏み抜き(compileSdk 37 要求など)を
  読み解くのが難しかったため。Renovate 側は `renovate.json` の packageRule で1PRにグルーピングしている。

## Notes

- License screen (AboutLibraries) is WIP — button and navigation are temporarily disabled
- `app/src/test/resources/robolectric.properties` で Robolectric のエミュレート SDK を 36 に固定している。
  targetSdk 37 に対して Robolectric 4.16.1 の同梱 SDK が 36 までのため。API 37 対応版が stable
  になったらこのファイルごと削除してよい。
- `ins.md` contains the Japanese development guide with phased architecture explanation