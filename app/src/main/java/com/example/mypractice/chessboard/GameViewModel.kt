package com.example.mypractice.chessboard

import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

//等待状况枚举
enum class WaitStatus(val info: String){
    WaitChessSynchronization("等待服务器同步棋局...")
}

//定义游戏事件
sealed class GameUiEvent: IUiEvent{
    data class ShowToast(val message: String, val isLong: Boolean = false): GameUiEvent()
}

data class GameUiState(val gamePlayUiState: GamePlayUiState, val networkState: NetworkState):IUiState

//定义联机状态
sealed class NetworkState{
    object Idle: NetworkState()
    object Connecting: NetworkState()
    data class Running(
        val command: BaseCommand?,
        val value: String
    ): NetworkState()
    data class Waiting(val waitStatus: WaitStatus): NetworkState()
    data class Error(val message: String): NetworkState()
    data class Reconnecting(val attempt: Int): NetworkState()
    object Disconnected: NetworkState()
}

fun TCPCommandState.toNetworkState(): NetworkState{
    return when(this){
        TCPCommandState.Connecting -> NetworkState.Connecting
        TCPCommandState.Disconnected -> NetworkState.Disconnected
        is TCPCommandState.Error -> NetworkState.Error(message)
        TCPCommandState.Idle -> NetworkState.Idle
        is TCPCommandState.Reconnecting -> NetworkState.Reconnecting(attempt)
        is TCPCommandState.Running -> NetworkState.Running(command, value)
    }
}

//定义游戏状态
sealed class GamePlayUiState {
    //未开始游戏状态
    object Idle: GamePlayUiState()

    //游戏结束状态
    object Ended: GamePlayUiState()

    //游戏开始状态
    data class Running(
        val onlineState: OnlineState,
        val currentPlayer: Int,
        val localPlayer: Int,
        val currentBoard: List<List<List<ChessPiece>>>,
        val selectedPiece: ChessPiece?,
        val canToLocation: List<Pair<Int, Int>>
    ): GamePlayUiState()

    //游戏错误状态
    data class Error(
        val message: String
    ): GamePlayUiState()
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
    QueryData,   //请求数据
    MoveChess,   //移动棋子
}

