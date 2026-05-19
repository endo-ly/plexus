package dev.muxport.shared.di

import android.content.Context
import dev.muxport.shared.cache.DiskCacheContext
import dev.muxport.shared.core.platform.PlatformPreferences
import org.koin.dsl.module

val androidModule =
    module {
        single<DiskCacheContext> {
            DiskCacheContext(get<Context>().cacheDir.absolutePath)
        }

        single<PlatformPreferences> {
            PlatformPreferences(get<Context>())
        }
    }
