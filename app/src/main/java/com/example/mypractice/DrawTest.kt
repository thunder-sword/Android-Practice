package com.example.mypractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.mypractice.ui.theme.MyPracticeTheme
import kotlin.math.pow

class DrawTest : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    DrawMain()
                }
            }
        }
    }
}

//画棋盘
@Composable
fun ChessBoard() {
    // 棋盘行列数
    val boardCols = 9
    val boardRows = 10

    // 加载棋盘图片
    val chessBoardImage = ImageBitmap.imageResource(id = R.drawable.board) // 替换为你的棋盘图片资源

    // 定义图片有效区域比例（图片裁剪用）
    val paddingTop = 0.02f   // 图片顶部空白占比
    val paddingBottom = 0.02f // 图片底部空白占比
    val paddingLeft = 0.05f   // 图片左侧空白占比
    val paddingRight = 0.04f  // 图片右侧空白占比

    // 定义棋盘边框的内边距（交叉点起始位置）
    val borderTop = 0.08f    // 棋盘上方边框高度占有效高度的比例
    val borderBottom = 0.08f // 棋盘下方边框高度占有效高度的比例
    val borderLeft = 0.03f   // 棋盘左侧边框宽度占有效宽度的比例
    val borderRight = 0.03f  // 棋盘右侧边框宽度占有效宽度的比例

    // 屏幕宽高获取
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // 计算棋盘适配后的宽高
    val maxWidth = screenWidth // 最大宽度占屏幕宽度的100%
    val maxHeight = screenHeight // 最大高度占屏幕高度的100%

    val chessBoardWidth = if (maxWidth * boardRows / boardCols <= maxHeight) {
        maxWidth
    } else {
        maxHeight * boardCols / boardRows
    }

    val chessBoardHeight = chessBoardWidth * boardRows / boardCols

    // Box用于居中棋盘
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 绘制棋盘
        Canvas(
            modifier = Modifier
                .size(width = chessBoardWidth, height = chessBoardHeight)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val (col, row) = offsetToChessIndexWithBorder(
                            offset,
                            Size(chessBoardWidth.toPx(), chessBoardHeight.toPx()),
                            boardCols,
                            boardRows,
                            paddingLeft,
                            paddingTop,
                            paddingRight,
                            paddingBottom,
                            borderLeft,
                            borderTop,
                            borderRight,
                            borderBottom
                        )
                        println("点击了棋盘的坐标：列 $col, 行 $row")
                    }
                }
        ) {
            // 计算图片的裁剪区域
            val imageWidth = chessBoardImage.width.toFloat()
            val imageHeight = chessBoardImage.height.toFloat()
            val effectiveLeft = paddingLeft * imageWidth
            val effectiveRight = imageWidth * (1 - paddingRight)
            val effectiveTop = paddingTop * imageHeight
            val effectiveBottom = imageHeight * (1 - paddingBottom)

            // 裁剪后的棋盘有效区域
            val sourceRect = Rect(
                left = effectiveLeft,
                top = effectiveTop,
                right = effectiveRight,
                bottom = effectiveBottom
            )

            // 绘制棋盘图片
            drawImage(
                image = chessBoardImage,
                srcOffset = IntOffset(sourceRect.left.toInt(), sourceRect.top.toInt()),
                srcSize = IntSize(
                    (sourceRect.width).toInt(),
                    (sourceRect.height).toInt()
                ),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )

            // 绘制调试用交叉点
            val cellWidth = size.width * (1 - borderLeft - borderRight) / (boardCols - 1)
            val cellHeight = size.height * (1 - borderTop - borderBottom) / (boardRows - 1)

            for (col in 0 until boardCols) {
                for (row in 0 until boardRows) {
                    drawCircle(
                        color = Color.Red,
                        radius = 3.dp.toPx(),
                        center = Offset(
                            x = size.width * borderLeft + col * cellWidth,
                            y = size.height * borderTop + row * cellHeight
                        )
                    )
                }
            }
        }
    }
}

