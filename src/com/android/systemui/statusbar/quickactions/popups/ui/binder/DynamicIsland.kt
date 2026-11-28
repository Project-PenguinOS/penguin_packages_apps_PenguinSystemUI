/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.quickactions.popups.ui.binder

import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.android.compose.theme.PlatformTheme
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.penguin.StatusBarIsland
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.KeyguardStatusBarView
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.quickactions.popups.ui.compose.StatusBarDynamicIslandContainer
import com.android.systemui.statusbar.quickactions.popups.ui.compose.dynamicIslandAvoidsAppHandles
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.DynamicIslandChipsViewModelStore
import com.android.wm.shell.windowdecor.viewholder.AppHandlePositionCallback
import com.android.wm.shell.windowdecor.viewholder.AppHandles
import java.util.Optional
import java.util.concurrent.Executor
import javax.inject.Inject

class DynamicIsland
@Inject
constructor(
    private val chipsViewModelStore: DynamicIslandChipsViewModelStore,
    private val keyguardViewBinder: KeyguardDynamicIslandViewBinder,
    private val appHandles: Optional<AppHandles>,
    @Main private val mainExecutor: Executor,
) : StatusBarIsland {

    override fun attachKeyguard(statusBar: View) {
        keyguardViewBinder.bind(statusBar as KeyguardStatusBarView)
    }

    override fun attach(statusBar: View, onBoundsChanged: (Rect) -> Unit) {
        val context = statusBar.context
        val overlap = StatusIconOverlap(statusBar)

        overlap.enabled = isDynamicIslandSettingEnabled(context)
        val settingObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    overlap.enabled = isDynamicIslandSettingEnabled(context)
                    if (!overlap.enabled) overlap.reset()
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND),
            false,
            settingObserver,
            UserHandle.USER_ALL,
        )
        statusBar.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    context.contentResolver.unregisterContentObserver(settingObserver)
                }
            }
        )

        val reportBounds = { bounds: Rect ->
            onBoundsChanged(bounds)
            publishIslandBounds(context, bounds)
            overlap.collapse(bounds)
        }

        val centeredArea = statusBar.requireViewById<ViewGroup>(R.id.centered_area)
        val composeView =
            ComposeView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { gravity = Gravity.CENTER }
                setContent {
                    PlatformTheme {
                        // the display.
                        val islandViewModel =
                            remember(context.displayId) {
                                chipsViewModelStore.forDisplay(context.displayId)
                            }
                        val appHandleBounds by
                            produceState(emptyList<Rect>(), context.displayId) {
                                val handles = appHandles.orElse(null) ?: return@produceState
                                val listener = AppHandlePositionCallback { all ->
                                    value =
                                        all.values
                                            .filter { it.displayId == context.displayId }
                                            .map { it.rect }
                                }
                                handles.addListener(mainExecutor, listener)
                                awaitDispose { handles.removeListener(listener) }
                            }
                        val island = @Composable {
                            StatusBarDynamicIslandContainer(
                                chips = islandViewModel.shownPopupChips,
                                onMediaControlPopupVisibilityChanged = {},
                                onIslandBoundsChanged = reportBounds,
                                appHandleBounds = appHandleBounds,
                            )
                        }
                        // A tablet island may step beside an app handle. Span the status bar so
                        // it stays inside this view and keeps receiving taps; empty space passes
                        // touches through to the status bar below.
                        val spanStatusBar = dynamicIslandAvoidsAppHandles()
                        SideEffect {
                            val width =
                                if (spanStatusBar) {
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                } else {
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                }
                            if (layoutParams.width != width) {
                                layoutParams = layoutParams.apply { this.width = width }
                            }
                        }
                        if (spanStatusBar) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                island()
                            }
                        } else {
                            island()
                        }
                    }
                }
            }
        centeredArea.addView(composeView)

        centeredArea.viewTreeObserver.addOnGlobalLayoutListener {
            if (
                centeredArea.visibility != View.VISIBLE || composeView.visibility != View.VISIBLE
            ) {
                onBoundsChanged(Rect())
                overlap.collapse(Rect())
            }
        }
    }

    private class StatusIconOverlap(statusBar: View) {
        private val statusIcons: StatusIconContainer? = statusBar.findViewById(R.id.statusIcons)
        private val dotParent: ViewGroup? =
            statusBar.findViewById(R.id.status_bar_end_side_content)
        private var dot: View? = null
        /** Views this hid because the island covers them; only these are shown again. */
        private val hidden = mutableSetOf<View>()
        private var pendingCheck: Runnable? = null
        private var lastBounds = Rect()
        var enabled = false

        init {
            val icons = statusIcons
            if (icons != null && dotParent != null) {
                val listener =
                    ViewTreeObserver.OnGlobalLayoutListener { apply(icons, dotParent, lastBounds) }
                icons.viewTreeObserver.addOnGlobalLayoutListener(listener)
                icons.addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}

                        override fun onViewDetachedFromWindow(v: View) {
                            icons.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                        }
                    }
                )
            }
        }

        fun collapse(islandBounds: Rect) {
            val icons = statusIcons ?: return
            val parent = dotParent ?: return
            if (!enabled) return
            lastBounds = islandBounds
            pendingCheck?.let { icons.removeCallbacks(it) }
            val runnable = Runnable { apply(icons, parent, islandBounds) }
            pendingCheck = runnable
            icons.postDelayed(runnable, 300L)
        }

        /**
         * Everything at the end of the status bar the island can reach: each icon in every icon
         * group (the status icons, and the demo mode icons beside them), and the views beside the
         * icons (network traffic, the user switcher).
         */
        private fun candidates(icons: StatusIconContainer, parent: ViewGroup): List<View> =
            buildList {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChildAt(i)
                    if (child === dot) continue
                    if (child.id != R.id.system_icons || child !is ViewGroup) {
                        add(child)
                        continue
                    }
                    for (j in 0 until child.childCount) {
                        val group = child.getChildAt(j)
                        if (group === icons || group.javaClass.simpleName == "DemoStatusIcons") {
                            val icons = group as ViewGroup
                            for (k in 0 until icons.childCount) add(icons.getChildAt(k))
                        } else {
                            add(group)
                        }
                    }
                }
            }

        private fun apply(icons: StatusIconContainer, parent: ViewGroup, islandBounds: Rect) {
            if (!enabled) return
            val childRect = Rect()
            val loc = IntArray(2)
            var anyOverlap = false

            for (child in candidates(icons, parent)) {
                // Gone views take no room; a view hidden here keeps its size.
                if (child.width == 0 || child.visibility == View.GONE) continue
                if (child.visibility == View.INVISIBLE && child !in hidden) continue
                child.getLocationInWindow(loc)
                childRect.set(loc[0], loc[1], loc[0] + child.width, loc[1] + child.height)
                val overlaps = !islandBounds.isEmpty && Rect.intersects(childRect, islandBounds)
                if (overlaps) {
                    if (child.visibility == View.VISIBLE) {
                        child.visibility = View.INVISIBLE
                        hidden += child
                    }
                    anyOverlap = true
                } else if (hidden.remove(child)) {
                    child.visibility = View.VISIBLE
                }
            }

            // Added as a sibling of StatusIconContainer, not a child of it -- StatusIconContainer
            // expects its children to be status-icon views and can crash if a plain View is added.
            val dot =
                dot
                    ?: TextView(parent.context)
                        .apply {
                            text = "•"
                            textSize = 10f
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                            parent.addView(this, 0)
                        }
                        .also { dot = it }
            val dotVisibility = if (anyOverlap) View.VISIBLE else View.GONE
            if (dot.visibility != dotVisibility) {
                dot.visibility = dotVisibility
            }
            // The dot leads the end side, which is where the island reaches; move it out beside
            // the island once it is laid out.
            dot.post { placeDot(dot, islandBounds) }
        }

        private fun placeDot(dot: View, islandBounds: Rect) {
            if (dot.visibility != View.VISIBLE || islandBounds.isEmpty) {
                dot.translationX = 0f
                return
            }
            val loc = IntArray(2)
            dot.getLocationInWindow(loc)
            val restingLeft = loc[0] - dot.translationX
            val gap = 2f * dot.resources.displayMetrics.density
            dot.translationX =
                if (dot.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    minOf(0f, islandBounds.left - gap - (restingLeft + dot.width))
                } else {
                    maxOf(0f, islandBounds.right + gap - restingLeft)
                }
        }

        fun reset() {
            hidden.forEach { if (it.visibility == View.INVISIBLE) it.visibility = View.VISIBLE }
            hidden.clear()
            dot?.visibility = View.GONE
            dot?.translationX = 0f
        }
    }

    private companion object {
        const val ISLAND_BOUNDS_SETTING = "penguin_island_bounds"

        val boundsHandler = Handler(Looper.getMainLooper())
        var publishedBounds: String? = null

        fun publishIslandBounds(context: Context, bounds: Rect) {
            val value = if (bounds.isEmpty) "" else bounds.flattenToString()
            boundsHandler.removeCallbacksAndMessages(null)
            boundsHandler.postDelayed(
                {
                    if (value != publishedBounds) {
                        publishedBounds = value
                        Settings.Secure.putString(
                            context.contentResolver,
                            ISLAND_BOUNDS_SETTING,
                            value,
                        )
                    }
                },
                100,
            )
        }

        fun isDynamicIslandSettingEnabled(context: Context): Boolean =
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND,
                0,
            ) != 0
    }
}
