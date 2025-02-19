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
import com.example.mypractice.chat.MessageChatActivity
import com.example.mypractice.ui.theme.MyPracticeTheme
import com.example.mypractice.utils.TCPConnectorActivity
import com.example.mypractice.utils.TCPListenerActivity

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
                            1 -> startActivity(Intent(this, OLDTCPListenerActivity::class.java))
                            2 -> startActivity(Intent(this, OLDTCPConnectorActivity::class.java))
                            3 -> startActivity(Intent(this, ChessGameActivity::class.java))
                            4 -> startActivity(Intent(this, ChatActivity::class.java))
                            5 -> startActivity(Intent(this, TCPListenerActivity::class.java))
                            6 -> startActivity(Intent(this, TCPConnectorActivity::class.java))
                            7 -> startActivity(Intent(this, MessageChatActivity::class.java))
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

            Button(
                onClick = {
                    startUp(4)
                }
            ) {
                Text("聊天室")
            }

            Button(
                onClick = {
                    startUp(5)
                }
            ) {
                Text("MVI服务端")
            }

            Button(
                onClick = {
                    startUp(6)
                }
            ) {
                Text("MVI客户端")
            }

            Button(
                onClick = {
                    startUp(7)
                }
            ) {
                Text("MVI聊天室")
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