//将点击的位置转换为棋盘上的坐标
fun offsetToChessIndexWithBorder(
    offset: Offset,
    size: Size,
    cols: Int,
    rows: Int,
    paddingLeft: Float,
    paddingTop: Float,
    paddingRight: Float,
    paddingBottom: Float,
    borderLeft: Float,
    borderTop: Float,
    borderRight: Float,
    borderBottom: Float,
    tolerance: Float = 100f // 容差范围（单位：像素）
): Pair<Int, Int> {
    // 计算有效绘制区域（裁剪后）
    val effectiveWidth = size.width * (1 - paddingLeft - paddingRight)
    val effectiveHeight = size.height * (1 - paddingTop - paddingBottom)

    val effectiveLeft = size.width * paddingLeft
    val effectiveTop = size.height * paddingTop

    // 计算棋盘区域内的偏移
    val chessBoardLeft = effectiveLeft + effectiveWidth * borderLeft
    val chessBoardTop = effectiveTop + effectiveHeight * borderTop
    val chessBoardWidth = effectiveWidth * (1 - borderLeft - borderRight)
    val chessBoardHeight = effectiveHeight * (1 - borderTop - borderBottom)

    // 计算交叉点的宽高
    val cellWidth = chessBoardWidth / (cols - 1)
    val cellHeight = chessBoardHeight / (rows - 1)

    // 遍历所有交叉点，计算距离
    var closestPoint: Pair<Int, Int>? = null
    var minDistance = Float.MAX_VALUE

    for (col in 0 until cols) {
        for (row in 0 until rows) {
            val centerX = chessBoardLeft + col * cellWidth
            val centerY = chessBoardTop + row * cellHeight
            val distance = Math.sqrt((offset.x - centerX).toDouble().pow(2) + (offset.y - centerY).toDouble().pow(2))

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


@Preview(showBackground = true)
@Composable
fun DrawMain(){

    ChessBoard()

//    //检查是否已获取到图片的边界
//    imageBounds.value?.let { bounds ->
//        // 图片实际的宽高和起始位置
//        val width = bounds.width
//        val height = bounds.height
//        val startX = bounds.left
//        val startY = bounds.top
//
//        // 网格尺寸
//        val gridWidth = width / 9
//        val gridHeight = height / 20
//        val density = LocalDensity.current.density
//
//        val textPaint= Paint().apply{
//            color=Color.Blue.toArgb()
//            textSize=40f
//            isAntiAlias = true
//        }
//        // 绘制交叉点
//        Canvas(modifier = Modifier.fillMaxSize()) {
//            //打印当前棋盘位置尺寸信息
//            drawContext.canvas.nativeCanvas.drawText(
//                "棋盘信息：x: ${startX}, y: ${startY}, width: ${width}, height: ${height}",
//                50f,
//                50f,
//                textPaint
//            )
//            //打印当前密度
//            drawContext.canvas.nativeCanvas.drawText(
//                "密度： ${density}",
//                50f,
//                100f,
//                textPaint
//            )
//
//
//            //打印交叉点
//            for (row in 0..9) {
//                for (col in 0..8) {
//                    val x = startX + col * gridWidth
//                    val y = startY + row * gridHeight
//
//                    drawCircle(
//                        color = Color.Red,
//                        radius = 5f,
//                        center = Offset(x, y)
//                    )
//                }
//            }
//        }
//    }
}

//@Composable
//fun ChessBoard() {
//    Canvas(modifier = Modifier.fillMaxSize()) {
//        val boardWidth = size.width
//        val boardHeight = size.height
//        val cellWidth = boardWidth / 9 // 象棋棋盘宽度分为9列
//        val cellHeight = boardHeight / 10 // 高度分为10行
//
//        // 绘制棋盘边框
//        drawRect(
//            color = Color.Black,
//            size = Size(boardWidth, boardHeight),
//            style = Stroke(width = 5f)
//        )
//
//        // 绘制水平线
//        for (i in 0..10) { // 10 行线
//            val y = i * cellHeight
//            drawLine(
//                color = Color.Black,
//                start = Offset(0f, y),
//                end = Offset(boardWidth, y),
//                strokeWidth = 2f
//            )
//        }
//
//        // 绘制垂直线
//        for (j in 0..9) { // 9 列线
//            val x = j * cellWidth
//            drawLine(
//                color = Color.Black,
//                start = Offset(x, 0f),
//                end = Offset(x, boardHeight),
//                strokeWidth = 2f
//            )
//        }
//
//        // 绘制河界
//        val riverTop = 4 * cellHeight
//        val riverBottom = 5 * cellHeight
//        drawLine(
//            color = Color.Blue,
//            start = Offset(0f, riverTop),
//            end = Offset(boardWidth, riverTop),
//            strokeWidth = 4f
//        )
//        drawLine(
//            color = Color.Blue,
//            start = Offset(0f, riverBottom),
//            end = Offset(boardWidth, riverBottom),
//            strokeWidth = 4f
//        )
//    }
//}

