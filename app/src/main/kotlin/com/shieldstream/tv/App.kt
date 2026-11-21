package com.shieldstream.tv

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.AppLanguage
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.shieldstream.tv.data.security.TokenStore
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.watchnext.WatchNextManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), ImageLoaderFactory, Configuration.Provider {

    // Perezoso: Coil no hace falta hasta la primera imagen y así no retrasa el arranque.
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var tokenStore: TokenStore

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var episodeTracking: EpisodeTrackingRepository

    @Inject
    lateinit var watchNext: WatchNextManager

    @Inject
    lateinit var settings: com.shieldstream.tv.domain.SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        StartupTrace.mark("App.onCreate")
        Lang.current = AppLanguage.fromName(
            getSharedPreferences(AppLanguage.PREFS, MODE_PRIVATE).getString(AppLanguage.PREF_KEY, null),
        ) ?: AppLanguage.default()
        super.onCreate()
        StartupTrace.mark("App inyectada")
        // Fork táctil: los secretos de la APK preconfigurada van PRIMERO. seedOnce marca la clave como sembrada aunque el
        // valor venga vacío, así que si fueran después (tmdb_key, subdl_key, alldebrid_key) ya no entrarían.
        seedSecrets(BuildConfig.SEED_SECRETS)
        tokenStore.seedOnce(TokenStore.RD_TOKEN, BuildConfig.RD_TOKEN)
        tokenStore.seedOnce(TokenStore.TMDB_KEY, BuildConfig.TMDB_API_KEY)
        tokenStore.seedOnce(TokenStore.SUBDL_KEY, BuildConfig.SUBDL_API_KEY)
        // Fork táctil: APK preconfigurada (-PseedFile): clave de AllDebrid y servicio debrid elegido, una sola vez.
        tokenStore.seedOnce(TokenStore.keyNameFor(ApiService.ALLDEBRID), BuildConfig.ALLDEBRID_KEY)
        seedDebridChoice(BuildConfig.SEED_DEBRID)
        // Solo en Bambi Pruebas: si no hay ningún addon, se deja preconfigurado Torrentio (código abierto) limitado
        // a las webs españolas, para tener una sección solo con fuentes en castellano. Se quita desde Ajustes → Addons.
        // Fork táctil: también en la versión normal (en upstream solo en Bambi Pruebas). Solo si no hay ningún addon.
        tokenStore.seedOnce(TokenStore.keyNameFor(ApiService.AIOSTREAMS), DEBUG_SPANISH_ADDON)
        StartupTrace.mark("App claves")
        watchNext.start()
        StartupTrace.mark("App watchNext")
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            episodeTracking.notificationsEnabled.collect { enabled ->
                EpisodeNotificationScheduler.sync(this@App, enabled)
            }
        }
    }

    /** APK preconfigurada: el resto de secretos («nombre=valor» por línea, en base64), cada uno una sola vez. */
    private fun seedSecrets(blob: String) {
        if (blob.isBlank()) return
        String(android.util.Base64.decode(blob, android.util.Base64.DEFAULT)).lineSequence().forEach { line ->
            val i = line.indexOf('=')
            if (i > 0) tokenStore.seedOnce(line.substring(0, i), line.substring(i + 1))
        }
    }

    /** Deja elegido el servicio debrid de la APK preconfigurada si el usuario aún no ha elegido ninguno. */
    private fun seedDebridChoice(name: String) {
        if (name.isBlank()) return
        val service = runCatching { com.shieldstream.tv.domain.DebridService.valueOf(name) }.getOrNull() ?: return
        val prefs = getSharedPreferences(AppLanguage.PREFS, MODE_PRIVATE)
        if (prefs.contains(KEY_DEBRID_CHOICE)) return
        kotlinx.coroutines.runBlocking { settings.setDebridService(service) }
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    private companion object {
        /** Misma clave que SettingsRepositoryImpl.KEY_DEBRID. */
        const val KEY_DEBRID_CHOICE = "debrid_service"
        const val DEBUG_SPANISH_ADDON =
            "https://torrentio.strem.fun/providers=mejortorrent,wolfmax4k,cinecalidad|language=spanish/manifest.json"
    }
}
