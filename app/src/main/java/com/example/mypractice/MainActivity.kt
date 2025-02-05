package com.example.mypractice

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mypractice.ui.theme.MyPracticeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    ChessGameMainScreen {
                        x -> when(x) {
                            1 -> startActivity(Intent(this, TCPListenerActivity::class.java))
                            2 -> startActivity(Intent(this, TCPConnectorActivity::class.java))
                            3 -> startActivity(Intent(this, DrawTest::class.java))
                        else -> Toast.makeText(this@MainActivity, "未知的跳转页面", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChessGameMainScreen(startUp: (Int) -> Unit) {
    Box (
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        Column (
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    startUp(1)
                }
            ) {
                Text("创建房间")
            }

            Button(
                onClick = {
                    startUp(2)
                }
            ) {
                Text("连接服务器")
            }

            Button(
                onClick = {
                    startUp(3)
                }
            ) {
                Text("本地双人游玩")
            }
        }
    }
}

@Suppress("UNUSED_EXPRESSION")
@Preview(name = "lightScheme", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "darkScheme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TestMain(){
    MyPracticeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            ChessGameMainScreen { _ -> 1 }
        }
    }
}