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
sealed class TcpConnectionState : IUiState {
    object Idle : TcpConnectionState() // 初始状态
    object Connecting : TcpConnectionState() // 正在连接
    data class Connected(
        val ip: String,
        val port: Int,
        val message: String = ""
    ) : TcpConnectionState() // 连接成功状态
    data class Reconnecting(val attempt: Int, val status: String) : TcpConnectionState() // 正在重连
    data class ConnectionFailed(val error: String) : TcpConnectionState() // 连接失败
    object Disconnected : TcpConnectionState() // 主动断开
}

//tcp用户意图
sealed class TcpConnectionIntent : IUiIntent {
    data class Connect(val ip: String, val port: Int) : TcpConnectionIntent()    // 发起连接
    object Disconnect : TcpConnectionIntent()                                   // 断开连接
    object Reconnect : TcpConnectionIntent()                                    // 重连
    data class SendMessage(val message: String) : TcpConnectionIntent()           // 发送消息
}

//tcp-viewModel
class TcpConnectorViewModel : BaseViewModel<TcpConnectionState, TcpConnectionIntent>() {
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

    override fun initUiState(): TcpConnectionState = TcpConnectionState.Idle

    override fun handleIntent(intent: TcpConnectionIntent) {
        when (intent) {
            is TcpConnectionIntent.Connect -> connect(intent.ip, intent.port)
            TcpConnectionIntent.Disconnect -> disconnect()
            TcpConnectionIntent.Reconnect -> reconnect()
            is TcpConnectionIntent.SendMessage -> sendMessage(intent.message)
        }
    }

    // 发起连接
    private fun connect(ip: String, port: Int) {
        currentIp = ip
        currentPort = port
        viewModelScope.launch(Dispatchers.IO) {
            updateState { TcpConnectionState.Connecting }
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), connectTimeoutMillis)
                socket = s
                writer = PrintWriter(s.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(s.getInputStream()))
                updateState { TcpConnectionState.Connected(ip, port, message = "") }
                // 启动消息监听
                listenForMessages()
            } catch (e: SocketTimeoutException) {
                updateState { TcpConnectionState.ConnectionFailed("Timeout: ${e.message}") }
            } catch (e: Exception) {
                updateState { TcpConnectionState.ConnectionFailed(e.message ?: "Unknown error") }
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
            } finally {
                socket = null
                writer = null
                reader = null
                updateState { TcpConnectionState.Disconnected }
            }
        }
    }

    // 重连逻辑
    private fun reconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            while (reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++
                updateState { TcpConnectionState.Reconnecting(reconnectAttempts, "Attempt $reconnectAttempts") }
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(currentIp, currentPort), connectTimeoutMillis)
                    socket = s
                    writer = PrintWriter(s.getOutputStream(), true)
                    reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    updateState { TcpConnectionState.Connected(currentIp, currentPort, message = "") }
                    reconnectAttempts = 0
                    // 启动消息监听
                    listenForMessages()
                    return@launch
                } catch (e: Exception) {
                    delay(reconnectInterval)
                }
            }
            updateState { TcpConnectionState.ConnectionFailed("Max reconnect attempts reached") }
        }
    }

    // 发送消息
    private fun sendMessage(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (socket == null || socket!!.isClosed) {
                updateState { TcpConnectionState.ConnectionFailed("Socket closed, cannot send message") }
                return@launch
            }
            try {
                writer?.println(message)
                writer?.flush()
                // 此处可以选择更新状态以反映已发送消息，
                // 例如追加到 Connected 状态中的 messages 字段
            } catch (e: Exception) {
                updateState { TcpConnectionState.ConnectionFailed("Failed to send message: ${e.message}") }
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
                    if (currentState is TcpConnectionState.Connected) {
                        updateState { TcpConnectionState.Connected(
                            currentState.ip,
                            currentState.port,
                            line
                        ) }
                    }
                }
            } catch (e: Exception) {
                updateState { TcpConnectionState.ConnectionFailed("Error receiving messages: ${e.message}") }
            } finally {
                // 当循环退出，表示连接关闭
                disconnect()
            }
        }
    }
}

