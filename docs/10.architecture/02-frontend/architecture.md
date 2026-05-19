# フロントエンドアーキテクチャ

## 概要

Muxport フロントエンドは Android 向けターミナルクライアント。
tmux を中心としたランタイムにモバイルから接続するための UI 層。

EgoGraph 由来の共通実装を土台にするが、Muxport ではチャット中心アプリではなくターミナル中心アプリとして責務を整理している。

**対象範囲**

- `frontend/shared/src/commonMain/kotlin/dev/plexus/shared/`
- セッション一覧 / ターミナルセッション / ゲートウェイ設定 / システムプロンプトエディタ
- Android 固有の描画補助や WebView bridge と連携する共有フロントエンド設計

## アーキテクチャスタイル

`Screen → ScreenModel → Repository` の MVVM 構成。
UI 状態は `StateFlow`、単発イベントは `Channel` で扱う。

```text
┌──────────────────────────────────────────────────────────┐
│ Presentation                                             │
│ - Screen (Compose UI)                                    │
│ - Sidebar navigation / transitions                       │
└──────────────────────────┬───────────────────────────────┘
                           │ collectAsState / effect collect
                           ▼
┌──────────────────────────────────────────────────────────┐
│ ScreenModel                                              │
│ - AgentListScreenModel                                   │
│ - GatewaySettingsScreenModel                             │
│ - SystemPromptEditorScreenModel                          │
└──────────────────────────┬───────────────────────────────┘
                           │ repository call
                           ▼
┌──────────────────────────────────────────────────────────┐
│ Domain / Data                                            │
│ - TerminalRepository                                     │
│ - SystemPromptRepository                                 │
│ - ThemeRepository                                        │
│ - RepositoryClient                                       │
└──────────────────────────────────────────────────────────┘
```

## 各層の責務

| 層 | 責務 |
| --- | --- |
| Screen | Compose UI、ユーザー操作受付、エフェクト消費 |
| ScreenModel | 状態更新、読み込み制御、設定保存、リポジトリ呼び出し |
| Repository | ゲートウェイ API / バックエンド API 通信、簡易キャッシュ、データ取得 |

## パッケージ構成

```text
frontend/shared/src/commonMain/kotlin/dev/plexus/shared/
├── core/
│   ├── data/
│   │   └── repository/
│   ├── domain/
│   │   ├── model/
│   │   └── repository/
│   ├── network/
│   ├── platform/
│   ├── settings/
│   └── ui/
├── di/
└── features/
    ├── navigation/
    ├── sidebar/
    ├── systemprompt/
    └── terminal/
        ├── agentlist/
        ├── session/
        └── settings/
```

## ナビゲーションモデル

起点は `SidebarScreen`。`MainView` でメインコンテンツを切り替える。

| MainView | 責務 |
| --- | --- |
| `Terminal` | セッション一覧の表示 |
| `TerminalSession` | 選択済みセッションのターミナル表示 |
| `GatewaySettings` | ゲートウェイ URL / API キー / テーマ設定 |
| `SystemPrompt` | システムプロンプトの編集 |

`Terminal` と `TerminalSession` はスワイプナビゲーションの対象。モバイルでのターミナル利用に必要な画面往復を短く保つ。

## 状態管理

状態管理は `StateFlow + Channel` を基本とする。

### State

- 継続する UI 状態を `data class` で表現する
- `collectAsState()` で Compose UI から監視する
- ScreenModel が `_state.update { ... }` で変更する

該当例:

- `AgentListState`
- `GatewaySettingsState`
- `SystemPromptEditorState`

### Effect

- Snackbar や保存完了通知など、一度だけ消費するイベント
- `Channel` で配信し、UI 側で受け取る

該当例:

- `AgentListEffect`
- `GatewaySettingsEffect`
- `SystemPromptEditorEffect`

## 依存性注入

DI には Koin を用いる。

- **`appModule`**: 共通 HTTP クライアント、バックエンドクライアント、テーマ / システムプロンプト関連
- **`terminalModule`**: ゲートウェイクライアントと `TerminalRepository`

バックエンド API とゲートウェイ API を分けて扱うため、DI でも `BackendClient` と `GatewayClient` を分離している。

## ターミナル固有設計

ターミナル機能は `features/terminal/` に集約する。

### agentlist

- セッション一覧取得
- リフレッシュ
- セッション選択
- ゲートウェイ設定への導線

### session

- WebView + xterm.js によるターミナル描画
- WebSocket トークンを使った接続開始
- 再接続バックオフ
- 特殊キー補助
- コピーモード / スナップショット表示
- 音声入力コーディネータ連携

### settings

- ゲートウェイ URL / API キーの保存
- テーマ設定
- 最終セッションの保持に関わる設定管理

## リポジトリ境界

`TerminalRepository` はフロントエンドがランタイム API を扱う入口。

- セッション一覧の取得
- セッション詳細の取得
- WebSocket トークン発行
- ターミナルスナップショットの取得

通信やエンドポイントの詳細はリポジトリ内に閉じ込め、ScreenModel と Screen はターミナル操作のユースケースだけを扱う。

## 設計意図

Muxport フロントエンドはターミナルアプリとして成立する最小境界を保つ。
EgoGraph 由来の実装要素が残っていても、責務は次のように読み替える。

- ターミナルアクセスに直接関係しない機能は中心に置かない
- ランタイム接続設定とターミナル UX をフロントエンドの主責務とする
- オーケストレーション追加時もターミナル機能を独立した機能群として維持する

## 関連ドキュメント

- [システムアーキテクチャ](../01-overview/system-architecture.md)
- [技術スタック](../01-overview/tech-stack.md)
- [ターミナル機能設計](./terminal.md)
