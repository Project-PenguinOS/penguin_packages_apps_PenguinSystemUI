/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.penguin.CosFeature
import com.android.systemui.penguin.rememberCosFeature
import com.android.systemui.qs.shared.style.LocalGlassBlurAlpha
import com.android.systemui.qs.ui.composable.LocalQsHostTransition
import kotlin.math.PI
import kotlin.math.pow

private const val HIDE_RESPONSE = 0.1f

/** Part of the reveal by which each module starts after the one above it. */
private const val CASCADE_STEP = 0.07f

/** Modules the cascade is spread over; later ones start with the last. */
private const val CASCADE_MODULES = 8

private val CascadeDrop = 24.dp
private val CascadeDropStep = 8.dp

/**
 * Like ColorOS, the Control Centre unfolds as a cascade that follows the finger: each module drops
 * in from above, growing and fading in, a little after the one above it and from a little further,
 * so the panel pours down; closing, the bottom modules lift away first. [index] counts from 1 at
 * the top.
 */
@Composable
fun Modifier.cosModuleReveal(index: Int): Modifier {
    if (!rememberCosFeature(CosFeature.Shade)) return this
    val transition = LocalQsHostTransition.current ?: return this
    val drop = with(LocalDensity.current) { (CascadeDrop + CascadeDropStep * (index - 1)).toPx() }
    return graphicsLayer {
        val p = cascadeProgress(transition.shownProgress(), index)
        val eased = LinearOutSlowInEasing.transform(p)
        translationY = -drop * (1f - eased)
        val scale = 0.9f + 0.1f * eased
        scaleX = scale
        scaleY = scale
        alpha = cascadeAlpha(p)
    }
}

private fun cascadeProgress(shown: Float, index: Int): Float {
    val start = CASCADE_STEP * (index - 1).coerceIn(0, CASCADE_MODULES - 1)
    val span = 1f - CASCADE_STEP * (CASCADE_MODULES - 1)
    return ((shown - start) / span).coerceIn(0f, 1f)
}

private fun cascadeAlpha(p: Float): Float = (p * 1.6f).coerceAtMost(1f)

/**
 * The glass under a module's tiles is a window blur, which takes neither the module's fade nor
 * its movement: it fades with the module here, so each tile keeps its outline while it shows.
 */
@Composable
fun CosModuleGlass(index: Int, content: @Composable () -> Unit) {
    val transition = LocalQsHostTransition.current
    if (!rememberCosFeature(CosFeature.Shade) || transition == null) {
        content()
        return
    }
    val outer = LocalGlassBlurAlpha.current
    CompositionLocalProvider(
        LocalGlassBlurAlpha provides
            remember(outer, transition, index) {
                { outer() * cascadeAlpha(cascadeProgress(transition.shownProgress(), index)) }
            },
        content = content,
    )
}

@Composable
fun Modifier.cosHeaderHide(leaving: Boolean): Modifier {
    if (!rememberCosFeature(CosFeature.Shade)) return this
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(leaving) {
        alpha.animateTo(
            if (leaving) 0f else 1f,
            spring(dampingRatio = 1f, stiffness = (2 * PI / HIDE_RESPONSE).pow(2).toFloat()),
        )
    }
    return graphicsLayer { this.alpha = alpha.value }
}
