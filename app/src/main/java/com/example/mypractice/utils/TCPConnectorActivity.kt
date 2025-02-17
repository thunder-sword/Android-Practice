package com.example.mypractice.utils

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.ui.theme.MyPracticeTheme

class TCPConnectorActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用 ViewModelProvider 获取实例
        val viewModel = ViewModelProvider(
            this
        )[TcpConnectorViewModel::class.java]

        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    TCPClientUI(viewModel)
                }
            }
        }
    }
}

@Composable
fun TCPClientUI(viewModel: TcpConnectorViewModel) {
    val current = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // 本地只管理用户输入，不再管理连接状态，这部分统一由 ViewModel 状态提供
    var ip by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("4399") }
    var messageToSend by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 输入 IP 与端口
        TextField(
            value = ip,
            onValueChange = { ip = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("输入IP地址") }
        )

        TextField(
            value = port,
            onValueChange = { port = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            label = { Text("输入端口") }
        )

        // 连接/断开操作
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val portNumber = port.toIntOrNull()
                if (ip.isBlank() || portNumber == null) {
                    Toast.makeText(current, "非法的 IP 或端口", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.sendUiIntent(TcpConnectionIntent.Connect(ip, portNumber))
            }) {
                Text("连接")
            }
            Button(onClick = {
                viewModel.sendUiIntent(TcpConnectionIntent.Disconnect)
            }) {
                Text("断开连接")
            }
        }

        // 显示当前连接状态（直接从 state 中取）
        Text(
            text = when(state) {
                is TcpConnectionState.Connected -> "网络连接成功"
                TcpConnectionState.Connecting -> "正在尝试连接"
                is TcpConnectionState.ConnectionFailed -> "连接失败: ${(state as TcpConnectionState.ConnectionFailed).error}"
                TcpConnectionState.Disconnected -> "已断开连接"
                TcpConnectionState.Idle -> "当前未连接"
                is TcpConnectionState.Reconnecting -> "连接断开，正在回连，尝试次数: ${(state as TcpConnectionState.Reconnecting).attempt}"
            },
            modifier = Modifier.padding(8.dp)
        )

        // 如果处于 Connected 状态，则显示消息内容
        if (state is TcpConnectionState.Connected) {
            val connectedState = state as TcpConnectionState.Connected
            // 显示接收到的消息
            Box(
                modifier = Modifier
                    .weight(3f)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                SelectionContainer {
                    Text(text = connectedState.message, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // 输入待发送消息
        TextField(
            value = messageToSend,
            onValueChange = { messageToSend = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("发送消息") }
        )
        Button(onClick = {
            if (messageToSend.isNotBlank()) {
                viewModel.sendUiIntent(TcpConnectionIntent.SendMessage(messageToSend))
                messageToSend = ""  // 发送后清空输入框
            }
        }) {
            Text("Send")
        }
    }
}
