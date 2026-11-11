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

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import com.android.systemui.penguin.LockscreenStack
import kotlin.math.roundToInt

object LockscreenMediaExpansion {
    var enabled by mutableStateOf(false)
        private set

    var expanded by mutableStateOf(false)
        private set

    val progress = Animatable(0f)

    /** Folded into the now bar: the card and notifications give way to the artwork. */
    var minimized by mutableStateOf(false)
        private set

    var nowBarEnabled by mutableStateOf(false)
        private set

    val nowBarProgress = Animatable(0f)

    /** The folded artwork can be turned off, leaving the wallpaper behind the now bar. */
    var nowBarArtEnabled by mutableStateOf(true)
        private set

    /** The folded artwork was swiped away; it comes back with the next fold. */
    var artDismissed by mutableStateOf(false)

    /** Set while the swiped-away artwork fades out, before the layout lets go of it. */
    var artDismissing by mutableStateOf(false)

    val artFade = Animatable(1f)

    /** The selected player card, kept for the now bar while the card itself is folded away. */
    var card by mutableStateOf<MediaCardViewModel?>(null)

    var supported by mutableStateOf(false)

    /** Set while the player card is on screen, in any lock screen layout. */
    var cardShown by mutableStateOf(false)

    var mediaVisible by mutableStateOf(false)

    var notificationsShowing by mutableStateOf(false)

    var artwork by mutableStateOf<Icon?>(null)

    var motionArtEnabled by mutableStateOf(false)
        private set

    var track by mutableStateOf<Pair<String, String>?>(null)

    var lockscreenAlpha by mutableStateOf(1f)

    var cardAlpha by mutableStateOf(1f)

    var artSlotBounds by mutableStateOf(Rect.Zero)

    var thumbnailBounds by mutableStateOf(Rect.Zero)

    var nowBarBounds by mutableStateOf(Rect.Zero)

    /** Where the artwork's cover goes, in window coordinates. */
    var artBounds by mutableStateOf(Rect.Zero)

    internal var animateNextChange = false

    internal var animateNowBar = false

    private var resolver: ContentResolver? = null

    val fraction: Float
        get() = progress.value.coerceIn(0f, 1f)

    val nowBarFraction: Float
        get() = nowBarProgress.value.coerceIn(0f, 1f)

    /**
     * The fold changes the layout once, halfway, while the card and the stack have faded out and
     * the pill and artwork have not yet faded in, instead of on every frame.
     */
    val nowBarFolded: Boolean
        get() = nowBarFraction >= 0.5f

    /** How far the second half of the fold has faded the pill and artwork in. */
    val nowBarReveal: Float
        get() = ((nowBarFraction - 0.5f) * 2f).coerceIn(0f, 1f)

    /** Either way of showing the large artwork is turned on. */
    val artEnabled: Boolean
        get() = enabled || nowBarEnabled

    private val canExpand: Boolean
        get() = supported || cardShown

    val nowBarTarget: Boolean
        get() = nowBarEnabled && minimized && canExpand && mediaVisible && artwork != null

    val expandedTarget: Boolean
        get() = enabled && expanded && canExpand && mediaVisible && artwork != null

    /**
     * The folded now bar shows the artwork across the screen. It follows the fold's progress rather
     * than [minimized], so the artwork also fades out with the unfold.
     */
    val nowBarShowsArt: Boolean
        get() =
            nowBarEnabled &&
                nowBarArtEnabled &&
                !artDismissed &&
                nowBarFolded &&
                canExpand &&
                mediaVisible &&
                artwork != null

    /**
     * Once folded halfway, the now bar decides about the artwork, so it can be swiped away; folding
     * from and unfolding to the expanded player keep its artwork up meanwhile.
     */
    val target: Boolean
        get() = nowBarShowsArt || expandedTarget && !(minimized && nowBarFolded)

    fun toggle() {
        if (!enabled || !canExpand || artwork == null) return
        persistExpanded(!expanded)
    }

    fun collapse() {
        // A tap over the art with the lock screen notification list open folds that list only.
        if (LockscreenStack.isExpanded()) return
        if (minimized) restore() else if (expanded) persistExpanded(false)
    }

    /** Unfolding returns to the expanded player when it was folded from there. */
    fun minimize() {
        if (!nowBarEnabled || artwork == null || minimized) return
        animateNowBar = true
        artDismissed = false
        minimized = true
        persist(NOW_BAR_MINIMIZED, true)
    }

