/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.quickactions.popups.ui.compose

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.qs.shared.style.LiquidGlassBlue
import com.android.systemui.qs.shared.style.liquidGlassEnabled
import com.android.systemui.statusbar.quickactions.popups.shared.model.PopupActionModel

/** True when island cards use the liquid glass (ColorOS 17 style) layout. */
@Composable fun islandGlassCards(): Boolean = liquidGlassEnabled()

private val GlassCardShape = RoundedCornerShape(28.dp)
private val GlassButtonFill = Color.White.copy(alpha = 0.16f)

/**
 * The ColorOS 17 style live card used by every island feature with liquid glass on: a glyph, a
 * large primary line over a small label, and up to two round buttons, on frosted glass.
 */
@Composable
fun IslandGlassCard(
    primary: String,
    secondary: String?,
    glyph: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    primaryAction: PopupActionModel? = null,
    secondaryAction: PopupActionModel? = null,
    primaryActionColor: Color = LiquidGlassBlue,
    below: (@Composable () -> Unit)? = null,
) {
    PopupSurface(
        shape = GlassCardShape,
        modifier =
            modifier.widthIn(min = 300.dp, max = 400.dp).pressScale(
                enabled = onClick != null,
                pressedScale = 0.97f,
            ) {
                onClick?.invoke()
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    glyph()
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primary,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    secondary?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (primaryAction != null || secondaryAction != null) {
                    Spacer(Modifier.width(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryAction?.let {
                            GlassRoundButton(it, fill = primaryActionColor, size = 52)
                        }
                        secondaryAction?.let {
                            GlassRoundButton(it, fill = GlassButtonFill, size = 48)
                        }
                    }
                }
            }
            below?.invoke()
        }
    }
}

@Composable
private fun GlassRoundButton(action: PopupActionModel, fill: Color, size: Int) {
    Box(
        modifier =
            Modifier.size(size.dp)
                .pressScale(onClick = action.onClick)
                .background(fill, CircleShape)
                .semantics { contentDescription = action.label },
        contentAlignment = Alignment.Center,
    ) {
        val icon = action.icon
        val vector = action.vector
        when {
            vector != null ->
                MaterialIcon(
                    imageVector = vector,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = Color.White,
                )
            icon != null -> Icon(icon = icon, modifier = Modifier.size(24.dp), tint = Color.White)
            else ->
                Text(
                    text = action.label.take(2),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
        }
    }
}

/** A thin glass progress track for live cards. */
@Composable
fun IslandGlassProgress(progress: Float, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape)
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(accent, CircleShape)
        )
    }
}
