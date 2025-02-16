package com.example.mypractice

import android.content.Context
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

//标识UI状态（Model）的接口
interface IUiState

//标识用户意图（Intent）的接口
interface IUiIntent

//定义游戏状态
sealed class GameUiState: IUiState{
    //未开始游戏状态
    object Idle: GameUiState()

    //游戏结束状态
    object Ended: GameUiState()

    //游戏开始状态
    data class Running(
        val chessBoard: ChessBoard,
        val currentPlayer: Int,
        val localPlayer: Int,
        val networkState: NetworkState = NetworkState.Normal,
        val players: Array<PieceCamp>,
        val currentBoard: Array<Array<MutableList<ChessPiece>>>
    ): GameUiState(){
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

        // 定义网络/远程状态的子类型
        sealed class NetworkState {
            // 正常运行状态（无等待）
            object Normal : NetworkState()
            // 等待网络重连
            object WaitingForReconnect : NetworkState()
            // 等待远程主机回复
            object WaitingForRemoteResponse : NetworkState()
        }
    }
}

//定义用户意图
sealed class GameUiIntent: IUiIntent{
    //点击开始游戏
    object StartGame: GameUiIntent()

    //点击悔棋
    object tryBackStep: GameUiIntent()

    //点击重新开始游戏
    object tryRestartGame: GameUiIntent()

    //点击棋盘
    data class TapBoard(val offset: Offset): GameUiIntent()
}

//游戏ViewModel
class GameViewModel(context: Context) : ViewModel() {
    //初始化图片加载器
    val imageLoader = ImageLoader(context)

    //初始化状态为Idle
    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Idle)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    //用Channel来接收用户意图
    private val _uiIntent = Channel<GameUiIntent>(Channel.UNLIMITED)
    val uiIntent: Flow<GameUiIntent> = _uiIntent.receiveAsFlow()
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
