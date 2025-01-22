package com.example.mypractice

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.BlendMode
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
            if(isOver) {        //如果棋子在其他棋子上面，则图像改为覆盖模式，并将中心点向上偏移30
                paint.blendMode = BlendMode.SrcOver
                centerY -= 30
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
}
