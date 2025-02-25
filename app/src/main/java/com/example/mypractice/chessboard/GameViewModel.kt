package com.example.mypractice.chessboard

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mypractice.ChessBoard
import com.example.mypractice.ChessPiece
import com.example.mypractice.DeadPiece
import com.example.mypractice.Operation
import com.example.mypractice.PieceArm
import com.example.mypractice.PieceCamp
import com.example.mypractice.PieceLocation
import com.example.mypractice.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        val onlineState: OnlineState,
        val currentPlayer: Int,
        val localPlayer: Int,
        val currentBoard: List<List<List<ChessPiece>>>,
        val selectedPiece: ChessPiece?,
        val canToLocation: List<Pair<Int, Int>>,
        val blockString: String
    ): GameUiState()

    //游戏错误状态
    data class Error(
        val message: String
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

enum class ChessGameCommand: BaseCommand{
    QueryData   //请求数据

}

//游戏ViewModel
class GameViewModel(
    private val onlineState: OnlineState = OnlineState.Local,        //联机状态
    private val tcpCommandViewModel: TCPCommandViewModel<ChessGameCommand>? = null,
): BaseViewModel<GameUiState, GameUiIntent>() {
    //棋盘实例
    private val chessBoard: ChessBoard = ChessBoard()
    // 玩家序列，直接用阵营表示
    private val players: Array<PieceCamp> = PieceCamp.values()
    //当前棋局
    private var currentBoard: List<List<MutableList<ChessPiece>>> = List(chessBoard.cols){ List(chessBoard.rows){ mutableListOf() } }

    //当前可触发的棋子列表（每叠棋子最上面那个）
    private val canTapPieces: List<ChessPiece>
        get() = currentBoard.flatten()  //展平棋盘
            .mapNotNull { it.lastOrNull() }       //只取最后一个棋子


    //存储自游戏开始以来的所有操作步数，用于悔棋操作
    private var operations: MutableList<Operation> = mutableListOf()
    //存储已死掉的所有棋子，并记录它死掉时的操作下标和位置
    private var deadPieces: MutableList<DeadPiece> = mutableListOf()

    // 所有棋子的坐标
    private var piecesLayout: List<PieceLocation> = listOf()

    // 所有棋子的类型
    private var piecesType: List<Pair<PieceCamp, PieceArm>> = listOf()

    // 内置棋子坐标
    private val defaultLayout: Map<String, List<PieceLocation>> = mapOf(
        "十字交叉型" to listOf(
            PieceLocation(0,0),
            PieceLocation(2,0),
            PieceLocation(4,0),
            PieceLocation(6,0),
            PieceLocation(8,0),
            PieceLocation(3,1),
            PieceLocation(5,1),
            PieceLocation(1,2),
            PieceLocation(2,2),
            PieceLocation(6,2),
            PieceLocation(7,2),
            PieceLocation(3,3),
            PieceLocation(5,3),
            PieceLocation(0,4),
            PieceLocation(2,4),
            PieceLocation(4,4),
            PieceLocation(6,4),
            PieceLocation(8,4),
            PieceLocation(3,5),
            PieceLocation(5,5),
            PieceLocation(1,6),
            PieceLocation(7,6),
            PieceLocation(0,7),
            PieceLocation(3,7),
            PieceLocation(4,7),
            PieceLocation(5,7),
            PieceLocation(8,7),
            PieceLocation(2,8),
            PieceLocation(6,8),
            PieceLocation(0,9),
            PieceLocation(4,9),
            PieceLocation(8,9)
        )
    )

    // 内置棋子类型
    private val defaultType: Map<String, List<Pair<PieceCamp, PieceArm>>> = mapOf(
        "等量经典棋数" to listOf(
            // 红方
            Pair(PieceCamp.Red, PieceArm.Jiang),  // 将
            Pair(PieceCamp.Red, PieceArm.Che), Pair(PieceCamp.Red, PieceArm.Che), // 两车
            Pair(PieceCamp.Red, PieceArm.Ma), Pair(PieceCamp.Red, PieceArm.Ma),   // 两马
            Pair(PieceCamp.Red, PieceArm.Pao), Pair(PieceCamp.Red, PieceArm.Pao), // 两炮
            Pair(PieceCamp.Red, PieceArm.Shi), Pair(PieceCamp.Red, PieceArm.Shi), // 两士
            Pair(PieceCamp.Red, PieceArm.Xiang), Pair(PieceCamp.Red, PieceArm.Xiang), // 两相
            Pair(PieceCamp.Red, PieceArm.Zu), Pair(PieceCamp.Red, PieceArm.Zu),
            Pair(PieceCamp.Red, PieceArm.Zu), Pair(PieceCamp.Red, PieceArm.Zu),
            Pair(PieceCamp.Red, PieceArm.Zu), // 五兵

            // 黑方
            Pair(PieceCamp.Black, PieceArm.Jiang),  // 将
            Pair(PieceCamp.Black, PieceArm.Che), Pair(PieceCamp.Black, PieceArm.Che), // 两车
            Pair(PieceCamp.Black, PieceArm.Ma), Pair(PieceCamp.Black, PieceArm.Ma),   // 两马
            Pair(PieceCamp.Black, PieceArm.Pao), Pair(PieceCamp.Black, PieceArm.Pao), // 两炮
            Pair(PieceCamp.Black, PieceArm.Shi), Pair(PieceCamp.Black, PieceArm.Shi), // 两士
            Pair(PieceCamp.Black, PieceArm.Xiang), Pair(PieceCamp.Black, PieceArm.Xiang), // 两相
            Pair(PieceCamp.Black, PieceArm.Zu), Pair(PieceCamp.Black, PieceArm.Zu),
            Pair(PieceCamp.Black, PieceArm.Zu), Pair(PieceCamp.Black, PieceArm.Zu),
            Pair(PieceCamp.Black, PieceArm.Zu) // 五卒
        )
    )

    //初始化状态为Idle
    override fun initUiState(): GameUiState {
        return GameUiState.Idle
    }

    //重载处理意图事件函数
    override fun handleIntent(state: GameUiState, intent: GameUiIntent) {
        val value = when (intent) {
            is GameUiIntent.StartGame -> startGame(state)
            is GameUiIntent.TapBoard -> handleTap(state, intent.offset)
            is GameUiIntent.TryRestartGame -> tryRestartGame()
            is GameUiIntent.TryBackStep -> tryBackStep()
        }
    }

    //检验当前布局和棋子数是否匹配
    private fun checkMatch(): Boolean {
        //如果数量不对应直接就不匹配
        if(piecesLayout.size != piecesType.size){
            return false
        }
        //布局数量存储字典
        val layoutCountMap: MutableMap<PieceCamp?, Int> = mutableMapOf()
        //棋子数量存储字典
        val typeCountMap: MutableMap<PieceCamp?, Int> = mutableMapOf()
        //统计布局数量
        for(location in piecesLayout){
            layoutCountMap[location.camp] = (layoutCountMap[location.camp] ?: 0) + 1
        }
        //统计不同阵营的棋子数量
        for((camp, _) in piecesType){
            typeCountMap[camp] = (typeCountMap[camp] ?: 0) + 1
        }
        //通用位置数量
        var generalCount = layoutCountMap[null] ?: 0
        //统计所有棋子是否有地方放
        //1.遍历所有位置，阵营特有位置数如果大于对应阵营的棋子数则不对应
        for((camp, count) in layoutCountMap){
            if(null != camp && (count > (typeCountMap[camp] ?: 0))){
                return false
            }
        }
        //2.否则使用完特定位置之后就要使用通用位置了
        for((camp, count) in typeCountMap){
            generalCount-=(count - (layoutCountMap[camp] ?: 0))
        }
        //最后判断通用位置数量，就知道位置是否已经全部用完，正好用完说明对应
        return 0 == generalCount
    }

    //作用：根据当前布局和棋子生成随机棋局
    private fun generateInitialBoard(){
        //每次生成随机棋局都将当前棋局初始化
        currentBoard = List(chessBoard.cols) { List(chessBoard.rows) { mutableListOf() } }
        operations = mutableListOf()
        deadPieces = mutableListOf()

        //现将布局和棋子根据阵营分类
        val campLayout = piecesLayout.groupBy { it.camp }
        val campType = piecesType
            .groupBy { it.first }
            .mapValues { (_, values) -> values.shuffled() }   //分类之后随机化一下列表 Map<PieceCamp, List<Pair<PieceCamp, PieceArm>>>
        //先将指定阵营的布局位置分配完
        val usedType = mutableMapOf<PieceCamp, Int>()  //用过的棋子下标长度
        for((camp, layoutList) in campLayout){
            if(null != camp){     //忽略不限制阵营的位置
                val pairList = campType[camp]!!     //获取对应阵营的棋子列表
                usedType[camp] = layoutList.size        //标记这个阵营用了几个棋子（列表前几个）
                for((index, layout) in layoutList.withIndex()){
                    val (_, arm) = pairList[index]
                    currentBoard[layout.col][layout.row].add(
                        ChessPiece(
                            position = Pair(layout.col, layout.row),
                            camp = camp,
                            arm = arm
                        )
                    )
                }
            }
        }
        //然后剩下的不指定布局随机分配
        // 将未标记的所有棋子放到同一个列表里，然后打乱
        val leftTypeList = mutableListOf<Pair<PieceCamp, PieceArm>>().apply {
            for((camp, pairList) in campType){
                addAll(
                    pairList.slice((usedType[camp] ?: 0) until pairList.size)
                )
            }
            shuffle()
        }
        // 打乱后分配给不规定阵营的布局节点即可
        for((index, layout) in (campLayout[null] ?: listOf()).withIndex()){
            val (camp, arm) = leftTypeList[index]
            currentBoard[layout.col][layout.row].add(
                ChessPiece(
                    position = Pair(layout.col, layout.row),
                    camp = camp,
                    arm = arm
                )
            )
        }
    }

    //将棋局序列化为字符串
    private fun serializeChessBoard(board: List<List<MutableList<ChessPiece>>>): String {
        val stringBuilder = StringBuilder()

        for (row in board) {
            for (col in row) {
                if (col.isNotEmpty()) {
                    // 如果格子里有多个棋子，将它们的序列化信息用逗号分隔
                    col.forEach { piece ->
                        val pieceCampChar = piece.camp.name[0]
                        val pieceArmChar = piece.arm.name[0]
                        val frontChar = if (piece.isFront) 'F' else 'B'
                        val overChar = if (piece.isOver) 'O' else 'N'

                        // 将棋子信息拼接成一个4个字符的字符串
                        stringBuilder.append("$pieceCampChar$pieceArmChar$frontChar$overChar,")
                    }
                    // 去掉最后一个逗号
                    stringBuilder.deleteCharAt(stringBuilder.length - 1)
                } else {
                    stringBuilder.append("    ")  // 空格表示没有棋子，每格四个字符
                }
            }
        }
        return stringBuilder.toString()
    }

    //将字符串反序列化为棋局
    private fun deserializeChessBoard(serializedBoard: String): List<List<MutableList<ChessPiece>>> {
        val board = List(chessBoard.cols) { List(chessBoard.rows) { mutableListOf<ChessPiece>() } }
        var index = 0

        for (row in 0 until 9) {
            for (col in 0 until 10) {
                val pieceString = serializedBoard.substring(index, index + 4)  // 每个棋子占4个字符
                index += 4

                if (pieceString.trim() != "") {  // 如果该位置有棋子
                    val piecesInfo = pieceString.split(",")  // 用逗号分隔不同棋子的序列化信息

                    piecesInfo.forEach { pieceStr ->
                        val pieceCamp = when (pieceStr[0]) {
                            'R' -> PieceCamp.Red
                            'B' -> PieceCamp.Black
                            else -> throw IllegalArgumentException("Invalid camp")
                        }

                        val pieceArm = when (pieceStr[1]) {
                            'C' -> PieceArm.Che
                            'J' -> PieceArm.Jiang
                            'M' -> PieceArm.Ma
                            'P' -> PieceArm.Pao
                            'S' -> PieceArm.Shi
                            'X' -> PieceArm.Xiang
                            'Z' -> PieceArm.Zu
                            else -> throw IllegalArgumentException("Invalid arm")
                        }

                        val isFront = pieceStr[2] == 'F'
                        val isOver = pieceStr[3] == 'O'

                        val position = Pair(row, col)
                        val chessPiece = ChessPiece(position, pieceCamp, pieceArm, isAlive = true, isFront, isOver)
                        board[row][col].add(chessPiece)
                    }
                }
            }
        }
        return board
    }


    //开始游戏
    private fun startGame(state: GameUiState){
        if(state is GameUiState.Running){
            println("Game is already running.")
        }
        else{
            var currentPlayer: Int = 0
            var localPlayer: Int = 0
            var blockString: String = ""

            //不是客户端则需要初始化棋盘
            if (OnlineState.Client != onlineState){
                //给当前布局和棋子数赋值
                piecesLayout = defaultLayout["十字交叉型"]!!
                piecesType = defaultType["等量经典棋数"]!!
                //检查布局和棋子数是否适合
                assert(checkMatch())

                //如果合适则自动生成随机棋局
                generateInitialBoard()

                //随机选择先手玩家
                currentPlayer = players.indices.random()
            }

            println("chessBoard: ${serializeChessBoard(currentBoard)}")
            println("currentPlayer: $currentPlayer")

            //如果是客户端
            if (OnlineState.Client == onlineState){
                //本地玩家为玩家2
                localPlayer = 1
                //等待服务器初始化房间
                blockString = "等待服务器创建棋局..."
            }

            //更新状态
            updateState {
                GameUiState.Running(
                    onlineState = onlineState,
                    currentPlayer = currentPlayer,
                    localPlayer = localPlayer,
                    currentBoard = currentBoard,
                    selectedPiece = null,
                    canToLocation = listOf(),
                    blockString = blockString
                )
            }

            //更新状态之后再创建等待程序
            if(OnlineState.Client == onlineState){
                viewModelScope.launch {
                    val runningState = uiState.value as? GameUiState.Running ?: return@launch
                    while (runningState.blockString.isNotEmpty()) {
                        //请求棋局
                        tcpCommandViewModel!!.sendUiIntent(
                            TCPCommandIntent.SendCommand(
                                ChessGameCommand.QueryData,
                                "chessBoard"
                            )
                        )
                        //请求当前玩家
                        tcpCommandViewModel.sendUiIntent(
                            TCPCommandIntent.SendCommand(
                                ChessGameCommand.QueryData,
                                "currentPlayer"
                            )
                        )
                        delay(300)
                    }
                }
            }
        }
    }

    // 点击事件锁
    private val tapMutex = Mutex()

    //获取可到达位置序列
    private fun getCanToLocation(selectedPiece: ChessPiece): List<Pair<Int, Int>>{
        return if (true == selectedPiece.isFront) { selectedPiece.canToLocation(currentBoard) ?: listOf() } else { listOf() }
    }

    //点击棋盘
    private fun handleTap(state: GameUiState, offset: Offset){
        val runningState = state as? GameUiState.Running ?: return
        val (col, row) = chessBoard.offsetToChessIndex(offset)
        println("点击了棋盘的坐标：列 $col, 行 $row")

        //如果当前处于加锁状态则取消操作
        if(tapMutex.isLocked){
            println("等待其他操作完成")
            //Toast.makeText(current, "等待其他操作完成", Toast.LENGTH_SHORT).show()
            return
        }

        //联机状态下，如果玩家不是自己，无法操作
        if(OnlineState.Local != onlineState && runningState.currentPlayer != runningState.localPlayer){
            println("等待远程玩家操作")
            return
        }

        viewModelScope.launch {
            //加锁，使每次事件引起的状态变化顺序进行
            tapMutex.withLock {
                //只取每一叠象棋最上面那个棋子进行判断（这个不加锁就会导致点不到的棋子被点到）
                val clickedPiece: ChessPiece? = canTapPieces.find { it.isAlive && it.position == Pair(col, row) }

                //1.如果当前没有选择棋子
                if(null == runningState.selectedPiece) {
                    //1.1.如果棋子是背面，则选中该棋子
                    //1.2.如果选择是当前玩家阵营的棋子，则选中该棋子（自动管理可到达位置序列）
                    if(false == clickedPiece?.isFront || players[runningState.currentPlayer] == clickedPiece?.camp){
                        //选中棋子
                        updateState {
                            GameUiState.Running(
                                onlineState = onlineState,
                                currentPlayer = runningState.currentPlayer,
                                localPlayer = runningState.localPlayer,
                                currentBoard = currentBoard,
                                selectedPiece = clickedPiece,
                                canToLocation = getCanToLocation(clickedPiece),
                                blockString = runningState.blockString
                            )
                        }
                    }
                }
                //2.如果当前选择了棋子
                else {
                    //2.1.如果还是点击此棋子，并且棋子是反面，则翻面【翻面后切换玩家】
                    if(false == clickedPiece?.isFront && clickedPiece == selectedPiece){
                        selectedPiece?.toFront()
                        //如果是联机状态，则发送移动指令给远程主机
                        if(OnlineState.Local != onlineState){
                            sendMessage("moveChess: ${serializeMoveTo(col, row)}")
                        }
                        //记录翻面操作
                        operations.add(Operation(players[currentPlayer], selectedPiece!!, Pair(col, row), Pair(col, row)))
                        //取消选中棋子
                        selectedPiece?.deselect()
                        selectedPiece = null
                        currentPlayer = ( currentPlayer + 1) % players.size
                    }
                    //2.2.如果点击位置不在可到达坐标序列中则取消选中该棋子
                    else if(null == canToLocation.find{ it == Pair(col, row) }){
                        selectedPiece?.deselect()
                        selectedPiece = null
                    }
                    //2.3.如果点击位置在可到达坐标序列则跳到可到达坐标（将isOver标记为false，如果到达点有背面棋子则标记棋子isOver，如果吃了棋子则删除目标棋子，更新棋子坐标和currentBoard
                    //吃掉棋子之后要判断被吃掉的棋子阵营是否还有存活棋子，没有则结束游戏）【移动棋子后切换当前玩家】
                    else{
                        //如果是联机状态，则发送移动指令给远程主机
                        if(com.example.mypractice.OnlineState.Local != onlineState){
                            sendMessage(serializeMoveTo(col, row))
                        }
                        //记录当前移动操作
                        operations.add(Operation(players[currentPlayer], selectedPiece!!, selectedPiece!!.position, Pair(col, row)))
                        movePieceTo(col=col,row=row)
                        currentPlayer = ( currentPlayer + 1) % players.size
                    }
                }
            }
        }
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
