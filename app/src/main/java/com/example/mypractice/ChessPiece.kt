package com.example.mypractice

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sqrt

class ChessPiece(
    var position: Pair<Int, Int>,  // 棋子当前坐标
    val image: ImageBitmap,       // 棋子图片
    var isAlive: Boolean = true   // 是否存活
) {
    var isSelected: Boolean = false // 是否被选中
    // 动画控制
    private val pairConverter = TwoWayConverter<Pair<Float, Float>, AnimationVector2D>(
        convertToVector = { pair -> AnimationVector2D(pair.first, pair.second) },
        convertFromVector = { vector -> Pair(vector.v1, vector.v2) }
    )
    private val combinedAnimation = Animatable(initialValue = Pair(1f, 0f), typeConverter = pairConverter)

    /**
     * 播放选中动画
     */
    suspend fun select() {
        isSelected = true
        combinedAnimation.animateTo(Pair(1.2f, 20f), animationSpec = tween(200)) // 缩放到 1.2 倍
    }

    /**
     * 播放取消选中动画
     */
    suspend fun deselect() {
        isSelected = false
        combinedAnimation.animateTo(Pair(1f, 0f), animationSpec = tween(200))
    }

    /**
     * 绘制棋子
     * @param drawScope 当前 Canvas 的绘制范围
     * @param borderLeft 棋盘的左侧空白部分长度
     * @param borderTop 棋盘的顶部空白部分长度
     * @param cellWidth 每个格子的宽度
     * @param cellHeight 每个格子的高度
     */
    fun draw(drawScope: DrawScope, borderLeft: Float, borderTop: Float, cellWidth: Float, cellHeight: Float) {
        val (x, y) = position
        val centerX = borderLeft + cellWidth * x
        val centerY = borderTop + cellHeight * y - combinedAnimation.value.second
        val size = sqrt(cellWidth*cellWidth+cellHeight*cellHeight.toDouble()) *0.66 * combinedAnimation.value.first    //棋子的大小设定为0.66*格子的对角线长度

        drawScope.drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
            }

            // 定义源区域（裁剪整张图片）
            val srcOffset = IntOffset(0, 0)
            val srcSize = IntSize(image.width, image.height)

            // 定义目标区域（图片最终绘制的位置和大小）
            val dstOffset = IntOffset(
                (centerX - size / 2).toInt(),
                (centerY - size / 2).toInt()
            )
            val dstSize = IntSize(size.toInt(), size.toInt())
            // 绘制图片
            canvas.drawImageRect(
                image = image,
                srcOffset = srcOffset,
                srcSize = srcSize,
                dstOffset = dstOffset,
                dstSize = dstSize,
                paint = paint
            )
        }
    }
}
