package com.shieldstream.tv.ui.touch

import android.app.Activity
import android.app.UiModeManager
import android.content.ContextWrapper
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs

/**
 * Modo táctil (fork para móvil): true en un aparato con pantalla táctil que no es una tele.
 * En la tele vale false y la app se comporta exactamente igual que siempre.
 */
val LocalTouchUi = staticCompositionLocalOf { false }

/**
 * Lo mismo que [LocalTouchUi] para código que no está en una composición (modificadores). MainActivity lo fija una
 * vez antes de setContent; en la tele se queda en false y esos modificadores no añaden nada.
 */
object TouchMode {
    @Volatile
    var enabled: Boolean = false
}

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
            HideSystemBarsAgain()
            CompositionLocalProvider(LocalTouchUi provides true, LocalDensity provides density) {
                // Si el sistema saca sus barras (p.ej. con el gesto de atrás), la interfaz se aparta para que el menú de
                // arriba no quede debajo de la barra de estado. La escala se calcula con el alto completo (arriba) para
                // que no cambie el tamaño de todo mientras se ven.
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    content()
                }
            }
        }
    }
}

/**
 * Deslizar hacia arriba sobre una pantalla que no se desplaza (la portada del Inicio): en la tele se pasa a las
 * filas con ▼; con el dedo, con este gesto. No consume nada: las filas horizontales de dentro siguen funcionando.
 */
fun Modifier.touchSwipeUp(enabled: Boolean, onSwipe: () -> Unit): Modifier = if (!enabled) this else composed {
    val latest by rememberUpdatedState(onSwipe)
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var fired = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!fired && dy < -threshold && abs(dy) > 1.5f * abs(dx)) {
                    fired = true
                    latest()
                }
            } while (change.pressed)
        }
    }
}

/**
 * Tirar hacia abajo cuando la lista de dentro ya está arriba del todo (las filas del Inicio): vuelve a la vista de
 * encima, como ▲ en la primera fila con el mando. Escucha el desplazamiento anidado, así que solo salta con lo que la
 * lista no ha podido usar.
 */
fun Modifier.touchPullDown(enabled: Boolean, onPull: () -> Unit): Modifier = if (!enabled) this else composed {
    val latest by rememberUpdatedState(onPull)
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            var pulled = 0f
            var fired = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y > 0f && consumed.y == 0f) pulled += available.y else if (consumed.y != 0f) pulled = 0f
                    if (!fired && pulled > threshold) {
                        fired = true
                        latest()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                fired = false
                return Velocity.Zero
            }
        }
    }
    nestedScroll(connection)
}

/**
 * Deslizar a los lados sobre algo que en la tele cambia con ◀ ▶ (el carrusel de destacados del Inicio).
 * [onSwipe] recibe true si se desliza hacia la izquierda (= siguiente). Al deslizar no salta el toque de abrir.
 */
fun Modifier.touchSwipeHorizontal(enabled: Boolean, onSwipe: (forward: Boolean) -> Unit): Modifier = if (!enabled) this else composed {
    val latest by rememberUpdatedState(onSwipe)
    val threshold = with(LocalDensity.current) { HORIZONTAL_SWIPE_THRESHOLD.toPx() }
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var horizontal = false
            var fired = false
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!horizontal && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) horizontal = true
                if (horizontal) change.consume()
                if (horizontal && !fired && abs(dx) > threshold) {
                    fired = true
                    latest(dx < 0)
                }
            } while (change.pressed)
        }
    }
}

private val HORIZONTAL_SWIPE_THRESHOLD = 48.dp

private val SWIPE_THRESHOLD = 72.dp

/** Tiempo que se dejan ver las barras del sistema antes de volver a esconderlas. */
private const val SYSTEM_BARS_VISIBLE_MS = 2_000L

/**
 * HyperOS deja la barra de estado puesta tras el gesto de atrás aunque la ventana pida pantalla completa: se vuelve a
 * esconder sola a los [SYSTEM_BARS_VISIBLE_MS].
 */
@Composable
private fun HideSystemBarsAgain() {
    val view = LocalView.current
    val barsVisible = WindowInsets.systemBars.getTop(LocalDensity.current) > 0 ||
        WindowInsets.systemBars.getBottom(LocalDensity.current) > 0
    LaunchedEffect(barsVisible) {
        if (!barsVisible) return@LaunchedEffect
        delay(SYSTEM_BARS_VISIBLE_MS)
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
