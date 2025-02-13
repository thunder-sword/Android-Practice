package com.example.mypractice

import android.content.Context
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

//MVI中所有数据都是state
data class GameStates (
    val playState: GamePlayState = GamePlayState.Ended,
    val chatMessage: String = "",
    val chessBoard: ChessBoard = ChessBoard(),
    val players: Array<PieceCamp> = PieceCamp.values(),
    val currentPlayer: Int = 0,
    val localPlayer: Int = 0,
    val currentBoard: Array<Array<MutableList<ChessPiece>>> = Array(chessBoard.cols) { Array(chessBoard.rows) { mutableListOf() } },

){
    //当前存活的棋子列表
    val alivePieces: MutableList<ChessPiece>
        get() = currentBoard.flatten()  // 展平棋盘
            .flatten()  // 展平所有棋子的列表
            .toMutableStateList()
    //.groupBy { it.camp }  // 根据阵营分组
    //当前可触发的棋子列表（每叠棋子最上面那个）
    private val canTapPieces: List<ChessPiece>
        get() = currentBoard.flatten()  //展平棋盘
            .mapNotNull { it.lastOrNull() }       //只取最后一个棋子
}

class GameViewModel(context: Context) : ViewModel() {
    val imageLoader = ImageLoader(context)
}

//传入context参数
class GameViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


//图片加载类，在首次开启游戏时自动加载所有图片
class ImageLoader(context: Context) {

    // 存储所有预加载的图片
    private val imageCache: MutableMap<String, ImageBitmap> = mutableMapOf()

    init {
        // 初始化时加载所有图片
        preloadImages(context)
    }

    private fun preloadImages(context: Context) {
        val resources = listOf(
            R.drawable.board,
            R.drawable.back,
            R.drawable.bg,
            R.drawable.back,
            R.drawable.r_box,
            R.drawable.r_minibox,
            R.drawable.b_c,
            R.drawable.b_j,
            R.drawable.b_m,
            R.drawable.b_p,
            R.drawable.b_s,
            R.drawable.b_x,
            R.drawable.b_z,
            R.drawable.r_c,
            R.drawable.r_j,
            R.drawable.r_m,
            R.drawable.r_p,
            R.drawable.r_s,
            R.drawable.r_x,
            R.drawable.r_z
        )
        for (resId in resources) {
            val image = ImageBitmap.imageResource(context.resources, resId)
            imageCache[context.resources.getResourceEntryName(resId)] = image
        }
    }

    // 获取图片
    fun getImage(name: String): ImageBitmap? {
        return imageCache[name]
    }
}
