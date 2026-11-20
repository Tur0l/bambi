package com.shieldstream.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.theme.AppColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/*
 * Bloqueo infantil del reproductor (fork táctil). Bloqueado, la pantalla no responde a nada (ni gestos de brillo,
 * volumen o búsqueda, ni botones, ni el gesto de atrás, ni los botones físicos de volumen). Solo aparece el candado,
 * que hay que mantener pulsado [UNLOCK_HOLD_MS] para desbloquear.
 */

/**
 * Vive mientras vive la app, no la pantalla: al acabar un episodio el reproductor se cierra y se abre otro para el
 * siguiente, y el bloqueo tiene que seguir puesto.
 */
internal object PlayerChildLock {
    val locked = MutableStateFlow(false)
}

private const val UNLOCK_HOLD_MS = 1_800
private const val HINT_VISIBLE_MS = 3_000L

/**
 * Capa por encima de todo el reproductor: se queda con todos los toques. [hint] cambia cada vez que se intenta algo
 * (tecla, gesto de atrás) para enseñar el candado.
 */
@Composable
internal fun PlayerLockOverlay(hint: Int, onUnlock: () -> Unit) {
    var shownAt by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(hint, shownAt) {
        visible = true
        delay(HINT_VISIBLE_MS)
        visible = false
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    shownAt++
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HoldToUnlockButton(onUnlock = onUnlock, onPress = { shownAt++ })
                Text(
                    localizedStringResource(StringsR.string.player_touch_locked),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HoldToUnlockButton(onUnlock: () -> Unit, onPress: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val accent = AppColors.Accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    onPress()
                    val hold: Job = scope.launch {
                        progress.animateTo(1f, tween(UNLOCK_HOLD_MS, easing = LinearEasing))
                        onUnlock()
                    }
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    hold.cancel()
                    scope.launch { progress.snapTo(0f) }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val stroke = 5.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, useCenter = false, style = Stroke(stroke))
            if (progress.value > 0f) {
                drawArc(accent, -90f, 360f * progress.value, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Icon(AppIcons.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
    }
}
