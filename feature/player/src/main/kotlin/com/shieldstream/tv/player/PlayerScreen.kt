package com.shieldstream.tv.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.rememberTexts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import com.shieldstream.tv.model.PlaybackVideoInfo
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import com.shieldstream.tv.ui.touch.LocalTouchUi
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.PlaybackMediaBadges
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onSleepTimerFinished: () -> Unit = onBack,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val videoInfo by viewModel.videoInfo.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val externalSubs by viewModel.externalSubs.collectAsStateWithLifecycle()
    val activeExternal by viewModel.activeExternal.collectAsStateWithLifecycle()
    val subtitleTiming by viewModel.subtitleTiming.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val subtitleAppearance by viewModel.subtitleAppearance.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val videoFit by viewModel.videoFit.collectAsStateWithLifecycle()
    val tracksPanelCard by viewModel.tracksPanelCard.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val sleepTimerRemainingMinutes by viewModel.sleepTimerRemainingMinutes.collectAsStateWithLifecycle()
    val thumbnailsEnabled by viewModel.seekThumbnails.collectAsStateWithLifecycle()
    val nextUp by viewModel.nextUp.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val skipPrompt by viewModel.skipPrompt.collectAsStateWithLifecycle()
    val texts = rememberTexts()
    LaunchedEffect(Unit) { viewModel.goNext.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.backToSources.collect { onBack() } }
    LaunchedEffect(Unit) {
        viewModel.sleepTimerFinished.collect {
            delay(1_500)
            onSleepTimerFinished()
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_500)
            viewModel.clearNotice()
        }
    }

    LaunchedEffect(Unit) { viewModel.start() }
    BackHandler(onBack = onBack)

    var tracksOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = tracksOpen) { tracksOpen = false }
    var syncOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = syncOpen) { syncOpen = false }
    LaunchedEffect(activeExternal) { if (activeExternal == null) syncOpen = false }

    LaunchedEffect(externalSubs) {
        if (externalSubs is ExternalSubsState.Attached) {
            tracksOpen = false
            viewModel.resetExternalSubtitles()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStart()
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val playback = rememberPlaybackState(player)
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(900)
            seekFeedback = null
        }
    }

    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var resumeAfterScrub by remember { mutableStateOf(false) }
    val scrubbing = scrubMs != null
    val mediaUri = remember(player, scrubbing) {
        player?.currentMediaItem?.localConfiguration?.uri
            ?.takeUnless { player?.videoFormat?.codecs?.let { c -> c.startsWith("dvhe.05") || c.startsWith("dvh1.05") } == true }
    }
    val pqHdr = remember(player, scrubbing) {
        player?.videoFormat?.let {
            it.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 || it.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
        } ?: false
    }
    val thumbs = rememberSeekThumbnails(mediaUri, pqHdr, active = scrubbing)

    // Bandas negras grabadas dentro de un vídeo 16:9 (remux): el reproductor no las ve, así que se miden en unos
    // fotogramas la primera vez que se pide Ampliar/Estirar con ese archivo y se amplía la superficie de vídeo.
    var videoSize by remember(player) { mutableStateOf(player?.videoSize ?: androidx.media3.common.VideoSize.UNKNOWN) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                videoSize = size
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    val frameIs16x9 = videoSize.width > 0 && videoSize.height > 0 &&
        kotlin.math.abs(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height - 16f / 9f) < 0.03f
    val itemUri = player?.currentMediaItem?.localConfiguration?.uri
    var letterboxScale by remember(itemUri) { mutableStateOf<Float?>(null) }
    val wantsNoBars = videoFit != com.shieldstream.tv.model.VideoFit.FIT
    LaunchedEffect(itemUri, wantsNoBars, frameIs16x9) {
        val p = player
        if (!wantsNoBars || !frameIs16x9 || letterboxScale != null || itemUri == null || p == null) return@LaunchedEffect
        val format = p.videoFormat
        if (format?.codecs?.let { it.startsWith("dvhe.05") || it.startsWith("dvh1.05") } == true) {
            letterboxScale = 1f
            return@LaunchedEffect
        }
        val duration = androidx.compose.runtime.snapshotFlow { playback.durationMs }.first { it > 0 }
        delay(1_500)
        val pq = format?.let {
            it.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 || it.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
        } ?: false
        letterboxScale = LetterboxDetector.measureScale(context, itemUri, pq, duration)
    }
    val barScale = if (frameIs16x9) letterboxScale ?: 1f else 1f
    LaunchedEffect(player) { scrubMs = null }

    fun showControls() {
        controlsVisible = true
        interactions++
    }

    // --- Fork táctil: gestos y botones del móvil (PlayerTouch.kt). En la tele touchUi = false y no se usa nada. ---
    val touchUi = LocalTouchUi.current
    val childLock by PlayerChildLock.locked.collectAsStateWithLifecycle()
    val childLocked = touchUi && childLock
    var lockHint by remember { mutableIntStateOf(0) }
    // Bloqueado, el gesto de atrás no cierra la película: solo enseña el candado.
    BackHandler(enabled = childLocked) { lockHint++ }
    val touchActivity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var touchVolume by remember { mutableFloatStateOf(-1f) }
    var touchBrightness by remember { mutableFloatStateOf(-1f) }
    // El brillo que se pone con el dedo es solo para el reproductor: al salir vuelve el del sistema.
    DisposableEffect(touchActivity) {
        onDispose {
            touchActivity?.window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }

    fun touchPlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        showControls()
    }

    fun touchSeekBy(stepMs: Long) {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + stepMs).coerceIn(0L, duration))
        seekFeedback = if (stepMs < 0) "« ${-stepMs / 1000} s" else "${stepMs / 1000} s »"
        showControls()
    }

    fun touchScrubStart() {
        val p = player ?: return
        if (scrubMs == null) {
            resumeAfterScrub = p.isPlaying
            p.pause()
            scrubMs = p.currentPosition
        }
        showControls()
    }

    fun touchScrubTo(targetMs: Long) {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 } ?: return
        if (scrubMs == null) touchScrubStart()
        scrubMs = targetMs.coerceIn(0L, duration)
        showControls()
    }

    fun touchScrubEnd() {
        val target = scrubMs ?: return
        player?.seekTo(target)
        if (resumeAfterScrub) player?.play()
        scrubMs = null
        showControls()
    }

    fun touchAdjustBrightness(delta: Float) {
        val w = touchActivity?.window ?: return
        if (touchBrightness < 0f) {
            touchBrightness = w.attributes.screenBrightness.takeIf { it >= 0f } ?: run {
                val raw = runCatching {
                    android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
                }.getOrDefault(128)
                if (raw in 0..255) raw / 255f else 0.5f
            }
        }
        touchBrightness = (touchBrightness + delta).coerceIn(0.01f, 1f)
        w.attributes = w.attributes.apply { screenBrightness = touchBrightness }
        seekFeedback = texts.get(StringsR.string.player_touch_brightness, (touchBrightness * 100).toInt())
    }

    fun touchAdjustVolume(delta: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (touchVolume < 0f) touchVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        touchVolume = (touchVolume + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(touchVolume * max), 0)
        seekFeedback = texts.get(StringsR.string.player_touch_volume, Math.round(touchVolume * 100))
    }

    LaunchedEffect(controlsVisible, interactions, playback.isPlaying) {
        if (controlsVisible && playback.isPlaying) {
            delay(4_000)
            controlsVisible = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(tracksOpen) { if (!tracksOpen) runCatching { focusRequester.requestFocus() } }

    val skipTarget = (skipPrompt as? SkipPromptUiState.Showing)?.targetMs
    var skipExpired by remember { mutableStateOf(false) }
    LaunchedEffect(skipTarget) {
        skipExpired = false
        if (skipTarget != null) {
            delay(SKIP_VISIBLE_MS)
            skipExpired = true
        }
    }
    val skipVisible = skipPrompt is SkipPromptUiState.Showing && (!skipExpired || controlsVisible) &&
        nextUp !is NextUpUiState.Showing

    fun handleKey(event: KeyEvent): Boolean {
        // Bloqueo infantil: se tragan todas las teclas, también Atrás y los botones físicos de volumen.
        if (childLocked) {
            if (event.type == KeyEventType.KeyDown) lockHint++
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        if (event.key == Key.MediaStop) {
            if (event.nativeKeyEvent.repeatCount == 0) onBack()
            return true
        }
        val p = player ?: return false

        fun seekDirectly(stepMs: Long) {
            val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            p.seekTo((p.currentPosition + stepMs).coerceIn(0L, duration))
            seekFeedback = if (stepMs < 0) "« ${-stepMs / 1000} s" else "${stepMs / 1000} s »"
            showControls()
        }

        if (tracksOpen || syncOpen) {
            return when (event.key) {
                Key.DirectionLeft -> if (syncOpen) {
                    viewModel.adjustSubtitleOffset(-100)
                    true
                } else false
                Key.DirectionRight -> if (syncOpen) {
                    viewModel.adjustSubtitleOffset(100)
                    true
                } else false
                Key.DirectionDown -> if (syncOpen) {
                    viewModel.adjustSubtitleOffset(-1_000)
                    true
                } else false
                Key.DirectionUp -> if (syncOpen) {
                    viewModel.adjustSubtitleOffset(1_000)
                    true
                } else false
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (syncOpen) {
                    viewModel.autoSyncNow()
                    true
                } else false
                Key.MediaPlayPause, Key.Spacebar -> {
                    if (p.isPlaying) p.pause() else p.play()
                    true
                }
                Key.MediaPlay -> { p.play(); true }
                Key.MediaPause -> { p.pause(); true }
                Key.MediaRewind -> { seekDirectly(-seekStepMs(event.nativeKeyEvent.repeatCount)); true }
                Key.MediaFastForward -> { seekDirectly(seekStepMs(event.nativeKeyEvent.repeatCount)); true }
                else -> false
            }
        }
        val okKey = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (okKey && nextUp is NextUpUiState.Showing && scrubMs == null && !syncOpen) {
            viewModel.playNextNow()
            return true
        }
        if (okKey && skipVisible && scrubMs == null && !syncOpen) {
            viewModel.skipSegment()
            return true
        }
        fun scrub(deltaMs: Long) {
            val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            val from = scrubMs ?: run {
                resumeAfterScrub = p.isPlaying
                p.pause()
                p.currentPosition
            }
            val snapped = Math.round((from + deltaMs).toDouble() / THUMB_SLOT_MS) * THUMB_SLOT_MS
            scrubMs = snapped.coerceIn(0L, duration)
            showControls()
        }
        val target = scrubMs
        if (target != null) {
            when (event.key) {
                // OK / Play: saltar al punto elegido y seguir viendo.
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar, Key.MediaPlay -> {
                    p.seekTo(target)
                    p.play()
                    scrubMs = null
                    showControls()
                    return true
                }
                Key.DirectionUp, Key.DirectionDown, Key.MediaPause -> return true
                else -> Unit
            }
        }
        return when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                if (p.isPlaying) p.pause() else p.play()
                showControls()
                true
            }
            Key.MediaPlay -> { p.play(); showControls(); true }
            Key.MediaPause -> { p.pause(); showControls(); true }
            Key.DirectionLeft, Key.MediaRewind -> {
                val step = seekStepMs(event.nativeKeyEvent.repeatCount)
                if (thumbnailsEnabled) {
                    scrub(-step)
                } else {
                    seekDirectly(-step)
                }
                true
            }
            Key.DirectionRight, Key.MediaFastForward -> {
                val step = seekStepMs(event.nativeKeyEvent.repeatCount)
                if (thumbnailsEnabled) {
                    scrub(step)
                } else {
                    seekDirectly(step)
                }
                true
            }
            Key.DirectionDown, Key.Menu, Key.Captions -> {
                if (controlsVisible || event.key != Key.DirectionDown) {
                    controlsVisible = false
                    tracksOpen = true
                } else {
                    showControls()
                }
                true
            }
            Key.DirectionUp, Key.Info -> { showControls(); true }
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onBackKey(enabled = scrubMs != null) {
                scrubMs = null
                if (resumeAfterScrub) player?.play()
                showControls()
            }
            .onBackKey(enabled = tracksOpen) { tracksOpen = false }
            .onBackKey(enabled = syncOpen) { syncOpen = false }
            .onBackKey(enabled = nextUp is NextUpUiState.Showing && !tracksOpen && !syncOpen && scrubMs == null) {
                viewModel.dismissNextUp()
            }
            .focusRequester(focusRequester)
            .onKeyEvent(::handleKey)
            .focusable(),
    ) {
        player?.let { exoPlayer ->
            val displayModeRequest = remember(exoPlayer, viewModel.earlyFrameRate) {
                viewModel.earlyFrameRate?.let { rate ->
                    context.findActivity()?.let { activity -> EarlyDisplayModeRequest(activity, rate) }
                }
            }
            LaunchedEffect(displayModeRequest, exoPlayer) {
                displayModeRequest?.applyAndWait()
                viewModel.onEarlyDisplayModeReady(exoPlayer)
            }
            DisposableEffect(displayModeRequest) {
                onDispose { displayModeRequest?.restore() }
            }
            val playerView = remember(exoPlayer) {
                PlayerView(context).apply {
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                }
            }
            DisposableEffect(playerView, exoPlayer) {
                val matcher = ContentFrameRateMatcher(
                    playerView = playerView,
                    player = exoPlayer,
                    enabled = viewModel.matchContentFrameRate,
                    initialFrameRate = viewModel.earlyFrameRate,
                )
                matcher.attach()
                onDispose { matcher.detach() }
            }
            AndroidView(
                factory = { playerView },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = when (videoFit) {
                        com.shieldstream.tv.model.VideoFit.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        com.shieldstream.tv.model.VideoFit.ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        com.shieldstream.tv.model.VideoFit.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                    view.videoSurfaceView?.let { surface ->
                        val (sx, sy) = when (videoFit) {
                            com.shieldstream.tv.model.VideoFit.FIT -> 1f to 1f
                            com.shieldstream.tv.model.VideoFit.ZOOM -> barScale to barScale
                            com.shieldstream.tv.model.VideoFit.STRETCH -> 1f to barScale
                        }
                        surface.scaleX = sx
                        surface.scaleY = sy
                    }
                    val evaluating = (externalSubs as? ExternalSubsState.Results)?.evaluating == true
                    view.subtitleView?.applySubtitleAppearance(subtitleAppearance)
                    view.subtitleView?.visibility = if (activeExternal != null || evaluating) View.GONE else View.VISIBLE
                },
                modifier = Modifier.fillMaxSize(),
            )
            activeExternal?.let { ExternalSubtitleOverlay(exoPlayer, it, subtitleTiming, subtitleAppearance) }
        }

        if (touchUi && state == PlayerUiState.Playing && !syncOpen && !childLocked) {
            PlayerTouchGestures(
                onTap = {
                    when {
                        tracksOpen -> tracksOpen = false
                        controlsVisible -> controlsVisible = false
                        else -> showControls()
                    }
                },
                onDoubleTap = { zone ->
                    when (zone) {
                        TouchZone.LEFT -> touchSeekBy(-TOUCH_SEEK_STEP_MS)
                        TouchZone.RIGHT -> touchSeekBy(TOUCH_SEEK_STEP_MS)
                        TouchZone.CENTER -> touchPlayPause()
                    }
                },
                onScrubStart = ::touchScrubStart,
                onScrub = { fraction ->
                    scrubMs?.let { from -> touchScrubTo(from + (fraction * TOUCH_SCRUB_FULL_WIDTH_MS).toLong()) }
                },
                onScrubEnd = ::touchScrubEnd,
                onBrightness = ::touchAdjustBrightness,
                onVolume = ::touchAdjustVolume,
                onAdjustEnd = {
                    touchVolume = -1f
                    touchBrightness = -1f
                },
                onPinch = { zoomIn -> viewModel.setVideoFit(if (zoomIn) com.shieldstream.tv.model.VideoFit.ZOOM else com.shieldstream.tv.model.VideoFit.FIT) },
            )
        }

        if (state == PlayerUiState.Playing) {
            AnimatedVisibility(visible = controlsVisible && !syncOpen, enter = fadeIn(), exit = fadeOut()) {
                ControlsOverlay(
                    title = title,
                    videoInfo = videoInfo,
                    playback = playback,
                    scrubMs = scrubMs,
                    thumbs = thumbs,
                    thumbnailsEnabled = thumbnailsEnabled,
                    touch = touchUi,
                    onTouchSeek = { fraction, done ->
                        val duration = playback.durationMs
                        // fraction < 0 = el dedo se ha soltado: se salta a donde estaba.
                        if (duration > 0 && fraction >= 0f) touchScrubTo((fraction * duration).toLong())
                        if (done) touchScrubEnd()
                    },
                )
            }
            if (touchUi) {
                PlayerTouchButtons(
                    visible = controlsVisible && !syncOpen && !tracksOpen && scrubMs == null && !childLocked,
                    isPlaying = playback.isPlaying,
                    onBack = onBack,
                    onPlayPause = ::touchPlayPause,
                    onSeekBack = { touchSeekBy(-TOUCH_SEEK_STEP_MS) },
                    onSeekForward = { touchSeekBy(TOUCH_SEEK_STEP_MS) },
                    onOpenTracks = {
                        controlsVisible = false
                        tracksOpen = true
                    },
                    onLock = {
                        PlayerChildLock.locked.value = true
                        controlsVisible = false
                        tracksOpen = false
                        lockHint++
                        // Con el foco en el reproductor, los botones de volumen pasan por handleKey y se bloquean.
                        runCatching { focusRequester.requestFocus() }
                    },
                )
            }
            seekFeedback?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        if (syncOpen && state == PlayerUiState.Playing) {
            SubtitleSyncBar(timing = subtitleTiming, status = syncStatus, modifier = Modifier.align(Alignment.TopCenter))
        }

        if (state == PlayerUiState.Playing && !syncOpen) {
            notice?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        if (tracksOpen && state == PlayerUiState.Playing) {
            player?.let {
                TracksPanel(
                    player = it,
                    external = externalSubs,
                    onSearchExternal = { lang -> viewModel.searchExternalSubtitles(lang) },
                    onPickExternal = { sub -> viewModel.downloadExternalSubtitle(sub) },
                    onCloseExternal = { viewModel.resetExternalSubtitles() },
                    externalLabel = activeExternal?.label,
                    syncDetail = syncStatus.describe(texts).ifBlank { subtitleTiming.describe(texts) },
                    appearance = subtitleAppearance,
                    onAppearanceChange = viewModel::setSubtitleAppearance,
                    onExternalOff = { viewModel.disableExternalSubtitle() },
                    onManualSubtitleSelection = viewModel::onManualSubtitleSelection,
                    onOpenSync = {
                        tracksOpen = false
                        syncOpen = true
                    },
                    playbackSpeed = playbackSpeed,
                    videoFit = videoFit,
                    onCycleVideoFit = viewModel::cycleVideoFit,
                    cardStyle = tracksPanelCard,
                    onCycleSpeed = viewModel::cyclePlaybackSpeed,
                    sleepTimer = sleepTimer,
                    sleepTimerRemainingMinutes = sleepTimerRemainingMinutes,
                    onCycleSleepTimer = viewModel::cycleSleepTimer,
                    onAudioSelected = viewModel::rememberAudioLanguage,
                    onSubtitleSelected = viewModel::rememberSubtitleLanguage,
                    onSubtitlesOff = viewModel::rememberSubtitlesOff,
                )
            }
        }

        val pillBottom by animateDpAsState(if (controlsVisible) 116.dp else 48.dp, label = "pillBottom")
        val pillModifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = pillBottom)
        if (state == PlayerUiState.Playing && !tracksOpen) {
            (nextUp as? NextUpUiState.Showing)?.let { next ->
                val target = if (next.secondsLeft <= NEXT_FILL_SECONDS) 1f - next.secondsLeft / NEXT_FILL_SECONDS.toFloat() else 0f
                val fill by animateFloatAsState(target, animationSpec = tween(500, easing = LinearEasing), label = "nextFill")
                PlayerPill(
                    localizedStringResource(StringsR.string.player_next_episode), AppIcons.SkipNext, fill,
                    if (touchUi) pillModifier.clickable { viewModel.playNextNow() } else pillModifier,
                )
            }
            if (skipVisible) {
                (skipPrompt as? SkipPromptUiState.Showing)?.let { skip ->
                    PlayerPill(skip.label, AppIcons.FastForward, null, if (touchUi) pillModifier.clickable { viewModel.skipSegment() } else pillModifier)
                }
            }
        }

        when (val s = state) {
            is PlayerUiState.Resolving -> StatusOverlay(title = title, message = s.message, loading = true)
            is PlayerUiState.Error -> StatusOverlay(title = title, message = s.message, loading = false)
            PlayerUiState.Playing -> Unit
        }

        if (childLocked) {
            PlayerLockOverlay(
                hint = lockHint,
                onUnlock = {
                    PlayerChildLock.locked.value = false
                    showControls()
                },
            )
        }
    }
}

private const val NEXT_FILL_SECONDS = 10
private const val SKIP_VISIBLE_MS = 10_000L

@Composable
private fun PlayerPill(text: String, icon: ImageVector, fill: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.62f))
            .drawBehind {
                if (fill != null && fill > 0f) drawRect(Color.White.copy(alpha = 0.28f), size = size.copy(width = size.width * fill))
            }
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), shape)
            .padding(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    }
}

