package com.example.mypractice.chessboard

import android.content.Context
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.ChessBoard
import com.example.mypractice.ChessPiece
import com.example.mypractice.OnlineState
import com.example.mypractice.PieceCamp
import com.example.mypractice.TCPConnector
import com.example.mypractice.utils.BaseViewModel
import com.example.mypractice.utils.IUiIntent
import com.example.mypractice.utils.IUiState
import com.example.mypractice.utils.ImageLoader

//定义游戏状态
sealed class GameUiState: IUiState {
    //未开始游戏状态
    object Idle: GameUiState()

    //游戏结束状态
    object Ended: GameUiState()

    //游戏开始状态
    data class Running(
        val onlineState: OnlineState = OnlineState.Local,        //联机状态
        val tcpConnector: TCPConnector? = null
    ): GameUiState(){
        val chessBoard: ChessBoard = ChessBoard()
        val currentPlayer: Int = 0
        val localPlayer: Int = 0
        // 玩家序列，直接用阵营表示
        val players: Array<PieceCamp> = PieceCamp.values()
        //当前游戏棋盘状态
        private var currentBoard: Array<Array<MutableList<ChessPiece>>> = Array(chessBoard.cols) { Array(chessBoard.rows) { mutableListOf() } }

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
            //未连接
            object NotConnected: NetworkState()
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
sealed class GameUiIntent: IUiIntent {
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
class GameViewModel(context: Context): BaseViewModel<GameUiState, GameUiIntent>() {
    //初始化图片加载器
    val imageLoader = ImageLoader(context)

    //初始化状态为Idle
    override fun initUiState(): GameUiState {
        return GameUiState.Idle
    }

    //重载处理意图事件函数
    override fun handleIntent(intent: GameUiIntent) {
        val value = when (intent) {
            is GameUiIntent.StartGame -> startGame()
            is GameUiIntent.TapBoard -> handleTap(intent.offset)
            is GameUiIntent.tryRestartGame -> tryRestartGame()
            is GameUiIntent.tryBackStep -> tryBackStep()
        }
    }

    //开始游戏
    private fun startGame(){

    }

    //点击棋盘
    private fun handleTap(offset: Offset){

    }

    //尝试重新开始游戏
    private fun tryRestartGame(){

    }

    //尝试悔棋
    private fun tryBackStep(){

    }
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
