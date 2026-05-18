package dev.plexus.android

import android.app.Application
import dev.plexus.android.notifications.NotificationChannelManager
import dev.plexus.shared.di.androidModule
import dev.plexus.shared.di.appModule
import dev.plexus.shared.di.terminalModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Muxport Application class
 *
 * Initializes Koin dependency injection container on app startup.
 * Must be declared in AndroidManifest.xml as the application class.
 */
class MuxportApplication : Application() {
    /**
     * Application entry point
     *
     * Starts Koin with all application modules before any activity is launched.
     */
    override fun onCreate() {
        super.onCreate()

        // 通知チャンネルを作成（Android 8.0+）
        NotificationChannelManager.createNotificationChannel(this)

        // Initialize Koin DI container
        startKoin {
            androidContext(this@MuxportApplication)
            modules(appModule, androidModule, terminalModule)
        }
    }
}