private fun seekStepMs(repeatCount: Int): Long = when {
    repeatCount > 20 -> 60_000L
    repeatCount > 5 -> 30_000L
    else -> 10_000L
}

@Stable
private class PlaybackState {
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
}

@Composable
private fun rememberPlaybackState(player: ExoPlayer?): PlaybackState {
    val state = remember { PlaybackState() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

        }
        player?.addListener(listener)
        state.isPlaying = player?.isPlaying == true
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            player?.let {
                state.positionMs = it.currentPosition
                state.durationMs = it.duration.coerceAtLeast(0L)
                state.bufferedMs = it.bufferedPosition
            }
            delay(500)
        }
    }
    return state
}

@Composable
private fun ControlsOverlay(
    title: String,
    videoInfo: PlaybackVideoInfo?,
    playback: PlaybackState,
    scrubMs: Long?,
    thumbs: SeekThumbnails?,
    thumbnailsEnabled: Boolean,
    touch: Boolean = false,
    onTouchSeek: (fraction: Float, done: Boolean) -> Unit = { _, _ -> },
) {
    val shownMs = scrubMs ?: playback.positionMs
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent))),
        )
        Column(
            // Con el dedo, a los lados del título van los botones de volver y de audio/subtítulos.
            Modifier.align(Alignment.TopStart).padding(start = if (touch) 72.dp else 48.dp, top = 28.dp, end = if (touch) 136.dp else 48.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            videoInfo?.let {
                Spacer(Modifier.height(8.dp))
                PlaybackMediaBadges(it)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
        ) {
            if (scrubMs != null) {
                SeekThumbnailStrip(thumbs = thumbs, targetMs = scrubMs, durationMs = playback.durationMs)
                Spacer(Modifier.height(18.dp))
            }
            if (touch) TouchSeekArea(onTouchSeek) { SeekBar(playback, shownMs) } else SeekBar(playback, shownMs)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (playback.isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = if (playback.isPlaying) localizedStringResource(StringsR.string.player_pause) else localizedStringResource(StringsR.string.player_play),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = formatTime(shownMs),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                // Las ayudas del mando (OK, flechas) no valen con el dedo.
                if (!touch) Text(
                    text = when {
                        scrubMs != null -> localizedStringResource(StringsR.string.player_ok_jump_here_back_return)
                        thumbnailsEnabled -> localizedStringResource(StringsR.string.player_ok_pause_seek_audio_subtitles)
                        else -> localizedStringResource(StringsR.string.player_ok_pause_10_s_audio_subtitles)
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(24.dp))
                Text(
                    text = if (playback.durationMs > 0) "-" + formatTime(playback.durationMs - shownMs) else "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/**
 * Fraction of [durationMs] covered by [ms], or null while the duration is unknown (0 or less).
 * Without this guard an unknown duration made any position render as a full bar.
 */
internal fun seekFraction(ms: Long, durationMs: Long): Float? =
    if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else null

@Composable
private fun SeekBar(playback: PlaybackState, positionMs: Long) {
    val played = seekFraction(positionMs, playback.durationMs)
    val buffered = seekFraction(playback.bufferedMs, playback.durationMs)
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
        if (played != null && buffered != null) {
            Box(Modifier.fillMaxWidth(buffered).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.45f)))
            Box(Modifier.fillMaxWidth(played).height(4.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.Accent))
            Box(
                Modifier
                    .offset(x = maxWidth * played - 7.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent),
            )
        }
    }
}

@Composable
private fun StatusOverlay(title: String, message: String, loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
        }
        if (loading) {
            CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 640.dp),
        )
        if (!loading) {
            Spacer(Modifier.height(16.dp))
            Text(localizedStringResource(StringsR.string.player_press_back_to_pick_another_source), color = Color(0xFFB3B3B3), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TouchSeekArea(onSeek: (fraction: Float, done: Boolean) -> Unit, bar: @Composable () -> Unit) {
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

