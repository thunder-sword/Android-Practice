package com.example.mypractice

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sqrt

//棋子阵营枚举
enum class PieceCamp {
    Red,
    Black
}

//棋子兵种枚举
enum class PieceArm {
    Che,
    Jiang,  //就不写帅了，直接用将
    Ma,
    Pao,
    Shi,
    Xiang,
    Zu      //同样也不写兵了，直接用卒
}

//完整棋子类
class ChessPiece(
    var position: Pair<Int, Int>,  // 棋子当前坐标
    val camp: PieceCamp,    // 什么阵营
    val arm: PieceArm,       // 什么兵种
    var isAlive: Boolean = true,   // 是否存活
    var isFront: Boolean = false,  // 是否已翻面
    var isOver: Boolean = false     // 是否位于其他棋子上层
) {
    var isSelected: Boolean = false // 是否被选中
    // 动画控制
    private val pairConverter = TwoWayConverter<Pair<Float, Float>, AnimationVector2D>(
        convertToVector = { pair -> AnimationVector2D(pair.first, pair.second) },
        convertFromVector = { vector -> Pair(vector.v1, vector.v2) }
    )
    // 棋子被选中的动画
    private val selectedAnimation = Animatable(initialValue = Pair(1f, 0f), typeConverter = pairConverter)
    // 棋子翻面的动画
    private val shakingAnimation = Animatable(0f)
    // 晃动幅度
    private val shakingOffset = 50f

    // 播放翻面动画
    suspend fun toFront() {
        isFront = true
        shakingAnimation.animateTo(
            targetValue = 0f, // 最终目标是回到原点
            animationSpec = keyframes {
                durationMillis = 400 // 总时长
                -1f at 100 // 第一次向左
                1f at 200 // 向右
                -0.5f at 300 // 小幅向左
                0f at 400 // 回到原点
            }
        )
    }

    /**
     * 播放选中动画
     */
    suspend fun select() {
        isSelected = true
        selectedAnimation.animateTo(Pair(1.2f, 20f), animationSpec = tween(200)) // 缩放到 1.2 倍
    }

    /**
     * 播放取消选中动画
     */
    suspend fun deselect() {
        isSelected = false
        selectedAnimation.animateTo(Pair(1f, 0f), animationSpec = tween(200))
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
    fun draw(drawScope: DrawScope, imageLoader: ImageLoader, borderLeft: Float, borderTop: Float, cellWidth: Float, cellHeight: Float) {
        //如果棋子已经死了，就不要再画了
        if(!isAlive){
            return
        }

        val image: ImageBitmap = imageLoader.getImage("${camp.name.substring(0,1).lowercase()}_${arm.name.substring(0,1).lowercase()}")!!
        val backImage: ImageBitmap = imageLoader.getImage("back")!!
        // 棋子要展示的图片
        val img = if (isFront) { image } else { backImage }
        // 棋子的位置和大小信息
        val (x, y) = position
        val centerX = borderLeft + cellWidth * x + shakingAnimation.value * shakingOffset
        var centerY = borderTop + cellHeight * y - selectedAnimation.value.second
        val size = sqrt(cellWidth*cellWidth+cellHeight*cellHeight.toDouble()) *0.66 * selectedAnimation.value.first    //棋子的大小设定为0.66*格子的对角线长度

        drawScope.drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
            }
            if(isOver) {        //如果棋子在其他棋子上面，则图像改为覆盖模式，并将中心点向下偏移一些
                //paint.blendMode = BlendMode.SrcOver
                centerY -= 15
            }

            // 定义目标区域（图片最终绘制的位置和大小）
            val dstOffset = IntOffset(
                (centerX - size / 2).toInt(),
                (centerY - size / 2).toInt()
            )
            val dstSize = IntSize(size.toInt(), size.toInt())
            // 绘制图片
            canvas.drawImageRect(
                image = img,
                dstOffset = dstOffset,
                dstSize = dstSize,
                paint = paint
            )
        }
    }

    //获取当前棋子可前往的位置列表
    fun canToLocation(currentBoard: Array<Array<MutableList<ChessPiece>>>): List<Pair<Int, Int>> {
//        val allPosition = (0..9).flatMap { x ->
//            (0..10).map { y ->
//                Pair(x, y)
//            }
//        }
//        return allPosition

        // 判断位置是否在棋盘内
        fun isValidPosition(x: Int, y: Int): Boolean = x in 0 until currentBoard.size && y in 0 until currentBoard[0].size

        // 辅助函数：判断路径是否畅通（无棋子或只有背面棋子）
        fun isPathClear(board: Array<Array<MutableList<ChessPiece>>>, col: Int, row: Int): Boolean =
            board[col][row].all { !it.isFront } // 目标位置无正面棋子

        // 辅助函数：判断是否可吃子
        fun canCapture(board: Array<Array<MutableList<ChessPiece>>>, col: Int, row: Int): Boolean =
            board[col][row].any { it.isFront && it.camp != camp } // 目标位置有正面敌方棋子

        // 辅助函数：判断是否可跳跃吃子
        fun canJumpToCapture(board: Array<Array<MutableList<ChessPiece>>>, position: Pair<Int, Int>, col: Int, row: Int): Boolean {
            val startCol = position.first
            val startRow = position.second

            // 判断横向或纵向是否可以跳跃
            if (startRow == row) {
                val range = if (startCol < col) (startCol + 1 until col) else (col + 1 until startCol)
                val blockers = range.map { board[it][row] }.count { column -> column.any { it.isFront } }
                return blockers == 1 && board[col][row].any { it.isFront && it.camp != camp }
            } else if (startCol == col) {
                val range = if (startRow < row) (startRow + 1 until row) else (row + 1 until startRow)
                val blockers = range.map { board[col][it] }.count { row -> row.any { it.isFront } }
                return blockers == 1 && board[col][row].any { it.isFront && it.camp != camp }
            }

            return false
        }

        // 返回值列表
        val possibleLocations = mutableListOf<Pair<Int, Int>>()

        // 不同棋子的移动规则
        when (arm) {
            PieceArm.Che -> {
                // 车：直线横纵移动，无限制，路径不能有阻挡
                for (i in -1..1 step 2) {
                    // 横向和纵向分别搜索
                    for (deltaCol in 1..8) {
                        val targetCol = position.first + deltaCol * i
                        if (isValidPosition(targetCol, position.second)) {
                            if (isPathClear(currentBoard, targetCol, position.second)) {
                                possibleLocations.add(Pair(targetCol, position.second))
                            } else if (canCapture(currentBoard, targetCol, position.second)) {
                                possibleLocations.add(Pair(targetCol, position.second))
                                break
                            } else break
                        }
                    }
                    for (deltaRow in 1..9) {
                        val targetRow = position.second + deltaRow * i
                        if (isValidPosition(position.first, targetRow)) {
                            if (isPathClear(currentBoard, position.first, targetRow)) {
                                possibleLocations.add(Pair(position.first, targetRow))
                            } else if (canCapture(currentBoard, position.first, targetRow)) {
                                possibleLocations.add(Pair(position.first, targetRow))
                                break
                            } else break
                        }
                    }
                }
            }
            PieceArm.Ma -> {
                // 马：日字跳，需要判断是否蹩脚
                val knightMoves = listOf(
                    Pair(2, 1), Pair(2, -1), Pair(-2, 1), Pair(-2, -1),
                    Pair(1, 2), Pair(1, -2), Pair(-1, 2), Pair(-1, -2)
                )
                for (move in knightMoves) {
                    val targetCol = position.first + move.first
                    val targetRow = position.second + move.second
                    val midCol = position.first + move.first / 2
                    val midRow = position.second + move.second / 2
                    if (isValidPosition(targetCol, targetRow) &&
                        isPathClear(currentBoard, midCol, midRow)
                    ) {
                        if (canCapture(currentBoard, targetCol, targetRow) || isPathClear(currentBoard, targetCol, targetRow)) {
                            possibleLocations.add(Pair(targetCol, targetRow))
                        }
                    }
                }
            }
            PieceArm.Pao -> {
                // 炮：与车类似，但吃子需要隔一跳
                for (i in -1..1 step 2) {
                    for (deltaCol in 1..8) {
                        val targetCol = position.first + deltaCol * i
                        if (isValidPosition(targetCol, position.second)) {
                            if (isPathClear(currentBoard, targetCol, position.second)) {
                                possibleLocations.add(Pair(targetCol, position.second))
                            } else if (canJumpToCapture(currentBoard, position, targetCol, position.second)) {
                                possibleLocations.add(Pair(targetCol, position.second))
                                break
                            } else break
                        }
                    }
                    for (deltaRow in 1..9) {
                        val targetRow = position.second + deltaRow * i
                        if (isValidPosition(position.first, targetRow)) {
                            if (isPathClear(currentBoard, position.first, targetRow)) {
                                possibleLocations.add(Pair(position.first, targetRow))
                            } else if (canJumpToCapture(currentBoard, position, position.first, targetRow)) {
                                possibleLocations.add(Pair(position.first, targetRow))
                                break
                            } else break
                        }
                    }
                }
            }
            PieceArm.Jiang -> {
                // 将/帅：九宫格内移动，一次一格
                val kingMoves = listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
                val palaceCols = 3..5
                val palaceRows = if (camp == PieceCamp.Red) 7..9 else 0..2
                for (move in kingMoves) {
                    val targetCol = position.first + move.first
                    val targetRow = position.second + move.second
                    if (isValidPosition(targetCol, targetRow) &&
                        targetCol in palaceCols && targetRow in palaceRows
                    ) {
                        if (canCapture(currentBoard, targetCol, targetRow) || isPathClear(currentBoard, targetCol, targetRow)) {
                            possibleLocations.add(Pair(targetCol, targetRow))
                        }
                    }
                }
            }
            PieceArm.Shi -> {
                // 士：九宫格内斜线移动
                val advisorMoves = listOf(Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1))
                val palaceCols = 3..5
                val palaceRows = if (camp == PieceCamp.Red) 7..9 else 0..2
                for (move in advisorMoves) {
                    val targetCol = position.first + move.first
                    val targetRow = position.second + move.second
                    if (isValidPosition(targetCol, targetRow) &&
                        targetCol in palaceCols && targetRow in palaceRows
                    ) {
                        if (canCapture(currentBoard, targetCol, targetRow) || isPathClear(currentBoard, targetCol, targetRow)) {
                            possibleLocations.add(Pair(targetCol, targetRow))
                        }
                    }
                }
            }
            PieceArm.Xiang -> {
                // 象：斜线移动两格，不能过河
                val bishopMoves = listOf(Pair(2, 2), Pair(2, -2), Pair(-2, 2), Pair(-2, -2))
                val riverBoundary = if (camp == PieceCamp.Red) 4 else 5
                for (move in bishopMoves) {
                    val targetCol = position.first + move.first
                    val targetRow = position.second + move.second
                    val midCol = position.first + move.first / 2
                    val midRow = position.second + move.second / 2
                    if (isValidPosition(targetCol, targetRow) &&
                        (if (camp == PieceCamp.Red) targetRow >= riverBoundary else targetRow <= riverBoundary) &&
                        isPathClear(currentBoard, midCol, midRow)
                    ) {
                        if (canCapture(currentBoard, targetCol, targetRow) || isPathClear(currentBoard, targetCol, targetRow)) {
                            possibleLocations.add(Pair(targetCol, targetRow))
                        }
                    }
                }
            }
            PieceArm.Zu -> {
                // 卒/兵：单步向前，过河后可左右移动
                val forward = if (camp == PieceCamp.Red) -1 else 1
                val pawnMoves = mutableListOf(Pair(0, forward))
                if ((camp == PieceCamp.Red && position.second <= 4) || (camp == PieceCamp.Black && position.second >= 5)) {
                    pawnMoves.add(Pair(-1, 0))
                    pawnMoves.add(Pair(1, 0))
                }
                for (move in pawnMoves) {
                    val targetCol = position.first + move.first
                    val targetRow = position.second + move.second
                    if (isValidPosition(targetCol, targetRow)) {
                        if (canCapture(currentBoard, targetCol, targetRow) || isPathClear(currentBoard, targetCol, targetRow)) {
                            possibleLocations.add(Pair(targetCol, targetRow))
                        }
                    }
                }
            }
        }

        return possibleLocations

    }
}
