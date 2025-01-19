package com.example.mypractice

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

class ChessPiece(
    var position: Pair<Int, Int>,  // 棋子当前坐标
    val image: ImageBitmap,       // 棋子图片
    var isAlive: Boolean = true   // 是否存活
) {
    val scaleAnimation = Animatable(1f) // 缩放动画
    val liftAnimation = Animatable(0f) // 抬起动画 (z-index 偏移)
    var isSelected: Boolean = false // 是否被选中

    /**
     * 播放选中动画
     */
    suspend fun select() {
        isSelected = true
        scaleAnimation.animateTo(1.2f, animationSpec = tween(300)) // 缩放到 1.2 倍
        liftAnimation.animateTo(20f, animationSpec = tween(300))   // 抬起 20 像素
    }

    /**
     * 播放取消选中动画
     */
    suspend fun deselect() {
        isSelected = false
        scaleAnimation.animateTo(1f, animationSpec = tween(300))   // 恢复原始大小
        liftAnimation.animateTo(0f, animationSpec = tween(300))    // 恢复原始位置
    }

    /**
     * 绘制棋子
     * @param drawScope 当前 Canvas 的绘制范围
     * @param cellSize 每个格子的宽高
     */
    fun draw(drawScope: DrawScope, borderLeft: Float, borderTop: Float, cellWidth: Float, cellHeight: Float, chess_size: Int) {
        val (x, y) = position
        val centerX = cellWidth * x + cellWidth / 2
        val centerY = cellHeight * y + cellHeight / 2 - liftAnimation.value
        val size = (cellWidth * scaleAnimation.value).toInt()

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
            val dstSize = IntSize(size, size)
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

//        drawScope.drawImage(
//            image = image,
//            dstOffset = IntOffset(
//                x = (borderLeft + x * cellWidth - 0.55 * chess_size).toInt(),
//                y = (borderTop + y * cellHeight - 0.55 * chess_size).toInt()
//            ),
//            dstSize = IntSize(chess_size, chess_size)
//        )
    }
}
