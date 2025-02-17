package com.example.mypractice.utils

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.example.mypractice.R

//图片加载类，在首次开启游戏时自动加载所有图片
class ImageLoader(context: Context) {

    // 存储所有预加载的图片
    private val imageCache: MutableMap<String, ImageBitmap> = mutableMapOf()

    init {
        // 初始化时加载所有图片
        preloadImages(context)
    }

    private fun preloadImages(context: Context) {
        val resources = listOf(
            R.drawable.board,
            R.drawable.back,
            R.drawable.bg,
            R.drawable.back,
            R.drawable.r_box,
            R.drawable.r_minibox,
            R.drawable.b_c,
            R.drawable.b_j,
            R.drawable.b_m,
            R.drawable.b_p,
            R.drawable.b_s,
            R.drawable.b_x,
            R.drawable.b_z,
            R.drawable.r_c,
            R.drawable.r_j,
            R.drawable.r_m,
            R.drawable.r_p,
            R.drawable.r_s,
            R.drawable.r_x,
            R.drawable.r_z
        )
        for (resId in resources) {
            val image = ImageBitmap.imageResource(context.resources, resId)
            imageCache[context.resources.getResourceEntryName(resId)] = image
        }
    }

    // 获取图片
    fun getImage(name: String): ImageBitmap? {
        return imageCache[name]
    }
}