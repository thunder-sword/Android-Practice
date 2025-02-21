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
import androidx.compose.runtime.LaunchedEffect
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
        )[TCPConnectorViewModel::class.java]

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

    //Android12以上返回不会清理Activity，手动清理下
    override fun onBackPressed() {
        this.finish()
    }
}

@Composable
fun TCPClientLinkUI(viewModel: TCPConnectorViewModel){
    val current = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var ip by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("4399") }

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
            viewModel.sendUiIntent(TCPConnectionIntent.Connect(ip, portNumber))
        }) {
            Text("连接")
        }
        Button(onClick = {
            viewModel.sendUiIntent(TCPConnectionIntent.Disconnect)
        }) {
            Text("断开连接")
        }
    }

    // 显示当前连接状态（直接从 state 中取）
    Box(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        SelectionContainer {
            Text(
                text = when (state) {
                    is TCPConnectionState.Connected -> "网络连接成功"
                    TCPConnectionState.Connecting -> "正在尝试连接"
                    is TCPConnectionState.ConnectionFailed -> "连接失败: ${(state as TCPConnectionState.ConnectionFailed).error}"
                    TCPConnectionState.Disconnected -> "已断开连接"
                    TCPConnectionState.Idle -> "当前未连接"
                    is TCPConnectionState.Reconnecting -> "连接断开，正在回连，尝试次数: ${(state as TCPConnectionState.Reconnecting).attempt}"
                },
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun TCPClientUI(viewModel: TCPConnectorViewModel) {
    //val current = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // 本地只管理用户输入，不再管理连接状态，这部分统一由 ViewModel 状态提供
    var messageToSend by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TCPClientLinkUI(viewModel)

        // 如果处于 Connected 状态，则显示消息内容
        val messagesScrollState = rememberScrollState()
        if (state is TCPConnectionState.Connected) {
            val connectedState = state as TCPConnectionState.Connected
            // 显示接收到的消息
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(messagesScrollState)
                    .padding(8.dp)
            ) {
                SelectionContainer {
                    Text(text = connectedState.info, modifier = Modifier.fillMaxWidth())
                }
            }
            //每次更新消息时自动滑动到最下面
            LaunchedEffect(connectedState.info) {
                messagesScrollState.scrollTo(messagesScrollState.maxValue)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 输入待发送消息
            TextField(
                value = messageToSend,
                onValueChange = { messageToSend = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                label = { Text("发送消息") }
            )
            Button(onClick = {
                if (messageToSend.isNotBlank()) {
                    viewModel.sendUiIntent(TCPConnectionIntent.SendMessage(messageToSend))
                    messageToSend = ""  // 发送后清空输入框
                }
            }) {
                Text("发送")
            }
        }
    }
}
