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

**Prebuilt AAR:** `app/libs/tun2socks.aar` is checked in. Only rebuild when Go code changes.

## Tech Stack

- Kotlin 2.3.20, Compose BOM 2026.03.01, Material 3, Navigation 3
- Target/Compile SDK 36, Min SDK 26, Java 11
- Go 1.25.0 + gomobile (tun2socks v2.6.0, gVisor TCP/IP stack)
- Gradle 9.1.0, version catalog at `gradle/libs.versions.toml`

## Notes

- License screen (AboutLibraries) is WIP — button and navigation are temporarily disabled
- `ins.md` contains the Japanese development guide with phased architecture explanation