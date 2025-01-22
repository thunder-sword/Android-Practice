package com.example.mypractice

import android.annotation.SuppressLint

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

class GameManager {
    //当前游戏状态
    var currentState: GameState = GameState.Ended
        private set

    //当前棋盘实例
    var chessBoard: ChessBoard = ChessBoard()
        private set

    //当前游戏棋盘状态
    var currentBoard: Array<Array<MutableList<ChessPiece>>> = Array(chessBoard.cols) { Array(chessBoard.rows) { mutableListOf() } }
        private set
    //当前存活的棋子列表
    val alivePieces: Map<PieceCamp, List<ChessPiece>>
        get() = currentBoard.flatten()  // 展平棋盘
            .flatten()  // 展平所有棋子的列表
            .groupBy { it.camp }  // 根据阵营分组

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

    @SuppressLint("AssertionSideEffect")
    fun startGame() {
        if (currentState == GameState.Ended) {
            currentState = GameState.Running
            println("Game started! Current state: $currentState")

            //给当前布局和棋子数赋值
            piecesLayout = defaultLayout["十字交叉型"]!!
            piecesType = defaultType["等量经典棋数"]!!
            //检查布局和棋子数是否适合
            assert(checkMatch())

            //如果合适则自动生成随机棋局
            generateInitialBoard()
        } else {
            println("Game is already running.")
            return
        }
    }

    fun endGame() {
        if (currentState == GameState.Running) {
            currentState = GameState.Ended
            println("Game ended! Current state: $currentState")
        } else {
            println("Game is already ended.")
        }
    }

    fun getStatus(): String {
        return "Current game state: $currentState"
    }
}

//fun main() {
//    val gameManager = GameManager()
//
//    println(gameManager.getStatus()) // 初始状态
//    gameManager.startGame()          // 启动游戏
//    println(gameManager.getStatus()) // 游戏状态
//    gameManager.endGame()            // 结束游戏
//    println(gameManager.getStatus()) // 游戏状态
//}
