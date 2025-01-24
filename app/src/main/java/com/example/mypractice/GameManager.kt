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

//棋子坐标类，可规定是否只能存放某阵营棋子
data class PieceLocation (
    val col: Int,
    val row: Int,
    val camp: PieceCamp? = null
)

class GameManager(
    private val tapScope: CoroutineScope    //在点击事件发生时发起协程，只有在Composable函数创建才能使用动画
) {
    //当前游戏状态
    var currentState by mutableStateOf(GameState.Ended)
        private set

    //棋盘实例
    var chessBoard: ChessBoard = ChessBoard()
        private set

    //玩家序列，直接用阵营表示
    val players: Array<PieceCamp> = PieceCamp.values()
    //当前行动玩家
    var currentPlayer by mutableStateOf(0)

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
    val selectedPiece = mutableStateOf<ChessPiece?>(null)
    // 点击事件锁
    private val tapMutex = Mutex()
    // 可到达位置序列
    val canToLocation: List<Pair<Int, Int>>
        get() = if (true == selectedPiece.value?.isFront) { selectedPiece.value?.canToLocation(currentBoard) ?: listOf() } else { listOf() }

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

        tapScope.launch {
            //加锁，使每次事件引起的状态变化顺序进行
            tapMutex.withLock {
                //只取每一叠象棋最上面那个棋子进行判断（这个不加锁就会导致点不到的棋子被点到）
                val clickedPiece: ChessPiece? = canTapPieces.find { it.isAlive && it.position == Pair(col, row) }

                //1.如果当前没有选择棋子
                if(null == selectedPiece.value) {
                    //1.1.如果棋子是背面，则选中该棋子
                    //1.2.如果选择是当前玩家阵营的棋子，则选中该棋子（自动管理可到达位置序列）
                    if(false == clickedPiece?.isFront || players[currentPlayer] == clickedPiece?.camp){
                        selectedPiece.value=clickedPiece
                        selectedPiece.value?.select()
                    }
                }
                //2.如果当前选择了棋子
                else {
                    //2.1.如果还是点击此棋子，并且棋子是反面，则翻面【翻面后切换玩家】
                    if(false == clickedPiece?.isFront && clickedPiece == selectedPiece.value){
                        selectedPiece.value?.toFront()
                        //取消选中棋子
                        selectedPiece.value?.deselect()
                        selectedPiece.value = null
                        currentPlayer = ( currentPlayer + 1) % players.size
                    }
                    //2.2.如果点击位置不在可到达坐标序列中则取消选中该棋子
                    else if(null == canToLocation.find{ it == Pair(col, row) }){
                        selectedPiece.value?.deselect()
                        selectedPiece.value = null
                    }
                    //2.3.如果点击位置在可到达坐标序列则跳到可到达坐标（将isOver标记为false，如果到达点有背面棋子则标记棋子isOver，如果吃了棋子则删除目标棋子，更新棋子坐标和currentBoard
                    //吃掉棋子之后要判断被吃掉的棋子阵营是否还有存活棋子，没有则结束游戏）【移动棋子后切换当前玩家】
                    else{
                        //切换当前坐标
                        selectedPiece.value?.isOver=false
                        currentBoard[selectedPiece.value?.position!!.first][selectedPiece.value?.position!!.second].remove(selectedPiece.value)
                        selectedPiece.value?.position=Pair(col, row)
                        //处理目标节点
                        //(1)如果目标没放棋子，直接放那即可
                        if(0 == currentBoard[col][row].size){
                            currentBoard[col][row].add(selectedPiece.value!!)
                        }
                        //(2)如果有棋子则判断最上面那个棋子，棋子是否是反面，反面就放到它上面
                        else if(!currentBoard[col][row].last().isFront){
                            selectedPiece.value?.isOver=true
                            currentBoard[col][row].add(selectedPiece.value!!)
                        }
                        //(3)正面就吃掉它，同样判断最上面的棋子
                        else{
                            //如果被吃掉的棋子在其他棋子上面，本棋子要继承此状态
                            if(currentBoard[col][row].last().isOver){
                                selectedPiece.value?.isOver = true
                            }
                            val ateCamp = currentBoard[col][row].last().camp
                            currentBoard[col][row].last().isAlive=false     //棋子被吃掉了
                            currentBoard[col][row].removeLast()
                            currentBoard[col][row].add(selectedPiece.value!!)
                            //如果对面棋子全部被吃完，游戏结束
                            if(0 == (alivePieces.groupBy { it.camp }[ateCamp]?.size ?: 0)){
                                endGame()
                            }
                        }
                        //取消选中棋子
                        selectedPiece.value?.deselect()
                        selectedPiece.value = null
                        currentPlayer = ( currentPlayer + 1) % players.size
                    }
                }
            }
        }
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

