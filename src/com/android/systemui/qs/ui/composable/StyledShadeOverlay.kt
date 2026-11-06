/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.ui.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.PlatformSliderDefaults
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.lifecycle.DisposableEffectWithLifecycle
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.compose.modifiers.thenIf
import com.android.systemui.brightness.ui.compose.BrightnessSliderContainer
import com.android.systemui.brightness.ui.compose.BrightnessSliderDimensions
import com.android.systemui.brightness.ui.compose.ContainerColors
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.development.ui.compose.BuildNumber
import com.android.systemui.development.ui.viewmodel.BuildNumberViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.notifications.ui.composable.SnoozableHeadsUpNotificationPlaceholder
import com.android.systemui.penguin.CosFeature
import com.android.systemui.penguin.CosShadeSwitch
import com.android.systemui.penguin.panelSwitchAlpha
import com.android.systemui.penguin.rememberCosFeature
import com.android.systemui.qs.composefragment.BrightnessLayout
import com.android.systemui.qs.composefragment.ColorOsEditor
import com.android.systemui.qs.composefragment.VolumeLayout
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.composefragment.LocalQsTileDetails
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.EditMode
import com.android.systemui.qs.panels.ui.compose.TileDetails
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.panels.ui.compose.toolbar.Toolbar
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.shared.style.LocalGlassBlurAllowed
import com.android.systemui.qs.shared.style.LocalGlassBlurAlpha
import com.android.systemui.qs.shared.style.QsPanelStyle
import com.android.systemui.qs.shared.style.panelStyle
import com.android.systemui.qs.shared.ui.QuickSettings
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.qs.ui.composable.QuickSettingsShade.systemGestureExclusionInShade
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayActionsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayContentViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.LocalSceneContainerPreloadedResources
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.shade.ui.composable.ChipHighlightModel
import com.android.systemui.shade.ui.composable.LocalCosSwitchMotion
import com.android.systemui.shade.ui.composable.OverlayShade
import com.android.systemui.shade.ui.composable.OverlayShadeHeader
import com.android.systemui.shade.ui.composable.QuickSettingsOverlayHeader
import com.android.systemui.shade.ui.composable.QuickSettingsOverlayPrivacyChip
import com.android.systemui.shade.ui.composable.isLandscapeSplitShade
import com.android.systemui.shade.ui.composable.isTabletPanelSwitch
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlider
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSliderDimensions
import dagger.Lazy
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ContentScope.StyledOverlayBody(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    buildNumber: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalQsWide provides isLandscapeSplitShade()) {
        StyledOverlayBodyContent(qsContainerViewModel, buildNumber)
    }
}

@Composable
private fun ContentScope.StyledOverlayBodyContent(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    buildNumber: @Composable ColumnScope.() -> Unit,
) {
    if (qsContainerViewModel.panelStyle == QsPanelStyle.Penguin) {
        Column(modifier = Modifier.fillMaxWidth().bodyScroll()) {
            PenguinGridQuickSettings(viewModel = qsContainerViewModel)
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
        }
    } else if (qsContainerViewModel.panelStyle == QsPanelStyle.OneUi) {
        Column(modifier = Modifier.fillMaxWidth().bodyScroll()) {
            OneUiGridQuickSettings(viewModel = qsContainerViewModel)
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
        }
    } else if (qsContainerViewModel.panelStyle != QsPanelStyle.Default) {
        Column(modifier = Modifier.fillMaxWidth().bodyScroll()) {
            QuickSettingsContent(
                viewModel = qsContainerViewModel,
                mediaInRow = false,
            )
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().bodyScroll()) {
            val isIdle = layoutState.transitionState is TransitionState.Idle
            val horizontalSpacing = dimensionResource(id = R.dimen.qs_tile_margin_horizontal)
            val showMedia = qsContainerViewModel.showMedia
            val top2Specs = remember(qsContainerViewModel.tileGridViewModel.tileViewModels) {
                qsContainerViewModel.tileGridViewModel.tileViewModels.take(2).map { it.spec }
            }

            QsColumns(
                gap = dimensionResource(R.dimen.qs_tile_margin_vertical),
                modifier = Modifier.fillMaxWidth(),
                first = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (showMedia) {
                                Media(
                                    viewModelFactory = qsContainerViewModel.mediaViewModelFactory,
                                    presentationStyle = MediaPresentationStyle.Default,
                                    behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                                    onDismissed = qsContainerViewModel::onMediaSwipeToDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                    location = Media.Location.QS,
                                )
                            } else {
                                var listening by remember { mutableStateOf(false) }
                                LifecycleStartEffect(Unit) {
                                    listening = true
                                    onStopOrDispose { listening = false }
                                }
                                TileGrid(
                                    viewModel = qsContainerViewModel.tileGridViewModel,
                                    includeSpecs = top2Specs,
                                    columnsOverride = 1,
                                    forceLargeTiles = true,
                                    listening = { listening },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            Element(key = QuickSettings.Elements.BrightnessSlider, modifier = Modifier) {
                                val tileHeight = dimensionResource(id = R.dimen.common_tile_default_tile_height)
                                val tileSpacing = dimensionResource(id = R.dimen.qs_tile_margin_vertical)
                                val headerHeight = tileHeight * 2 + tileSpacing
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        horizontalSpacing,
                                        Alignment.CenterHorizontally
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BrightnessLayout(enable = isIdle, sliderHeight = headerHeight)
                                    VolumeLayout(enable = isIdle, sliderHeight = headerHeight)
                                }
                            }
                        }
                    }

                },
                second = {
                    GridAnchor()
                    TileGrid(
                        viewModel = qsContainerViewModel.tileGridViewModel,
                        excludeSpecs = top2Specs,
                        modifier = Modifier.fillMaxWidth(),
                        enableRevealEffect = TileRevealFlag.isEnabled,
                    )
                },
            )

            buildNumber()
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
        }
    }
}

