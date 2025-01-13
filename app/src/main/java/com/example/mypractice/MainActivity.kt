package com.example.mypractice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.example.mypractice.ui.theme.MyPracticeTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyPracticeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val current= LocalContext.current
    var inputText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = inputText,
                onValueChange = {inputText=it},
                modifier = Modifier.padding(bottom = 16.dp),
                placeholder = { Text(text = "请输入内容！")}
            )
            Button(onClick = {
                Toast.makeText(current, "当前输入值为【${inputText}】", Toast.LENGTH_SHORT).show()
                performPing(current, inputText)
            }) {
                Text(text = "点我啦！")
            }
        }
    }
}

// 执行Ping操作
fun performPing(context: android.content.Context, ipAddress: String) {
    // 启动协程
    kotlinx.coroutines.GlobalScope.launch {
        val isReachable = try {
            // 使用InetAddress来检查连接
            java.net.InetAddress.getByName(ipAddress).isReachable(2000) // 超时2秒
        } catch (e: Exception) {
            false
        }

        // 在主线程更新UI
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            val message = if (isReachable) {
                "IP地址 $ipAddress 可达！"
            } else {
                "无法连接到IP地址 $ipAddress"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyPracticeTheme {
        MainScreen()
    }
}