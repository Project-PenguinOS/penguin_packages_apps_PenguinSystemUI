/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.composable.media

import android.graphics.Bitmap
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.animateFloatAsState
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.android.systemui.common.shared.model.Icon as CommonIcon
import com.android.systemui.qs.composefragment.CosHooks
import com.android.systemui.qs.shared.style.GlassBackdrop
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TopBand = 186.dp
private val BottomBand = 346.dp
private const val BLUR_RADIUS = 120f
private const val BLUR_CURVE = 1.4f
private const val MASK_ALPHA = 0.4f
private const val BAND_ALPHA = 0.6f
private val GlassOverlay = Color(0x66999999)
private val GlassDodge = Color(0x4D737373)

// Tablets keep the phone's proportions: the bands cover the same share of the screen and the
// cover's lower edge sits at the same height, where the bottom band's blur has taken over.
// Blur is low-frequency content: process one sixteenth as many pixels, then upscale.
// The sharp cover/video and the glass masks stay at the display's full resolution.
private const val TabletBlurScale = 0.25f
private const val TABLET_MIN_WIDTH_DP = 600
private const val TopBandFraction = 0.2f
private const val BottomBandFraction = 0.38f
private const val CoverTopFraction = 0.2f
private const val CoverBottomFraction = 0.65f
private const val SideFeather = 0.16f
private const val SeamRampFraction = 0.25f

@Composable
internal fun CosImmersiveArt(
    artwork: CommonIcon,
    video: File?,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
) {
    val maskColor by
        produceState(Color.Transparent, artwork) {
            value = withContext(Dispatchers.Default) { cosMaskColor(artwork) }
        }
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rims = remember { HashMap<Any, RuntimeShader?>() }
    // The cover's mirrored and blurred copies follow the video, not the static art under it.
    val frames = remember(video) { video?.let { VideoFrames() } }
    DisposableEffect(Unit) {
        GlassBackdrop.drawing.value = true
        onDispose { GlassBackdrop.drawing.value = false }
    }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val width = maxWidth
        val height = maxHeight
        val topBand = if (tablet) maxOf(TopBand, height * TopBandFraction) else TopBand
        val bottomBand = if (tablet) maxOf(BottomBand, height * BottomBandFraction) else BottomBand
        // Tablet portrait: the mirrored copy under the cover would show through the bottom band's
        // gradual blur, so the blur is complete by the cover's lower edge instead.
        val coverTop = minOf(height * CoverTopFraction, height * CoverBottomFraction - width)
        val seam = if (tablet && width <= height) coverTop + width else null
        // ColorOS tints only what it blurs, the edges and the glass; the cover itself stays true.
        val base: @Composable (Modifier, File?, Boolean, Float) -> Unit =
            { layer, motion, tinted, renderScale ->
            val renderWidth = width * renderScale
            val renderHeight = height * renderScale
            Box(layer.fillMaxSize()) {
                when {
                    // Tablet landscape: the square fits the height over a full-bleed copy of
                    // itself, its sides fading into that copy instead of leaving bars.
                    tablet && width > height -> WideArt(
                        artwork, cover, motion, frames, renderHeight, maskColor,
                        BLUR_RADIUS * renderScale,
                    )
                    // Wide (tablet landscape) screens fit the square to the height.
                    width > height -> ArtStack(artwork, cover, motion, frames, renderHeight, 0.dp)
                    // Tablet portrait: lift the square so its lower edge sits where the phone's
                    // does, instead of near the bottom of the much less tall screen.
                    tablet -> ArtStack(artwork, cover, motion, frames, renderWidth, coverTop * renderScale)
                    else -> ArtStack(artwork, cover, motion, frames, renderWidth, renderHeight * 0.2f)
                }
                if (tinted) {
                    Box(Modifier.fillMaxSize().background(maskColor.copy(alpha = MASK_ALPHA)))
                }
            }
        }
        base(Modifier, video, false, 1f)
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
                    val blurEnd = seam?.toPx() ?: size.height
                    val blurStart =
                        if (seam != null) blurEnd - size.height * SeamRampFraction
                        else size.height - bottomBand.toPx()
                    val mask = blurMask(size.height, topBand.toPx(), blurStart, blurEnd)
                    onDrawWithContent {
                        drawContent()
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(Rect(Offset.Zero, size), maskPaint)
                            drawRect(mask)
                        for (region in GlassBackdrop.regions.values) {
                            drawRoundRect(
                                Color.Black.copy(alpha = region.alpha),
                                region.bounds.topLeft - origin,
                                region.bounds.size,
                                CornerRadius(region.corner),
                            )
                        }
                            canvas.restore()
                        }
                    }
                }
        ) {
            base(
                if (tablet) Modifier.lowResolutionBlur(BLUR_RADIUS, TabletBlurScale)
                else Modifier.graphicsLayer {
                    renderEffect = BlurEffect(BLUR_RADIUS, BLUR_RADIUS, TileMode.Clamp)
                },
                null,
                true,
                if (tablet) TabletBlurScale else 1f,
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topBand)
                .background(
                    Brush.verticalGradient(
                        listOf(maskColor.copy(alpha = BAND_ALPHA), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomBand)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, maskColor.copy(alpha = BAND_ALPHA))
                    )
                )
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                val regions = GlassBackdrop.regions
                rims.keys.retainAll(regions.keys)
                for ((key, region) in regions) {
                    val rim = rims.getOrPut(key) { CosHooks.createRim() }
                    drawGlass(region, region.bounds.topLeft - origin, rim)
                }
            }
        )
    }
}

