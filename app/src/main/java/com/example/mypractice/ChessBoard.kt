package com.example.mypractice

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.pow
import kotlin.math.sqrt

class ChessBoard {
    val cols: Int = 9
    val rows: Int = 10

    //图片相关信息
    private val paddingTopPercent: Float = 0.02f   // 图片顶部空白占比
    private val paddingBottomPercent: Float = 0.02f // 图片底部空白占比
    private val paddingLeftPercent: Float = 0.03f   // 图片左侧空白占比
    private val paddingRightPercent: Float = 0.02f  // 图片右侧空白占比
    private val borderTopPercent: Float = 0.08f    // 棋盘上方边框高度占有效高度的比例
    private val borderBottomPercent: Float = 0.08f // 棋盘下方边框高度占有效高度的比例
    private val borderLeftPercent: Float = 0.05f   // 棋盘左侧边框宽度占有效宽度的比例
    private val borderRightPercent: Float = 0.05f  // 棋盘右侧边框宽度占有效宽度的比例


    // 棋盘单元格大小
    var cellWidth: Float = 0f
    var cellHeight: Float = 0f
    // 棋盘内部边框大小（对于屏幕）
    var borderLeft: Float = 0f
    var borderTop: Float = 0f

    // 初始化棋盘大小
    fun initialize(canvasSize: Size) {
        borderLeft = canvasSize.width * borderLeftPercent
        borderTop = canvasSize.height * borderTopPercent
        cellWidth = canvasSize.width * ( 1 - borderLeftPercent - borderRightPercent) / (cols - 1)
        cellHeight = canvasSize.height * ( 1 - borderTopPercent - borderBottomPercent) / (rows - 1)
    }

    // 转换屏幕坐标为棋盘索引
    fun offsetToChessIndex(
            offset: Offset,
            tolerance: Float = 100f // 容差范围（单位：像素）
        ): Pair<Int, Int> {
        // 遍历所有交叉点，计算距离
        var closestPoint: Pair<Int, Int>? = null
        var minDistance = Float.MAX_VALUE

        for (col in 0 until cols) {
            for (row in 0 until rows) {
                val centerX = borderLeft + col * cellWidth
                val centerY = borderTop + row * cellHeight
                val distance = sqrt((offset.x - centerX).toDouble().pow(2) + (offset.y - centerY).toDouble().pow(2))

                if (distance < minDistance) {
                    minDistance = distance.toFloat()
                    closestPoint = Pair(col, row)
                }
            }
        }

        // 判断最近的交叉点是否在容差范围内
        return if (minDistance <= tolerance) {
            closestPoint ?: Pair(-1, -1)
        } else {
            Pair(-1, -1)
        }
    }

    // 绘制棋盘（包括图片裁剪逻辑）
    fun draw(drawScope: DrawScope, imageLoader: ImageLoader) {
        val image: ImageBitmap = imageLoader.getImage("board")!!

        val srcRect = Rect(
            paddingLeftPercent * image.width.toFloat(),
            paddingTopPercent * image.height.toFloat(),
            image.width.toFloat() * (1 - paddingRightPercent),
            image.height.toFloat() * (1 - paddingBottomPercent)
        )

        drawScope.drawIntoCanvas { canvas ->
            canvas.drawImageRect(
                image = image,
                srcOffset = IntOffset(srcRect.left.toInt(), srcRect.top.toInt()),
                srcSize = IntSize(srcRect.width.toInt(), srcRect.height.toInt()),
                dstSize = IntSize(drawScope.size.width.toInt(), drawScope.size.height.toInt()),
                paint = Paint().apply { isAntiAlias = true }
            )

//            // 绘制交叉点（调试用，可移除）
//            for (col in 0 until cols) {
//                for (row in 0 until rows) {
//                    val centerX = borderLeft + col * cellWidth
//                    val centerY = borderTop + row * cellHeight
//                    drawScope.drawCircle(
//                        color = Color.Red,
//                        radius = 9f,
//                        center = Offset(centerX, centerY)
//                    )
//                }
//            }

        }
    }
}
