package com.shieldstream.tv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.shieldstream.tv.model.VideoFit
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.rememberTexts
import com.shieldstream.tv.ui.touch.LocalTouchUi
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
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            TouchRoundButton(TouchIcons.Back, size = 44.dp, onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 22.dp))
            TouchRoundButton(TouchIcons.Subtitles, size = 44.dp, onClick = onOpenTracks, modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 22.dp))
            // Bloqueo infantil (PlayerLock.kt).
            TouchRoundButton(AppIcons.Lock, size = 44.dp, onClick = onLock, modifier = Modifier.align(Alignment.TopEnd).padding(end = 76.dp, top = 22.dp))
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

// ---------------------------------------------------------------------------------------------------------------
// Enganche con PlayerScreen. El estado del reproductor (búsqueda, controles, panel de pistas…) vive en PlayerScreen;
// aquí solo se lee y se cambia a través de PlayerTouchBridge. Así PlayerScreen (que upstream toca a menudo) solo
// lleva unas pocas líneas del fork y traer versiones nuevas del original da pocos conflictos.
// ---------------------------------------------------------------------------------------------------------------

/** Lo que PlayerScreen comparte con la capa táctil. */
internal class PlayerTouchBridge(
    val player: () -> ExoPlayer?,
    val scrubMs: () -> Long?,
    val setScrubMs: (Long?) -> Unit,
    val resumeAfterScrub: () -> Boolean,
    val setResumeAfterScrub: (Boolean) -> Unit,
    val controlsVisible: () -> Boolean,
    val setControlsVisible: (Boolean) -> Unit,
    val showControls: () -> Unit,
    val tracksOpen: () -> Boolean,
    val setTracksOpen: (Boolean) -> Unit,
    val syncOpen: () -> Boolean,
    val feedback: (String) -> Unit,
    val focusPlayer: () -> Unit,
    val onBack: () -> Unit,
    val setVideoFit: (VideoFit) -> Unit,
)

@Composable
internal fun rememberPlayerTouch(bridge: PlayerTouchBridge): PlayerTouch {
    val enabled = LocalTouchUi.current
    val context = LocalContext.current
    val texts = rememberTexts()
    val touch = remember(context, texts) { PlayerTouch(enabled, context, texts) }
    touch.bridge = bridge
    val childLock by PlayerChildLock.locked.collectAsStateWithLifecycle()
    touch.locked = enabled && childLock
    // Bloqueado, el gesto de atrás no cierra la película: solo enseña el candado.
    BackHandler(enabled = touch.locked) { touch.lockHint++ }
    // El brillo que se pone con el dedo es solo para el reproductor: al salir vuelve el del sistema.
    DisposableEffect(touch) { onDispose { touch.restoreBrightness() } }
    return touch
}

/** Gestos, botones y candado del reproductor en el móvil. Con [enabled] = false (la tele) no hace nada. */
@Stable
internal class PlayerTouch(val enabled: Boolean, context: Context, private val texts: Texts) {
    lateinit var bridge: PlayerTouchBridge
    var locked by mutableStateOf(false)
    var lockHint by mutableIntStateOf(0)
    private val activity = context.findActivity()
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var volume = -1f
    private var brightness = -1f

    /** Bloqueo infantil: se tragan todas las teclas, también Atrás y los botones físicos de volumen. */
    fun consumesKey(event: KeyEvent): Boolean {
        if (!locked) return false
        if (event.type == KeyEventType.KeyDown) lockHint++
        return true
    }

    fun tappable(modifier: Modifier, onTap: () -> Unit): Modifier = if (enabled) modifier.clickable(onClick = onTap) else modifier

    private fun playPause() {
        val p = bridge.player() ?: return
        if (p.isPlaying) p.pause() else p.play()
        bridge.showControls()
    }

