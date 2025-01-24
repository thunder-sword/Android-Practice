package com.example.mypractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
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

    // 确保游戏启动只在开始执行一次
    LaunchedEffect(Unit) {
        //启动游戏
        gameManager.startGame()
    }

    // 屏幕宽高
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    // 使用 derivedStateOf 和 remember，让其只有在屏幕大小发生变化时才更改棋盘宽高
    val chessBoardSize by remember(screenWidth, screenHeight) {
        derivedStateOf {
            gameManager.getBoardSize(screenWidth, screenHeight)
        }
    }
    // 解构棋盘宽高
    val (chessBoardWidth, chessBoardHeight) = chessBoardSize

    // 用于控制弹窗是否显示
    var showDialog by remember { mutableStateOf(false) }

    // 监听 gameManager.currentState 的状态变化
    LaunchedEffect(gameManager.currentState) {
        if (gameManager.currentState == GameState.Ended) {
            showDialog = true
        }
    }

    // 显示弹窗
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
            title = { Text(text = "游戏结束") },
            text = { Text(text = "是否要重新开始游戏？") },
            confirmButton = {
                Button(onClick = {
                    // 重置游戏状态
                    gameManager.startGame()
                    showDialog = false
                }) {
                    Text("重新开始")
                }
            },
            dismissButton = {
                Button(onClick = {
                    // 关闭弹窗
                    showDialog = false
                }) {
                    Text("退出")
                }
            }
        )
    }


    Box(
        contentAlignment = Alignment.TopStart
    ){
        Button(
            onClick = {
                //重启游戏
                gameManager.endGame()
                gameManager.startGame()
            }
        ){
            Text(text = "重新开始")
        }
    }

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = -1f
                scaleY = -1f
                           }, // 垂直对角翻转180度
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
    ){
        Text(
            "玩家2",
            color = Color.Blue,
            fontSize = 40.sp
        )
        Text(
            text = " ${gameManager.alivePieces.groupBy { it.camp }[PieceCamp.Black]?.size ?: 0}"
                    + if(1==gameManager.currentPlayer) { "【到你了】" } else "",
            color = Color.Blue,
            fontSize = 30.sp
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
            for (piece in gameManager.alivePieces) {
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
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
    ){
        Text(
            "玩家1",
            color = Color.Red,
            fontSize = 40.sp,
        )
        Text(
            text = " ${gameManager.alivePieces.groupBy { it.camp }[PieceCamp.Red]?.size ?: 0}"
                    + if(0==gameManager.currentPlayer) { "【到你了】" } else "",
            color = Color.Red,
            fontSize = 30.sp
        )
    }
}


@Suppress("PreviewAnnotationInFunctionWithParameters")
@Preview(showBackground = true)
@Composable
fun DrawMain(viewModel: GameViewModel) {
    ChessBoard(viewModel)
}

