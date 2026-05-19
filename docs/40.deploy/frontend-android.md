# Frontend Deploy (Android)

Muxport の Android アプリをビルドし、内部配布用 debug APK を作成する手順。
Kotlin Multiplatform + Compose Multiplatform を使用し、Android ネイティブアプリとして実機確認に使う。

## 1. 前提条件

- **JDK**: 17 以上（推奨: JDK 21）
- **Android SDK**: API 34（コマンドラインツール）
- **Gradle**: 8.8+ (Wrapper 同梱)

### 1.1 Android SDK セットアップ

Android Studio を使わずに CLI で開発する場合:

```bash
# Android SDK Command-line Tools をダウンロード
# https://developer.android.com/studio#command-tools

# SDK マネージャーで必要なパッケージをインストール
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 環境変数設定
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

## 2. ビルド手順

### 2.1 デバッグビルド

```bash
cd frontend
./gradlew :androidApp:assembleDebug
# 成果物: androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### 2.2 内部配布向け署名付き debug ビルド

内部配布で使う debug APK に独自キーストアを使う場合の手順。

#### A. キーストア作成（初回のみ）

```bash
keytool -genkey -v \
  -keystore debug.keystore \
  -alias muxport \
  -keyalg RSA -keysize 2048 -validity 10000
```

補足:

- Android の debug ビルドでは `debug.keystore` が必要です
- Android Studio / Gradle が自動生成した標準 debug keystore は通常 `/root/.android/debug.keystore` にあります
- internal 配布用 workflow でも同じ debug keystore を使えます
- 既存の EgoGraph 用 debug keystore を流用しても、`applicationId` が `dev.muxport.app` ならアプリ共存には影響しません

#### B. ビルド実行

環境変数を設定してビルドします。

```bash
export KEYSTORE_PASSWORD="your-password"
export KEY_PASSWORD="your-password"

./gradlew :androidApp:assembleDebug
# 成果物: androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## 3. インストール

### デバイスへのインストール

```bash
./gradlew :androidApp:installDebug
```

## 4. CI/CD

`ci-frontend.yml` で自動テストと debug ビルドを行い、internal debug APK publish workflow で実機確認用 APK を配布できます。

内部配布用 artifact は production release ではありません。用途を明示したうえで GitHub pre-release へ配置してください。

### 4.1 GitHub Secrets

Frontend の CI / internal APK 配布では次の secrets を使います。

- `GOOGLE_SERVICES_JSON_BASE64`
  `google-services.json` を base64 化した文字列
- `DEBUG_KEYSTORE_BASE64`
  `debug.keystore` を base64 化した文字列
- `DEBUG_KEYSTORE_PASSWORD`
  debug keystore のパスワード

Linux での生成例:

```bash
base64 -w0 /absolute/path/to/google-services.json
base64 -w0 /root/.android/debug.keystore
```
