package dev.muxport.shared.di

import dev.muxport.shared.cache.DiskCache
import dev.muxport.shared.cache.DiskCacheContext
import dev.muxport.shared.core.data.repository.internal.InMemoryCache
import dev.muxport.shared.core.data.repository.internal.RepositoryClient
import dev.muxport.shared.core.network.HttpClientConfig
import dev.muxport.shared.core.network.HttpClientConfigProvider
import dev.muxport.shared.core.network.provideHttpClient
import dev.muxport.shared.core.platform.PlatformPreferences
import dev.muxport.shared.core.platform.PlatformPrefsDefaults
import dev.muxport.shared.core.platform.PlatformPrefsKeys
import dev.muxport.shared.core.platform.getDefaultBaseUrl
import dev.muxport.shared.core.platform.normalizeBaseUrl
import dev.muxport.shared.core.settings.ThemeRepository
import dev.muxport.shared.core.settings.ThemeRepositoryImpl
import dev.muxport.shared.features.terminal.agentlist.AgentListScreenModel
import dev.muxport.shared.features.terminal.settings.GatewaySettingsScreenModel
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Application-wide DI module
 *
 * Provides all application dependencies using Koin's traditional module definition.
 */
val appModule =
    module {
        // === Configuration ===
        single<String>(qualifier = named("BaseUrl")) {
            val preferences = getOrNull<PlatformPreferences>()
            val savedUrl = preferences?.getString(PlatformPrefsKeys.KEY_API_URL, PlatformPrefsDefaults.DEFAULT_API_URL)
            val rawUrl = if (savedUrl.isNullOrBlank()) getDefaultBaseUrl() else savedUrl
            try {
                normalizeBaseUrl(rawUrl)
            } catch (e: IllegalArgumentException) {
                getDefaultBaseUrl()
            }
        }

        single<String>(qualifier = named("ApiKey")) {
            val preferences = getOrNull<PlatformPreferences>()
            preferences?.getString(PlatformPrefsKeys.KEY_API_KEY, PlatformPrefsDefaults.DEFAULT_API_KEY) ?: ""
        }

        // === HTTP Client Configuration ===
        single<HttpClientConfigProvider> { HttpClientConfigProvider() }

        single<HttpClientConfig> {
            get<HttpClientConfigProvider>().getConfig()
        }

        // === Core ===
        single<HttpClient> {
            provideHttpClient(get<HttpClientConfig>())
        }

        single<DiskCache?> {
            val context = getOrNull<DiskCacheContext>()
            context?.let { DiskCache(it) }
        }

        // === RepositoryClient (Backend API) ===
        single<RepositoryClient>(qualifier = named("BackendClient")) {
            RepositoryClient(
                httpClient = get(),
                baseUrl = get(qualifier = named("BaseUrl")),
                apiKey = get(qualifier = named("ApiKey")),
            )
        }

        // === Cache ===
        single<InMemoryCache<String, Any>> { InMemoryCache() }

        // === Repositories ===
        single<ThemeRepository> {
            ThemeRepositoryImpl(preferences = get())
        }

        // === ScreenModels ===
        factory {
            AgentListScreenModel(
                terminalRepository = get(),
                preferences = get(),
            )
        }

        factory {
            GatewaySettingsScreenModel(
                preferences = get(),
                themeRepository = get(),
                httpClient = get(),
            )
        }
    }
