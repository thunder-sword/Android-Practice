package com.example.mypractice

import android.content.Context
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
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.ui.theme.MyPracticeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class TCPConnecterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用 ViewModelProvider 获取实例
        val viewModel = ViewModelProvider(
            this,
            GameViewModelFactory(applicationContext)
        )[GameViewModel::class.java]

        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

class TCPConnecter{
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    var isConnect by mutableStateOf(false)
    var ip by mutableStateOf("")
    var port by mutableStateOf("")
    var connectionStatus by mutableStateOf("Not Connected")
    var messageToSend by mutableStateOf("")
    var receivedMessages by mutableStateOf("")

    //作用：断开连接
    private fun disConnect(current: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                isConnect = false
                socket?.close()
                writer?.close()
                reader?.close()
                withContext(Dispatchers.Main) {
                    connectionStatus = "Connection closed"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(current, "断开连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //作用：启动监听
    private fun listenForMessages(onMessageReceived: (String) -> Unit) {
        try {
            while (true) {
                val message = reader?.readLine() ?: break
                onMessageReceived(message)
            }
        } catch (e: Exception) {
            onMessageReceived("Error: ${e.message}")
        } finally {
            isConnect = false
            onMessageReceived("Info: Connection closed.")
        }
    }

    fun onDestroy() {
        isConnect = false
        socket?.close()
        writer?.close()
        reader?.close()
    }
}

@Composable
fun MainScreen(viewModel: GameViewModel) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val tcpConnecter = remember {
            TCPConnecter()
        }
        val ip = tcpConnecter.ip
        val port = tcpConnecter.port
        val connectionStatus = tcpConnecter.connectionStatus
        val messageToSend = tcpConnecter.messageToSend
        val receivedMessages = tcpConnecter.receivedMessages
        val isConnect = tcpConnecter.isConnect

        if(!isConnect) {
            TCPClientUI()
        }
        else {
            // 用于控制弹窗是否显示
            var showDialog by remember { mutableStateOf(false) }

            // 显示弹窗
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
                    title = { Text(text = "连接成功") },
                    text = { Text(text = "已与服务器连接成功，可以开始游戏") },
                    confirmButton = {
                        Button(onClick = {
                            showDialog = false
                        }) {
                            Text("开始")
                        }
                    },
                    dismissButton = {
                        Button(onClick = {
                            // 关闭弹窗
                            showDialog = false
                        }) {
                            Text("退出")
                        }
                    }
                )
            }

            DrawMain(viewModel)
        }
    }
}

@Composable
fun TCPClientUI() {
    val current = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = ip,
            onValueChange = { ip = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("Enter IP Address") }
        )

        TextField(
            value = port,
            onValueChange = { port = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            label = { Text("Enter Port") }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                val portNumber = port.toIntOrNull()
                if (ip.isBlank() || portNumber == null) {
                    Toast.makeText(current, "Invalid IP or Port", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        socket = Socket(ip, portNumber)
                        writer = PrintWriter(socket!!.getOutputStream(), true)
                        reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                        withContext(Dispatchers.Main) {
                            connectionStatus = "Connected to $ip:$portNumber"
                            //标记为已连接
                            isConnect = true
                        }

                        listenForMessages { message ->
                            receivedMessages += "\n$message"
                        }

                        //主线程监听是否断开
                        while (isConnect && socket?.isClosed == false) {
                            delay(500) // 每隔500ms检查一次
                        }
                        isConnect = false
                        socket?.close()
                        writer?.close()
                        reader?.close()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(current, "连接已断开", Toast.LENGTH_SHORT).show()
                            connectionStatus = "Connection closed"
                        }

                    } catch (e: Exception) {
                        Toast.makeText(current, "连接失败", Toast.LENGTH_SHORT).show()
                        withContext(Dispatchers.Main) {
                            isConnect = false
                            connectionStatus = "Connection failed: ${e.message}"
                        }
                    }
                }
            }) {
                Text("Connect")
            }

            Button(
                onClick = {
                    disConnect(current)
                }
            ) {
                Text("Disconnect")
            }
        }

        //让文本框支持向下滚动
        val scrollState1 = rememberScrollState()
        Box(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState1)
                .padding(8.dp)
        ) {
            SelectionContainer {
                Text(connectionStatus, Modifier.padding(8.dp))
            }
        }

        //让文本框支持向下滚动
        val scrollState2 = rememberScrollState()
        Box(
            Modifier
                .fillMaxSize()
                .weight(3f)
                .verticalScroll(scrollState2)
                .padding(8.dp)
        ) {
            TextField(
                value = messageToSend,
                onValueChange = { messageToSend = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                label = { Text("Enter Message") }
            )
        }
        //每次输入消息时自动滑动到最下面
        LaunchedEffect(messageToSend) {
            scrollState2.scrollTo(scrollState2.maxValue)
        }

        Button(onClick = {
            // 发送前检测socket是否处于连接状态
            if (!isConnect || null == socket || socket?.isClosed == true || socket?.isInputShutdown == true || socket?.isOutputShutdown == true) {
                isConnect = false
                Toast.makeText(current, "当前未连接，无法发送", Toast.LENGTH_SHORT).show()
                return@Button
            }

            if (messageToSend.isBlank()) {
                Toast.makeText(current, "发送内容不能为空", Toast.LENGTH_SHORT).show()
                return@Button
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    writer?.println(messageToSend)
                    withContext(Dispatchers.Main) {
                        receivedMessages += "\nSent: $messageToSend"
                        //发送信息后清空输入框
                        messageToSend = ""
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        receivedMessages += "\nFailed to send: ${e.message}"
                    }
                }
            }
        }) {
            Text("Send")
        }

        //让文本框支持向下滚动
        val scrollState3 = rememberScrollState()
        Box(
            Modifier
                .fillMaxWidth()
                .weight(3f)
                .verticalScroll(scrollState3)
                .padding(8.dp)
        ) {
            SelectionContainer {
                Text(
                    receivedMessages,
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
        //每次更新消息时自动滑动到最下面
        LaunchedEffect(receivedMessages) {
            scrollState3.scrollTo(scrollState3.maxValue)
        }
    }
}