# ゲートウェイアーキテクチャ

## 概要

Muxport ゲートウェイは tmux を中心としたランタイムをフロントエンドや外部呼び出し元から扱うためのランタイム API 層。
HTTP API、WebSocket ターミナル、プッシュ webhook を 1 つの Starlette アプリケーションとしてまとめ、tmux セッションへのアタッチとモバイルクライアント向け通信を提供する。

## 責務

- tmux セッション一覧 / 詳細の提供
- ターミナルスナップショットの取得
- セッションごとの WebSocket トークン発行
- ターミナル WebSocket の認証と双方向 I/O 仲介
- FCM トークン登録と webhook 経由プッシュ通知
- Tailnet / localhost 前提の接続制御

## ランタイム構成

```text
┌──────────────────────────────────────────────────────────────┐
│ Clients                                                      │
│ - Muxport frontend                                            │
│ - webhook sender                                             │
└──────────────────────────────┬───────────────────────────────┘
                               │ HTTP / WebSocket
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ Starlette App                                                │
│ - /health                                                    │
│ - /api/v1/terminal/*                                         │
│ - /v1/push/*                                                 │
│ - /api/ws/terminal                                           │
└──────────────────────────────┬───────────────────────────────┘
                               │
               ┌───────────────┼────────────────┐
               ▼               ▼                ▼
┌─────────────────────┐ ┌────────────────┐ ┌────────────────────┐
│ infrastructure/tmux │ │ services/*     │ │ infrastructure/*   │
│ - list sessions     │ │ - websocket    │ │ - database         │
│ - session exists    │ │ - PTY manager  │ │ - repositories     │
│ - pane metadata     │ │ - ws token     │ │ - auth helpers     │
└─────────────────────┘ │ - FCM service  │ └────────────────────┘
                        └────────────────┘
```

## モジュール構成

```text
gateway/
├── api/              # HTTP / WebSocket ルート定義
├── domain/           # リクエスト / レスポンス / WebSocket メッセージモデル
├── infrastructure/   # tmux、DB、リポジトリ、認証関連の詳細実装
├── services/         # PTY アタッチ、WebSocket セッション処理、FCM、ws トークンストア
├── config.py         # 環境変数ベースの設定
├── dependencies.py   # 設定読み込みとリクエスト認証
└── main.py           # Starlette アプリケーションの組み立て
```

## 各層の責務

### main.py

アプリケーションの組み立て。

- ミドルウェア設定
- ルートマウント
- データベース初期化
- uvicorn 起動

### api/

外部に公開する通信インターフェースを定義する。

- **`terminal.py`**: セッション一覧 / 詳細 / ws トークン / スナップショット / ターミナル WebSocket
- **`push.py`**: FCM トークン登録、webhook ベースプッシュ

API 層はリクエスト検証とレスポンス整形を行う。tmux アタッチの本体処理は service / infrastructure に委譲する。

### services/

実行中セッションとの I/O 仲介やステートフル処理。

- **`pty_manager.py`**: tmux アタッチ、読み書き、リサイズ、スクロール、スナップショット
- **`websocket_handler.py`**: クライアントメッセージと PTY の橋渡し
- **`ws_token_store.py`**: 一回限り・短命な WebSocket トークン
- **`fcm_service.py`**: webhook ペイロードからプッシュ通知送信

### infrastructure/

OS や外部システムに近い責務を閉じ込める。

- **`tmux.py`**: tmux セッションの列挙、存在確認、ペインメタデータ取得
- **`database.py`**: ゲートウェイ用永続化初期化
- **`repositories.py`**: プッシュトークンなどの保存
- **`auth.py`**: 認証に関わる低レベル処理

### domain/

API リクエスト / レスポンスと WebSocket メッセージスキーマを Pydantic モデルとして定義する。

## リクエストフロー

### ターミナル HTTP API

1. リクエストがミドルウェアを通過する
2. `verify_gateway_token` が `X-API-Key` を検証する
3. `api/terminal.py` がセッション ID などを検証する
4. `infrastructure.tmux` または `services.pty_manager` を呼ぶ
5. ドメインモデルに沿ってレスポンスを返す

### ターミナル WebSocket

1. `/api/ws/terminal` に接続する
2. クライアント IP / Host / Origin を検証する
3. `auth` メッセージで ws トークンを受け取る
4. `ws_token_store.consume` で一回限り認証する
5. `TerminalWebSocketHandler` が PTY アタッチと双方向通信を開始する

### プッシュ Webhook

1. `/v1/push/webhook` に webhook リクエストが届く
2. `X-Webhook-Secret` を検証する
3. ペイロードを検証する
4. `FcmService` が登録済みトークンに通知送信する

## セキュリティ

ゲートウェイはモバイルからアクセス可能だが、無制限公開の API ではない。

- HTTP リクエストは Tailnet / localhost 起点を前提に制限する
- Host / Origin を検証する
- HTTP API は `X-API-Key` による認証
- webhook は `X-Webhook-Secret` による認証
- WebSocket は短命な ws トークンを追加で要求する

ターミナルアクセスの通信経路を確保しつつ、tmux セッションへの直接接続は露出しない構成。

## 設計メモ

- ws トークンストアは現状インメモリ実装（単一プロセス前提）
- フロントエンド向けターミナル UX とランタイム API を分離し、将来のオーケストレーション基盤の追加を容易にする
- tmux セッションはゲートウェイが扱うランタイムオブジェクトであり、単なるシェルプロキシではない

## 関連ドキュメント

- [システムアーキテクチャ](../01-overview/system-architecture.md)
- [技術スタック](../01-overview/tech-stack.md)
- [フロントエンドアーキテクチャ](../02-frontend/architecture.md)
