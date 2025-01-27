package com.example.mypractice

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

//游戏状态枚举
enum class GameState {
    Running,
    Ended
}

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

class GameManager(
    private val tapScope: CoroutineScope,    //在点击事件发生时发起协程，只有在Composable函数创建才能使用动画
    val onlineState: OnlineState = OnlineState.Local,        //联机状态
    val tcpConnecter: TCPConnecter? = null      //所用tcp连接器
) {
    //当前游戏状态
    var currentState by mutableStateOf(GameState.Ended)
        private set

    //棋盘实例
    var chessBoard: ChessBoard = ChessBoard()
        private set

    // 玩家序列，直接用阵营表示
    val players: Array<PieceCamp> = PieceCamp.values()
    // 当前行动玩家
    var currentPlayer by mutableStateOf(0)
    // 本地玩家下标（联机状态下时本机玩家的阵营下标）
    var localPlayer by mutableStateOf(0)

    //当前游戏棋盘状态
    var currentBoard: Array<Array<MutableList<ChessPiece>>> = Array(chessBoard.cols) { Array(chessBoard.rows) { mutableListOf() } }
        private set
    //当前存活的棋子列表
    val alivePieces: MutableList<ChessPiece>
        get() = currentBoard.flatten()  // 展平棋盘
            .flatten()  // 展平所有棋子的列表
            .toMutableStateList()
            //.groupBy { it.camp }  // 根据阵营分组
    //当前可触发的棋子列表（每叠棋子最上面那个）
    val canTapPieces: List<ChessPiece>
        get() = currentBoard.flatten()  //展平棋盘
            .mapNotNull { it.lastOrNull() }       //只取最后一个棋子

    //存储自游戏开始以来的所有操作步数，用于悔棋操作
    var operations: MutableList<Operation> = mutableListOf()
    //存储已死掉的所有棋子，并记录它死掉时的操作下标和位置
    var deadPieces: MutableList<DeadPiece> = mutableListOf()


    // 所有棋子的坐标
    var piecesLayout: List<PieceLocation> = listOf()
        private set
    // 所有棋子的类型
    var piecesType: List<Pair<PieceCamp, PieceArm>> = listOf()
        private set

    // 内置棋子坐标
    private val defaultLayout: Map<String, List<PieceLocation>> = mapOf(
        "十字交叉型" to listOf(
            PieceLocation(0,0),PieceLocation(2,0),PieceLocation(4,0),PieceLocation(6,0),PieceLocation(8,0),
            PieceLocation(3,1),PieceLocation(5,1),
            PieceLocation(1,2),PieceLocation(2,2),PieceLocation(6,2),PieceLocation(7,2),
            PieceLocation(3,3),PieceLocation(5,3),
            PieceLocation(0,4),PieceLocation(2,4),PieceLocation(4,4),PieceLocation(6,4),PieceLocation(8,4),
            PieceLocation(3,5),PieceLocation(5,5),
            PieceLocation(1,6),PieceLocation(7,6),
            PieceLocation(0,7),PieceLocation(3,7),PieceLocation(4,7),PieceLocation(5,7),PieceLocation(8,7),
            PieceLocation(2,8),PieceLocation(6,8),
            PieceLocation(0,9),PieceLocation(4,9),PieceLocation(8,9)
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

    // 当前被选中棋子
    var selectedPiece by mutableStateOf<ChessPiece?>(null)
    // 点击事件锁
    private val tapMutex = Mutex()
    // 可到达位置序列
    val canToLocation: List<Pair<Int, Int>>
        get() = if (true == selectedPiece?.isFront) { selectedPiece?.canToLocation(currentBoard) ?: listOf() } else { listOf() }

    // 返回放大棋盘后的宽高
    fun getBoardSize(screenWidth: Dp, screenHeight: Dp, percent: Float = 1f): Pair<Dp, Dp> {
        val maxWidth = screenWidth * percent
        val maxHeight = screenHeight * percent
        val aspectRatio = chessBoard.rows.toFloat() / chessBoard.cols

        // 计算等比宽高
        val chessBoardWidth = if (maxWidth * aspectRatio <= maxHeight) {
            maxWidth
        } else {
            maxHeight / aspectRatio
        }
        val chessBoardHeight = chessBoardWidth * aspectRatio
        return Pair(chessBoardWidth, chessBoardHeight)
    }

    //检验当前布局和棋子数是否匹配
    fun checkMatch(): Boolean {
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
    fun generateInitialBoard(){
        //每次生成随机棋局都将当前棋局初始化
        currentBoard = Array(chessBoard.cols) { Array(chessBoard.rows) { mutableListOf() } }
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
    fun serializeChessBoard(board: Array<Array<MutableList<ChessPiece>>>): String {
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
    fun deserializeChessBoard(serializedBoard: String): Array<Array<MutableList<ChessPiece>>> {
        val board = Array(9) { Array(10) { mutableListOf<ChessPiece>() } }
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

    /**
     * 绘制棋子
     * @param drawScope 当前 Canvas 的绘制范围
     * @param imageLoader 获取图片器
     * @param borderLeft 棋盘的左侧空白部分长度
     * @param borderTop 棋盘的顶部空白部分长度
     * @param cellWidth 每个格子的宽度
     * @param cellHeight 每个格子的高度
     */
    fun drawBox(drawScope: DrawScope, imageLoader: ImageLoader, borderLeft: Float, borderTop: Float, cellWidth: Float, cellHeight: Float) {
        val image: ImageBitmap = imageLoader.getImage("r_box")!!
        for((x, y) in canToLocation) {
            val centerX = borderLeft + cellWidth * x
            val centerY = borderTop + cellHeight * y
            val size =
                sqrt(cellWidth * cellWidth + cellHeight * cellHeight.toDouble()) * 0.68 //提示格的大小设定为0.70*格子的对角线长度

            drawScope.drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    isAntiAlias = true
                }

                // 定义目标区域（图片最终绘制的位置和大小）
                val dstOffset = IntOffset(
                    (centerX - size / 2).toInt(),
                    (centerY - size / 2).toInt()
                )
                val dstSize = IntSize(size.toInt(), size.toInt())
                // 绘制图片
                canvas.drawImageRect(
                    image = image,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    paint = paint
                )
            }
        }
    }

    //作用：处理点击事件
    fun handleTap(offset: Offset){
        val (col, row) = chessBoard.offsetToChessIndex(offset)
        println("点击了棋盘的坐标：列 $col, 行 $row")

        //如果当前处于加锁状态则取消操作
        if(tapMutex.isLocked){
            println("等待其他操作完成")
            return
        }

        //联机状态下，如果玩家不是自己，无法操作
        if(OnlineState.Local != onlineState && currentPlayer != localPlayer){
            println("等待远程玩家操作")
            return
        }

        tapScope.launch {
            //加锁，使每次事件引起的状态变化顺序进行
            tapMutex.withLock {
                //只取每一叠象棋最上面那个棋子进行判断（这个不加锁就会导致点不到的棋子被点到）
                val clickedPiece: ChessPiece? = canTapPieces.find { it.isAlive && it.position == Pair(col, row) }

                //1.如果当前没有选择棋子
                if(null == selectedPiece) {
                    //1.1.如果棋子是背面，则选中该棋子
                    //1.2.如果选择是当前玩家阵营的棋子，则选中该棋子（自动管理可到达位置序列）
                    if(false == clickedPiece?.isFront || players[currentPlayer] == clickedPiece?.camp){
                        selectedPiece=clickedPiece
                        selectedPiece?.select()
                    }
                }
                //2.如果当前选择了棋子
                else {
                    //2.1.如果还是点击此棋子，并且棋子是反面，则翻面【翻面后切换玩家】
                    if(false == clickedPiece?.isFront && clickedPiece == selectedPiece){
                        selectedPiece?.toFront()
                        //将翻面操作记录
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
                        if(OnlineState.Local != onlineState){
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

    //作用：移动指令序列化
    fun serializeMoveTo(col: Int, row: Int): String{
        return "(${selectedPiece?.position!!.first},${selectedPiece?.position!!.second})->(${col},${row})"
    }

    //作用：移动指令反序列化
    fun deserializeMove(moveString: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
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


    //作用：发送信息给远程主机
    fun sendMessage(message: String){

    }

    //作用：悔棋一次，回复上次操作布局
    fun backStep(): Boolean{
        if(0==operations.size){
            return false
        }
        //悔棋过程由子进程加锁完成
        tapScope.launch {
            tapMutex.withLock {
                //恢复操作序列
                val index = operations.size - 1
                val operation = operations.last()
                operations.removeAt(index)
                //恢复指定操作前的棋局
                //1.如果是翻面则翻回去
                if (operation.srcLocation == operation.dstLocation) {
                    operation.piece.isFront = false
                }
                //2.否则则将棋子移回去
                selectedPiece = operation.piece
                currentPlayer = players.indexOf(operation.camp)  //当时是谁操作的，还让谁操作
                movePieceTo(
                    operation.srcLocation.first,
                    operation.srcLocation.second
                )  //默认当前的棋子坐标就是dstLocation，也就是只能顺序悔棋
                //最后不需要切换玩家
                //3.如果当前操作吃掉了棋子，则将那个棋子复活到原位置
                var restorePiece: DeadPiece? = null
                for (deadPiece in deadPieces) {
                    if (deadPiece.index == index) { //判断棋子是不是在本部死的
                        restorePiece = deadPiece
                        break
                    }
                }
                if(null!=restorePiece){
                    restorePiece.piece.isAlive = true
                    currentBoard[restorePiece.location.first][restorePiece.location.second].add(
                        restorePiece.piece
                    )
                    //将它从死亡列表中移除
                    deadPieces.remove(restorePiece)
                }
            }
        }
        return true
    }

    //作用：将选中棋子移动到指定位置
    suspend fun movePieceTo(col: Int, row: Int){
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
            deadPieces.add(DeadPiece(operations.size-1, atePiece, atePiece.position))
            //吃掉棋子
            currentBoard[col][row].last().isAlive=false     //棋子被吃掉了
            deadPieces.add(DeadPiece(operations.size, currentBoard[col][row].last(), Pair(col, row)))
            currentBoard[col][row].removeLast()
            currentBoard[col][row].add(selectedPiece!!)
            //如果对面棋子全部被吃完，游戏结束
            if(0 == (alivePieces.groupBy { it.camp }[atePiece.camp]?.size ?: 0)){
                endGame()
            }
        }
        //取消选中棋子
        selectedPiece?.deselect()
        selectedPiece = null
    }

    @SuppressLint("AssertionSideEffect")
    fun startGame() {
//        println("******************************[Stack Start]******************************")
//        for (stackTrackElement: StackTraceElement in Thread.currentThread().getStackTrace()) {
//            println("at " + stackTrackElement.getClassName() + "." + stackTrackElement.getMethodName() + "(" + stackTrackElement.getFileName() + ":" + stackTrackElement.getLineNumber() + ")")
//        }
//        println("******************************[Stack End]******************************")

        if (currentState == GameState.Ended) {
            currentState = GameState.Running
            println("Game started! Current state: ${currentState}")

            //给当前布局和棋子数赋值
            piecesLayout = defaultLayout["十字交叉型"]!!
            piecesType = defaultType["等量经典棋数"]!!
            //检查布局和棋子数是否适合
            assert(checkMatch())

            //如果合适则自动生成随机棋局
            generateInitialBoard()

            //随机选择先手玩家
            currentPlayer = (0..players.size - 1).random()
        } else {
            println("Game is already running.")
            return
        }
    }

    fun endGame() {
        if (currentState == GameState.Running) {
            currentState = GameState.Ended
            println("Game ended! Current state: ${currentState}")
        } else {
            println("Game is already ended.")
        }
    }
}

