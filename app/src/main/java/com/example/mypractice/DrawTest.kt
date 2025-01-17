package com.example.mypractice

import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mypractice.ui.theme.MyPracticeTheme

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

@Preview(showBackground = true)
@Composable
fun DrawMain(){
    // 记录图片的尺寸和位置
    val imageBounds = remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 棋盘背景
        Image(
            painter = painterResource(id = R.drawable.board),
            contentDescription = "Chess Board",
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    imageBounds.value = coordinates.boundsInParent()// 获取图片的位置和尺寸
                },
            contentScale = ContentScale.Fit     //等比扩大图片
        )
//        //棋盘Box
//        Box(
//            modifier = Modifier.fillMaxSize().padding(5.dp),
//            contentAlignment = Alignment.TopStart,
//        ) {
//            Canvas(modifier = Modifier.fillMaxSize()) {
//                // 绘制棋盘边框
//                drawRect(
//                    color = Color.Black,
//                    size = Size(size.width, size.height),
//                    style = Stroke(width = 5f)
//                )
//            }
//        }

        // 棋子
        Image(
            painter = painterResource(id = R.drawable.b_c), // 替换为你的棋子图片资源ID
            contentDescription = "Chess Piece",
            modifier = Modifier
                .size(35.dp) // 棋子的大小
                .offset(x = -169.dp, y = -198.dp) // 放置在棋盘上的位置
        )
        // 棋子
        Image(
            painter = painterResource(id = R.drawable.b_c), // 替换为你的棋子图片资源ID
            contentDescription = "Chess Piece",
            modifier = Modifier
                .size(35.dp) // 棋子的大小
                .offset(x = 170.dp, y = -198.dp) // 放置在棋盘上的位置
        )
        // 棋子
        Image(
            painter = painterResource(id = R.drawable.b_c), // 替换为你的棋子图片资源ID
            contentDescription = "Chess Piece",
            modifier = Modifier
                .size(35.dp) // 棋子的大小
                .offset(x = -169.dp, y = 194.dp) // 放置在棋盘上的位置
        )
    }
    // 检查是否已获取到图片的边界
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
//            color=Color.Black.toArgb()
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

