/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.composable.media

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * The folded artwork is drawn behind the lock screen, where the shade's swipe would take a pull
 * meant for it: this catches touches over the artwork from above the lock screen instead, in any
 * layout. A tap unfolds the now bar; a pull down leaves the wallpaper.
 */
@Composable
internal fun LockscreenArtGestures() {
    val state = LockscreenMediaExpansion
    val active by remember { derivedStateOf { state.nowBarShowsArt } }
    val bounds = state.artBounds
    if (!active || bounds.isEmpty) return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        Box(
            Modifier.offset {
                    IntOffset(
                        (bounds.left - origin.x).roundToInt(),
                        (bounds.top - origin.y).roundToInt(),
                    )
                }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .pointerInput(Unit) { detectTapGestures { state.collapse() } }
                .onPullDown({ state.nowBarShowsArt }) { state.dismissArt() }
        )
    }
}
