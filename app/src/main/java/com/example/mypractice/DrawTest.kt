package com.example.mypractice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
fun ChessBoard(viewModel: GameViewModel, onlineState: OnlineState = OnlineState.Local, tcpConnecter: TCPConnecter? = null) {
    val current = LocalContext.current
    //点击协程实例
    val scope = rememberCoroutineScope()
    //初始化游戏管理器
    val gameManager = remember {
        GameManager(
            current = current,
            scope = scope,
            onlineState = onlineState,
            tcpConnecter = tcpConnecter
        )
    }

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

    // 重新开始游戏弹窗是否显示
    var restartDialog by remember { mutableStateOf(false) }
    // 是否悔棋游戏弹窗是否显示
    var backDialog by remember { mutableStateOf(false) }

    // 监听 gameManager.currentState 的状态变化
    LaunchedEffect(gameManager.currentState) {
        if (gameManager.currentState == GameState.Ended) {
            restartDialog = true
        }
    }

    //如果有blockQuery信息则显示
    if (!gameManager.blockQueryString.isEmpty()) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
            title = { Text(text = "对方请求") },
            text = { Text(text = gameManager.blockQueryString) },
            confirmButton = {
                Button(onClick = {
                    restartDialog = false
                    gameManager.onBlockQueryYes?.invoke()
                }) {
                    Text("是")
                }
            },
            dismissButton = {
                Button(onClick = {
                    // 关闭弹窗
                    restartDialog = false
                    gameManager.onBlockQueryNo?.invoke()
                }) {
                    Text("否")
                }
            }
        )
    }

    //如果有block信息则显示
    if (!gameManager.blockString.isEmpty()) {
        Dialog(
            onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            // 居中显示的加载提示框
            Surface(
                modifier = Modifier
                    .width(200.dp)
                    .height(150.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.Black) // 加载指示器
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(gameManager.blockString, color = Color.Black)
                }
            }
        }
    }

    // 显示重新开始弹窗
    if (restartDialog) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
            title = { Text(text = "确定") },
            text = { Text(text = "是否要重新开始游戏？") },
            confirmButton = {
                Button(onClick = {
                    gameManager.tryRestartGame()
                    restartDialog = false
                }) {
                    Text("重新开始")
                }
            },
            dismissButton = {
                Button(onClick = {
                    // 关闭弹窗
                    restartDialog = false
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 显示悔棋确定弹窗
    if (backDialog) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
            title = { Text(text = "确定") },
            text = { Text(text = "是否真的要悔棋？") },
            confirmButton = {
                Button(onClick = {
                    if (!gameManager.tryBackStep()) {
                        Toast.makeText(current, "不能再悔棋了", Toast.LENGTH_LONG).show()
                    }
                    backDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(onClick = {
                    // 关闭弹窗
                    backDialog = false
                }) {
                    Text("取消")
                }
            }
        )
    }

    Box(
        contentAlignment = Alignment.TopStart
    ) {
        Button(
            onClick = {
                restartDialog = true
            }
        ) {
            Text(text = "重新开始")
        }
    }

    val boxModifier = Modifier
        .fillMaxWidth()

    if(0!=gameManager.localPlayer){
        boxModifier.graphicsLayer {
            scaleX = -1f
            scaleY = -1f
        } // 垂直对角翻转180度
    }

    Box(
        modifier = boxModifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Top, // 垂直方向上靠上对齐
            horizontalAlignment = Alignment.CenterHorizontally // 水平方向上居中对齐
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = -1f
                        scaleY = -1f
                    }, // 垂直对角翻转180度
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    "玩家2",
                    color = Color.Blue,
                    fontSize = 40.sp
                )
                Text(
                    text = " ${gameManager.alivePieces.groupBy { it.camp }[PieceCamp.Black]?.size ?: 0}"
                            + if (1 == gameManager.currentPlayer) {
                        "【到你了】"
                    } else "",
                    color = Color.Blue,
                    fontSize = 30.sp
                )
            }

            if (OnlineState.Local != onlineState) {
                //让文本框支持向下滚动
                val scrollState1 = rememberScrollState()
                Box(
                    Modifier
                        .verticalScroll(scrollState1)
                        .padding(8.dp),
                    Alignment.Center
                ) {
                    SelectionContainer {
                        Text(tcpConnecter!!.connectionStatus, Modifier.padding(8.dp))
                    }
                }
            }
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
                    //黑子的正面棋子需要旋转180度
                    val isRotate: Boolean = (piece.isFront && PieceCamp.Black == piece.camp)
                    piece.draw(
                        this,
                        imageLoader = viewModel.imageLoader,
                        borderLeft = gameManager.chessBoard.borderLeft,
                        borderTop = gameManager.chessBoard.borderTop,
                        cellWidth = gameManager.chessBoard.cellWidth,
                        cellHeight = gameManager.chessBoard.cellHeight,
                        isRotate = isRotate
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
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                "玩家1",
                color = Color.Red,
                fontSize = 40.sp,
            )
            Text(
                text = " ${gameManager.alivePieces.groupBy { it.camp }[PieceCamp.Red]?.size ?: 0}"
                        + if (0 == gameManager.currentPlayer) {
                    "【到你了】"
                } else "",
                color = Color.Red,
                fontSize = 30.sp
            )
        }
    }

    Box(
        contentAlignment = Alignment.BottomEnd
    ){
        Button(
            onClick = {
                backDialog = true
            }
        ){
            Text(text = "悔棋")
        }
    }
}


@Suppress("PreviewAnnotationInFunctionWithParameters")
@Preview(showBackground = true)
@Composable
fun DrawMain(viewModel: GameViewModel) {
    ChessBoard(viewModel)
}