    fun restore() {
        if (!minimized) return
        animateNowBar = true
        minimized = false
        persist(NOW_BAR_MINIMIZED, false)
    }

    fun dismissArt() {
        if (!minimized || artDismissed) return
        artDismissing = true
    }

    private fun persistExpanded(value: Boolean) {
        animateNextChange = true
        expanded = value
        persist(Settings.System.LS_MEDIA_EXPANDED, value)
    }

    private fun persist(key: String, value: Boolean) {
        resolver?.let {
            Settings.System.putIntForUser(it, key, if (value) 1 else 0, UserHandle.USER_CURRENT)
        }
    }

    @Composable
    fun ObserveSettings() {
        val context = LocalContext.current
        DisposableEffect(context) {
            val contentResolver = context.contentResolver
            resolver = contentResolver
            fun read() {
                enabled =
                    Settings.System.getIntForUser(
                        contentResolver,
                        Settings.System.LS_MEDIA_EXPAND,
                        0,
                        UserHandle.USER_CURRENT,
                    ) != 0
                expanded =
                    Settings.System.getIntForUser(
                        contentResolver,
                        Settings.System.LS_MEDIA_EXPANDED,
                        0,
                        UserHandle.USER_CURRENT,
                    ) != 0
                motionArtEnabled =
                    Settings.System.getIntForUser(
                        contentResolver,
                        Settings.System.LS_MEDIA_MOTION_ART,
                        0,
                        UserHandle.USER_CURRENT,
                    ) != 0
                nowBarEnabled =
                    Settings.System.getIntForUser(
                        contentResolver,
                        NOW_BAR,
                        1,
                        UserHandle.USER_CURRENT,
                    ) != 0
                nowBarArtEnabled =
                    Settings.System.getIntForUser(
                        contentResolver,
                        NOW_BAR_ART,
                        1,
                        UserHandle.USER_CURRENT,
                    ) != 0
                minimized =
                    Settings.System.getIntForUser(
                        contentResolver,
                        NOW_BAR_MINIMIZED,
                        0,
                        UserHandle.USER_CURRENT,
                    ) != 0
            }
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = read()
                }
            read()
            listOf(
                    Settings.System.LS_MEDIA_EXPAND,
                    Settings.System.LS_MEDIA_EXPANDED,
                    Settings.System.LS_MEDIA_MOTION_ART,
                    NOW_BAR,
                    NOW_BAR_MINIMIZED,
                    NOW_BAR_ART,
                )
                .forEach {
                    contentResolver.registerContentObserver(
                        Settings.System.getUriFor(it),
                        false,
                        observer,
                        UserHandle.USER_ALL,
                    )
                }
            onDispose { contentResolver.unregisterContentObserver(observer) }
        }
    }

    private const val NOW_BAR = "ls_now_bar"
    private const val NOW_BAR_MINIMIZED = "ls_now_bar_minimized"
    private const val NOW_BAR_ART = "ls_now_bar_art"

    /** ColorOS folds the cards into the pill in about a third of a second, then shows the pill. */
    internal val NowBarSpec = tween<Float>(durationMillis = 550, easing = FastOutSlowInEasing)

    internal val Spec = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

internal fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

internal fun Float.within(start: Float, end: Float) = ((this - start) / (end - start)).coerceIn(0f, 1f)

/**
 * Runs [action] on a downward pull of [distance] or more. Only a downward pull is taken, so an
 * upward swipe still unlocks and, when [enabled] is false, a pull still opens the shade.
 */
internal fun Modifier.onPullDown(
    enabled: () -> Boolean,
    distance: Dp = 48.dp,
    action: () -> Unit,
): Modifier =
    pointerInput(Unit) {
        val threshold = distance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var pulled = 0f
            val drag =
                awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over > 0f && enabled()) {
                        change.consume()
                        pulled = over
                    }
                } ?: return@awaitEachGesture
            verticalDrag(drag.id) { change ->
                pulled += change.positionChange().y
                change.consume()
            }
            if (pulled > threshold) action()
        }
    }

internal fun Modifier.collapsible(fraction: () -> Float): Modifier =
    this.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0))
            val height = (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()
            layout(placeable.width, height) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            val f = fraction()
            alpha = f
            clip = f < 1f
        }
