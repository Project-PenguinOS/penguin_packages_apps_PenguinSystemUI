/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.composable.clock

import android.app.WallpaperManager
import android.content.Context
import android.database.ContentObserver
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.android.compose.modifiers.onUnplaced
import com.android.systemui.clocks.ClockStyle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.data.repository.InWindowLauncherUnlockAnimationRepository
import com.android.systemui.penguin.CosFeature
import com.android.systemui.penguin.CosFeatures
import com.android.systemui.penguin.LockscreenClockFace
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.clocks.DEFAULT_CLOCK_ID
import com.android.systemui.shared.clocks.FluidDigits
import com.android.systemui.shared.system.smartspace.SmartspaceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

data class FluidClockPose(val left: Float, val top: Float, val textSize: Float, val height: Float) {
    fun lerp(to: FluidClockPose, f: Float) =
        FluidClockPose(
            left + (to.left - left) * f,
            top + (to.top - top) * f,
            textSize + (to.textSize - textSize) * f,
            height + (to.height - height) * f,
        )
}

@SysUISingleton
class CosFluidClockFace
@Inject
constructor(
    @Application context: Context,
    private val clockRegistry: ClockRegistry,
    private val launcherState: InWindowLauncherUnlockAnimationRepository,
) : LockscreenClockFace {
    private var settingOn by mutableStateOf(false)
    private var defaultFace by mutableStateOf(true)
    private var customStyle by mutableStateOf(0)
    private var handedOff by mutableStateOf(false)
    // Each nested scene owns its placeholder. Old/new scenes can coexist during a switch.
    private data class ClockRegion(val large: Boolean, val bounds: ComposeRect)
    private val regions = mutableStateMapOf<Any, ClockRegion>()
    private var screenOffset = Offset.Zero
    private var lastDigits: Rect? = null
    private var lastHeight = 0
    // The launcher's flight shows a few frames after the handoff, and tells us once it has drawn
    // by sending its clock state again. Until then this face keeps drawing the clock where it
    // left it, so the clock never blinks out. A short timeout covers a launcher that never says.
    private var stateAtHandoff: SmartspaceState? = null
    private var lastPose: FluidClockPose? = null
    private var lastTime = ""
    private var lastAlpha = 0f
    private var lastTint = WHITE
    private var holdAlpha by mutableFloatStateOf(1f)

    init {
        val resolver = context.contentResolver
        val read = {
            settingOn = CosFeatures.isOn(context, CosFeature.Clock)
            customStyle = Settings.Secure.getInt(resolver, ClockStyle.CLOCK_STYLE_KEY, 0)
        }
        read()
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = read()
            }
        resolver.registerContentObserver(Settings.Secure.getUriFor(CosFeature.ALL), false, observer)
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(CosFeature.Clock.key),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(ClockStyle.CLOCK_STYLE_KEY),
            false,
            observer,
        )
        val face = { defaultFace = clockRegistry.currentClockId == DEFAULT_CLOCK_ID }
        face()
        clockRegistry.registerClockChangeListener(
            object : ClockRegistry.ClockChangeListener {
                override fun onCurrentClockChanged() = face()
            }
        )
    }

    override val cosUnlock: Boolean
        get() = settingOn

    override val isEnabled: Boolean
        get() = cosUnlock && defaultFace && customStyle == 0

    @Composable
    override fun Placeholder(large: Boolean, modifier: Modifier) {
        val density = LocalDensity.current
        val owner = remember(large) { Any() }
        DisposableEffect(owner) { onDispose { regions.remove(owner) } }
        val sized =
            if (large) modifier
            else modifier.fillMaxWidth().height(with(density) { smallHeight(this).toDp() })
        Box(
            sized.onPlaced { regions[owner] = ClockRegion(large, it.boundsInWindow()) }
                .onUnplaced { regions.remove(owner) }
        )
    }

    @Composable
    override fun Overlay(alpha: () -> Float, unlocking: () -> Float) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val view = LocalView.current
        DisposableEffect(Unit) {
            handedOff = false
            onDispose {
                handedOff = false
                lastDigits = null
                lastPose = null
            }
        }
        val now by
            produceState(System.currentTimeMillis()) {
                while (true) {
                    value = System.currentTimeMillis()
                    delay(60_000 - value % 60_000)
                }
            }
        val tint = remember { wallpaperTint(context) }
        val launcherNow by launcherState.launcherSmartspaceState.collectAsState()
        LaunchedEffect(handedOff) {
            holdAlpha = 1f
            if (handedOff) {
                kotlinx.coroutines.delay(HANDOFF_TIMEOUT_MS)
                animate(1f, 0f, animationSpec = tween(HANDOFF_FADE_MS, easing = LinearEasing)) { v, _ ->
                    holdAlpha = v
                }
            }
        }
        val time = remember(now) {
            DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm", now)
                .toString()
        }
        val date = remember(now) { SimpleDateFormat("d MMM EEE", Locale.getDefault()).format(Date(now)) }
        val digitPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        val datePaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val selected =
                regions.values.lastOrNull { it.large && !it.bounds.isEmpty } ?:
                    regions.values.lastOrNull { !it.bounds.isEmpty } ?: return@Canvas
            val large = selected.large
            val region = selected.bounds
            if (handedOff) {
                val pose = lastPose ?: return@Canvas
                val hold = holdAlpha
                if (hold <= 0f || launcherNow !== stateAtHandoff) return@Canvas
                val bottom = pose.top + pose.textSize * FluidDigits.heightEm(pose.height)
                digitPaint.shader =
                    LinearGradient(0f, pose.top, 0f, bottom, WHITE, lastTint, Shader.TileMode.CLAMP)
                digitPaint.alpha = (255 * lastAlpha * hold).roundToInt()
                drawIntoCanvas {
                    FluidDigits.draw(
                        it.nativeCanvas,
                        lastTime,
                        pose.left,
                        pose.top,
                        pose.textSize,
                        pose.height,
                        digitPaint,
                    )
                }
                return@Canvas
            }
            val location = IntArray(2).also { view.getLocationOnScreen(it) }
            screenOffset = Offset(location[0].toFloat(), location[1].toFloat())
            val a = alpha().coerceIn(0f, 1f)
            if (a <= 0f) return@Canvas
            val p = unlocking().coerceIn(0f, 1f)

            val rest = restPose(region, large, time, digitPaint, density)
            val home = landing() ?: homePose(size.width, size.height, time, digitPaint, density)
            val pose = rest.lerp(home, FOLLOW_HAND * p)
            val h = pose.height.roundToInt().coerceIn(1, 1000)
            val width = FluidDigits.width(time, pose.textSize)
            val bottom = pose.top + pose.textSize * FluidDigits.heightEm(pose.height)
            digitPaint.shader =
                LinearGradient(0f, pose.top, 0f, bottom, WHITE, tint, Shader.TileMode.CLAMP)
            digitPaint.alpha = (255 * a).roundToInt()
            lastDigits =
                Rect(
                    (pose.left + screenOffset.x).roundToInt(),
                    (pose.top + screenOffset.y).roundToInt(),
                    (pose.left + width + screenOffset.x).roundToInt(),
                    (bottom + screenOffset.y).roundToInt(),
                )
            lastHeight = h
            lastPose = pose
            lastTime = time
            lastAlpha = a
            lastTint = tint

            val dateSize = with(density) { (if (large) 17.sp else 14.sp).toPx() }
            datePaint.textSize = dateSize
            datePaint.color = WHITE
            datePaint.alpha = (230 * a * (1f - (p * 4f).coerceAtMost(1f))).roundToInt()
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.drawText(
                    date,
                    pose.left + width / 2f,
                    pose.top - with(density) { 10.dp.toPx() },
                    datePaint,
                )
                FluidDigits.draw(native, time, pose.left, pose.top, pose.textSize, pose.height, digitPaint)
            }
        }
    }

    override fun handoff(): LockscreenClockFace.Handoff? {
        if (!isEnabled || handedOff) return null
        val digits = lastDigits ?: return null
        return LockscreenClockFace.Handoff(Rect(digits), lastHeight)
    }

    override fun onHandedOff() {
        stateAtHandoff = launcherState.launcherSmartspaceState.value
        handedOff = true
    }

    override fun onHandoffFinished() {
        // The overlay can stay composed across unlock -> sleep -> AOD. Its draw suppression
        // must end with the handoff, rather than waiting for DisposableEffect to run again.
        handedOff = false
    }

    private fun smallHeight(density: Density): Float =
        with(density) { 14.sp.toPx() + 10.dp.toPx() + SMALL_SIZE.toPx() * FluidDigits.heightEm(SMALL_HEIGHT) + 12.dp.toPx() }

    private fun restPose(
        region: ComposeRect,
        large: Boolean,
        time: String,
        paint: Paint,
        density: Density,
    ): FluidClockPose =
        with(density) {
            val dateRow = (if (large) 17.sp else 14.sp).toPx() + 10.dp.toPx()
            if (large) {
                val size = minOf(LARGE_SIZE.toPx(), region.width * 0.84f / FluidDigits.width(time, 1f))
                val inset = LARGE_INSET.toPx()
                val room = region.height - inset - dateRow - 24.dp.toPx()
                val height = ((room / size - 0.742f) / 0.003456f).coerceIn(SMALL_HEIGHT, LARGE_HEIGHT)
                FluidClockPose(centred(region, time, size, height, paint), region.top + inset + dateRow, size, height)
            } else {
                val size = SMALL_SIZE.toPx()
                FluidClockPose(centred(region, time, size, SMALL_HEIGHT, paint), region.top + dateRow, size, SMALL_HEIGHT)
            }
        }

    private fun landing(): FluidClockPose? {
        val state = launcherState.launcherSmartspaceState.value ?: return null
        if (!state.visibleOnScreen || state.boundsOnScreen.isEmpty) return null
        val digits = state.boundsOnScreen
        val height = digits.height().toFloat()
        val size = digits.width() / FluidDigits.width("00:00", 1f)
        val fontHeight = FluidDigits.stretchFor(height, size).coerceIn(1f, 1000f)
        return FluidClockPose(digits.left - screenOffset.x, digits.top - screenOffset.y, size, fontHeight)
    }

    private fun homePose(width: Float, height: Float, time: String, paint: Paint, density: Density) =
        with(density) {
            val size = HOME_SIZE.toPx()
            val region = ComposeRect(0f, 0f, width, height)
            FluidClockPose(centred(region, time, size, HOME_HEIGHT, paint), height * 0.57f, size, HOME_HEIGHT)
        }

    private fun centred(region: ComposeRect, time: String, size: Float, height: Float, paint: Paint): Float {
        return region.left + (region.width - FluidDigits.width(time, size)) / 2f
    }

    private fun wallpaperTint(context: Context): Int {
        val manager = WallpaperManager.getInstance(context)
        val colors =
            runCatching {
                    manager.getWallpaperColors(WallpaperManager.FLAG_LOCK)
                        ?: manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                }
                .getOrNull() ?: return LAVENDER
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colors.primaryColor.toArgb(), hsl)
        hsl[1] = hsl[1].coerceAtMost(0.6f)
        hsl[2] = 0.84f
        return ColorUtils.HSLToColor(hsl)
    }

    private companion object {
        val LARGE_SIZE = 108.dp
        val LARGE_INSET = 64.dp
        val SMALL_SIZE = 40.dp
        val HOME_SIZE = 96.dp
        const val LARGE_HEIGHT = 680f
        const val SMALL_HEIGHT = 50f
        const val HOME_HEIGHT = 330f
        const val FOLLOW_HAND = 0.5f
        const val HANDOFF_TIMEOUT_MS = 150L
        const val HANDOFF_FADE_MS = 50
        const val WHITE = 0xFFFFFFFF.toInt()
        const val LAVENDER = 0xFFD9CCFF.toInt()
    }
}