//游戏ViewModel
class GameViewModel(
    private val onlineState: OnlineState = OnlineState.Local,        //联机状态
    private val tcpCommandViewModel: TCPCommandViewModel<ChessGameCommand>? = null,
): BaseViewModel<GameUiState, GameUiIntent, GameUiEvent>() {
    //注册自动更新网络状态
    init{
        viewModelScope.launch {
            tcpCommandViewModel?.uiState?.collect{
                updateState {
                    GameUiState(
                        uiState.value.gamePlayUiState,
                        tcpCommandViewModel.uiState.value.toNetworkState()
                    )
                }
            }
        }
    }

    // 事件锁
    private val mutex = Mutex()

    //棋盘实例
    private val chessBoard: ChessBoard = ChessBoard()
    // 玩家序列，直接用阵营表示
    private val players: Array<PieceCamp> = PieceCamp.values()
    //当前棋局
    private var currentBoard: List<List<MutableList<ChessPiece>>> = List(chessBoard.cols){ List(chessBoard.rows){ mutableListOf() } }

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
        return GameUiState(GamePlayUiState.Idle, NetworkState.Idle)
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

    //作用：移动指令序列化
    private fun serializeMoveTo(selectedPiece: ChessPiece, col: Int, row: Int): String{
        return "(${selectedPiece.position!!.first},${selectedPiece.position!!.second})->(${col},${row})"
    }

    //作用：移动指令反序列化
    private fun deserializeMove(moveString: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val parts = moveString.split("->")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid move string format")
        }

        val from = parts[0].removeSurrounding("(", ")").split(",").map { it.toInt() }
        val to = parts[1].removeSurrounding("(", ")").split(",").map { it.toInt() }

        if (from.size != 2 || to.size != 2) {
            throw IllegalArgumentException("Invalid coordinates in move string")
        }

        return Pair(Pair(from[0], from[1]), Pair(to[0], to[1]))
    }

    //作用：将选中棋子移动到指定位置
    private suspend fun movePieceTo(selectedPiece: ChessPiece,col: Int, row: Int){
        //切换当前坐标
        selectedPiece?.isOver=false
        currentBoard[selectedPiece?.position!!.first][selectedPiece?.position!!.second].remove(selectedPiece)
        selectedPiece?.position=Pair(col, row)
        //处理目标节点
        //(1)如果目标没放棋子，直接放那即可
        if(0 == currentBoard[col][row].size){
            currentBoard[col][row].add(selectedPiece!!)
        }
        //(2)如果有棋子则判断最上面那个棋子，棋子是否是反面，反面就放到它上面
        else if(!currentBoard[col][row].last().isFront){
            selectedPiece?.isOver=true
            currentBoard[col][row].add(selectedPiece!!)
        }
        //(3)正面就吃掉它，同样判断最上面的棋子
        else{
            //如果被吃掉的棋子在其他棋子上面，本棋子要继承此状态
            if(currentBoard[col][row].last().isOver){
                selectedPiece?.isOver = true
            }
            val atePiece = currentBoard[col][row].last()
            //记录被吃掉的棋子
            deadPieces.add(
                DeadPiece(
                    operations.size - 1,
                    atePiece,
                    atePiece.position
                )
            )
            //吃掉棋子
            atePiece.isAlive=false     //棋子被吃掉了
            currentBoard[col][row].removeLast()
            currentBoard[col][row].add(selectedPiece!!)
            //如果对面棋子全部被吃完，游戏结束
            if(0 == (alivePieces.groupBy { it.camp }[atePiece.camp]?.size ?: 0)){
                updateState { GameUiState(GamePlayUiState.Ended, uiState.value.networkState) }
            }
        }
        //取消选中棋子
        selectedPiece?.deselect()//【动画】
        val runningState = uiState.value.gamePlayUiState as GamePlayUiState.Running
        updateState {
            GameUiState(
                GamePlayUiState.Running(
                    onlineState = onlineState,
                    currentPlayer = ( runningState.currentPlayer + 1) % players.size,
                    localPlayer = runningState.localPlayer,
                    currentBoard = currentBoard,
                    selectedPiece = null,
                    canToLocation = listOf()
                ),
                uiState.value.networkState
            )
        }
    }

    //开始游戏
    private fun startGame(state: GameUiState){
        if(state.gamePlayUiState is GamePlayUiState.Running){
            println("Game is already running.")
        }
        else{
            var currentPlayer: Int = 0
            var localPlayer: Int = 0
            var waitStatus: WaitStatus? = null

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
                waitStatus = WaitStatus.WaitChessSynchronization
            }

            //更新状态
            updateState {
                GameUiState(
                    GamePlayUiState.Running(
                        onlineState = onlineState,
                        currentPlayer = currentPlayer,
                        localPlayer = localPlayer,
                        currentBoard = currentBoard,
                        selectedPiece = null,
                        canToLocation = listOf()
                    ),
                    if(waitStatus!=null) NetworkState.Waiting(waitStatus) else state.networkState
                )
            }

            //更新状态之后再创建等待程序
            if(OnlineState.Client == onlineState){
                viewModelScope.launch {
                    val runningState = uiState.value.networkState as? NetworkState.Waiting ?: return@launch
                    while (runningState.waitStatus == WaitStatus.WaitChessSynchronization) {
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

    //获取可到达位置序列
    private fun getCanToLocation(selectedPiece: ChessPiece): List<Pair<Int, Int>>{
        return if (selectedPiece.isFront) { selectedPiece.canToLocation(currentBoard) ?: listOf() } else { listOf() }
    }

    //点击棋盘
    private fun handleTap(state: GameUiState, offset: Offset){
        val runningState = state.gamePlayUiState as? GamePlayUiState.Running ?: return
        val (col, row) = chessBoard.offsetToChessIndex(offset)
        println("点击了棋盘的坐标：列 $col, 行 $row")

        //如果当前处于加锁状态则取消操作
        if(mutex.isLocked){
            println("等待其他操作完成")
            sendUiEvent(GameUiEvent.ShowToast("等待其他操作完成"))
            return
        }

        //联机状态下，如果玩家不是自己，无法操作
        if(OnlineState.Local != onlineState && runningState.currentPlayer != runningState.localPlayer){
            println("等待远程玩家操作")
            sendUiEvent(GameUiEvent.ShowToast("等待远程玩家操作"))
            return
        }

        viewModelScope.launch {
            val selectedPiece: ChessPiece? = runningState.selectedPiece
            //加锁，使每次事件引起的状态变化顺序进行
            mutex.withLock {
                //只取每一叠象棋最上面那个棋子进行判断（这个不加锁就会导致点不到的棋子被点到）
                val clickedPiece: ChessPiece? = canTapPieces.find { it.isAlive && it.position == Pair(col, row) }

                //1.如果当前没有选择棋子
                if(null == runningState.selectedPiece) {
                    //1.1.如果棋子是背面，则选中该棋子
                    //1.2.如果选择是当前玩家阵营的棋子，则选中该棋子（自动管理可到达位置序列）
                    if(false == clickedPiece?.isFront || players[runningState.currentPlayer] == clickedPiece?.camp){
                        //选中棋子
                        updateState {
                            GameUiState(
                                GamePlayUiState.Running(
                                    onlineState = onlineState,
                                    currentPlayer = runningState.currentPlayer,
                                    localPlayer = runningState.localPlayer,
                                    currentBoard = currentBoard,
                                    selectedPiece = clickedPiece,
                                    canToLocation = getCanToLocation(clickedPiece)
                                ),
                                state.networkState
                            )
                        }
                    }
                }
                //2.如果当前选择了棋子
                else {
                    //2.1.如果还是点击此棋子，并且棋子是反面，则翻面【翻面后切换玩家】
                    if(false == clickedPiece?.isFront && clickedPiece == selectedPiece){
                        selectedPiece.toFront() //【动画】
                        //如果是联机状态，则发送移动指令给远程主机
                        if(OnlineState.Local != onlineState){
                            tcpCommandViewModel!!.sendUiIntent(TCPCommandIntent.SendCommand(ChessGameCommand.MoveChess, serializeMoveTo(selectedPiece, col, row)))
                            //sendMessage("moveChess: ${serializeMoveTo(col, row)}")
                        }
                        //记录翻面操作
                        operations.add(Operation(players[runningState.currentPlayer], runningState.selectedPiece!!, Pair(col, row), Pair(col, row)))
                        //取消选中棋子
                        selectedPiece.deselect()//【动画】
                        updateState {
                            GameUiState(
                                GamePlayUiState.Running(
                                    onlineState = onlineState,
                                    currentPlayer = ( runningState.currentPlayer + 1) % players.size,
                                    localPlayer = runningState.localPlayer,
                                    currentBoard = currentBoard,
                                    selectedPiece = null,
                                    canToLocation = listOf()
                                ),
                                state.networkState
                            )
                        }
                    }
                    //2.2.如果点击位置不在可到达坐标序列中则取消选中该棋子
                    else if(null == runningState.canToLocation.find{ it == Pair(col, row) }){
                        selectedPiece?.deselect()//【动画】
                        //选中棋子
                        updateState {
                            GameUiState(
                                    GamePlayUiState.Running(
                                    onlineState = onlineState,
                                    currentPlayer = runningState.currentPlayer,
                                    localPlayer = runningState.localPlayer,
                                    currentBoard = currentBoard,
                                    selectedPiece = null,
                                    canToLocation = listOf()
                                ),
                                state.networkState
                            )
                        }
                    }
                    //2.3.如果点击位置在可到达坐标序列则跳到可到达坐标（将isOver标记为false，如果到达点有背面棋子则标记棋子isOver，如果吃了棋子则删除目标棋子，更新棋子坐标和currentBoard
                    //吃掉棋子之后要判断被吃掉的棋子阵营是否还有存活棋子，没有则结束游戏）【移动棋子后切换当前玩家】
                    else{
                        //如果是联机状态，则发送移动指令给远程主机
                        if(OnlineState.Local != onlineState){
                            //sendMessage(serializeMoveTo(col, row))
                            tcpCommandViewModel!!.sendUiIntent(TCPCommandIntent.SendCommand(ChessGameCommand.MoveChess, serializeMoveTo(selectedPiece!!, col, row)))
                        }
                        //记录当前移动操作
                        operations.add(Operation(players[runningState.currentPlayer], selectedPiece!!, selectedPiece!!.position, Pair(col, row)))
                        movePieceTo(selectedPiece, col=col,row=row)
                        //currentPlayer = ( currentPlayer + 1) % players.size
                        updateState {
                            GameUiState(
                                GamePlayUiState.Running(
                                    onlineState = onlineState,
                                    currentPlayer = ( runningState.currentPlayer + 1) % players.size,
                                    localPlayer = runningState.localPlayer,
                                    currentBoard = currentBoard,
                                    selectedPiece = null,
                                    canToLocation = listOf()
                                ),
                                state.networkState
                            )
                        }
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
class GameViewModelFactory(
    private val onlineState: OnlineState = OnlineState.Local,        //联机状态
    private val tcpCommandViewModel: TCPCommandViewModel<ChessGameCommand>? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(onlineState, tcpCommandViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
