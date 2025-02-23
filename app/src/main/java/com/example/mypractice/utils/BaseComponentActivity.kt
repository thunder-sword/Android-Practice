package com.example.mypractice.utils

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class BaseComponentActivity: ComponentActivity() {
    private var isTwiceTapBack: Boolean = false
    private val backTwiceTapDelay: Long = 2000

    //Android12以上返回不会清理Activity，添加返回确认并手动清理
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if(!isTwiceTapBack){
            Toast.makeText(this, "再按一次返回退出", Toast.LENGTH_SHORT).show()
            isTwiceTapBack = true
            lifecycleScope.launch {
                delay(backTwiceTapDelay)  //指定时间内再点击退出有效
                isTwiceTapBack = false
            }
        }else {
            this.finish()
        }
    }
}