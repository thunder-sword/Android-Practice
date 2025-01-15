package com.example.mypractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
                    ChessBoard()
                }
            }
        }
    }
}

@Composable
fun ChessBoard() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boardWidth = size.width
        val boardHeight = size.height
        val cellWidth = boardWidth / 9 // 象棋棋盘宽度分为9列
        val cellHeight = boardHeight / 10 // 高度分为10行

        // 绘制棋盘边框
        drawRect(
            color = Color.Black,
            size = Size(boardWidth, boardHeight),
            style = Stroke(width = 5f)
        )

        // 绘制水平线
        for (i in 0..10) { // 10 行线
            val y = i * cellHeight
            drawLine(
                color = Color.Black,
                start = Offset(0f, y),
                end = Offset(boardWidth, y),
                strokeWidth = 2f
            )
        }

        // 绘制垂直线
        for (j in 0..9) { // 9 列线
            val x = j * cellWidth
            drawLine(
                color = Color.Black,
                start = Offset(x, 0f),
                end = Offset(x, boardHeight),
                strokeWidth = 2f
            )
        }

        // 绘制河界
        val riverTop = 4 * cellHeight
        val riverBottom = 5 * cellHeight
        drawLine(
            color = Color.Blue,
            start = Offset(0f, riverTop),
            end = Offset(boardWidth, riverTop),
            strokeWidth = 4f
        )
        drawLine(
            color = Color.Blue,
            start = Offset(0f, riverBottom),
            end = Offset(boardWidth, riverBottom),
            strokeWidth = 4f
        )
    }
}

