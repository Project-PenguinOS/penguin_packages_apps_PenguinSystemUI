/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.composable.media

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.notifications.ui.composable.LocalLockscreenStackTopListener
import com.android.systemui.penguin.LockscreenPlayer
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.icon.domain.interactor.NotificationIconsInteractor
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map

private val PillMargin = 16.dp

// The gap ConstrainedNotificationStack keeps under the stack, above the lock icon.
private val StackBottomGap = 16.dp

class ExpandableLockscreenPlayer
@Inject
constructor(iconsInteractor: NotificationIconsInteractor) : LockscreenPlayer {

    private val nowBarNotifications =
        iconsInteractor
            .filteredNotifSet(showDismissed = false, showRepliedMessages = false)
            .map { icons ->
                icons.map { icon ->
                    // Pipeline keys are "user|package|id|tag|uid".
                    NowBarNotification(
                        key = icon.notifKey,
                        packageName = icon.notifKey.split('|').getOrElse(1) { icon.notifKey },
                        icon = icon.shelfIcon ?: icon.statusBarIcon,
                    )
                }
            }

    override val isEnabled: Boolean
        get() = LockscreenMediaExpansion.artEnabled

    override val expansion: Float
        get() = LockscreenMediaExpansion.fraction

    override val nowBarFraction: Float
        get() = LockscreenMediaExpansion.nowBarFraction

    override val canFoldIntoNowBar: Boolean
        get() =
            with(LockscreenMediaExpansion) {
                nowBarEnabled && mediaVisible && artwork != null && !minimized
            }

    override fun foldIntoNowBar() {
        LockscreenMediaExpansion.minimize()
    }

    @Composable
    override fun ArtGestures() {
        LockscreenArtGestures()
    }

    @Composable
    override fun NowBar(modifier: Modifier) {
        val notifications by
            nowBarNotifications.collectAsStateWithLifecycle<List<NowBarNotification>?>(null)
        LockscreenNowBar(notifications, modifier)
    }

    @Composable
    override fun ObserveSettings() {
        LockscreenMediaExpansion.ObserveSettings()
    }

    override fun setMediaVisible(visible: Boolean) {
        LockscreenMediaExpansion.mediaVisible = visible
    }

    @Composable
    override fun Card(
        viewModelFactory: MediaViewModel.Factory,
        behavior: MediaUiBehavior,
        modifier: Modifier,
    ) {
        LockscreenMediaCard(
            viewModelFactory = viewModelFactory,
            behavior = behavior,
            modifier = modifier,
        )
    }

    @Composable
    override fun ExpandedArt(
        alpha: () -> Float,
        covered: () -> Float,
        unlocking: () -> Float,
        cardAlpha: () -> Float,
    ) {
        LockscreenExpandedMediaArt(alpha = alpha, covered = covered, unlocking = unlocking)
        val latestCardAlpha by rememberUpdatedState(cardAlpha)
        LaunchedEffect(Unit) {
            snapshotFlow { latestCardAlpha() }.collect { LockscreenMediaExpansion.cardAlpha = it }
        }
    }

    @Composable
    override fun ColumnScope.SmallClockColumn(
        notificationsActive: Boolean,
        clock: @Composable () -> Unit,
        media: @Composable ColumnScope.() -> Unit,
        notifications: @Composable (Modifier) -> Unit,
    ) {
        DisposableEffect(Unit) {
            LockscreenMediaExpansion.supported = true
            onDispose { LockscreenMediaExpansion.supported = false }
        }
        SideEffect { LockscreenMediaExpansion.notificationsShowing = notificationsActive }
        val expansion = LockscreenMediaExpansion.fraction
        val notifications by
            animateFloatAsState(
                if (notificationsActive) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "ExpandedMediaNotifications",
            )
        // Like iOS, the expanded player sits at the bottom with the notifications collapsed into
        // the minimized pill below it: their share of the weighted height is just the pill slot.
        var artHeight by remember { mutableFloatStateOf(0f) }
        var notificationsHeight by remember { mutableFloatStateOf(0f) }
        val pillSlot =
            with(LocalDensity.current) {
                (dimensionResource(R.dimen.notification_shelf_height) + PillMargin).toPx()
            }
        val weighted = notificationsHeight + if (expansion > 0f) artHeight else 0f
        val pillShare = if (weighted > 0f) (pillSlot / weighted).coerceIn(0f, 0.5f) else 0.22f
        val folded by remember { derivedStateOf { LockscreenMediaExpansion.nowBarFolded } }
        val artWeight = expansion * (1f - pillShare * notifications * if (folded) 0f else 1f)
        val collapseOnTap =
            Modifier.pointerInput(Unit) { detectTapGestures { LockscreenMediaExpansion.collapse() } }
        Box(Modifier.collapsible { 1f - LockscreenMediaExpansion.fraction.within(0f, 0.6f) }) {
            clock()
        }
        if (expansion > 0f) {
            LockscreenExpandedClock(
                Modifier.collapsible { LockscreenMediaExpansion.fraction.within(0.3f, 1f) }
                    .then(collapseOnTap)
                    .padding(top = 8.dp, bottom = 16.dp)
            )
        }
        if (artWeight > 0.001f) {
            Spacer(
                Modifier.weight(artWeight)
                    .fillMaxWidth()
                    .onSizeChanged { artHeight = it.height.toFloat() }
                    .onGloballyPositioned {
                        LockscreenMediaExpansion.artSlotBounds =
                            Rect(it.positionInWindow(), it.size.toSize())
                    }
                    .then(collapseOnTap)
                    // Pulled down again, the folded artwork goes and leaves the wallpaper.
                    .onPullDown({ LockscreenMediaExpansion.nowBarShowsArt }) {
                        LockscreenMediaExpansion.dismissArt()
                    }
            )
        }
        // The notifications rest at the bottom, so the collapsed player moves down to sit on them
        // instead of leaving the gap between the two. Without notifications it rests where their
        // bottom would be, so it does not jump up and down as they come and go.
        var stackTop by remember { mutableFloatStateOf(Float.NaN) }
        var slotTop by remember { mutableFloatStateOf(Float.NaN) }
        val stackBottomGap = with(LocalDensity.current) { StackBottomGap.toPx() }
        Column(
            Modifier.fillMaxWidth().offset {
                val alone = (notificationsHeight - stackBottomGap).coerceAtLeast(0f)
                val onStack =
                    if (stackTop.isNaN() || slotTop.isNaN()) alone
                    else (stackTop - slotTop).coerceAtLeast(0f)
                val drop = alone + (onStack - alone) * notifications
                IntOffset(0, (drop * (1f - expansion)).roundToInt())
            }
        ) {
            media()
        }
        CompositionLocalProvider(LocalLockscreenStackTopListener provides { stackTop = it }) {
            notifications(
                Modifier.weight(maxOf(1f - artWeight, 0.001f))
                    .onSizeChanged { notificationsHeight = it.height.toFloat() }
                    .onGloballyPositioned { slotTop = it.positionInWindow().y }
            )
        }
    }
}
