package com.example.mypractice

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mypractice.ui.theme.MyPracticeTheme
import kotlinx.coroutines.launch

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

    //获取当前上下文
    val current= LocalContext.current

    // 处理棋子状态的协程
    val coroutineScope = rememberCoroutineScope()
    // 当前被选中棋子
    val selectedPiece = remember { mutableStateOf<ChessPiece?>(null) }

    // 加载棋盘图片
    val chessBoardImage = ImageBitmap.imageResource(id = R.drawable.board)
    // 加载棋子图片
    val chess_b_c = ImageBitmap.imageResource(id = R.drawable.b_c)
    // 定义90个棋子
    val allPieces = remember {
        mutableStateListOf<ChessPiece>().apply {
            for (col in 0..8) {
                for (row in 0..9) {
                    add(
                        ChessPiece(
                            position = Pair(col, row),
                            image = chess_b_c,
                            isAlive = true
                        )
                    )
                }
            }
        }
    }

    //初始化棋盘实例
    val chessBoard = remember {
        ChessBoard(
            cols = 9,
            rows = 10,
            image = chessBoardImage,
            paddingTopPercent = 0.02f,   // 图片顶部空白占比
            paddingBottomPercent = 0.02f, // 图片底部空白占比
            paddingLeftPercent = 0.05f,   // 图片左侧空白占比
            paddingRightPercent = 0.04f,  // 图片右侧空白占比
            borderTopPercent = 0.08f,    // 棋盘上方边框高度占有效高度的比例
            borderBottomPercent = 0.08f, // 棋盘下方边框高度占有效高度的比例
            borderLeftPercent = 0.03f,   // 棋盘左侧边框宽度占有效宽度的比例
            borderRightPercent = 0.03f  // 棋盘右侧边框宽度占有效宽度的比例
        )
    }

    // 屏幕宽高获取
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // 计算棋盘适配后的宽高
    val maxWidth = screenWidth // 最大宽度占屏幕宽度的100%
    val maxHeight = screenHeight // 最大高度占屏幕高度的100%

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
                        //Toast.makeText(current, "列 $col, 行 $row", Toast.LENGTH_SHORT).show()
                        //如果棋子被点击则切换其被选择状态
                        val clickedPiece = allPieces.find { it.isAlive && it.position == Pair(col, row) }

                        if (clickedPiece != null) {
                            //println("原始动画信息：${clickedPiece.scaleAnimation.value}，${clickedPiece.liftAnimation.value}")
                            // 切换选中状态
                            coroutineScope.launch {
                                if (selectedPiece.value == clickedPiece) {
                                    clickedPiece.deselect()
                                    selectedPiece.value = null
                                    Toast.makeText(current, "取消选中了列 $col, 行 $row 的棋子", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedPiece.value?.deselect()
                                    clickedPiece.select()
                                    selectedPiece.value = clickedPiece
                                    Toast.makeText(current, "选中了列 $col, 行 $row 的棋子", Toast.LENGTH_SHORT).show()
                                }
                            }
                            //println("点击后动画信息：${clickedPiece.scaleAnimation.value}，${clickedPiece.liftAnimation.value}")
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


@Preview(showBackground = true)
@Composable
fun DrawMain() {
    ChessBoard()
}

