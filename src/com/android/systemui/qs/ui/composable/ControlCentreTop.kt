/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.ui.composable

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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.compose.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.animation.Expandable
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.composefragment.cosGlass
import com.android.systemui.qs.composefragment.cosHeaderHide
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.shared.style.QsPanelStyle
import com.android.systemui.qs.shared.style.glassBlurRegion
import com.android.systemui.qs.shared.style.glassPress
import com.android.systemui.qs.shared.style.liquidGlassEnabled
import com.android.systemui.qs.shared.style.liquidGlassCosStyle
import com.android.systemui.qs.shared.style.liquidGlassRim
import com.android.systemui.qs.shared.style.panelStyle
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.shade.ui.composable.ShadeStatusRow

private val ButtonSize = 44.dp

@Composable
fun controlCentreTopShown(viewModel: QuickSettingsContainerViewModel): Boolean =
    viewModel.panelStyle == QsPanelStyle.ColorOs ||
        (viewModel.panelStyle == QsPanelStyle.Penguin && liquidGlassEnabled())

@Composable
fun controlCentreWide(viewModel: QuickSettingsContainerViewModel): Boolean =
    viewModel.panelStyle == QsPanelStyle.Penguin && controlCentreTopShown(viewModel) &&
        wideControlCentre()

@Composable
fun ContentScope.ControlCentreTop(
    viewModel: QuickSettingsContainerViewModel,
    toolbarViewModel: ToolbarViewModel,
) {
    val editButton =
        rememberViewModel("ControlCentreTop") {
            toolbarViewModel.editModeButtonViewModelFactory.create()
        }
    val colorOs = viewModel.panelStyle == QsPanelStyle.ColorOs
    if (!colorOs && wideControlCentre()) {
        ShadeStatusRow(
            viewModel.shadeHeaderViewModel,
            Modifier.fillMaxWidth()
                .wrapContentWidth()
                .width(wideGridWidth())
                .padding(horizontal = PenguinGridMargin + 4.dp)
                .padding(top = 4.dp, bottom = 12.dp),
        )
        return
    }
    val own = qsHostTransition()
    val leaving = LocalQsHostTransition.current?.leaving == true || own.leaving
    Column(
        Modifier.fillMaxWidth()
            .cosHeaderHide(leaving)
            .padding(horizontal = PenguinGridMargin)
            .padding(top = 12.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (colorOs) {
                GlassButton(Icons.Rounded.Edit, cos = true) { editButton.onButtonClick() }
                GlassButton(Icons.Rounded.Settings, cos = true) {
                    toolbarViewModel.settingsButtonViewModel?.onClick?.invoke(it)
                }
            } else {
                GlassButton(Icons.Rounded.Add) { editButton.onButtonClick() }
                GlassButton(Icons.Rounded.PowerSettingsNew) {
                    toolbarViewModel.powerButtonViewModel.onClick(it)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        ShadeStatusRow(
            viewModel.shadeHeaderViewModel,
            Modifier.padding(horizontal = 4.dp),
            onDarkGlass = colorOs,
        )
    }
}

@Composable
private fun GlassButton(icon: ImageVector, cos: Boolean = false, onClick: (Expandable) -> Unit) {
    Box(
        Modifier.size(ButtonSize)
            .then(
                if (cos) Modifier.cosGlass(CircleShape)
                else if (liquidGlassCosStyle()) Modifier.glassBlurRegion(CircleShape)
                else Modifier.glassBlurRegion(CircleShape).liquidGlassRim(CircleShape)
            )
            .glassPress()
    ) {
        Expandable(
            controller =
                rememberExpandableController(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = CircleShape,
                ),
            useModifierBasedImplementation = true,
            defaultMinSize = false,
            onClick = onClick,
            modifier = Modifier.size(ButtonSize),
        ) {
            Box(Modifier.size(ButtonSize), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
