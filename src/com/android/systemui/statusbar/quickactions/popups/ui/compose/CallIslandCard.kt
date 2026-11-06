/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.quickactions.popups.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.common.ui.compose.Icon as StatusBarIcon
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.POPUP_COLOR_MODE_DEFAULT
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.POPUP_COLOR_MODE_SOLID_BLACK
import com.android.systemui.statusbar.quickactions.popups.ui.model.CallPopupModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupContentModel
import com.penguin.systemui.R as PenguinR

private val CallCardShape = RoundedCornerShape(36.dp)
private val CallButtonSize = 56.dp
private val CallEndRed = Color(0xFFFF453A)
private val CallAnswerGreen = Color(0xFF30D158)

/** The expanded island of a call: who it is, how long it has run and the call's own buttons. */
@Composable
fun CallIslandCard(
    model: PopupContentModel.SystemEvent,
    call: CallPopupModel,
    modifier: Modifier = Modifier,
) {
    // Black like the island itself, unless the user picked blur or liquid glass for popups.
    val colorMode =
        rememberPopupColorMode().let {
            if (it == POPUP_COLOR_MODE_DEFAULT) POPUP_COLOR_MODE_SOLID_BLACK else it
        }
    PopupSurface(
        shape = CallCardShape,
        colorMode = colorMode,
        modifier = modifier.widthIn(min = 320.dp, max = 420.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallAvatar(call, model.title, size = if (call.incoming) 48.dp else 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = callSubtitle(model, call),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (call.incoming) {
                    call.onDecline?.let {
                        CallFilledButton(
                            Icons.Filled.CallEnd,
                            stringResource(PenguinR.string.dynamic_island_call_decline),
                            CallEndRed,
                            48.dp,
                            it,
                        )
                    }
                    call.onAnswer?.let {
                        CallFilledButton(
                            Icons.Filled.Call,
                            stringResource(PenguinR.string.dynamic_island_call_answer),
                            CallAnswerGreen,
                            48.dp,
                            it,
                        )
                    }
                } else {
                    val moreLabel = stringResource(PenguinR.string.dynamic_island_call_more)
                    call.onOpen?.let { open ->
                        Box(
                            modifier =
                                Modifier.size(40.dp)
                                    .clip(CircleShape)
                                    .pressScale(onClick = open)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = moreLabel
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = LocalContentColor.current.copy(alpha = 0.8f),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
            if (!call.incoming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CallToggleButton(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        stringResource(PenguinR.string.dynamic_island_call_speaker),
                        on = call.speakerOn,
                        onClick = call.onSpeaker,
                    )
                    CallToggleButton(
                        if (call.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        stringResource(PenguinR.string.dynamic_island_call_mute),
                        on = call.muted,
                        onClick = call.onMute,
                    )
                    CallToggleButton(
                        Icons.Filled.MoreHoriz,
                        stringResource(PenguinR.string.dynamic_island_call_more),
                        on = false,
                        onClick = call.onOpen,
                    )
                    call.onEnd?.let {
                        CallFilledButton(
                            Icons.Filled.CallEnd,
                            stringResource(PenguinR.string.dynamic_island_call_end),
                            CallEndRed,
                            CallButtonSize,
                            it,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun callSubtitle(model: PopupContentModel.SystemEvent, call: CallPopupModel): String =
    when {
        call.connected && model.callStartTimeMs != null ->
            rememberCallDurationText(model.callStartTimeMs)
        call.incoming ->
            model.text.ifBlank { stringResource(PenguinR.string.dynamic_island_call_incoming) }
        else -> stringResource(PenguinR.string.dynamic_island_call_calling)
    }

/** The caller's photo, else their initials, else a call glyph, on a soft grey disc. */
@Composable
private fun CallAvatar(call: CallPopupModel, name: String, size: Dp) {
    Box(
        modifier =
            Modifier.size(size)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF9A9AA2), Color(0xFF5C5C63)))
                ),
        contentAlignment = Alignment.Center,
    ) {
        val photo = call.photo
        val initials = initialsOf(name)
        when {
            photo != null ->
                StatusBarIcon(icon = photo, tint = Color.Unspecified, modifier = Modifier.size(size))
            initials != null ->
                Text(
                    initials,
                    color = Color.White,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            else ->
                Icon(
                    Icons.Filled.Call,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.45f),
                )
        }
    }
}

/** Up to two initials from a contact name; numbers and symbols get none. */
private fun initialsOf(name: String): String? {
    val words = name.split(' ', '-', '_').filter { it.firstOrNull()?.isLetter() == true }
    if (words.isEmpty()) return null
    return words.take(2).joinToString("") { it.first().uppercase() }
}

@Composable
private fun CallToggleButton(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onClick: (() -> Unit)?,
) {
    val content = LocalContentColor.current
    // On is the inverse of the card, like a lit key: white on a dark card, dark on a light one.
    val onIcon = if (content.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier =
            Modifier.size(CallButtonSize)
                .alpha(if (onClick == null) 0.35f else 1f)
                .clip(CircleShape)
                .background(if (on) content else content.copy(alpha = 0.14f))
                .pressScale(enabled = onClick != null) { onClick?.invoke() }
                .semantics {
                    role = Role.Switch
                    contentDescription = label
                    toggleableState = ToggleableState(on)
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (on) onIcon else content,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun CallFilledButton(
    icon: ImageVector,
    label: String,
    color: Color,
    size: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.size(size)
                .clip(CircleShape)
                .background(color)
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .pressScale(onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.46f))
    }
}
