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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.chessboard.GameViewModel
import com.example.mypractice.chessboard.GameViewModelFactory
import com.example.mypractice.ui.theme.MyPracticeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class OLDTCPConnectorActivity : ComponentActivity() {
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
                    ConnectorMainScreen(viewModel)
                }
            }
        }
    }
}

open class TCPConnector{
    protected var socket: Socket? = null
    protected var writer: PrintWriter? = null
    protected var reader: BufferedReader? = null
    var isConnect by mutableStateOf(false)
    var ip by mutableStateOf("")
    var port by mutableStateOf("4399")
    var connectionStatus by mutableStateOf("Not Connected")
    var messageToSend by mutableStateOf("")
    var receivedMessages by mutableStateOf("")
    private var message by mutableStateOf("")

    //TCP超时时间
    private var connectTimeoutMillis: Int = 5000 //毫秒

    //重连相关变量
    protected var reconnectAttempts = 0
    protected val maxReconnectAttempts = 3 // 最大重连次数
    protected val reconnectInterval = 2000L // 重连间隔时间（毫秒）
    var isReconnecting by mutableStateOf(false)

    var onMessageReceived: ((String) -> Unit)? = null

    //作用：发送消息
    fun send(message: String){
        //println("正要发送信息：$message")
        // 发送前检测socket是否处于连接状态
        if (!isConnect) {
            println("当前未连接，无法发送消息")
            return
        }

        if (message.isBlank()) {
            println("发送内容不能为空")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                writer?.println(message)
                writer?.flush()
                withContext(Dispatchers.Main) {
                    println("Sent: $message")
                    receivedMessages += "\nSent: $message"
                    //发送信息后清空输入框
                    messageToSend = ""
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    println("Failed to sent: $message")
                    receivedMessages += "\nFailed to send: ${e.message}"
                }
            }
        }
    }

    //作用：实际连接函数
    private suspend fun _connect(): Boolean{
        try {
            val portNumber = port.toIntOrNull()!!
            socket = Socket()
            withContext(Dispatchers.IO) {
                socket!!.connect(InetSocketAddress(ip, portNumber), connectTimeoutMillis)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            }
        } catch (e: SocketTimeoutException){
            withContext(Dispatchers.Main) {
                connectionStatus = "Timeout failed to connected to [$ip]:$port"
                println("TCP Socket连接超时")
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    //作用：连接函数
    open fun connect(onConnectSuccess: (() -> Unit)? = null): Boolean {
        val portNumber = port.toIntOrNull()
        if (ip.isBlank() || portNumber == null) {
            //Toast.makeText(current, "Invalid IP or Port", Toast.LENGTH_SHORT).show()
            return false
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                //标记为已连接
                isConnect = _connect()
                if(!isConnect){
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    connectionStatus = "Connected to ${ip}:$portNumber"
                    onConnectSuccess?.invoke() //回调函数调用
                }

                listenForMessages { message ->
                    receivedMessages += "\n$message"
                    onMessageReceived?.invoke(message)
                }
            } catch (e: Exception) {
                //Toast.makeText(current, "连接失败", Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.Main) {
                    isConnect = false
                    connectionStatus = "Connection failed: ${e.message}"
                }
            }
        }
        return true
    }

    //作用：断开连接
    open fun disConnect(current: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                isConnect = false
                socket?.close()
                writer?.close()
                reader?.close()
                withContext(Dispatchers.Main) {
                    connectionStatus = "Make connection closed"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(current, "断开连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //作用：重连
    open fun startReconnect(onReconnectSuccess: (() -> Unit)? = null) {
        println("当前重连次数：$reconnectAttempts")
        if (reconnectAttempts >= maxReconnectAttempts) {
            connectionStatus = "已达最大重连次数，可手动重新连接"
            isReconnecting = false
            reconnectAttempts = 0
            return
        }

        isReconnecting = true
        reconnectAttempts++

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    connectionStatus = "Reconnecting... Attempt $reconnectAttempts"
                }

                if (_connect()) {
                    isConnect = true
                    isReconnecting = false
                    reconnectAttempts = 0

                    withContext(Dispatchers.Main) {
                        connectionStatus = "Reconnected to ${ip}:${port}"
                        onReconnectSuccess?.invoke() // 调用回调
                    }

                    listenForMessages { message ->
                        receivedMessages += "\n$message"
                        onMessageReceived?.invoke(message)
                    }
                } else {
                    delay(reconnectInterval)
                    startReconnect(onReconnectSuccess)
                }
            } catch (e: Exception) {
                startReconnect(onReconnectSuccess)
            }
        }
    }

    //作用：启动监听
    fun listenForMessages(onMessageReceived: (String) -> Unit) {
        try {
            while (true) {
                message = reader?.readLine() ?: break
                onMessageReceived(message)
            }
        } catch (e: Exception) {
            onMessageReceived("Error: ${e.message}")
        } finally {
            isConnect = false
            onMessageReceived("Info: Connection closed.")
        }
    }

    open fun onDestroy() {
        isConnect = false
        socket?.close()
        writer?.close()
        reader?.close()
    }
}

@Composable
fun ConnectorMainScreen(viewModel: GameViewModel) {
    val tcpConnector = remember {
        TCPConnector()
    }
    if (!tcpConnector.isConnect && !tcpConnector.isReconnecting) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            OLDTCPClientUI(tcpConnector)
        }
    } else {
        // 用于控制弹窗是否显示
        var showDialog by rememberSaveable { mutableStateOf(true) }

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
                        Text("好")
                    }
                }
            )
        }

        ChessBoard(viewModel, OnlineState.Client, tcpConnector)
    }
}

@Composable
fun OLDTCPClientUI(tcpConnector: TCPConnector) {
    val current = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = tcpConnector.ip,
            onValueChange = { tcpConnector.ip = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("Enter IP Address") }
        )

        TextField(
            value = tcpConnector.port,
            onValueChange = { tcpConnector.port = it },
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
                tcpConnector.connect()
            }) {
                Text("Connect")
            }

            Button(
                onClick = {
                    tcpConnector.disConnect(current)
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
                Text(tcpConnector.connectionStatus, Modifier.padding(8.dp))
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
                value = tcpConnector.messageToSend,
                onValueChange = { tcpConnector.messageToSend = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                label = { Text("Enter Message") }
            )
        }
        //每次输入消息时自动滑动到最下面
        LaunchedEffect(tcpConnector.messageToSend) {
            scrollState2.scrollTo(scrollState2.maxValue)
        }

        Button(onClick = {
            tcpConnector.send(tcpConnector.messageToSend)
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
                    tcpConnector.receivedMessages,
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
        //每次更新消息时自动滑动到最下面
        LaunchedEffect(tcpConnector.receivedMessages) {
            scrollState3.scrollTo(scrollState3.maxValue)
        }
    }
}


