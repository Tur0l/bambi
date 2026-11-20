package com.shieldstream.tv.di

import com.shieldstream.tv.BuildConfig
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.TraktCredentials
import com.shieldstream.tv.domain.AppVersion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSelectedContentHolder(): SelectedContentHolder = SelectedContentHolder()

    @Provides
    @Singleton
    fun provideAppVersion(): AppVersion =
        AppVersion(
            name = BuildConfig.VERSION_NAME,
            isDebug = BuildConfig.DEBUG || BuildConfig.APPLICATION_ID.endsWith(".debug"),
            // Fork táctil: las actualizaciones salen de las Releases del fork (el APK de la tele va firmado con otra clave).
            updateRepo = "Tur0l/bambi",
        )

    @Provides
    @Singleton
    fun provideTraktCredentials(): TraktCredentials =
        TraktCredentials(BuildConfig.TRAKT_CLIENT_ID, BuildConfig.TRAKT_CLIENT_SECRET, BuildConfig.TRAKT_REDIRECT_URI)
}