private fun DrawScope.drawGlass(region: GlassBackdrop.Region, at: Offset, rim: RuntimeShader?) {
    val size = region.bounds.size
    val shape =
        Path().apply { addRoundRect(RoundRect(Rect(at, size), CornerRadius(region.corner))) }
    clipPath(shape) {
        drawRect(GlassOverlay, at, size, alpha = region.alpha, blendMode = BlendMode.Overlay)
        drawRect(GlassDodge, at, size, alpha = region.alpha, blendMode = BlendMode.ColorDodge)
    }
    if (rim != null) {
        CosHooks.updateRim(rim, size.width, size.height, region.corner, false, true)
        translate(at.x, at.y) { drawRect(ShaderBrush(rim), size = size, alpha = region.alpha) }
    }
}

@Composable
private fun ArtStack(
    artwork: CommonIcon,
    cover: ImageBitmap?,
    video: File?,
    frames: VideoFrames?,
    side: Dp,
    top: Dp,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val mirrored = Modifier.size(side).graphicsLayer { scaleY = -1f }
        Art(artwork, cover, Modifier.offset(y = top - side).then(mirrored), frames)
        Box(Modifier.offset(y = top).size(side)) {
            Art(artwork, cover, Modifier.fillMaxSize(), frames)
            video?.let { MotionArtVideo(it, Modifier.fillMaxSize(), crop = true, frames = frames) }
        }
        Art(artwork, cover, Modifier.offset(y = top + side).then(mirrored), frames)
    }
}

@Composable
private fun WideArt(
    artwork: CommonIcon,
    cover: ImageBitmap?,
    video: File?,
    frames: VideoFrames?,
    side: Dp,
    tint: Color,
    blurRadius: Float,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Art(
            artwork,
            cover,
            Modifier.fillMaxSize().lowResolutionBlur(blurRadius, TabletBlurScale),
            frames,
        )
        Box(Modifier.fillMaxSize().background(tint.copy(alpha = MASK_ALPHA)))
        Box(
            Modifier.size(side)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            SideFeather to Color.Black,
                            1f - SideFeather to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
        ) {
            Art(artwork, cover, Modifier.fillMaxSize(), frames)
            video?.let { MotionArtVideo(it, Modifier.fillMaxSize(), crop = true, frames = frames) }
        }
    }
}

/**
 * Keep the parent's full-size layout and absolute origin, but allocate the image-filter layer at
 * reduced resolution. Absolute placement also keeps the backdrop aligned in RTL layouts. Scaling
 * the blur radius with its input size preserves the screen-space blur after the layer is enlarged.
 */
private fun Modifier.lowResolutionBlur(radius: Float, scale: Float): Modifier {
    val effect = BlurEffect(radius * scale, radius * scale, TileMode.Clamp)
    return this
        .layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val image = measurable.measure(
                Constraints.fixed(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                )
            )
            layout(width, height) { image.place(0, 0) }
        }
        .graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = 1f / scale
            scaleY = 1f / scale
            renderEffect = effect
        }
}

/**
 * The store's full-size [cover] when there is one; the session's far smaller bitmap otherwise.
 * Once the motion art plays, its latest [frames] cover the static art.
 */
@Composable
private fun Art(
    artwork: CommonIcon,
    cover: ImageBitmap?,
    modifier: Modifier,
    frames: VideoFrames? = null,
) {
    Box(modifier) {
        if (cover != null) {
            Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Artwork(artwork, Modifier.fillMaxSize())
        }
        if (frames != null) {
            val shown by animateFloatAsState(if (frames.ready) 1f else 0f, label = "videoFrames")
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val frame = frames.frame ?: return@drawBehind
                    drawImage(
                        frame,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        alpha = shown,
                    )
                }
            )
        }
    }
}

/** Blurred under [top] and from [bottomStart], fully so from [bottomEnd] down. */
private fun blurMask(height: Float, top: Float, bottomStart: Float, bottomEnd: Float): Brush {
    val steps = 8
    val stops = ArrayList<Pair<Float, Color>>()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        stops += (t * top / height) to Color.Black.copy(alpha = (1f - t).pow(BLUR_CURVE))
    }
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val y = bottomStart + t * (bottomEnd - bottomStart)
        stops += (y / height) to Color.Black.copy(alpha = t.pow(BLUR_CURVE))
    }
    return Brush.verticalGradient(*stops.toTypedArray())
}

private fun cosMaskColor(artwork: CommonIcon): Color {
    val drawable = (artwork as? CommonIcon.Loaded)?.drawable ?: return Color.Transparent
    val bitmap = drawable.toBitmap(36, 36, Bitmap.Config.ARGB_8888)
    val rgb = Palette.from(bitmap).generate().dominantSwatch?.rgb ?: return Color.Transparent
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rgb, hsv)
    hsv[1] = maxOf(hsv[1], 0.6f)
    hsv[2] = 0.8f
    return Color(ColorUtils.setAlphaComponent(android.graphics.Color.HSVToColor(hsv), 255))
}
