package dev.muxport.android

import android.app.Application
import dev.muxport.android.notifications.NotificationChannelManager
import dev.muxport.shared.di.androidModule
import dev.muxport.shared.di.appModule
import dev.muxport.shared.di.terminalModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Muxport アプリケーションクラス。
 *
 * アプリ起動時に Koin DI コンテナを初期化する。
 * `AndroidManifest.xml` の `android:name` に指定する必要がある。
 */
class MuxportApplication : Application() {
    /**
     * アプリケーションのエントリポイント。
     *
     * Activity が起動する前に Koin に全モジュールを登録し、
     * 通知チャンネルの作成を行う。
     */
    override fun onCreate() {
        super.onCreate()

        // 通知チャンネルを作成（Android 8.0+）
        NotificationChannelManager.createNotificationChannel(this)

        // Koin DI コンテナを初期化
        startKoin {
            androidContext(this@MuxportApplication)
            modules(appModule, androidModule, terminalModule)
        }
    }
}
