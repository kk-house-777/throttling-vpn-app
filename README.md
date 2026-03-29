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
- Go 1.20+ (`go version`)
- gomobile (`go install golang.org/x/mobile/cmd/gomobile@latest`)
- Android NDK (via SDK Manager)

### Build the Go library

```bash
cd go/tun2sockslib
go get golang.org/x/mobile/bind
ANDROID_NDK_HOME=/path/to/ndk gomobile bind -target=android -androidapi 26 -o ../../app/libs/tun2socks.aar .
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

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- Navigation 3, DataStore Preferences
- Go, gomobile bind, gVisor TCP/IP stack
- xjasonlyu/tun2socks v2, golang.org/x/time/rate

## How It Works

See [`docs/`](docs/) for detailed documentation:
- [Phase 2 Forwarding Plan](docs/plan-phase2-forwarding.md) — tun2socks integration architecture
- [gomobile Setup Guide](docs/gomobile-setup-guide.md) — Go ↔ Android binding setup
