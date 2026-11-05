/*
 * Copyright (C) 2026 The PenguinOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.keyguard.ui.composable.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.systemui.penguin.CosFeature
import com.android.systemui.penguin.DepthSubject
import com.android.systemui.penguin.rememberCosFeature
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private val CoveredBlur = 40.dp
private const val ART_FADE_MS = 250

private val CoverGap = 10.dp

private val CoverMin = 96.dp

private const val TallArtAspect = 3f / 4f
private const val TallArtFadeStart = 0.82f
private const val TallArtScreenFraction = 0.78f

@Composable
fun LockscreenExpandedMediaArt(
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    covered: () -> Float = { 0f },
    unlocking: () -> Float = { 0f },
) {
    val state = LockscreenMediaExpansion
    LaunchedEffect(Unit) {
        snapshotFlow { state.target }
            .distinctUntilChanged()
            .collect { target ->
                val value = if (target) 1f else 0f
                if (state.animateNextChange) {
                    state.animateNextChange = false
                    state.progress.animateTo(value, state.Spec)
                } else {
                    state.progress.snapTo(value)
                }
            }
    }
    // Swiped away, the artwork only fades: the layout lets go of it in one step once it is
    // invisible, instead of playing the collapse back to the folded player on every frame.
    LaunchedEffect(Unit) {
        if (state.artDismissing) {
            state.artDismissed = true
            state.artDismissing = false
        }
        state.artFade.snapTo(1f)
        snapshotFlow { state.artDismissing }
            .filter { it }
            .collect {
                state.artFade.animateTo(0f, tween(durationMillis = ART_FADE_MS))
                state.artDismissed = true
                state.artDismissing = false
                snapshotFlow { state.fraction }.first { it == 0f }
                state.artFade.snapTo(1f)
            }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { state.nowBarTarget }
            .distinctUntilChanged()
            .collect { target ->
                val value = if (target) 1f else 0f
                if (state.animateNowBar) {
                    state.animateNowBar = false
                    state.nowBarProgress.animateTo(value, state.NowBarSpec)
                } else {
                    state.nowBarProgress.snapTo(value)
                }
            }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { state.fraction > 0f || (state.artEnabled && state.mediaVisible) }
            .distinctUntilChanged()
            .collect { DepthSubject.get()?.setExpandedMediaArtVisible(it) }
    }

    val latestAlpha by rememberUpdatedState(alpha)
    LaunchedEffect(Unit) { snapshotFlow { latestAlpha() }.collect { state.lockscreenAlpha = it } }

    val context = LocalContext.current
    var tallArt by remember(state.track) { mutableStateOf<File?>(null) }
    var squareArt by remember(state.track) { mutableStateOf<File?>(null) }
    var tallRendering by remember(state.track) { mutableStateOf(false) }
    var tallFailed by remember(state.track) { mutableStateOf(false) }
    val track = state.track
    val cos = rememberCosFeature(CosFeature.LockscreenMedia)
    val lookupVisible = state.target && alpha() > 0f
    LaunchedEffect(
        state.artEnabled,
        state.motionArtEnabled,
        track,
        lookupVisible,
        tallFailed,
        cos,
    ) {
        if (!state.artEnabled || !state.motionArtEnabled || track == null) {
            tallArt = null
            squareArt = null
            tallRendering = false
            return@LaunchedEffect
        }
        if (!lookupVisible) return@LaunchedEffect
        val (artist, title) = track
        // Retry transient failures while visible, and on the next lockscreen visit.
        repeat(3) { attempt ->
            var square = if (cos) MotionArt.find(context, artist, title, MotionArt.Kind.SQUARE) else null
            val tall =
                if (square != null || tallFailed) null
                else MotionArt.find(context, artist, title, MotionArt.Kind.TALL)
            if (tall == null && square == null && !cos) {
                square = MotionArt.find(context, artist, title, MotionArt.Kind.SQUARE)
            }
            tallArt = tall
            // The cover only hides behind a tall video that is still shown.
            if (tall == null) tallRendering = false
            squareArt = square
            if (tall != null || square != null) return@LaunchedEffect
            if (attempt < 2) delay(15_000L * (attempt + 1))
        }
    }
    // Tablets draw the cover far larger than the session's bitmap: use the store's full-size one.
    var cover by remember(state.track) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state.artEnabled, state.motionArtEnabled, track, lookupVisible) {
        if (!state.artEnabled || !state.motionArtEnabled || track == null || !lookupVisible) {
            return@LaunchedEffect
        }
        if (cover == null) {
            cover = MotionArt.findCover(context, track.first, track.second)?.asImageBitmap()
        }
    }
    val coverAlpha by animateFloatAsState(if (tallRendering) 0f else 1f, label = "cover")
    // Folding in from the compact player, the artwork fades in with the pill.
    val reveal = {
        if (!state.expandedTarget && state.nowBarFraction > 0f) state.nowBarReveal else 1f
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableStateOf(0f) }
    var width by remember { mutableStateOf(0f) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                height = it.size.height.toFloat()
                width = it.size.width.toFloat()
            }
            .graphicsLayer {
                this.alpha = alpha() * state.artFade.value
                val radius = CoveredBlur.toPx() * covered()
                renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Clamp) else null
            }
    ) {
        val f = state.fraction
        val artwork = state.artwork
        if (f <= 0f || artwork == null) return@Box
        val density = LocalDensity.current
        if (cos) {
            CosImmersiveArt(
                artwork,
                squareArt ?: tallArt,
                Modifier.fillMaxSize().graphicsLayer { this.alpha = state.fraction * reveal() },
                cover,
            )
            return@Box
        }

        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = state.fraction * reveal() }) {
            // Like iOS, the tall video fades into a blur of the cover rather than into black.
            Artwork(artwork, Modifier.fillMaxSize().blur(60.dp))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            tallArt?.let {
                // Like iOS, the video covers most of the screen (cropping its sides if needed) and
                // only fades out near its lower edge, behind the player.
                val videoHeight = maxOf(width / TallArtAspect, height * TallArtScreenFraction)
                MotionArtVideo(
                    it,
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(with(density) { videoHeight.toDp() })
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    TallArtFadeStart to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                    crop = true,
                    onError = { tallArt = null; tallRendering = false; tallFailed = true },
                ) {
                    tallRendering = true
                }
            }
        }

        val gap = with(density) { CoverGap.toPx() }
        val rise = Offset(0f, unlocking() * height)
        // Layouts without a reserved art slot (the wide tablet ones) get a cover of their own in the
        // start column, beside the clock in the other one. Folded or not and in either orientation
        // it keeps that slot, so it neither covers the clock nor moves with the fold.
        val reserved =
            if (state.artSlotBounds.isEmpty) {
                Rect(0.06f * width, 0.30f * height, 0.48f * width, 0.86f * height).translate(rise)
            } else {
                state.artSlotBounds.translate(-origin + rise)
            }
        // Folded, the cover ends above the now bar, which sits lower than the player did.
        val pill = state.nowBarBounds
        val slot =
            if (state.nowBarFolded && !pill.isEmpty) {
                val bottom = pill.top - origin.y - gap + rise.y
                Rect(reserved.left, reserved.top, reserved.right, minOf(reserved.bottom, bottom))
            } else {
                reserved
            }
        // Like iOS, centre the cover between the clock and the player.
        val side = minOf(slot.width, slot.height - 2f * gap).coerceAtLeast(0f)
        val top = slot.top + (slot.height - side) / 2f
        val to =
            Rect(
                left = slot.left + (slot.width - side) / 2f,
                top = top,
                right = slot.left + (slot.width + side) / 2f,
                bottom = top + side,
            )
        val from = state.thumbnailBounds.translate(-origin + rise)
        val slotInWindow = slot.translate(origin)
        SideEffect { state.artBounds = slotInWindow }
        if (slot.isEmpty || from.isEmpty) return@Box
        val rect =
            Rect(
                lerp(from.left, to.left, f),
                lerp(from.top, to.top, f),
                lerp(from.right, to.right, f),
                lerp(from.bottom, to.bottom, f),
            )
        val minSide = with(density) { CoverMin.toPx() }
        val size = with(density) { DpSize(rect.width.toDp(), rect.height.toDp()) }
        Box(
            Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(size)
                .graphicsLayer {
                    this.alpha =
                        lerp(1f, side.within(minSide * 0.6f, minSide), f) * coverAlpha * reveal()
                }
                .clip(RoundedCornerShape(lerp(10f, 22f, f).dp))
                .pointerInput(Unit) { detectTapGestures { state.collapse() } }
        ) {
            cover?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } ?: Artwork(artwork, Modifier.fillMaxSize())
            squareArt?.let { MotionArtVideo(it, Modifier.fillMaxSize()) }
        }
    }
}
