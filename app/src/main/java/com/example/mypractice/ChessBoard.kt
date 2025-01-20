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

class ChessBoard(
    val cols: Int,
    val rows: Int,
    val image: ImageBitmap,
    val paddingTopPercent: Float,   //顶部空白区域百分比
    val paddingBottomPercent: Float,     //底部空白区域百分比
    val paddingLeftPercent: Float,  //左侧空白区域百分比
    val paddingRightPercent: Float,     //右侧空白区域百分比
    val borderTopPercent: Float,    // 棋盘上方边框高度占有效高度的比例
    val borderBottomPercent: Float,  // 棋盘下方边框高度占有效高度的比例
    val borderLeftPercent: Float,   // 棋盘左侧边框宽度占有效宽度的比例
    val borderRightPercent: Float  // 棋盘右侧边框宽度占有效宽度的比例
) {
    // 棋盘单元格大小
    var cellWidth: Float = 0f
    var cellHeight: Float = 0f
    // 棋盘空白区域大小（对于屏幕）
    var paddingLeft: Float = 0f
    var paddingTop: Float = 0f
    // 棋盘内部边框大小（对于屏幕）
    var borderLeft: Float = 0f
    var borderTop: Float = 0f

    // 初始化棋盘大小
    fun initialize(canvasSize: Size) {
        paddingLeft = canvasSize.width * paddingLeftPercent
        paddingTop = canvasSize.height * paddingTopPercent
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
                val centerX = paddingLeft + borderLeft + col * cellWidth
                val centerY = paddingTop + borderTop + row * cellHeight
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
    fun draw(drawScope: DrawScope) {
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
        }
    }
}
