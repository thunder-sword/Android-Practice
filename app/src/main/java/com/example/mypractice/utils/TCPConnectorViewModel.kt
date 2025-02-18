package com.example.mypractice.utils

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

//tcp网络连接状态
sealed class TCPConnectionState : IUiState {
    object Idle : TCPConnectionState() // 初始状态
    object Connecting : TCPConnectionState() // 正在连接
    data class Connected(
        val ip: String,
        val port: Int,
        val message: String = "",   //接收的当前信息
        val info: String = ""       //用于显示的信息
    ) : TCPConnectionState() // 连接成功状态
    data class Reconnecting(val attempt: Int, val status: String) : TCPConnectionState() // 正在重连
    data class ConnectionFailed(val error: String) : TCPConnectionState() // 连接失败
    object Disconnected : TCPConnectionState() // 主动断开
}

//tcp用户意图
sealed class TCPConnectionIntent : IUiIntent {
    data class Connect(val ip: String, val port: Int) : TCPConnectionIntent()    // 发起连接
    object Disconnect : TCPConnectionIntent()                                   // 断开连接
    object Reconnect : TCPConnectionIntent()                                    // 重连
    data class SendMessage(val message: String) : TCPConnectionIntent()           // 发送消息
}

//tcp-viewModel
class TCPConnectorViewModel : BaseViewModel<TCPConnectionState, TCPConnectionIntent>() {
    // 内部 TCP 连接相关变量
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    // TCP 连接参数
    private var connectTimeoutMillis: Int = 5000 // 毫秒

    // 重连相关变量
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    private val reconnectInterval = 2000L

    // 保存当前的 IP 与端口（用于重连）
    private var currentIp: String = ""
    private var currentPort: Int = 0

    override fun initUiState(): TCPConnectionState = TCPConnectionState.Idle

    override fun handleIntent(intent: TCPConnectionIntent) {
        when (intent) {
            is TCPConnectionIntent.Connect -> connect(intent.ip, intent.port)
            TCPConnectionIntent.Disconnect -> disconnect()
            TCPConnectionIntent.Reconnect -> reconnect()
            is TCPConnectionIntent.SendMessage -> sendMessage(intent.message)
        }
    }

    // 发起连接
    private fun connect(ip: String, port: Int) {
        //连接前先断开旧连接
        disconnect()

        currentIp = ip
        currentPort = port
        viewModelScope.launch(Dispatchers.IO) {
            updateState { TCPConnectionState.Connecting }
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), connectTimeoutMillis)
                socket = s
                writer = PrintWriter(s.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(s.getInputStream()))
                updateState { TCPConnectionState.Connected(ip, port, message = "", info = "") }
                // 启动消息监听
                listenForMessages()
            } catch (e: SocketTimeoutException) {
                updateState { TCPConnectionState.ConnectionFailed("Timeout: ${e.message}") }
            } catch (e: Exception) {
                updateState { TCPConnectionState.ConnectionFailed(e.message ?: "Unknown error") }
            }
        }
    }

    // 断开连接
    private fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                socket?.close()
                writer?.close()
                reader?.close()
            } catch (e: Exception) {
                // 这里可以记录日志
                e.printStackTrace()
            } finally {
                socket = null
                writer = null
                reader = null
                updateState { TCPConnectionState.Disconnected }
            }
        }
    }

    // 重连逻辑
    private fun reconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            while (reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++
                updateState { TCPConnectionState.Reconnecting(reconnectAttempts, "Attempt $reconnectAttempts") }
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(currentIp, currentPort), connectTimeoutMillis)
                    socket = s
                    writer = PrintWriter(s.getOutputStream(), true)
                    reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    updateState { TCPConnectionState.Connected(currentIp, currentPort, message = "", info = "") }
                    reconnectAttempts = 0
                    // 启动消息监听
                    listenForMessages()
                    return@launch
                } catch (e: Exception) {
                    delay(reconnectInterval)
                }
            }
            updateState { TCPConnectionState.ConnectionFailed("Max reconnect attempts reached") }
        }
    }

    // 发送消息
    private fun sendMessage(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (socket == null || socket!!.isClosed) {
                updateState { TCPConnectionState.ConnectionFailed("Socket closed, cannot send message") }
                return@launch
            }
            try {
                writer?.println(message)
                writer?.flush()
                // 此处可以选择更新状态以反映已发送消息
                val currentState = uiState.value
                if (currentState is TCPConnectionState.Connected) {
                    updateState { TCPConnectionState.Connected(
                        currentState.ip,
                        currentState.port,
                        currentState.message,
                        currentState.info+"\nSend: $message"
                    ) }
                }
            } catch (e: Exception) {
                updateState { TCPConnectionState.ConnectionFailed("Failed to send message: ${e.message}") }
            }
        }
    }

    // 监听接收消息
    private fun listenForMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    // 当状态为 Connected 时，更新接收到的消息
                    val currentState = uiState.value
                    if (currentState is TCPConnectionState.Connected) {
                        updateState { TCPConnectionState.Connected(
                            currentState.ip,
                            currentState.port,
                            line,
                            currentState.info+"\nReceived: $line"
                        ) }
                    }
                }
            } catch (e: Exception) {
                updateState { TCPConnectionState.ConnectionFailed("Error receiving messages: ${e.message}") }
            } finally {
                // 当循环退出，表示连接关闭
                disconnect()
            }
        }
    }

    //释放资源
    override fun onCleared() {
        disconnect() // 复用现有的断开逻辑
        super.onCleared()
    }
}

