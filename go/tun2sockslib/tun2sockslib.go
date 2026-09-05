// Package tun2sockslib wraps xjasonlyu/tun2socks v2 engine for Android VpnService.
//
// Throttling (rate limiting) is inserted between the TUN interface and tun2socks.
//
//	TUN fd <-> [throttle layer] <-> socketpair <-> tun2socks engine
//
// This wrapper is compiled to .aar via gomobile bind and called from Kotlin.
package tun2sockslib

import (
	"context"
	"fmt"
	"math"
	"os"
	"sync"
	"syscall"
	"time"

	"golang.org/x/time/rate"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

// This is based on https://docs.aws.amazon.com/ja_jp/AWSEC2/latest/UserGuide/network_mtu.html
const mtu = 1500

var (
	relayCancel context.CancelFunc
	relayWg     sync.WaitGroup
	tunFile     *os.File
	proxyFile   *os.File
	mu          sync.Mutex
)

// Start receives a TUN fd and begins packet forwarding with rate limiting.
//
// fd: integer obtained from Android VpnService's ParcelFileDescriptor.detachFd()
// speedKbps: rate limit (KB/s). -1 = Block, 0 = Unlimited, >0 = Throttle
//
// Internal flow:
//  1. Create a Unix socketpair (bidirectional pipe)
//  2. Pass one end (engineFd) to tun2socks
//  3. Relay packets between the other end (proxyFd) and the TUN fd
//  4. Apply token bucket rate limiting during relay
func Start(fd int, speedKbps int) {
	mu.Lock()
	defer mu.Unlock()

	fds, err := syscall.Socketpair(syscall.AF_UNIX, syscall.SOCK_DGRAM, 0)
	if err != nil {
		fmt.Fprintf(os.Stderr, "socketpair failed: %v\n", err)
		return
	}
	proxyFd := fds[0]  // used by throttle layer
	engineFd := fds[1] // passed to tun2socks engine

	key := &engine.Key{
		Device: fmt.Sprintf("fd://%d", engineFd),
		Proxy:  "direct://",
		MTU:    mtu,
	}
	engine.Insert(key)
	engine.Start()

	// os.NewFile only registers a fd with Go's runtime poller (required for
	// SetDeadline to actually interrupt a blocked Read/Write, and for a
	// concurrent Close to unblock one safely) if the fd is already
	// non-blocking. Without this, stopRelay's SetDeadline call is silently a
	// no-op, relayWg.Wait() never returns, and Stop() hangs the calling
	// thread forever (ANR).
	_ = syscall.SetNonblock(fd, true)
	_ = syscall.SetNonblock(proxyFd, true)

	tunFile = os.NewFile(uintptr(fd), "tun")
	proxyFile = os.NewFile(uintptr(proxyFd), "proxy")

	// Start throttle goroutines
	startRelay(speedKbps)
}

// SetSpeed dynamically changes the rate limit. No VPN restart required.
//
// speedKbps: -1 = Block, 0 = Unlimited, >0 = Throttle (KB/s)
//
// Implementation: cancels existing throttle goroutines and restarts with new limiters.
// Simply calling SetLimit() on rate.Limiter is insufficient because WaitN() may be
// blocked, so the goroutines must be restarted entirely.
func SetSpeed(speedKbps int) {
	mu.Lock()
	defer mu.Unlock()

	if tunFile == nil || proxyFile == nil {
		return
	}

	stopRelay()
	startRelay(speedKbps)
}

// Stop halts packet forwarding and releases resources.
// Closing tunFile (dup'd TUN fd) causes the OS to release the VPN interface.
func Stop() {
	mu.Lock()
	defer mu.Unlock()

	stopRelay()
	engine.Stop()
	// Close the dup'd fd. This lets the OS recognize there are no more references
	// to the TUN, fully closing the VPN interface and removing the key icon from the status bar.
	if tunFile != nil {
		tunFile.Close()
		tunFile = nil
	}
	if proxyFile != nil {
		proxyFile.Close()
		proxyFile = nil
	}
}

// stopRelay cancels the running relay goroutines and waits for them to fully
// exit before returning. Must be called with mu held.
//
// ctx cancellation alone does not interrupt a goroutine parked in a blocking
// Read/Write syscall, so a past version of this code closed tunFile/proxyFile
// immediately after cancelling — while a relay goroutine could still be
// mid-syscall on that same fd. Once closed, the fd number can be reassigned
// to something else in the process, and the still-running goroutine's syscall
// completing afterward corrupts fd ownership tracking, which crashes the app
// with a SIGABRT from fdsan ("fd is owned by unique_fd, was expected to be
// unowned"). Setting an immediate deadline unblocks the syscalls without
// closing the file, so relayWg.Wait() can complete before anything is closed.
func stopRelay() {
	if relayCancel == nil {
		return
	}
	relayCancel()
	relayCancel = nil
	if tunFile != nil {
		tunFile.SetDeadline(time.Now())
	}
	if proxyFile != nil {
		proxyFile.SetDeadline(time.Now())
	}
	relayWg.Wait()
}

// startRelay launches throttle goroutines. Must be called with mu held.
//
// speedKbps: -1 = Block, 0 = Unlimited, >0 = Throttle
func startRelay(speedKbps int) {
	ul, dl := makeLimiters(speedKbps)

	ctx, cancel := context.WithCancel(context.Background())
	relayCancel = cancel

	relayWg.Add(2)

	// Upload: TUN -> (throttle) -> socketpair -> tun2socks -> internet
	go func() {
		defer relayWg.Done()
		throttleRelay(ctx, tunFile, proxyFile, ul)
	}()

	// Download: internet -> tun2socks -> socketpair -> (throttle) -> TUN
	go func() {
		defer relayWg.Done()
		throttleRelay(ctx, proxyFile, tunFile, dl)
	}()
}

// makeLimiters creates a pair of token bucket limiters based on the speed setting.
//
// Token bucket algorithm:
//   - Refill (speedKbps * 1024) bytes worth of tokens per second
//   - Consume tokens equal to packet size on each send
//   - WaitN blocks until tokens are available (= rate limiting)
func makeLimiters(speedKbps int) (upload, download *rate.Limiter) {
	switch {
	case speedKbps < 0:
		// Block: rate 0 -> WaitN blocks forever -> traffic blocked
		upload = rate.NewLimiter(0, 0)
		download = rate.NewLimiter(0, 0)
	case speedKbps > 0:
		// Throttle: limit to specified speed
		bytesPerSec := rate.Limit(float64(speedKbps) * 1024)
		upload = rate.NewLimiter(bytesPerSec, mtu*10)
		download = rate.NewLimiter(bytesPerSec, mtu*10)
	default:
		// Unlimited: very large rate, effectively no limit
		upload = rate.NewLimiter(rate.Limit(math.MaxFloat64), math.MaxInt)
		download = rate.NewLimiter(rate.Limit(math.MaxFloat64), math.MaxInt)
	}
	return
}

// throttleRelay relays packets from src to dst, applying rate limiting via limiter.
//
// Each packet read consumes tokens equal to its size from the limiter.
// If tokens are insufficient, WaitN() blocks until refilled (= rate limiting).
// When the context is cancelled, WaitN returns immediately and the goroutine exits.
func throttleRelay(ctx context.Context, src, dst *os.File, limiter *rate.Limiter) {
	buf := make([]byte, mtu)
	for {
		// 停止チェック
		select {
		case <-ctx.Done():
			return
		default:
		}

		// OSからパケットの実態を1500バイト読み取る。fd経由, nはサイズ
		n, err := src.Read(buf)
		if err != nil {
			if ctx.Err() != nil {
				return // normal shutdown
			}
			continue
		}
		if n == 0 {
			continue
		}

		// Token bucket rate limiting.
		// WaitN blocks until enough tokens are available.
		// Returns immediately with error if context is cancelled.
		//「nバイト分のトークンが貯まるまで待つ」。例えば 100 KB/s 制限で 1500
		// バイトのパケットなら約 15ms 待たされる。
		if err := limiter.WaitN(ctx, n); err != nil {
			return // context cancelled -> goroutine exit
		}

		_, err = dst.Write(buf[:n])
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			continue
		}
	}
}
