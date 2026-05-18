# プロジェクト概要

## 1. Muxportとは

**Muxport** は、tmux + WebSocket + モバイルの便利インフラです。

tmux セッションをモバイルからアクセスできるようにし、ターミナルサーフェスを通じて人間が実行中の tmux セッションに接続できるようにします。

---

## 2. プロジェクトの目的

### 2.1 ビジョン

**「tmux にモバイルからアクセスできる便利インフラを作る」**

- tmux を実行環境の中心に据える
- モバイルからのターミナルアクセスを主なインターフェースとする
- プッシュ通知で離席中も繋がり続ける

### 2.2 解決する課題

| 課題 | Muxportによる解決 |
| --- | --- |
| **実行環境が脆い** | tmux session を中心に、再接続可能で継続性のある実行環境を作る |
| **端末アクセスが desktop 前提** | モバイルからも tmux session に接続できるターミナルサーフェスを提供する |
| **セッションの状態がモバイルから見えない** | スナップショットとプッシュ通知で常に状況を把握できる |

---

## 3. 基本方針 (Design Philosophy)

### 3.1 tmux-Centered

Muxport は、tmux を単なる terminal multiplexer ではなく、実行の中核として扱います。

session はただの画面ではなく、

- 実行の継続点
- attach 可能な接続先
- 観測・管理の対象

として機能します。

### 3.2 Terminal Infrastructure

Muxport はインフラツールであり、オーケストレーションではありません。提供するのは:

- **Terminal Surface**: session list, websocket terminal, snapshot, mobile access
- **Session Management**: tmux セッションの作成・閲覧・管理
- **Push Notifications**: セッションイベントの通知

これらは tmux セッション内で動くものに対する基盤として機能します。

---

## 4. システム像

Muxport は大きく次の要素で構成されます。

- **tmux session**: 実行の中心
- **gateway**: モバイルクライアントと tmux を繋ぐ API 層
- **frontend**: モバイルターミナルクライアント

---

## 5. 名前の由来

**Muxport** は **mux**（multiplexer）と **port**（港・入り口）の組み合わせです。

- **mux** は tmux を直接指す
- **port** は入り口・港、つまりモバイルと tmux セッションの間のゲートウェイを表す
- ふたつ合わせて「tmux マルチプレクサへの港」＝ツールの機能そのものを表す

短く、打ちやすく、覚えやすい名前です。
