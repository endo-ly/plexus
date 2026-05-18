# Muxport Gateway

tmux session への WebSocket 接続、snapshot 取得、push 通知を提供する runtime API です。

## 機能

- **tmux セッション管理**: tmux セッションを列挙・管理
- **WebSocket 接続**: 端末入出力の双方向通信
- **Snapshot**: セッション内容のプレーンテキスト取得
- **プッシュ通知**: FCM 経由の通知送信
- **認証**: API Key と Webhook シークレットによる認証

## 開発

### 依存関係のインストール

```bash
cd /root/workspace/plexus
uv sync
```

### 環境変数

必須:

- `GATEWAY_API_KEY`: Gateway API Key（32バイト以上推奨）
- `GATEWAY_WEBHOOK_SECRET`: Webhook シークレット（32バイト以上推奨）
- `CORS_ORIGINS`: ブラウザアクセスを許可する Origin

プッシュ通知利用時:

- `FCM_PROJECT_ID`: Firebase プロジェクト ID
- `FCM_CREDENTIALS_PATH`: Firebase サービスアカウント JSON パス（省略時 `gateway/firebase-service-account.json`）

### サーバー起動

```bash
uv run --project gateway python -m gateway.main
```

`uvicorn gateway.main:app` を直接使うと `GATEWAY_HOST` / `GATEWAY_PORT` が反映されないため、
設定値で起動したい場合は `python -m gateway.main` を使用してください。

### systemd での自動起動

本番運用では systemd サービスとして登録推奨

```bash
# インストール・起動
sudo bash gateway/deploy/plexus install

# アップデート
sudo bash gateway/deploy/plexus update

# アンインストール
sudo bash gateway/deploy/plexus uninstall

# 状態確認
bash gateway/deploy/plexus status
```

詳細は [docs/40.deploy/gateway-systemd.md](../docs/40.deploy/gateway-systemd.md) を参照。

### テスト実行

```bash
cd gateway
uv run pytest tests -v
uv run pytest tests/unit -v
```

## API エンドポイント

### `GET /health`

ヘルスチェック

### `GET /api/v1/terminal/sessions`

tmux セッション一覧を取得

認証: X-API-Key 必須

### Terminal WebSocket トークン運用上の注意

- `terminal_ws_token_store` は in-memory 実装です。
- `POST /api/v1/terminal/sessions/{session_id}/ws-token` で発行したトークンは、同じプロセスでの `terminal_ws_token_store.consume` でのみ検証できます。
- マルチプロセス / マルチPod 構成では、sticky-session で同一インスタンスへ到達させるか、共有ストア実装（例: Redis）に置き換えてください。

## プロジェクト構成

```text
gateway/
├── api/              # API ルート
├── domain/           # ドメインモデル
├── infrastructure/   # インフラストラクチャ（DB、tmux）
├── services/         # websocket / push / token store
├── tests/            # テスト
├── config.py         # 設定管理
├── dependencies.py   # 依存関数（認証など）
└── main.py           # アプリケーションエントリーポイント
```
