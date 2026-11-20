package com.shieldstream.tv.media

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi

/**
 * Fork táctil: en el móvil, el audio 5.1/7.1 se mezcla a estéreo DENTRO de la app.
 *
 * En la tele el multicanal va tal cual al amplificador. En un móvil lo mezclaba el sistema y en el POCO F8 Ultra
 * (HyperOS, Android 17) se perdía el canal central: se oían la música y los efectos, pero no los diálogos, que van ahí.
 * Media3 1.6 no trae matrices de 6→2 ni de 8→2 («not yet implemented»), así que van aquí.
 *
 * Orden de canales de Android/Media3: FL FR FC LFE BL BR (5.1), + BC (6.1), + SL SR en vez de BC (7.1).
 * Cada fila es un canal de entrada: [a la izquierda, a la derecha].
 */
@OptIn(UnstableApi::class)
internal object StereoDownmix {
    /** Centro (diálogos) a -3 dB en los dos lados, como en una mezcla Lo/Ro normal. */
    private const val C = 0.707f

    /** Envolventes algo más bajas, para que no tapen las voces en unos altavoces de móvil. */
    private const val S = 0.5f

    /** Margen para que la suma de canales no sature (el LFE se descarta: un móvil no lo reproduce). */
    private const val GAIN = 0.65f

    fun processor(): AudioProcessor = ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2))
        putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2))
        // FL FR FC
        putChannelMixingMatrix(matrix(3, 1f, 0f, 0f, 1f, C, C))
        // Cuadrafónico: FL FR BL BR
        putChannelMixingMatrix(matrix(4, 1f, 0f, 0f, 1f, S, 0f, 0f, S))
        // FL FR FC BL BR
        putChannelMixingMatrix(matrix(5, 1f, 0f, 0f, 1f, C, C, S, 0f, 0f, S))
        // 5.1: FL FR FC LFE BL BR
        putChannelMixingMatrix(matrix(6, 1f, 0f, 0f, 1f, C, C, 0f, 0f, S, 0f, 0f, S))
        // 6.1: FL FR FC LFE BL BR BC
        putChannelMixingMatrix(matrix(7, 1f, 0f, 0f, 1f, C, C, 0f, 0f, S, 0f, 0f, S, S * C, S * C))
        // 7.1: FL FR FC LFE BL BR SL SR
        putChannelMixingMatrix(matrix(8, 1f, 0f, 0f, 1f, C, C, 0f, 0f, S, 0f, 0f, S, S, 0f, 0f, S))
    }

    private fun matrix(inputChannels: Int, vararg coefficients: Float): ChannelMixingMatrix {
        require(coefficients.size == inputChannels * 2)
        return ChannelMixingMatrix(inputChannels, 2, coefficients).scaleBy(GAIN)
    }

    /** Tele o caja Android TV: ahí el multicanal se deja intacto (sale por HDMI al amplificador). */
    fun isTelevision(context: Context): Boolean {
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
