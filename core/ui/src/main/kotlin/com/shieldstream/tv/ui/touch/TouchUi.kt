package com.shieldstream.tv.ui.touch

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Modo táctil (fork para móvil): true en un aparato con pantalla táctil que no es una tele.
 * En la tele vale false y la app se comporta exactamente igual que siempre.
 */
val LocalTouchUi = staticCompositionLocalOf { false }

/** Alto, en dp, para el que está diseñada la interfaz: una tele 1080p (960×540 dp). */
const val TV_DESIGN_HEIGHT_DP = 540f

/** Tele o caja Android TV (leanback o modo de interfaz TV) frente a móvil/tableta con pantalla táctil. */
fun Context.isTouchFirstDevice(): Boolean {
    val pm = packageManager
    val uiMode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    val tv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    return !tv && pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

/**
 * Envuelve la app en el modo táctil:
 * - deja libre el hueco de la cámara (la pantalla del móvil en horizontal lo tiene en un lateral);
 * - escala la interfaz para que el alto útil equivalga a los 540 dp de una tele. Así las pantallas, pensadas para
 *   1080p, caben enteras en el móvil sin rediseñarlas una a una (un móvil en horizontal ronda los 410 dp de alto).
 *   Solo se reduce: en una tableta con más de 540 dp de alto se queda la densidad del sistema.
 */
@Composable
fun ProvideTouchUi(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val system = LocalDensity.current
            val heightPx = constraints.maxHeight.toFloat()
            val density = remember(heightPx, system) {
                val fitted = heightPx / TV_DESIGN_HEIGHT_DP
                if (heightPx > 0f && fitted < system.density) Density(fitted, fontScale = 1f) else system
            }
            CompositionLocalProvider(LocalTouchUi provides true, LocalDensity provides density) {
                content()
            }
        }
    }
}
