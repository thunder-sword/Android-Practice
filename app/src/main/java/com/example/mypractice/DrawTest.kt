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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.ui.theme.MyPracticeTheme
import kotlinx.coroutines.launch

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
    //初始化游戏管理器
    val gameManager = remember { GameManager() }

    // 处理棋子状态的协程
    val coroutineScope = rememberCoroutineScope()
    // 当前被选中棋子
    val selectedPiece = remember { mutableStateOf<ChessPiece?>(null) }

    //初始化棋盘实例
    val chessBoard = remember {
        ChessBoard(
            imageLoader = viewModel.imageLoader
        )
    }

    //启动游戏
    gameManager.startGame()
    //生成随机序列，用于放置棋子
    val randomIndices : List<Int> = remember { (0 until gameManager.piecesType.size).shuffled() }
    var pieceIndex: Int = remember { 0 }
    //从游戏管理器里读取棋子状态和布局，并初始化
    val allPieces = remember {
        mutableStateListOf<ChessPiece>().apply {
            for (location in gameManager.piecesLayout){
                val (camp, arm) = gameManager.piecesType[randomIndices[pieceIndex]]
                add(
                    ChessPiece(
                        position = Pair(location.col, location.row),
                        camp = camp,
                        arm = arm,
                        imageLoader = viewModel.imageLoader
                    )
                )
                pieceIndex++
            }
        }
    }

//    // 定义90个棋子
//    val allPieces = remember {
//        mutableStateListOf<ChessPiece>().apply {
//            for (col in 0..8) {
//                for (row in 0..9) {
//                    add(
//                        ChessPiece(
//                            position = Pair(col, row),
//                            camp = PieceCamp.Red,
//                            arm = PieceArm.Jiang,
//                            imageLoader = viewModel.imageLoader
//                        )
//                    )
//                }
//            }
//        }
//    }
//


    // 屏幕宽高获取
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp // 最大宽度占屏幕宽度的100%
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp // 最大高度占屏幕高度的100%

    // 计算棋盘适配后的宽高（大概运算，使图片等比扩大）
    val chessBoardWidth = if (maxWidth * chessBoard.rows / chessBoard.cols <= maxHeight) {
        maxWidth
    } else {
        maxHeight * chessBoard.cols / chessBoard.rows
    }
    val chessBoardHeight = chessBoardWidth * chessBoard.rows / chessBoard.cols

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
                        val (col, row) = chessBoard.offsetToChessIndex(offset)
                        println("点击了棋盘的坐标：列 $col, 行 $row")

                        //如果棋子被点击则切换其被选择状态
                        val clickedPiece = allPieces.find { it.isAlive && it.position == Pair(col, row) }

                        //1.如果原来选中了棋子，而且不是再次点击的棋子，则直接取消选择棋子
                        if(null != selectedPiece.value){
                            coroutineScope.launch {
                            //1.1.如果是原来的棋子，而且棋子是背面的，先翻面再取消选择（正面直接取消选择）
                            if (clickedPiece == selectedPiece.value && false == clickedPiece?.isFront) {
                                selectedPiece.value?.toFront()
                            }
                                selectedPiece.value?.deselect()
                                selectedPiece.value = null
                            }
                        } else { //2.否则判断是否点中棋子，如果点中则选中，否则不操作
                            if (clickedPiece != null) {
                                // 切换选中状态
                                coroutineScope.launch {
                                    clickedPiece.select()
                                    selectedPiece.value = clickedPiece
                                }
                            }
                        }
                    }
                }
        ) {
            //绘制棋盘
            chessBoard.initialize(size)
            chessBoard.draw(this)

            // 绘制棋子图片
            for (piece in allPieces) {
                piece.draw(
                    this,
                    borderLeft = chessBoard.borderLeft,
                    borderTop = chessBoard.borderTop,
                    cellWidth = chessBoard.cellWidth,
                    cellHeight = chessBoard.cellHeight
                )
            }
        }
    }
}


@Suppress("PreviewAnnotationInFunctionWithParameters")
@Preview(showBackground = true)
@Composable
fun DrawMain(viewModel: GameViewModel) {
    ChessBoard(viewModel)
}