@Composable
fun ContentScope.StyledOverlayEditor(
    containerViewModel: QuickSettingsContainerViewModel,
    modifier: Modifier,
): Boolean {
    val editorModifier =
        modifier.fillMaxWidth().padding(vertical = QuickSettingsShade.Dimensions.VerticalPadding)
    when {
        containerViewModel.panelStyle == QsPanelStyle.OneUi && OneUiToggleEditing.active ->
            OneUiTogglesEditor(viewModel = containerViewModel, modifier = editorModifier)
        containerViewModel.panelStyle == QsPanelStyle.OneUi ->
            OneUiGridEditor(viewModel = containerViewModel, modifier = editorModifier)
        containerViewModel.panelStyle == QsPanelStyle.Penguin ->
            PenguinGridEditor(viewModel = containerViewModel, modifier = editorModifier)
        containerViewModel.panelStyle == QsPanelStyle.ColorOs ->
            ColorOsEditor(viewModel = containerViewModel, modifier = editorModifier)
        else -> return false
    }
    return true
}

@Composable
fun ContentScope.StyledOverlayHost(
    containerViewModel: QuickSettingsContainerViewModel,
    launchingActivity: StateFlow<Boolean>,
    content: @Composable () -> Unit,
) {
    val launching by launchingActivity.collectAsStateWithLifecycle()
    // The shade is removed a frame after the launch ends, so keep the content faded out a little
    // past the end instead of fading it back in over the app that just opened.
    var launchSuppressed by remember { mutableStateOf(false) }
    LaunchedEffect(launching) {
        if (launching) {
            launchSuppressed = true
        } else if (launchSuppressed) {
            delay(LAUNCH_END_HOLD_MS)
            launchSuppressed = false
        }
    }
    val launchFade by
        animateFloatAsState(
            if (launchSuppressed) 0f else 1f,
            animationSpec = tween(durationMillis = 120),
            label = "Launch fade",
        )
    val isEditing by containerViewModel.editModeViewModel.isEditing.collectAsStateWithLifecycle()
    LaunchedEffect(isEditing) { if (!isEditing) OneUiToggleEditing.active = false }
    val mirrorShowing by
        rememberUpdatedState(containerViewModel.brightnessSliderViewModel.showMirror)
    val allowed = remember { { !mirrorShowing } }
    val tabletSwitch = isTabletPanelSwitch()
    val fade =
        remember(layoutState, tabletSwitch) {
            {
                val state = layoutState.transitionState
                val transition = layoutState.currentTransition
                val shown = contentKey == state.currentScene || contentKey in state.currentOverlays
                launchFade *
                    when {
                        transition?.key == CosShadeSwitch && !tabletSwitch -> 1f
                        transition?.key == CosShadeSwitch ->
                            panelSwitchAlpha(
                                leaving = transition.fromContent == contentKey,
                                progress = transition.progress,
                            )
                        transition?.toContent == contentKey -> transition.progress
                        transition?.fromContent == contentKey -> 1f - transition.progress
                        shown -> 1f
                        else -> 0f
                    }
            }
        }
    // The COS shade puts dark glass behind every panel in both themes, so draw the other styles
    // with the dark palette there, like the COS panel and the Penguin grid; light-theme colours
    // leave their text dark, wallpaper-tinted and hard to read on it.
    val darkGlass =
        rememberCosFeature(CosFeature.Shade) &&
            containerViewModel.panelStyle != QsPanelStyle.ColorOs
    CompositionLocalProvider(
        LocalGlassBlurAllowed provides allowed,
        LocalGlassBlurAlpha provides fade,
        LocalQsTileDetails provides containerViewModel.detailsViewModel,
    ) {
        Box(Modifier.graphicsLayer { alpha = launchFade }, propagateMinConstraints = true) {
            if (darkGlass) PenguinGridTheme(content) else content()
        }
    }
}

@Composable
private fun VerticalSeparator(height: Dp) {
    Spacer(Modifier.height(height = height))
}

@Composable
private fun Modifier.bodyScroll(): Modifier {
    if (LocalQsWide.current) return this
    val scroll = rememberScrollState()
    // A vertical scroller also clips sideways, which cuts the COS rows that trail the panel while
    // it switches to notifications. Nothing scrolls during the switch, so lay the body out at the
    // same scroll position without the scroller (and its clip) until the switch ends.
    if (LocalCosSwitchMotion.current?.active != true) return verticalScroll(scroll)
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
        val width = constraints.constrainWidth(placeable.width)
        val height = constraints.constrainHeight(placeable.height)
        val offset = scroll.value.coerceIn(0, (placeable.height - height).coerceAtLeast(0))
        layout(width, height) { placeable.place(0, -offset) }
    }
}

// Matches the COS shade glass: the shade goes away a frame after an app launch ends.
private const val LAUNCH_END_HOLD_MS = 300L
