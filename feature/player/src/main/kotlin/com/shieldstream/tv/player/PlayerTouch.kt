package com.shieldstream.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.ui.component.AppIcons
import kotlin.math.abs

/*
 * Reproductor con el dedo (fork para móvil). Nada de esto se compone en la tele (LocalTouchUi = false).
 *
 * Capas, de abajo arriba: vídeo → PlayerTouchGestures (pantalla entera) → ControlsOverlay (la barra recoge sus
 * propios toques) → PlayerTouchButtons. Compose entrega cada toque solo a la capa más alta que tiene gestos en ese
 * punto, así que lo que no es la barra ni un botón cae en los gestos.
 */

internal enum class TouchZone { LEFT, CENTER, RIGHT }

/** Ancho de pantalla arrastrado con el dedo sobre el vídeo = este salto (para afinar; lo largo, con la barra). */
internal const val TOUCH_SCRUB_FULL_WIDTH_MS = 180_000L

/** Salto del doble toque y de los botones ±10. */
internal const val TOUCH_SEEK_STEP_MS = 10_000L

@Composable
internal fun PlayerTouchGestures(
    onTap: () -> Unit,
    onDoubleTap: (TouchZone) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (widthFraction: Float) -> Unit,
    onScrubEnd: () -> Unit,
    onBrightness: (heightFraction: Float) -> Unit,
    onVolume: (heightFraction: Float) -> Unit,
    onAdjustEnd: () -> Unit,
    onPinch: (zoomIn: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { at ->
                        val third = size.width / 3f
                        onDoubleTap(
                            when {
                                at.x < third -> TouchZone.LEFT
                                at.x > 2 * third -> TouchZone.RIGHT
                                else -> TouchZone.CENTER
                            },
                        )
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 0 = aún no se sabe, 1 = horizontal (buscar), 2 = vertical izquierda (brillo),
                    // 3 = vertical derecha (volumen), 4 = pellizco (formato de imagen).
                    var mode = 0
                    var travelled = Offset.Zero
                    var zoom = 1f
                    val slop = viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        if (mode == 0 && event.changes.count { it.pressed } >= 2) mode = 4
                        if (mode == 4) {
                            zoom *= event.calculateZoom()
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null) {
                                val delta = change.positionChange()
                                travelled += delta
                                if (mode == 0 && travelled.getDistance() > slop) {
                                    mode = when {
                                        abs(travelled.x) > abs(travelled.y) -> 1
                                        down.position.x < size.width / 2f -> 2
                                        else -> 3
                                    }
                                    if (mode == 1) onScrubStart()
                                }
                                when (mode) {
                                    1 -> onScrub(delta.x / size.width)
                                    2 -> onBrightness(-delta.y / size.height)
                                    3 -> onVolume(-delta.y / size.height)
                                }
                                if (mode != 0) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    when (mode) {
                        1 -> onScrubEnd()
                        2, 3 -> onAdjustEnd()
                        4 -> if (zoom > 1.15f) onPinch(true) else if (zoom < 0.87f) onPinch(false)
                    }
                }
            },
    )
}

/** Botones que solo existen con el dedo: volver, ±10 s, reproducir/pausa y audio/subtítulos. */
@Composable
internal fun PlayerTouchButtons(
    visible: Boolean,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onOpenTracks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            TouchRoundButton(TouchIcons.Back, size = 44.dp, onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 22.dp))
            TouchRoundButton(TouchIcons.Subtitles, size = 44.dp, onClick = onOpenTracks, modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 22.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                TouchRoundButton(TouchIcons.Replay, size = 60.dp, label = "10", onClick = onSeekBack)
                TouchRoundButton(if (isPlaying) AppIcons.Pause else AppIcons.Play, size = 76.dp, onClick = onPlayPause)
                TouchRoundButton(TouchIcons.Replay, size = 60.dp, label = "10", mirrored = true, onClick = onSeekForward)
            }
        }
    }
}

@Composable
private fun TouchRoundButton(
    icon: ImageVector,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    mirrored: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(size * 0.62f)
                .graphicsLayer { if (mirrored) scaleX = -1f },
        )
        if (label != null) {
            Text(label, color = Color.White, fontSize = (size.value * 0.2f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = size * 0.08f))
        }
    }
}

/** Iconos de Material (Apache 2.0) que la app de tele no necesitaba. */
private object TouchIcons {
    val Back = icon("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val Replay = icon("M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z")
    val Subtitles = icon(
        "M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM4,12h4v2H4v-2z" +
            "M14,18H4v-2h10v2zM20,18h-4v-2h4v2zM20,14H10v-2h10v2z",
    )

    private fun icon(path: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White))
            .build()
}
