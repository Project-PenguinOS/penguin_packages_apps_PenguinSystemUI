/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui.qs

import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import com.android.compose.animation.scene.SceneTransitionsBuilder
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.gesture.effect.rememberControlCentreOverscrollEffectFactory
import com.android.systemui.notifications.ui.composable.NotificationsShade
import com.android.systemui.penguin.CosFeature
import com.android.systemui.penguin.CosShadeSwitch
import com.android.systemui.penguin.ShadeMotion
import com.android.systemui.penguin.rememberCosFeature
import com.android.systemui.qs.composefragment.effectiveQsPanelStyle
import com.android.systemui.qs.shared.style.QsPanelStyle
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.transitions.cosNotificationsToQuickSettingsSwitch
import com.android.systemui.scene.ui.composable.transitions.cosPanelSwitchFade
import com.android.systemui.scene.ui.composable.transitions.cosSpring
import com.android.systemui.scene.ui.composable.transitions.notificationsToQuickSettingsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.styledQuickSettingsToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.styledShadeToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.styledToNotificationsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.styledToQuickSettingsShadeTransition
import com.android.systemui.shade.ui.composable.isTabletPanelSwitch
import javax.inject.Inject

class PenguinShadeMotion @Inject constructor() : ShadeMotion {
    private var defaultQsStyle = false
    private var cos = false
    private var tabletSwitch = false
    private var stockMotion = false
    private var cascade = false

    @Composable
    override fun key(): Any? {
        val style = effectiveQsPanelStyle()
        defaultQsStyle = style.stockLayout
        cos = rememberCosFeature(CosFeature.Shade)
        tabletSwitch = isTabletPanelSwitch()
        // The Default and Stock styles draw AOSP's own panels, so they also reveal like AOSP
        // unless the COS shade is on.
        stockMotion = defaultQsStyle && !cos
        // The ColorOS Control Centre unfolds module by module itself.
        cascade = cos && style == QsPanelStyle.ColorOs
        return listOf(defaultQsStyle, cos, tabletSwitch, cascade)
    }

    override fun useStockMotion(): Boolean = stockMotion

    override fun TransitionBuilder.shadeToQuickSettings(animateQsTilesAsShared: () -> Boolean) {
        styledShadeToQuickSettingsTransition(
            animateQsTilesAsShared = animateQsTilesAsShared,
            defaultQsStyle = defaultQsStyle,
        )
    }

    override fun TransitionBuilder.quickSettingsToShade(animateQsTilesAsShared: () -> Boolean) {
        styledQuickSettingsToShadeTransition(
            animateQsTilesAsShared = animateQsTilesAsShared,
            defaultQsStyle = defaultQsStyle,
        )
    }

    // Quick Settings reveals and dismisses exactly like the notification shade.
    override fun TransitionBuilder.toQuickSettingsShade(durationScale: Double) {
        styledToQuickSettingsShadeTransition(durationScale = durationScale, cascade = cascade)
        if (cos) spec = cosSpring(0.2, durationScale)
    }

    override fun TransitionBuilder.fromQuickSettingsShade() {
        reversed { toQuickSettingsShade(1.0) }
    }

    override fun TransitionBuilder.toNotificationsShade(
        durationScale: Double,
        enableSharedElements: Boolean,
    ) {
        styledToNotificationsShadeTransition(
            durationScale = durationScale,
            enableSharedElements = enableSharedElements,
            cascade = cos,
        )
        if (cos) spec = cosSpring(0.2, durationScale)
    }

    override fun SceneTransitionsBuilder.overlaySwitchTransitions() {
        from(
            Overlays.NotificationsShade,
            to = Overlays.QuickSettingsShade,
            key = CosShadeSwitch,
        ) {
            if (!tabletSwitch) {
                cosNotificationsToQuickSettingsSwitch()
            } else {
                cosPanelSwitchFade(
                    fromPanel = NotificationsShade.Elements.Panel,
                    fromStatusBar = NotificationsShade.Elements.StatusBar,
                    toPanel = QuickSettingsShade.Elements.Panel,
                    toStatusBar = QuickSettingsShade.Elements.StatusBar,
                )
            }
        }
        from(
            Overlays.QuickSettingsShade,
            to = Overlays.NotificationsShade,
            key = CosShadeSwitch,
        ) {
            if (!tabletSwitch) {
                reversed { cosNotificationsToQuickSettingsSwitch() }
            } else {
                cosPanelSwitchFade(
                    fromPanel = QuickSettingsShade.Elements.Panel,
                    fromStatusBar = QuickSettingsShade.Elements.StatusBar,
                    toPanel = NotificationsShade.Elements.Panel,
                    toStatusBar = NotificationsShade.Elements.StatusBar,
                )
            }
        }
        from(Overlays.NotificationsShade, to = Overlays.QuickSettingsShade) {
            notificationsToQuickSettingsShadeTransition()
        }
        from(Overlays.QuickSettingsShade, to = Overlays.NotificationsShade) {
            reversed { notificationsToQuickSettingsShadeTransition() }
        }
    }

    @Composable
    override fun quickSettingsShadeOverscroll(): OverscrollFactory =
        rememberControlCentreOverscrollEffectFactory()
}
