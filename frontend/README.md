# Muxport Android Terminal App

**Kotlin Multiplatform + Compose Multiplatform** のネイティブ Android ターミナルクライアントです。

## 概要

Muxport Gateway に接続して tmux terminal へアクセスするための Android アプリです。
tmux セッションへのモバイルアクセスを提供します。

- **Native Android**: Compose Multiplatform によるネイティブ UI
- **MVVM**: 状態管理
- **Terminal Access**: セッション一覧、接続、snapshot、特殊キー入力
- **Push-ready**: runtime 通知との統合を前提にした構成

## アーキテクチャ

- **Framework**: Kotlin 2.3 + Compose Multiplatform
- **Architecture**: MVVM
- **State Management**: StateFlow + Channel ( Kotlin Coroutines )
- **Navigation**: Voyager
- **HTTP Client**: Ktor 3.4.0
- **DI**: Koin 4.0.0
- **Persistence**: Android SharedPreferences (expect/actual)

### プロジェクト構成

```text
frontend/
├── shared/                 # Kotlin Multiplatform モジュール
│   ├── src/commonMain/     # プラットフォーム共通コード
│   │   ├── core/           # コア機能（domain, platform, settings, ui, network）
│   │   │   ├── domain/         # DTOs, Repository インターフェース
│   │   │   │   ├── model/       # データモデル
│   │   │   │   └── repository/  # Repository インターフェース
│   │   │   ├── platform/        # プラットフォーム抽象化
│   │   │   ├── settings/        # 設定
│   │   │   ├── ui/              # 共通UIコンポーネント
│   │   │   └── network/         # HTTPクライアント
│   │   ├── features/       # 機能モジュール（MVVM）
│   │   │   ├── terminal/        # セッション一覧 / ターミナル接続 / 設定
│   │   │   ├── navigation/      # ナビゲーション
│   │   │   └── sidebar/         # 画面遷移コンテナ
│   │   └── di/             # 依存性注入モジュール
│   ├── src/androidMain/    # Android 固有実装
│   └── src/commonTest/     # 共通テスト
└── androidApp/             # Android アプリエントリポイント
    └── src/main/           # AndroidManifest, MainActivity
```

### 画面構成（Screen + ScreenModel + State + Effect）

| レイヤー        | 役割                       | ファイル例 |
| --------------- | -------------------------- | ---------- |
| **Screen**      | Compose UI 表示            | `TerminalScreen.kt` |
| **ScreenModel** | ビジネスロジック・状態更新 | `AgentListScreenModel.kt` |
| **State**       | UI状態（データクラス）     | `AgentListState.kt` |
| **Effect**      | One-shotイベント           | `AgentListEffect.kt` |

## ビルド要件

### 必須ツール

- **JDK**: 17 以上（推奨: JDK 21）
- **Android SDK**: API 34 以上
- **Gradle**: Wrapper 同梱

### テストフレームワーク

- **kotlin-test**
- **Turbine**
- **MockK**
- **Ktor MockEngine**

## 内部配布用 Debug APK

実機確認や内部テスト用には debug APK をビルドします。
GitHub Actions の internal debug APK workflow も同じ `assembleDebug` を使います。

### 1. デバッグキーストアの用意

```bash
keytool -genkey -v \
  -keystore debug.keystore \
  -alias muxport \
  -keyalg RSA -keysize 2048 -validity 10000
```

### 2. 署名付き debug APK ビルド

```bash
export KEYSTORE_PASSWORD="your-password"

./gradlew :androidApp:assembleDebug
```

この APK は内部確認用です。本番配布物としては扱いません。
