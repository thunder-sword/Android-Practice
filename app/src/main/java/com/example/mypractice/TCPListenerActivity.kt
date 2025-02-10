package com.example.mypractice

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.ui.theme.MyPracticeTheme
import kotlinx.coroutines.*
import java.io.*
import java.net.*

class TCPListenerActivity : ComponentActivity() {
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
                    ListenerMainScreen(viewModel)
                }
            }
        }
    }
}

class TCPListener: TCPConnector(){
    internal var serverSocket: ServerSocket? = null
    var serverAddresses by mutableStateOf("")
    var clientAddresses by mutableStateOf("")

    //重载连接函数
    override fun connect(onConnectSuccess: (() -> Unit)?): Boolean {
        val portNumber = port.toIntOrNull() ?: return false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(portNumber, 0) //设置监听序列为0
                serverAddresses =
                    getLocalIPAddresses().joinToString("\n") { "$it:$portNumber" }

                withContext(Dispatchers.Main) {
                    connectionStatus = "Server running on\n$serverAddresses"
                }

                socket = serverSocket!!.accept()
                writer = PrintWriter(socket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                isConnect = true

                ip = socket?.inetAddress?.hostAddress ?: ""     //此处ip意义为连接来的客户端ip
                clientAddresses = "${socket?.inetAddress?.hostAddress}:${socket?.port}"

                withContext(Dispatchers.Main) {
                    connectionStatus = "Client connected from $clientAddresses"
                    onConnectSuccess?.invoke() //回调函数调用
                }

                listenForMessages { message ->
                    receivedMessages += "\n$message"
                    onMessageReceived?.invoke(message)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectionStatus = "Error: ${e.message}"
                }
            }
        }
        return true
    }

    //重载断开连接函数
    override fun disConnect(current: Context){
        CoroutineScope(Dispatchers.IO).launch {
            try {
                isConnect = false
                socket?.close()
                serverSocket?.close()

                withContext(Dispatchers.Main) {
                    connectionStatus = "Server stopped"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectionStatus = "Error stopping server: ${e.message}"
                }
            }
        }
    }

    //重载重连函数
    override fun startReconnect(onReconnectSuccess: (() -> Unit)?) {
        if (reconnectAttempts >= maxReconnectAttempts) {
            connectionStatus = "已达最大重连次数，可手动重新连接"
            isReconnecting = false
            reconnectAttempts = 0
            serverSocket = null
            return
        }

        isReconnecting = true
        reconnectAttempts++

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    connectionStatus = "Server running on\n$serverAddresses"
                }

                // 等待客户端连接
                socket = serverSocket!!.accept()
                writer = PrintWriter(socket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                isConnect = true
                isReconnecting = false
                reconnectAttempts = 0

                ip = socket?.inetAddress?.hostAddress ?: ""     //此处ip意义为连接来的客户端ip
                clientAddresses = "${socket?.inetAddress?.hostAddress}:${socket?.port}"

                withContext(Dispatchers.Main) {
                    connectionStatus = "Client reconnected from $clientAddresses"
                    onReconnectSuccess?.invoke() // 调用回调
                }

                // 重新开始监听消息
                listenForMessages { message ->
                    receivedMessages += "\n$message"
                    onMessageReceived?.invoke(message)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectionStatus = "Reconnection failed: ${e.message}"
                }
                delay(reconnectInterval)
                startReconnect(onReconnectSuccess) // 继续尝试重连
            }
        }
    }

    //重载销毁函数
    override fun onDestroy() {
        super.onDestroy()
        isConnect = false
        socket?.close()
        serverSocket?.close()
    }
}

//获取本机全部ip，同时获取ipv4和ipv6（过滤了链路地址），ipv6用中括号[]包裹
fun getLocalIPAddresses(): List<String> {
    return NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filter {
            !it.isLoopbackAddress &&
                    !(it is Inet6Address && it.isLinkLocalAddress)
        }
        .map { if(it is Inet6Address)
            "[${it.hostAddress}]"
        else it.hostAddress}
}

@Composable
fun ListenerMainScreen(viewModel: GameViewModel) {
    val tcpListener = remember {
        TCPListener()
    }

    if (!tcpListener.isConnect && null == tcpListener.serverSocket) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TCPServerUI(tcpListener)
        }
    } else {
        // 用于控制弹窗是否显示
        var showDialog by rememberSaveable { mutableStateOf(true) }
        // 显示弹窗
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
                title = { Text(text = "连接成功") },
                text = { Text(text = "已有客户端进入房间，可以开始游戏") },
                confirmButton = {
                    Button(onClick = {
                        showDialog = false
                    }) {
                        Text("好")
                    }
                }
            )
        }

        ChessBoard(viewModel, OnlineState.Server, tcpListener)
    }
}

@Composable
fun TCPServerUI(tcpListener: TCPListener) {
    val current = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = tcpListener.port,
            onValueChange = { tcpListener.port = it },
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
                if(!tcpListener.connect()){
                    Toast.makeText(current, "Invalid Port", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Start Server")
            }

            Button(onClick = {
                tcpListener.disConnect(current)
            }) {
                Text("Stop Server")
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
                Text(tcpListener.connectionStatus, Modifier.padding(8.dp))
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
                value = tcpListener.messageToSend,
                onValueChange = { tcpListener.messageToSend = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                label = { Text("Enter Message") }
            )
        }
        //每次输入消息时自动滑动到最下面
        LaunchedEffect(tcpListener.messageToSend) {
            scrollState2.scrollTo(scrollState2.maxValue)
        }

        Button(onClick = {
            tcpListener.send(tcpListener.messageToSend, current)
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
                    tcpListener.receivedMessages,
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
        //每次更新消息时自动滑动到最下面
        LaunchedEffect(tcpListener.receivedMessages) {
            scrollState3.scrollTo(scrollState3.maxValue)
        }
    }
}
