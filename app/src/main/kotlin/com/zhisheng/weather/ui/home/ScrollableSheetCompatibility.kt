/*
 * Copyright 2024 The Android Open Source Project
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

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.zhisheng.weather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetContent
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetDialog
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.internal.Strings
import androidx.compose.material3.internal.getString
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Identity limits the no-op shortcut to the two app-owned sheets with no transition veto.
// A caller supplying a custom confirmation callback always gets Material3's complete settle path.
@OptIn(ExperimentalMaterial3Api::class)
internal val scrollableSheetConfirmValueChange: (SheetValue) -> Boolean = { true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberScrollableSheetState(skipPartiallyExpanded: Boolean = false): SheetState =
    rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = scrollableSheetConfirmValueChange,
    )

/**
 * Only an exactly settled, expanded, app-owned sheet can skip the nested-fling no-op.
 *
 * Material3 1.3.1 computeTarget returns currentValue at its exact anchor, for every velocity. Its
 * animateTo nevertheless sets dragTarget and waits for an animation frame, making the parent's
 * startDragImmediately steal the next list gesture. A zero-duration animation still has that gap.
 * Do not widen this to an epsilon, a real animation, another anchor, or a custom confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun canSkipScrollableSheetSettle(sheetState: SheetState): Boolean {
    val dragState = sheetState.anchoredDraggableState
    return dragState.confirmValueChange === scrollableSheetConfirmValueChange &&
        dragState.currentValue == Expanded &&
        !dragState.isAnimationRunning &&
        dragState.offset == dragState.anchors.positionOf(Expanded)
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun CoroutineScope.launchScrollableSheetSettle(
    sheetState: SheetState,
    velocity: Float,
    onDismissRequest: () -> Unit,
): Job? {
    if (canSkipScrollableSheetSettle(sheetState)) return null
    // A queued fling may begin after another gesture has already returned to the expanded anchor.
    // Recheck at execution time; all real movement still delegates to the original state and spec.
    return launch {
        if (!canSkipScrollableSheetSettle(sheetState)) sheetState.settle(velocity)
    }.also { job ->
        job.invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
    }
}

/**
 * Scoped compatibility boundary for Material3 1.3.1 (strictly pinned by the build).
 *
 * The outer orchestration and Scrim below retain the upstream 1.3.1 implementation. Only
 * settleToDismiss uses the exact-rest guard above. Dialog, sheet content, anchors, drag thresholds,
 * shape, insets, predictive back and every actual moving animation remain the library's code.
 * No state replacement, private-field reflection, gesture interception or motion scaling is used.
 *
 * Upstream: material3-1.3.1-sources.jar, commonMain/androidx/compose/material3/ModalBottomSheet.kt
 * https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/1.3.1/material3-1.3.1-sources.jar
 * Re-audit this boundary before changing the pinned Material3 version.
 */
@Composable
@ExperimentalMaterial3Api
internal fun ScrollableModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberScrollableSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val animateToDismiss: () -> Unit = {
        val confirmValueChange: (SheetValue) -> Boolean =
            sheetState.anchoredDraggableState.confirmValueChange
        if (confirmValueChange(Hidden)) {
            scope
                .launch { sheetState.hide() }
                .invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismissRequest()
                    }
                }
        }
    }
    val settleToDismiss: (velocity: Float) -> Unit = {
        scope.launchScrollableSheetSettle(sheetState, it, onDismissRequest)
        Unit
    }

    val predictiveBackProgress = remember { Animatable(initialValue = 0f) }

    val dialogContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize().imePadding().semantics { isTraversalGroup = true }) {
            ScrollableSheetScrim(
                color = scrimColor,
                onDismissRequest = animateToDismiss,
                visible = sheetState.targetValue != Hidden,
            )
            ModalBottomSheetContent(
                predictiveBackProgress,
                scope,
                animateToDismiss,
                settleToDismiss,
                modifier,
                sheetState,
                sheetMaxWidth,
                shape,
                containerColor,
                contentColor,
                tonalElevation,
                dragHandle,
                contentWindowInsets,
                content
            )
        }
    }

    ModalBottomSheetDialog(
        properties = properties,
        onDismissRequest = {
            if (sheetState.currentValue == Expanded && sheetState.hasPartiallyExpandedState) {
                // Smoothly animate away predictive back transformations since we are not fully
                // dismissing. We don't need to do this in the else below because we want to
                // preserve the predictive back transformations (scale) during the hide animation.
                scope.launch { predictiveBackProgress.animateTo(0f) }
                scope.launch { sheetState.partialExpand() }
            } else { // Is expanded without collapsed state or is collapsed.
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
            }
        },
        predictiveBackProgress = predictiveBackProgress,
        content = dialogContent,
    )
    if (sheetState.hasExpandedState) {
        LaunchedEffect(sheetState) { sheetState.show() }
    }
}

@Composable
private fun ScrollableSheetScrim(color: Color, onDismissRequest: () -> Unit, visible: Boolean) {
    if (color.isSpecified) {
        val alpha by
            animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = TweenSpec())
        val closeSheet = getString(Strings.CloseSheet)
        val dismissSheet =
            if (visible) {
                Modifier.pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
                    .semantics(mergeDescendants = true) {
                        traversalIndex = 1f
                        contentDescription = closeSheet
                        onClick {
                            onDismissRequest()
                            true
                        }
                    }
            } else {
                Modifier
            }
        Canvas(Modifier.fillMaxSize().then(dismissSheet)) {
            drawRect(color = color, alpha = alpha.coerceIn(0f, 1f))
        }
    }
}
