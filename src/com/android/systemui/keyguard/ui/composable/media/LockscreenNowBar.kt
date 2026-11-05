/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.composable.media

import android.graphics.drawable.Icon
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import kotlin.math.abs
import kotlinx.coroutines.delay

private val PillHeight = 52.dp
private val PillMaxWidth = 260.dp
private val ThumbSize = 36.dp
private val PlaySize = 36.dp
private val AppIconSize = 22.dp
private const val MAX_APP_ICONS = 4
private const val PEEK_MS = 3000L
private const val SETTLE_MS = 2000L
private val Secondary = Color.White.copy(alpha = 0.6f)
private val PillShade = Color.Black.copy(alpha = 0.25f)
private val PillRim = Color.White.copy(alpha = 0.12f)

private const val PAGE_MEDIA = 0
private const val PAGE_NOTIFICATIONS = 1

/** One lock screen notification as the now bar shows it: its app and that app's icon. */
data class NowBarNotification(val key: String, val packageName: String, val icon: Icon?)

/**
 * The COS-style now bar: the player and the notifications folded into one pill between the lock
 * screen shortcuts. Swiping sideways switches between the player and the notification icons;
 * a tap or an upward swipe unfolds both again.
 */
@Composable
fun LockscreenNowBar(loaded: List<NowBarNotification>?, modifier: Modifier = Modifier) {
    val state = LockscreenMediaExpansion
    val card = state.card ?: return
    val notifications = loaded.orEmpty()
    var page by remember { mutableIntStateOf(PAGE_MEDIA) }
    val hasNotifications = notifications.isNotEmpty()
    val shownPage = if (hasNotifications) page else PAGE_MEDIA
    val showsArt by remember { derivedStateOf { state.nowBarShowsArt } }
    DisposableEffect(Unit) { onDispose { state.nowBarBounds = Rect.Zero } }

    // Like COS, a notification that arrives while folded shows its app for a moment.
    // The list settles over the first moments after the pill appears; that is not an arrival.
    var known by remember { mutableStateOf<Set<String>?>(null) }
    val shownAt = remember { SystemClock.uptimeMillis() }
    var peeks by remember { mutableIntStateOf(0) }
    LaunchedEffect(loaded) {
        val keys = loaded?.map { it.key }?.toSet() ?: return@LaunchedEffect
        val previous = known
        known = keys
        val settled = SystemClock.uptimeMillis() - shownAt > SETTLE_MS
        if (settled && previous != null && (keys - previous).isNotEmpty()) peeks++
    }
    LaunchedEffect(peeks) {
        if (peeks == 0) return@LaunchedEffect
        page = PAGE_NOTIFICATIONS
        delay(PEEK_MS)
        page = PAGE_MEDIA
    }

    Box(
        modifier
            .graphicsLayer {
                val f = state.nowBarReveal
                alpha = f
                scaleX = 0.9f + 0.1f * f
                scaleY = 0.9f + 0.1f * f
            }
            .widthIn(max = PillMaxWidth)
            .fillMaxWidth()
            .height(PillHeight)
            .onGloballyPositioned { state.nowBarBounds = it.boundsInWindow() }
            .clip(CircleShape)
            .pointerInput(hasNotifications) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    val drag =
                        awaitTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            total = over
                        } ?: return@awaitEachGesture
                    drag(drag.id) { change ->
                        total += change.positionChange()
                        change.consume()
                    }
                    when {
                        abs(total.y) > abs(total.x) -> if (total.y < 0f) state.restore()
                        hasNotifications ->
                            page = if (page == PAGE_MEDIA) PAGE_NOTIFICATIONS else PAGE_MEDIA
                    }
                }
            }
            .clickable { state.restore() }
            .semantics { contentDescription = "${card.title}, ${card.subtitle}" },
        contentAlignment = Alignment.Center,
    ) {
        // Frosted: the artwork behind is already blurred; the wallpaper gets a blur of its own.
        if (!showsArt) {
            WallpaperBlur(
                alpha = { state.nowBarReveal * state.lockscreenAlpha },
                corner = PillHeight / 2,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier.matchParentSize()
                .background(PillShade, CircleShape)
                .border(1.dp, PillRim, CircleShape)
        )
        AnimatedContent(
            modifier = Modifier.padding(horizontal = 8.dp),
            targetState = shownPage,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 2 else it / 2 } + fadeOut())
            },
            label = "NowBarPage",
        ) { shown ->
            if (shown == PAGE_MEDIA) MediaPage(card) else NotificationsPage(notifications)
        }
    }
}

@Composable
private fun MediaPage(card: MediaCardViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Artwork(card.background, Modifier.size(ThumbSize).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = card.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = card.subtitle,
                color = Secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val play = card.playPauseAction
        if (play != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(PlaySize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .then(play.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
                contentAlignment = Alignment.Center,
            ) {
                M3Icon(
                    painter = painterResource(playPauseIcon(play.state)),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(PlaySize * 0.6f),
                )
            }
        }
    }
}

@Composable
private fun NotificationsPage(notifications: List<NowBarNotification>) {
    val context = LocalContext.current
    val apps = notifications.distinctBy { it.packageName }.take(MAX_APP_ICONS)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEach { app ->
            val drawable = remember(app.icon) { app.icon?.loadDrawable(context) }
            if (drawable != null) {
                Image(
                    painter = rememberDrawablePainter(drawable),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(AppIconSize),
                )
            }
        }
        Text(
            text = notifications.size.toString(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
