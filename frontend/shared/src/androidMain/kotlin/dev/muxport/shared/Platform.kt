package dev.muxport.shared

actual fun getPlatformName(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
