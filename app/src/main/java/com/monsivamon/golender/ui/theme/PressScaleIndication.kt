package com.monsivamon.golender.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// 押下時に全体をわずかに縮小する Modifier.Node。
private class PressScaleIndicationNode(
    private val interactionSource: InteractionSource,
    private val pressedScale: Float,
    private val durationMillis: Int,
) : Modifier.Node(), DrawModifierNode {

    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)

    private val scaleAnim = Animatable(1f)

    // 押下・解放のインタラクションを監視して拡大縮小アニメーションを開始する。
    override fun onAttach() {
        scope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        launch { scaleAnim.animateTo(pressedScale, tween(durationMillis)) }
                    }
                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> {
                        launch { scaleAnim.animateTo(1f, tween(durationMillis)) }
                    }
                    else -> { }
                }
            }
        }
    }

    // ノード破棄時にコルーチンスコープをキャンセルする。
    override fun onDetach() {
        scope.cancel()
    }

    // 現在のスケール値で内容を描画する。
    override fun ContentDrawScope.draw() {
        val s = scaleAnim.value
        if (s == 1f) {
            drawContent()
        } else {
            withTransform({
                scale(s, s, pivot = center)
            }) {
                this@draw.drawContent()
            }
        }
    }
}

// アプリ全体で押下時の縮小フィードバックを提供する Indication。
object PressScaleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressScaleIndicationNode(
            interactionSource = interactionSource,
            pressedScale = 0.94f,
            durationMillis = 100,
        )

    // IndicationNodeFactory 用の hashCode を返す。
    override fun hashCode(): Int = "PressScaleIndication".hashCode()

    // IndicationNodeFactory 用の equals を返す。
    override fun equals(other: Any?): Boolean = other is PressScaleIndication
}