# Muxport 開発ガイドライン

## 概要

Muxport は、tmux + WebSocket + モバイルの便利インフラです。

構成:

- `gateway/`: Python / Starlette ベースの runtime API
- `frontend/`: Kotlin Multiplatform / Compose Multiplatform ベースの Android terminal client
- `maestro/`: terminal UI の E2E フロー
- `docs/`: terminal / FCM / webhook / deploy などの設計と運用知識

## アーキテクチャ

### gateway

Layered Architecture - Starlette ベースの軽量 API

- Terminal Surface: tmux session list / snapshot / websocket terminal / push
- Runtime Control: 認証、永続化、push token 管理
- Tech Stack: Python 3.12+, Starlette, Uvicorn, SQLite, WebSocket, libtmux

### frontend

MVVM (StateFlow + Channel) - Kotlin Multiplatform + Compose Multiplatform

- `core/domain/`: terminal model / repository interface
- `core/network/`: HTTP クライアント (Ktor)
- `core/platform/`: WebView, permissions, keyboard, preferences などの platform abstraction
- `features/terminal/`: session list / terminal session / gateway settings
- `di/`: Koin による依存性注入

| レイヤー | 役割 | 例 |
| --- | --- | --- |
| Screen | Compose UI 表示 | `TerminalScreen.kt` |
| ScreenModel | ビジネスロジック・状態更新 | `AgentListScreenModel.kt` |
| State | UI 状態 | `AgentListState.kt` |
| Effect | One-shot イベント | `AgentListEffect.kt` |

### runtime model

Muxport は tmux を runtime の中核として扱います。

- `tmux`: 実行 session の中心
- モバイルからの安全なアクセス（Tailscale + Bearer token）

## 開発コマンド

```bash
# === Gateway 起動（systemd サービス） ===
sudo bash gateway/deploy/plexus install          # 初回: インストール・起動・自動起動有効化
sudo bash gateway/deploy/plexus uninstall        # 削除
bash gateway/deploy/plexus status                # 状態確認
sudo journalctl -u plexus-gateway -f                 # ログ追跡
# 詳細: docs/40.deploy/gateway-systemd.md

# === Gateway 起動（手動・開発用） ===
tmux new-session -d -s 'uv run --project gateway python -m gateway.main'

# === Gateway テスト・リント ===
cd gateway
uv run pytest tests -v
uv run pytest tests/unit -v
uv run ruff check .
uv run ruff check . --fix
uv run ruff format .

# === Frontend ===
cd frontend
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
./gradlew :shared:testDebugUnitTest
./gradlew :shared:koverHtmlReportDebug
./gradlew ktlintFormat
./gradlew ktlintCheck
./gradlew detekt

# === E2E Test (Maestro) ===
maestro test maestro/flows/
```

## 規約

### 基本原則

- 長期的な保守性、コードの美しさ、堅牢性を優先する
- KISS / YAGNI / DRY を守る
- terminal surface と周辺機能（ファイルブラウザ等）の責務を混ぜない
- tmux / WebSocket / モバイルの役割を曖昧にしない
- 場当たり的なフォールバックで複雑さを増やさない

### 実装ルール

| 項目 | ルール |
| --- | --- |
| SQL | プレースホルダ必須: `execute(query, (param,))` |
| Logging | 遅延評価 `logger.info("k=%s", v)`、機密情報禁止 |
| APIエラー | 統一フォーマット `invalid_<field>: <reason>` |
| Docstring / KDoc | 日本語 |
| テスト | AAA パターンを基本とする |

### Git / CI

- GitHub Flow を基本とする
- コミットは Conventional Commits（英語）
- ワークフローは Muxport の責務に関係するものだけ残す

## デバッグ

### スキル選択

| シナリオ | 使用スキル | 説明 |
| --- | --- | --- |
| Gateway API の確認 | `tmux-api-debug` | runtime API の再現・ログ確認 |
| Android 実機 / エミュレータ確認 | `android-adb-debug` | frontend の挙動確認、インストール、UI確認 |

### Android / ADB 構成

```text
Linux ─ Gateway (tmux) + ADB Client
    ↓ Tailscale:100.x.x.x:5559
Windows ─ netsh (0.0.0.0:5559→127.0.0.1:5555) ─ Android Emulator (:5555)
```

※ 5559 を外部公開する理由: エミュレータの :5555 とのポート競合回避

### Frontend～Gateway 間の検証方法

1. Windows 側でエミュを起動、またはデバッグ用実機で adb 待ち受け
2. Linux から `adb connect <WINDOWS_OR_ANDROID_IP>:PORT` で接続
3. Gateway を起動
4. adb コマンドで現在挙動を確認しながら実装
5. frontend をビルドしてインストール
6. adb コマンドでビルド内容を確認

## その他

- コード変更後はテスト確認必須
- コードレビューで一つも指摘されないレベルの品質を目指す
- 後方互換のためだけの複雑化は禁止
- `.env` 系を読むことは禁止
- Firebase / FCM / webhook などの secrets 実体は repo に置かない
