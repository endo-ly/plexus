# Muxport 技術スタック

## 概要

Muxport はランタイム向けバックエンドと Android ターミナルクライアントを組み合わせた構成を取る。
コンポーネントごとの技術選定を整理する。

## コンポーネント別スタック

| コンポーネント | 主要技術 | 役割 |
| --- | --- | --- |
| `gateway/` | Python, Starlette, Uvicorn, WebSocket | tmux CLI（`subprocess.run` 経由）を API / WebSocket として公開する |
| `frontend/` | Kotlin Multiplatform, Compose Multiplatform, Voyager, Koin, Ktor | モバイルターミナルクライアントを実装する |
| ターミナル描画 | xterm.js, WebView | ターミナル画面を Android 上で描画する |
| プッシュ通知 | Firebase Cloud Messaging | ランタイム向けプッシュ通知を扱う |
| 実行基盤 | tmux | 実行継続とアタッチの基盤 |

##  Frontend（モバイル/Web アプリ）

- **Framework**: Kotlin Multiplatform + Compose Multiplatform
- **Language**: Kotlin 2.2.21
- **Mobile Runtime**: Native Android
- **UI System**: Material3 (Compose)
- **Navigation**: Voyager 1.1.0-beta03
- **State Management**: StateFlow + Channel (MVVM パターン)
- **DI**: Koin 4.0.0
- **HTTP Client**: Ktor 3.3.3
- **Terminal UI**: xterm.js (WebView), xterm-addon-fit
- **Push Notification**: Firebase Cloud Messaging (FCM)
- **音声入力**: Android SpeechRecognizer
- **Logging**: Kermit
- **テスト**: kotlin-test, Turbine, MockK, Ktor MockEngine
- **実行環境**: モバイル（Android）

##  Gateway（Terminal Gateway）

モバイル端末からの tmux セッション接続とプッシュ通知を担当する独立サービス。

- **Framework**: Starlette (Python 3.13)
- **Web Server**: Uvicorn (ASGI)
- **主要ライブラリ**:
  - `websockets`: WebSocket 通信（端末入出力）
  - `firebase-admin`: FCM プッシュ通知
  - `sqlite3`: プッシュトークン永続化
  - `pydantic`: データモデル定義
  - `pydantic-settings`: 環境変数管理
- **認証方式**: Bearer Token（環境変数照合）
- **実行環境**: LXC（常駐サーバー）
- **特性**:
  - tmux セッションの列挙・接続管理
  - WebSocket による双方向端末入出力
  - FCM によるタスク完了/入力要求通知
  - EgoGraph Backend からは独立したサービス

詳細: [Terminal Gateway 要件定義](../../00.requirements/mobile_terminal_gateway.md)

---
## 設計メモ

- tmux は単なるターミナルバックエンドではなく、実行基盤として扱う
- フロントエンドとゲートウェイは同じランタイムを別視点で扱うため、通信と UI を分離する
- xterm.js はターミナル描画に限定し、ランタイム状態はゲートウェイ API 経由で取得する

## 関連ドキュメント

- [システムアーキテクチャ](./system-architecture.md)
- [フロントエンドアーキテクチャ](../02-frontend/architecture.md)
- [ゲートウェイアーキテクチャ](../03-gateway/architecture.md)
