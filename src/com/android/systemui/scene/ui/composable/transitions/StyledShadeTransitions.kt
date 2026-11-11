/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementMatcher
import com.android.compose.animation.scene.FixedDistance
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.UserActionDistance
import com.android.compose.animation.scene.reveal.ContainerRevealHaptics
import com.android.compose.animation.scene.transformation.offsetSharedElementWithAnchor
import com.android.mechanics.behavior.VerticalExpandContainerSpec
import com.android.systemui.media.remedia.ui.compose.Media.Elements.MediaCarousel
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.notifications.ui.composable.NotificationsShade
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.shared.ui.QuickSettings.SHARED_TILE_PICKER_THRESHOLD
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.composable.OverlayShade
import com.android.systemui.shade.ui.composable.ShadeHeader
import kotlin.time.Duration.Companion.milliseconds

/** Quick Settings opens and closes like the notification shade. */
fun TransitionBuilder.styledToQuickSettingsShadeTransition(
    durationScale: Double = 1.0,
    cascade: Boolean = false,
) {
    spec = overlayShadeSpring(durationScale)
    distance = FixedDistance(ShadeRevealDistance)

    if (cascade) {
        fractionRange(end = 0.3f) { fade(QuickSettingsShade.Elements.Panel) }
    } else {
        oxygenPanelReveal(QuickSettingsShade.Elements.Panel)
    }

    fractionRange(start = .05f, end = 0.5f) { fade(QuickSettingsShade.Elements.StatusBar) }
    fractionRange(start = .1f, end = 0.5f) { fade(QuickSettingsShade.Elements.Header) }
    fractionRange(end = .35f) { fade(OverlayShade.Elements.Scrim) }
}

fun TransitionBuilder.styledToNotificationsShadeTransition(
    durationScale: Double = 1.0,
    enableSharedElements: Boolean,
    cascade: Boolean = false,
) {
    spec = overlayShadeSpring(durationScale)

    // Ensure the shared elements aren't clipped by the shade outline during the transition from
    // lockscreen.
    sharedElement(
        LockscreenElementKeys.Clock.Small,
        enabled = enableSharedElements,
        elevateInContent = Overlays.NotificationsShade,
    )
    sharedElement(
        LockscreenElementKeys.MediaCarousel,
        enabled = enableSharedElements,
        elevateInContent = Overlays.NotificationsShade,
    )
    sharedElement(Notifications.Elements.StackPlaceholder, enabled = enableSharedElements)
    sharedElement(
        Notifications.Elements.HeadsUpNotificationPlaceholder,
        enabled = enableSharedElements,
    )

    distance = FixedDistance(ShadeRevealDistance)
    if (cascade) {
        // The cards carry the motion themselves; the panel only fades in under them.
        fractionRange(end = 0.3f) { fade(NotificationsShade.Elements.Panel) }
    } else {
        oxygenPanelReveal(NotificationsShade.Elements.Panel)
        fractionRange(start = .05f, end = 0.5f) { fade(Notifications.Elements.StackPlaceholder) }
    }

    fractionRange(start = .05f, end = 0.5f) { fade(NotificationsShade.Elements.StatusBar) }
    fractionRange(end = .35f) { fade(OverlayShade.Elements.Scrim) }
}

fun TransitionBuilder.styledShadeToQuickSettingsTransition(
    durationScale: Double = 1.0,
    animateQsTilesAsShared: () -> Boolean = { true },
    defaultQsStyle: Boolean = false,
) {
    spec = tween(durationMillis = (ShadeToQsDuration * durationScale).inWholeMilliseconds.toInt())
    distance = UserActionDistance { fromContent, _, _ ->
        val distance =
            Notifications.Elements.NotificationScrim.targetOffset(Scenes.Shade)?.y
                ?: return@UserActionDistance 0f
        val fromContentSize = checkNotNull(fromContent.targetSize())
        fromContentSize.height - distance
    }

    translate(Notifications.Elements.NotificationScrim, Edge.Bottom)
    timestampRange(endMillis = 83) { fade(Elements.FooterActions) }

    if (defaultQsStyle) {
        fractionRange(start = 0.43f, end = 1f - SHARED_TILE_PICKER_THRESHOLD) {
            fade(Elements.QuickSettingsContent)
        }
    } else {
        fractionRange(start = 0.55f, end = 0.9f) { fade(Elements.QuickSettingsContent) }
    }

    anchoredTranslate(Elements.QuickSettingsContent, Elements.GridAnchor)

    sharedElement(Elements.TileElementMatcher, enabled = animateQsTilesAsShared())

    if (defaultQsStyle) fractionRange(end = 0.5f) { fade(StyledQqsTileElementMatcher) }
    anchoredTranslate(StyledQqsTileElementMatcher, Elements.GridAnchor)
    fade(MediaCarousel)

    val translationY = ShadeHeader.Dimensions.CollapsedHeightForTransitions
    translate(ShadeHeader.Elements.CollapsedContentStart, y = translationY)
    translate(ShadeHeader.Elements.CollapsedContentEnd, y = translationY)
    translate(
        ShadeHeader.Elements.ExpandedContent,
        y = -(ShadeHeader.Dimensions.ExpandedHeight - translationY),
    )
    translate(ShadeHeader.Elements.ShadeCarrierGroup, y = -translationY)

    fractionRange(end = .14f) {
        fade(ShadeHeader.Elements.CollapsedContentStart)
        fade(ShadeHeader.Elements.CollapsedContentEnd)
    }

    fractionRange(start = .58f) {
        fade(ShadeHeader.Elements.ExpandedContent)
        fade(ShadeHeader.Elements.ShadeCarrierGroup)
    }
}

fun TransitionBuilder.styledQuickSettingsToShadeTransition(
    durationScale: Double = 1.0,
    animateQsTilesAsShared: () -> Boolean = { true },
    defaultQsStyle: Boolean = false,
) {
    reversed {
        styledShadeToQuickSettingsTransition(
            durationScale = durationScale,
            animateQsTilesAsShared = animateQsTilesAsShared,
            defaultQsStyle = defaultQsStyle,
        )
    }
    // Translate the HeadsUpNotificationPlaceholder from the StackPlaceholder's start position to
    // its target. This ensures the HUN is "dragged" up by the Stack once the Stack's top edge
    // reaches the HUN's top.
    offsetSharedElementWithAnchor(
        matcher = Notifications.Elements.HeadsUpNotificationPlaceholder,
        anchor = Notifications.Elements.StackPlaceholder,
        offset = { from, to, fromAnchor, _ ->
            val animatedOffset = lerp(fromAnchor, to, transition.progress)
            Offset(animatedOffset.x, minOf(from.y, animatedOffset.y))
        },
    )
}

private val ShadeToQsDuration = 500.milliseconds

private val StyledQqsTileElementMatcher =
    object : ElementMatcher {
        override fun matches(key: ElementKey, content: ContentKey): Boolean {
            return content == Scenes.Shade && Elements.TileElementMatcher.matches(key, content)
        }
    }
