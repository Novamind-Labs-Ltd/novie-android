package com.novamind.app.feature.calendar.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/**
 * 让非 Lazy 布局（如普通 Column）中的子项在**位置变化时平滑滑动**，而非突变。
 *
 * 原理：[onPlaced] 记录该项在父容器中的目标位置；实际布局瞬间跳到新位，但通过 [Modifier.offset]
 * 用 [Animatable] 把渲染位置从旧位插值到新位。配合外层对每个子项 `key(id)`，Compose 会复用同一节点，
 * 于是「已完成任务下沉重排」表现为滑动动画。首次放置不产生动画。
 */
internal fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var targetOffset by remember { mutableStateOf(IntOffset.Zero) }
    var animatable by remember {
        mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null)
    }
    this
        .onPlaced { targetOffset = it.positionInParent().round() }
        .offset {
            val anim = animatable
                ?: Animatable(targetOffset, IntOffset.VectorConverter).also { animatable = it }
            if (anim.targetValue != targetOffset) {
                scope.launch { anim.animateTo(targetOffset, spring()) }
            }
            anim.value - targetOffset
        }
}
