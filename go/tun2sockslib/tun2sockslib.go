// Package tun2sockslib wraps xjasonlyu/tun2socks v2 engine for Android VpnService.
//
// tun2socks は TUN インターフェースから読んだ IP パケット (L3) をTCP/UDP ソケット (L4) に変換してインターネットに転送するライブラリ。
//
// スロットリング（速度制限）は TUN と tun2socks の間に挿入する。
//	TUN fd ←→ [スロットルレイヤー] ←→ socketpair ←→ tun2socks engine
//
// このラッパーは gomobile bind で .aar にコンパイルされ、Kotlin から呼び出される。
package tun2sockslib

import (
	"context"
	"fmt"
	"math"
	"os"
	"sync"
	"syscall"

	"golang.org/x/time/rate"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

const mtu = 1500

var (
	relayCancel context.CancelFunc
	tunFile   *os.File
	proxyFile *os.File
	mu        sync.Mutex
)

// Start は TUN fd を受け取り、速度制限付きでパケット転送を開始する。
//
// fd: Android VpnService の ParcelFileDescriptor.detachFd() で取得した整数値
// speedKbps: 速度制限 (KB/s)。-1 = Block, 0 = Unlimited, >0 = 速度制限
//
// 内部動作:
//  1. Unix socketpair を作成（双方向パイプ）
//  2. socketpair の片端 (engineFd) を tun2socks に渡す
//  3. もう片端 (proxyFd) と TUN fd の間でパケットを中継
//  4. 中継時にトークンバケットで速度制限を適用
func Start(fd int, speedKbps int) {
	mu.Lock()
	defer mu.Unlock()

	// Unix socketpair を作成
	// SOCK_DGRAM を使うことでパケット境界が保持される（IP パケット単位で送受信）
	fds, err := syscall.Socketpair(syscall.AF_UNIX, syscall.SOCK_DGRAM, 0)
	if err != nil {
		fmt.Fprintf(os.Stderr, "socketpair failed: %v\n", err)
		return
	}
	proxyFd := fds[0] // スロットルレイヤーが使う側
	engineFd := fds[1] // tun2socks engine に渡す側

	// tun2socks engine を socketpair の片端で起動
	key := &engine.Key{
		Device: fmt.Sprintf("fd://%d", engineFd),
		Proxy:  "direct://",
		MTU:    mtu,
	}
	engine.Insert(key)
	engine.Start()

	// File ハンドルを保持（SetSpeed で再利用）
	tunFile = os.NewFile(uintptr(fd), "tun")
	proxyFile = os.NewFile(uintptr(proxyFd), "proxy")

	// スロットルゴルーチンを起動
	startRelay(speedKbps)
}

// SetSpeed は動的に速度制限を変更する。VPN の再起動は不要。
//
// speedKbps: -1 = Block, 0 = Unlimited, >0 = 速度制限 (KB/s)
//
// 実装: 既存のスロットルゴルーチンを cancel して新しい limiter で再起動する。
// rate.Limiter.WaitN() がブロック中だと SetLimit() だけでは起きないため、
// ゴルーチンごと再起動する必要がある。
func SetSpeed(speedKbps int) {
	mu.Lock()
	defer mu.Unlock()

	if tunFile == nil || proxyFile == nil {
		return
	}

	// 既存のゴルーチンを停止
	if relayCancel != nil {
		relayCancel()
	}

	// 新しい limiter でゴルーチンを再起動
	startRelay(speedKbps)
}

// Stop はパケット転送を停止し、リソースを解放する。
// tunFile (dup した TUN fd) を close することで、OS が VPN インターフェースを解放する。
func Stop() {
	mu.Lock()
	defer mu.Unlock()

	if relayCancel != nil {
		relayCancel()
		relayCancel = nil
	}
	engine.Stop()
	// dup した fd を閉じる。これにより OS が TUN への参照がなくなったことを認識し、
	// VPN インターフェースが完全に閉じてステータスバーの鍵アイコンが消える。
	if tunFile != nil {
		tunFile.Close()
		tunFile = nil
	}
	if proxyFile != nil {
		proxyFile.Close()
		proxyFile = nil
	}
}

// startRelay はスロットルゴルーチンを起動する。mu を保持した状態で呼ぶこと。
//
// speedKbps: -1 = Block, 0 = Unlimited, >0 = 速度制限
func startRelay(speedKbps int) {
	ul, dl := makeLimiters(speedKbps)

	ctx, cancel := context.WithCancel(context.Background())
	relayCancel = cancel

	// アップロード方向: TUN → (スロットル) → socketpair → tun2socks → internet
	go throttleRelay(ctx, tunFile, proxyFile, ul)

	// ダウンロード方向: internet → tun2socks → socketpair → (スロットル) → TUN
	go throttleRelay(ctx, proxyFile, tunFile, dl)
}

// makeLimiters は速度設定に応じたトークンバケットペアを作成する。
//
// トークンバケットアルゴリズム:
//   - バケットに毎秒 (speedKbps * 1024) バイト分のトークンを補充
//   - パケット送信時にサイズ分のトークンを消費
//   - トークン不足なら補充されるまで WaitN がブロック（= 速度制限）
func makeLimiters(speedKbps int) (upload, download *rate.Limiter) {
	switch {
	case speedKbps < 0:
		// Block: レート0 → WaitN が永久にブロック → 通信遮断
		upload = rate.NewLimiter(0, 0)
		download = rate.NewLimiter(0, 0)
	case speedKbps > 0:
		// Throttle: 指定速度で制限
		bytesPerSec := rate.Limit(float64(speedKbps) * 1024)
		upload = rate.NewLimiter(bytesPerSec, mtu*10)
		download = rate.NewLimiter(bytesPerSec, mtu*10)
	default:
		// Unlimited: 非常に大きなレートで実質無制限
		upload = rate.NewLimiter(rate.Limit(math.MaxFloat64), math.MaxInt)
		download = rate.NewLimiter(rate.Limit(math.MaxFloat64), math.MaxInt)
	}
	return
}

// throttleRelay は src から dst へパケットを中継し、limiter で速度制限する。
//
// パケットを1つ読むたびに、そのサイズ分のトークンを消費する。
// トークンが不足していると WaitN() がブロックし、速度が制限される。
// context がキャンセルされると WaitN が即座に返り、ゴルーチンが終了する。
func throttleRelay(ctx context.Context, src, dst *os.File, limiter *rate.Limiter) {
	buf := make([]byte, mtu)
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		n, err := src.Read(buf)
		if err != nil {
			if ctx.Err() != nil {
				return // 正常停止
			}
			continue
		}
		if n == 0 {
			continue
		}

		// トークンバケットで速度制限
		// WaitN はトークンが補充されるまでブロックする。
		// context がキャンセルされると即座にエラーを返す。
		if err := limiter.WaitN(ctx, n); err != nil {
			return // context cancelled → ゴルーチン終了
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
