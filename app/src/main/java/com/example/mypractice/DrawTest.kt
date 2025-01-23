package com.example.mypractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.ui.theme.MyPracticeTheme

class DrawTest : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用 ViewModelProvider 获取实例
        val viewModel = ViewModelProvider(
            this,
            GameViewModelFactory(applicationContext)
        )[GameViewModel::class.java]

        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    DrawMain(viewModel)
                }
            }
        }
    }
}

//画棋盘
@Composable
fun ChessBoard(viewModel: GameViewModel) {
    //点击协程实例
    val tapScope = rememberCoroutineScope()
    //初始化游戏管理器
    val gameManager = remember { GameManager(tapScope) }

    //启动游戏
    gameManager.startGame()

    //从游戏管理器里读取棋子状态和布局，并初始化
    val alivePieces: List<ChessPiece> = remember { gameManager.alivePieces.flatMap { it.value } }

    //根据屏幕宽高获取棋盘宽高
    val (chessBoardWidth, chessBoardHeight) =
        gameManager.getBoardSize(LocalConfiguration.current.screenWidthDp.dp, LocalConfiguration.current.screenHeightDp.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.End
    ){
        Text(
            "Player2",
            color = Color.Red,
            fontSize = 40.sp,
            modifier = Modifier.graphicsLayer { scaleY = -1f } // 垂直翻转180度
        )
    }

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
                        gameManager.handleTap(offset)
                    }
                }
        ) {
            //绘制棋盘
            gameManager.chessBoard.initialize(size)
            gameManager.chessBoard.draw(this, imageLoader = viewModel.imageLoader)

            // 绘制棋子图片
            for (piece in alivePieces) {
                piece.draw(
                    this,
                    imageLoader = viewModel.imageLoader,
                    borderLeft = gameManager.chessBoard.borderLeft,
                    borderTop = gameManager.chessBoard.borderTop,
                    cellWidth = gameManager.chessBoard.cellWidth,
                    cellHeight = gameManager.chessBoard.cellHeight
                )
            }

            // 绘制可到达位置提示格
            gameManager.drawBox(
                this,
                imageLoader = viewModel.imageLoader,
                borderLeft = gameManager.chessBoard.borderLeft,
                borderTop = gameManager.chessBoard.borderTop,
                cellWidth = gameManager.chessBoard.cellWidth,
                cellHeight = gameManager.chessBoard.cellHeight
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
    ){
        Text(
            "Player1",
            color = Color.Black,
            fontSize = 40.sp,

        )
    }
}


@Suppress("PreviewAnnotationInFunctionWithParameters")
@Preview(showBackground = true)
@Composable
fun DrawMain(viewModel: GameViewModel) {
    ChessBoard(viewModel)
}

