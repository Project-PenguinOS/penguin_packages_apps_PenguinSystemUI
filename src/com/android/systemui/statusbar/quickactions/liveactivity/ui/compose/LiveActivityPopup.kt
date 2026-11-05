/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.quickactions.liveactivity.ui.compose

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.statusbar.quickactions.liveactivity.shared.model.LiveActivityPopupModel
import com.android.systemui.statusbar.quickactions.liveactivity.shared.model.LiveActivityTime
import com.android.systemui.statusbar.quickactions.liveactivity.ui.viewmodel.LiveActivityPopupChipsViewModel
import com.android.systemui.statusbar.quickactions.popups.ui.compose.IslandAccents
import com.android.systemui.statusbar.quickactions.popups.ui.compose.IslandGlassCard
import com.android.systemui.statusbar.quickactions.popups.ui.compose.IslandGlassProgress
import com.android.systemui.statusbar.quickactions.popups.ui.compose.IslandGlyphBadge
import com.android.systemui.statusbar.quickactions.popups.ui.compose.PopupActionChips
import com.android.systemui.statusbar.quickactions.popups.ui.compose.PopupSurface
import com.android.systemui.statusbar.quickactions.popups.ui.compose.islandGlassCards
import com.android.systemui.statusbar.quickactions.popups.ui.compose.pressScale
import kotlinx.coroutines.delay

private val PopupShape = RoundedCornerShape(32.dp)

/** An app's Live Update (promoted ongoing notification) as an island card. */
@Composable
fun LiveActivityPopup(model: LiveActivityPopupModel, modifier: Modifier = Modifier) {
    val accent = model.accent ?: IslandAccents.Timer
    val timeText = rememberLiveTimeText(model.time)
    if (islandGlassCards()) {
        val primary = timeText ?: model.shortText ?: model.title ?: model.appName
        val secondary =
            if (primary == model.title) model.text ?: model.appName
            else model.title ?: model.appName
        IslandGlassCard(
            primary = primary,
            secondary = secondary,
            glyph = { AppGlyph(model, accent, size = 30) },
            modifier = modifier,
            onClick = model.onOpen,
            primaryAction = model.actions.getOrNull(0),
            secondaryAction = model.actions.getOrNull(1),
            below =
                model.progress?.let { progress ->
                    { IslandGlassProgress(progress = progress, accent = accent) }
                },
        )
        return
    }

    PopupSurface(
        shape = PopupShape,
        modifier =
            modifier.widthIn(min = 300.dp, max = 380.dp).pressScale(
                enabled = model.onOpen != null,
                pressedScale = 0.97f,
            ) {
                model.onOpen?.invoke()
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IslandGlyphBadge(accent = accent) { AppGlyph(model, accent, size = 22) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = model.title ?: model.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (model.text ?: model.appName.takeIf { model.title != null })?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.73f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            (timeText ?: model.shortText)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            when {
                model.indeterminate ->
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.2f),
                    )
                model.progress != null ->
                    LinearProgressIndicator(
                        progress = { model.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.2f),
                    )
            }
            PopupActionChips(actions = model.actions, accent = accent)
        }
    }
}

@Composable
private fun AppGlyph(model: LiveActivityPopupModel, accent: Color, size: Int) {
    model.icon?.let { Icon(icon = it, modifier = Modifier.size(size.dp), tint = accent) }
}

/** The chronometer of a Live Update, ticking every second; null when it has no timer. */
@Composable
private fun rememberLiveTimeText(time: LiveActivityTime?): String? {
    if (time is LiveActivityTime.Paused) {
        return DateUtils.formatElapsedTime(time.durationMs / 1_000L)
    }
    val chronometer = time as? LiveActivityTime.Chronometer ?: return null
    val text by
        produceState(LiveActivityPopupChipsViewModel.formatChronometer(chronometer), chronometer) {
            while (true) {
                value = LiveActivityPopupChipsViewModel.formatChronometer(chronometer)
                delay(1_000L - SystemClock.elapsedRealtime() % 1_000L)
            }
        }
    return text
}
