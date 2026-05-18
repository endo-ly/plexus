package dev.plexus.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import com.google.firebase.messaging.FirebaseMessaging
import dev.plexus.android.fcm.FcmTokenManager
import dev.plexus.shared.core.platform.PlatformPrefsKeys
import dev.plexus.shared.core.platform.terminal.ActivityRecorder
import dev.plexus.shared.core.settings.AppTheme
import dev.plexus.shared.core.settings.ThemeRepository
import dev.plexus.shared.core.ui.theme.MuxportTheme
import dev.plexus.shared.features.navigation.TerminalNavigationScreen
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

/**
 * MainActivity
 *
 * アプリケーションのメインアクティビティ。
 * 音声認識のためにActivityRecorderにコンテキストを設定する。
 */
class MainActivity : ComponentActivity() {
    private var fcmTokenManager: FcmTokenManager? = null

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _: Boolean ->
            // Permission result handled by system
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "onCreate called")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Request FCM token explicitly
        Log.d("MainActivity", "Requesting FCM token...")
        runCatching {
            FirebaseMessaging.getInstance().token
        }.onSuccess { task ->
            task
                .addOnSuccessListener { token ->
                    Log.d("MainActivity", "FCM token received")
                    val manager = getTokenManager()
                    if (manager == null) {
                        Log.w("MainActivity", "Skip FCM token registration: gateway settings are empty")
                    } else {
                        manager.registerToken(token = token, deviceName = Build.MODEL)
                    }
                }.addOnFailureListener { e ->
                    Log.w("MainActivity", "Failed to get FCM token: ${e.message}")
                }
        }.onFailure { e ->
            Log.w("MainActivity", "Skipping FCM token request: ${e.message}")
        }

        // Set activity context for speech recognizer
        ActivityRecorder.currentActivity = this@MainActivity

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    ActivityRecorder.currentActivity = null
                }
            },
        )

        setContent {
            KoinContext {
                val themeRepository = koinInject<ThemeRepository>()
                val theme by themeRepository.theme.collectAsState()

                val darkTheme = theme == AppTheme.DARK

                MuxportTheme(darkTheme = darkTheme) {
                    Navigator(TerminalNavigationScreen()) {
                        CurrentScreen()
                    }
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun getTokenManager(): FcmTokenManager? {
        fcmTokenManager?.let { return it }

        val prefs = getSharedPreferences("plexus_prefs", MODE_PRIVATE)
        val gatewayUrl = prefs.getString(PlatformPrefsKeys.KEY_GATEWAY_API_URL, "")?.trim().orEmpty()
        val apiKey = prefs.getString(PlatformPrefsKeys.KEY_GATEWAY_API_KEY, "")?.trim().orEmpty()

        if (gatewayUrl.isBlank() || apiKey.isBlank()) {
            return null
        }

        fcmTokenManager = FcmTokenManager(gatewayUrl = gatewayUrl, apiKey = apiKey)
        return fcmTokenManager
    }

    override fun onDestroy() {
        fcmTokenManager?.cleanup()
        fcmTokenManager = null
        super.onDestroy()
    }
}
