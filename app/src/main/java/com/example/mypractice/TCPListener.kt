package com.example.mypractice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mypractice.ui.theme.MyPracticeTheme
import kotlinx.coroutines.*
import java.io.*
import java.net.*

class TCPListener : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var isRunning: Boolean = false

    @Composable
    fun TCPServerUI() {
        var port by remember { mutableStateOf("") }
        var connectionStatus by remember { mutableStateOf("Not Running") }
        var messageToSend by remember { mutableStateOf("") }
        var receivedMessages by remember { mutableStateOf("") }
        val current = LocalContext.current
        var serverAddresses by remember { mutableStateOf("Unknown") }
        var clientAddress by remember { mutableStateOf("Unknown") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicTextField(
                value = port,
                onValueChange = { port = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        if (port.isEmpty()) Text("Enter Port")
                        innerTextField()
                    }
                }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    val portNumber = port.toIntOrNull()
                    if (portNumber == null) {
                        Toast.makeText(current, "Invalid Port", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            serverSocket = ServerSocket(portNumber)
                            serverAddresses =
                                getLocalIPAddresses().map { "$it:$portNumber" }.joinToString("\n")
                            isRunning = true

                            withContext(Dispatchers.Main) {
                                connectionStatus = "Server running on\n$serverAddresses"
                            }

                            clientSocket = serverSocket!!.accept()
                            writer = PrintWriter(clientSocket!!.getOutputStream(), true)
                            reader =
                                BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))

                            clientAddress =
                                "${clientSocket?.inetAddress?.hostAddress}:${clientSocket?.port}"

                            withContext(Dispatchers.Main) {
                                connectionStatus = "Client connected from $clientAddress"
                            }

                            listenForMessages { message ->
                                receivedMessages += "\n$message"
                            }

                            //主线程监听是否断开
                            while (isRunning) {
                                delay(500) // 每隔500ms检查一次
                            }
                            isRunning = false
                            clientSocket?.close()
                            serverSocket?.close()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(current, "连接已断开", Toast.LENGTH_SHORT).show()
                                connectionStatus = "Connection closed"
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                connectionStatus = "Error: ${e.message}"
                            }
                        }
                    }
                }) {
                    Text("Start Server")
                }

                Button(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            isRunning = false
                            clientSocket?.close()
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
                    Text(connectionStatus, Modifier.padding(8.dp))
                }
            }

            //让文本框支持向下滚动
            val scrollState2 = rememberScrollState()
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(3f)
                    .verticalScroll(scrollState2)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = messageToSend,
                    onValueChange = { messageToSend = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            if (messageToSend.isEmpty()) Text("Enter Message")
                            innerTextField()
                        }
                    }
                )
            }
            //每次输入消息时自动滑动到最下面
            LaunchedEffect(messageToSend) {
                scrollState2.scrollTo(scrollState2.maxValue)
            }

            Button(onClick = {
                if (!isRunning || writer == null) {
                    Toast.makeText(current, "Server not running or no client connected", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        writer?.println(messageToSend)
                        withContext(Dispatchers.Main) {
                            receivedMessages += "\nSent: $messageToSend"
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

    //监听信息线程
    private fun listenForMessages(onMessageReceived: (String) -> Unit) {
        try {
            while (isRunning) {
                val message = reader?.readLine() ?: break
                onMessageReceived(message)
            }
        } catch (e: Exception) {
            onMessageReceived("Error: ${e.message}")
        } finally {
            isRunning=false
            onMessageReceived("Info: Connection closed.")
        }
    }

    //获取本机全部ip，同时获取ipv4和ipv6（过滤了链路地址），ipv6用中括号[]包裹
    private fun getLocalIPAddresses(): List<String> {
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

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        clientSocket?.close()
        serverSocket?.close()
    }

    @Composable
    fun MainScreen() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TCPServerUI()
        }
    }

}
