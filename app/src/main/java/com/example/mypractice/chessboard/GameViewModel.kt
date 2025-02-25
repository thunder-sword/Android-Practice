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
import com.example.mypractice.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//联机状态枚举
enum class OnlineState{
    Local,
    Server,
    Client
}

//棋子坐标类，可规定是否只能存放某阵营棋子
data class PieceLocation (
    val col: Int,
    val row: Int,
    val camp: PieceCamp? = null
)

//操作类，标识一次操作的基本信息
data class Operation (
    val camp: PieceCamp,  //哪个阵营操作的
    val piece: ChessPiece,  //操作的棋子是什么
    val srcLocation: Pair<Int, Int>,  //操作的棋子原本在哪
    val dstLocation: Pair<Int, Int>,   //棋子所到的目的地址（如果等于原位置说明是翻面）
)

//死亡棋子记录类
data class DeadPiece(
    val index: Int,
    val piece: ChessPiece,
    val location: Pair<Int, Int>
)


//定义游戏状态
sealed class GameUiState: IUiState {
    //未开始游戏状态
    object Idle: GameUiState()

    //游戏结束状态
    object Ended: GameUiState()

    //游戏开始状态
    data class Running(
        val onlineState: OnlineState = OnlineState.Local,
        val currentPlayer: Int = 0,
        val localPlayer: Int = 0,
        val currentBoard: List<List<List<ChessPiece>>>
    ): GameUiState()

    //游戏错误状态
    data class Error(
        val message: String
    ): GameUiState()

    //游戏等待/重连
    data class Waiting(
        val status: String
    ): GameUiState()
}

//定义用户意图
sealed class GameUiIntent: IUiIntent {
    //点击开始游戏
    object StartGame: GameUiIntent()

    //点击悔棋
    object TryBackStep: GameUiIntent()

    //点击重新开始游戏
    object TryRestartGame: GameUiIntent()

    //点击棋盘
    data class TapBoard(val offset: Offset): GameUiIntent()
}

//游戏ViewModel
class GameViewModel(
    private val context: Context,
    private val onlineState: OnlineState = OnlineState.Local,        //联机状态
    private val tcpConnector: TCPConnectorViewModel? = null,      //所用tcp连接器
    private val tcpListener: TCPListenerViewModel? = null
): BaseViewModel<GameUiState, GameUiIntent>() {
    //网络连接器


    //初始化图片加载器
    val imageLoader = ImageLoader(context)

    //棋盘实例
    //private val chessBoard: ChessBoard = ChessBoard()
    // 玩家序列，直接用阵营表示
    private val players: Array<PieceCamp> = PieceCamp.values()
    //当前游戏棋盘状态
    private var currentBoard: Array<Array<MutableList<ChessPiece>>> = Array(9) { Array(10) { mutableListOf() } }

    //当前存活的棋子列表
    private val alivePieces: MutableList<ChessPiece>
        get() = currentBoard.flatten()  // 展平棋盘
            .flatten()  // 展平所有棋子的列表
            .toMutableStateList()
    //.groupBy { it.camp }  // 根据阵营分组
    //当前可触发的棋子列表（每叠棋子最上面那个）
    private val canTapPieces: List<ChessPiece>
        get() = currentBoard.flatten()  //展平棋盘
            .mapNotNull { it.lastOrNull() }       //只取最后一个棋子

    //初始化状态为Idle
    override fun initUiState(): GameUiState {
        return GameUiState.Idle
    }

    //重载处理意图事件函数
    override fun handleIntent(state: GameUiState, intent: GameUiIntent) {
        val value = when (intent) {
            is GameUiIntent.StartGame -> startGame(state)
            is GameUiIntent.TapBoard -> handleTap(intent.offset)
            is GameUiIntent.TryRestartGame -> tryRestartGame()
            is GameUiIntent.TryBackStep -> tryBackStep()
        }
    }

    //开始游戏
    private fun startGame(state: GameUiState){
//        if(state is GameUiState.Running){
//            println("Game is already running.")
//        }
//        else{
//            //不是客户端则需要初始化棋盘
//            if (OnlineState.Client != onlineState){
//                //给当前布局和棋子数赋值
//                piecesLayout = defaultLayout["十字交叉型"]!!
//                piecesType = defaultType["等量经典棋数"]!!
//                //检查布局和棋子数是否适合
//                assert(checkMatch())
//
//                //如果合适则自动生成随机棋局
//                generateInitialBoard()
//
//                //随机选择先手玩家
//                currentPlayer = players.indices.random()
//            }
//
//            println("chessBoard: ${serializeChessBoard(currentBoard)}")
//            println("currentPlayer: $currentPlayer")
//
//            //如果是客户端
//            if (OnlineState.Client == onlineState){
//                //设置当前玩家为玩家2
//                localPlayer = 1
//                //等待服务器初始化房间
//                blockString = "等待服务器创建棋局..."
//                //启动一个协程不断发送请求
//                CoroutineScope(Dispatchers.IO).launch {
//                    while("等待服务器创建棋局..." == blockString){
//                        //请求棋局
//                        sendMessage("query: chessBoard")
//                        //请求当前玩家
//                        sendMessage("query: currentPlayer")
//                        delay(300)
//                    }
//                }
//            }
//        }
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