    private fun seekBy(stepMs: Long) {
        val p = bridge.player() ?: return
        val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + stepMs).coerceIn(0L, duration))
        bridge.feedback(if (stepMs < 0) "« ${-stepMs / 1000} s" else "${stepMs / 1000} s »")
        bridge.showControls()
    }

    private fun scrubStart() {
        val p = bridge.player() ?: return
        if (bridge.scrubMs() == null) {
            bridge.setResumeAfterScrub(p.isPlaying)
            p.pause()
            bridge.setScrubMs(p.currentPosition)
        }
        bridge.showControls()
    }

    private fun scrubTo(targetMs: Long) {
        val p = bridge.player() ?: return
        val duration = p.duration.takeIf { it > 0 } ?: return
        if (bridge.scrubMs() == null) scrubStart()
        bridge.setScrubMs(targetMs.coerceIn(0L, duration))
        bridge.showControls()
    }

    private fun scrubEnd() {
        val target = bridge.scrubMs() ?: return
        val p = bridge.player()
        p?.seekTo(target)
        if (bridge.resumeAfterScrub()) p?.play()
        bridge.setScrubMs(null)
        bridge.showControls()
    }

    /** Barra de progreso: [fraction] de la duración; < 0 = el dedo se ha soltado y se salta a donde estaba. */
    fun seekBarSeek(fraction: Float, done: Boolean) {
        val duration = bridge.player()?.duration?.takeIf { it > 0 }
        if (duration != null && fraction >= 0f) scrubTo((fraction * duration).toLong())
        if (done) scrubEnd()
    }

    private fun adjustBrightness(delta: Float) {
        val w = activity?.window ?: return
        if (brightness < 0f) {
            brightness = w.attributes.screenBrightness.takeIf { it >= 0f } ?: run {
                val raw = runCatching {
                    android.provider.Settings.System.getInt(w.context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
                }.getOrDefault(128)
                if (raw in 0..255) raw / 255f else 0.5f
            }
        }
        brightness = (brightness + delta).coerceIn(0.01f, 1f)
        w.attributes = w.attributes.apply { screenBrightness = brightness }
        bridge.feedback(texts.get(StringsR.string.player_touch_brightness, (brightness * 100).toInt()))
    }

    private fun adjustVolume(delta: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (volume < 0f) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        volume = (volume + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(volume * max), 0)
        bridge.feedback(texts.get(StringsR.string.player_touch_volume, Math.round(volume * 100)))
    }

    fun restoreBrightness() {
        activity?.window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    private fun lock() {
        PlayerChildLock.locked.value = true
        bridge.setControlsVisible(false)
        bridge.setTracksOpen(false)
        lockHint++
        // Con el foco en el reproductor, los botones de volumen pasan por consumesKey y se bloquean.
        bridge.focusPlayer()
    }

    /** Capa de gestos: va DEBAJO de los controles (que recogen sus propios toques) y encima del vídeo. */
    @Composable
    fun Gestures(active: Boolean) {
        if (!enabled || !active || locked || bridge.syncOpen()) return
        PlayerTouchGestures(
            onTap = {
                when {
                    bridge.tracksOpen() -> bridge.setTracksOpen(false)
                    bridge.controlsVisible() -> bridge.setControlsVisible(false)
                    else -> bridge.showControls()
                }
            },
            onDoubleTap = { zone ->
                when (zone) {
                    TouchZone.LEFT -> seekBy(-TOUCH_SEEK_STEP_MS)
                    TouchZone.RIGHT -> seekBy(TOUCH_SEEK_STEP_MS)
                    TouchZone.CENTER -> playPause()
                }
            },
            onScrubStart = ::scrubStart,
            onScrub = { fraction -> bridge.scrubMs()?.let { from -> scrubTo(from + (fraction * TOUCH_SCRUB_FULL_WIDTH_MS).toLong()) } },
            onScrubEnd = ::scrubEnd,
            onBrightness = ::adjustBrightness,
            onVolume = ::adjustVolume,
            onAdjustEnd = {
                volume = -1f
                brightness = -1f
            },
            onPinch = { zoomIn -> bridge.setVideoFit(if (zoomIn) VideoFit.ZOOM else VideoFit.FIT) },
        )
    }

    /** Botones de volver, ±10 s, pausa, pistas y candado: encima de los controles. */
    @Composable
    fun Buttons(isPlaying: Boolean) {
        if (!enabled) return
        PlayerTouchButtons(
            visible = bridge.controlsVisible() && !bridge.syncOpen() && !bridge.tracksOpen() && bridge.scrubMs() == null && !locked,
            isPlaying = isPlaying,
            onBack = bridge.onBack,
            onPlayPause = ::playPause,
            onSeekBack = { seekBy(-TOUCH_SEEK_STEP_MS) },
            onSeekForward = { seekBy(TOUCH_SEEK_STEP_MS) },
            onOpenTracks = {
                bridge.setControlsVisible(false)
                bridge.setTracksOpen(true)
            },
            onLock = ::lock,
        )
    }

    /** Candado del bloqueo infantil: encima de TODO el reproductor. */
    @Composable
    fun LockOverlay() {
        if (!locked) return
        PlayerLockOverlay(
            hint = lockHint,
            onUnlock = {
                PlayerChildLock.locked.value = false
                bridge.showControls()
            },
        )
    }
}

/** Zona táctil de la barra de progreso: más alta que la barra (14 dp) para que se acierte con el dedo. */
@Composable
internal fun TouchSeekArea(onSeek: (fraction: Float, done: Boolean) -> Unit, bar: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { at -> onSeek((at.x / size.width).coerceIn(0f, 1f), true) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { at -> onSeek((at.x / size.width).coerceIn(0f, 1f), false) },
                    onDragEnd = { onSeek(-1f, true) },
                    onDragCancel = { onSeek(-1f, true) },
                ) { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f), false)
                }
            }
            .padding(vertical = 14.dp),
    ) {
        bar()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
