package com.example.mypractice

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
    var currentState: GameState = GameState.Ended
        private set

    // 所有棋子的坐标
    private var piecesLayout: List<PieceLocation> = listOf()
    // 所有棋子的类型
    private var piecesType: List<Pair<PieceCamp, PieceArm>> = listOf()

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
            Pair(PieceCamp.RED, PieceArm.Jiang),  // 将
            Pair(PieceCamp.RED, PieceArm.Che), Pair(PieceCamp.RED, PieceArm.Che), // 两车
            Pair(PieceCamp.RED, PieceArm.Ma), Pair(PieceCamp.RED, PieceArm.Ma),   // 两马
            Pair(PieceCamp.RED, PieceArm.Pao), Pair(PieceCamp.RED, PieceArm.Pao), // 两炮
            Pair(PieceCamp.RED, PieceArm.Shi), Pair(PieceCamp.RED, PieceArm.Shi), // 两士
            Pair(PieceCamp.RED, PieceArm.Xiang), Pair(PieceCamp.RED, PieceArm.Xiang), // 两相
            Pair(PieceCamp.RED, PieceArm.Zu), Pair(PieceCamp.RED, PieceArm.Zu),
            Pair(PieceCamp.RED, PieceArm.Zu), Pair(PieceCamp.RED, PieceArm.Zu),
            Pair(PieceCamp.RED, PieceArm.Zu), // 五兵

            // 黑方
            Pair(PieceCamp.BLACK, PieceArm.Jiang),  // 将
            Pair(PieceCamp.BLACK, PieceArm.Che), Pair(PieceCamp.BLACK, PieceArm.Che), // 两车
            Pair(PieceCamp.BLACK, PieceArm.Ma), Pair(PieceCamp.BLACK, PieceArm.Ma),   // 两马
            Pair(PieceCamp.BLACK, PieceArm.Pao), Pair(PieceCamp.BLACK, PieceArm.Pao), // 两炮
            Pair(PieceCamp.BLACK, PieceArm.Shi), Pair(PieceCamp.BLACK, PieceArm.Shi), // 两士
            Pair(PieceCamp.BLACK, PieceArm.Xiang), Pair(PieceCamp.BLACK, PieceArm.Xiang), // 两相
            Pair(PieceCamp.BLACK, PieceArm.Zu), Pair(PieceCamp.BLACK, PieceArm.Zu),
            Pair(PieceCamp.BLACK, PieceArm.Zu), Pair(PieceCamp.BLACK, PieceArm.Zu),
            Pair(PieceCamp.BLACK, PieceArm.Zu) // 五卒
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

    fun startGame() {
        if (currentState == GameState.Ended) {
            currentState = GameState.Running
            println("Game started! Current state: $currentState")
        } else {
            println("Game is already running.")
            return
        }

        //给当前布局和棋子数赋值
        piecesLayout = defaultLayout["十字交叉型"]!!
        piecesType = defaultType["等量经典棋数"]!!

        //通过名字获取图片id
        //val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
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

fun main() {
    val gameManager = GameManager()

    println(gameManager.getStatus()) // 初始状态
    gameManager.startGame()          // 启动游戏
    println(gameManager.getStatus()) // 游戏状态
    gameManager.endGame()            // 结束游戏
    println(gameManager.getStatus()) // 游戏状态
}
