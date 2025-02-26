package com.example.mypractice.utils

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

// TCP 服务端状态
sealed class TCPListenerState : IUiState {
    object Idle : TCPListenerState()           // 初始状态
    data class Listening(
        val info: String            //提示信息
    ) : TCPListenerState()      // 正在监听
    data class Connected(
        val clientIp: String,
        val clientPort: Int,
        val receivedMessage: String = "",
        val info: String = ""
    ) : TCPListenerState()                     // 客户端已连接
    data class Error(val message: String) : TCPListenerState() // 错误状态
    object Stopped : TCPListenerState()        // 已停止监听
}

// TCP 服务端意图
sealed class TCPListenerIntent : IUiIntent {
    data class StartListening(val port: Int) : TCPListenerIntent() // 启动监听
    object StopListening : TCPListenerIntent()                     // 停止监听
    data class SendMessage(val message: String) : TCPListenerIntent() // 发送消息
    object Reconnect: TCPListenerIntent()
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

// TCPListener的ViewModel
class TCPListenerViewModel : BaseViewModel<TCPListenerState, TCPListenerIntent, IUiEvent>() {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    override fun initUiState(): TCPListenerState = TCPListenerState.Idle

    override fun handleIntent(state: TCPListenerState, intent: TCPListenerIntent) {
        when (intent) {
            is TCPListenerIntent.StartListening -> startListening(intent.port)
            TCPListenerIntent.StopListening -> stopListening()
            is TCPListenerIntent.SendMessage -> sendMessage(intent.message)
            TCPListenerIntent.Reconnect -> reconnect()
        }
    }

    // 启动监听
    private fun startListening(port: Int) {
        //监听前先解绑原监听
        stopListening()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port, 0)
                updateState { TCPListenerState.Listening(info = "Listen on\n"+getLocalIPAddresses()
                    .joinToString("\n") { "$it:$port" }) }

                // 接受客户端连接（阻塞操作）
                clientSocket = serverSocket?.accept()
                clientSocket?.let { socket ->
                    val clientAddress = socket.inetAddress.hostAddress ?: "unknown"
                    val clientPort = socket.port

                    writer = PrintWriter(socket.getOutputStream(), true)
                    reader = BufferedReader(InputStreamReader(socket.inputStream))

                    updateState {
                        TCPListenerState.Connected(
                            clientAddress,
                            clientPort,
                            info = "Client connected from $clientAddress:$clientPort"
                        )
                    }

                    // 启动消息监听
                    listenForMessages()
                }
            } catch (e: Exception) {
                updateState { TCPListenerState.Error("Failed to start listening: ${e.message}") }
            }
        }
    }

    // 停止监听
    private fun stopListening() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                reader?.close()
                writer?.close()
                clientSocket?.close()
                serverSocket?.close()
            } catch (e: Exception) {
                // 记录日志
                e.printStackTrace()
            } finally {
                updateState { TCPListenerState.Stopped }
            }
        }
    }

    //重连逻辑
    private fun reconnect(){
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 接受客户端连接（阻塞操作）
                clientSocket = serverSocket?.accept()
                clientSocket?.let { socket ->
                    val clientAddress = socket.inetAddress.hostAddress ?: "unknown"
                    val clientPort = socket.port

                    writer = PrintWriter(socket.getOutputStream(), true)
                    reader = BufferedReader(InputStreamReader(socket.inputStream))

                    updateState {
                        TCPListenerState.Connected(
                            clientAddress,
                            clientPort,
                            info = "Client reconnected from $clientAddress:$clientPort"
                        )
                    }

                    // 启动消息监听
                    listenForMessages()
                }
            } catch (e: Exception) {
                updateState { TCPListenerState.Error("Failed to reconnecting: ${e.message}") }
            }
        }
    }

    // 发送消息
    private fun sendMessage(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.println(message)
                writer?.flush()

                // 更新发送状态
                val currentState = uiState.value
                if (currentState is TCPListenerState.Connected) {
                    updateState { currentState.copy(
                        info = currentState.info + "\nSent: $message"
                    )}
                }
            } catch (e: Exception) {
                updateState { TCPListenerState.Error("Failed to send message: ${e.message}") }
            }
        }
    }

    // 监听接收消息
    private fun listenForMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val message = reader?.readLine() ?: break
                    val currentState = uiState.value
                    if (currentState is TCPListenerState.Connected) {
                        updateState { currentState.copy(
                            receivedMessage = message,
                            info = currentState.info + "\nReceived: $message"
                        )}
                    }
                }
            } catch (e: Exception) {
                updateState { TCPListenerState.Error("Connection lost: ${e.message}") }
            } finally {
                stopListening()
            }
        }
    }

    //释放资源
    override fun onCleared() {
        println("listener onCleared called!")
        reader?.close()
        writer?.close()
        clientSocket?.close()
        serverSocket?.close()
        super.onCleared()
    }
}
