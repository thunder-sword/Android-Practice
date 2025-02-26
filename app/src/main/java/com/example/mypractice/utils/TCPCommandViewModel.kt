package com.example.mypractice.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

//命令抽象接口
interface BaseCommand

// 聊天界面的状态
sealed class TCPCommandState : IUiState {
    object Idle : TCPCommandState()
    object Connecting: TCPCommandState()
    data class Running(
        val command: BaseCommand?,
        val value: String
    ) : TCPCommandState()
    data class Error(val message: String): TCPCommandState()
    data class Reconnecting(val attempt: Int, val status: String): TCPCommandState()
    object Disconnected: TCPCommandState() // 主动断开
}

// 聊天界面的意图
sealed class TCPCommandIntent : IUiIntent {
    data class SendCommand(val command: BaseCommand, val value: String) : TCPCommandIntent()
    object Reconnect: TCPCommandIntent()
    object Disconnect: TCPCommandIntent()
}

fun <T> TCPConnectionState.toCommandState(commandClass: KClass<T>): TCPCommandState where T : Enum<T>, T : BaseCommand{
    return when (this) {
        is TCPConnectionState.Connected -> {
            var command: BaseCommand? = null
            var value = ""
            try {
                val parts = message.split(": ", limit = 2)
                command = commandClass.java.enumConstants
                    ?.firstOrNull { it.name == parts[0] }
                value = parts[1]
            } catch (e: Exception) {
                println("未识别的指令：$message")
            }
            TCPCommandState.Running(
                command = command,
                value = value
            )
        }
        TCPConnectionState.Connecting -> TCPCommandState.Connecting
        is TCPConnectionState.ConnectionFailed -> TCPCommandState.Error(error)
        TCPConnectionState.Disconnected -> TCPCommandState.Disconnected
        TCPConnectionState.Idle -> TCPCommandState.Idle
        is TCPConnectionState.Reconnecting -> TCPCommandState.Reconnecting(attempt, status)
    }
}

fun <T> TCPListenerState.toCommandState(commandClass: KClass<T>): TCPCommandState where T : Enum<T>, T : BaseCommand{
    return when (this) {
        is TCPListenerState.Connected -> {
            var command: BaseCommand? = null
            var value = ""
            try {
                val parts = receivedMessage.split(": ", limit = 2)
                command = commandClass.java.enumConstants
                    ?.firstOrNull { it.name == parts[0] }
                value = parts[1]
            } catch (e: Exception) {
                println("未识别的指令：$receivedMessage")
            }
            TCPCommandState.Running(
                command = command,
                value = value
            )
        }
        is TCPListenerState.Error -> TCPCommandState.Error(message)
        TCPListenerState.Idle -> TCPCommandState.Idle
        is TCPListenerState.Listening -> TCPCommandState.Disconnected
        TCPListenerState.Stopped -> TCPCommandState.Idle
    }
}

// 新增统一网络接口
interface TcpNetworkHandler {
    val uiState: Flow<Any> // 原始状态流
    fun sendMessage(message: String)
    fun reconnect()
    fun disconnect()
}

// 实现到具体 ViewModel
class TCPConnectorHandler(private val vm: TCPConnectorViewModel) : TcpNetworkHandler {
    override val uiState = vm.uiState
    override fun sendMessage(message: String) = vm.sendUiIntent(TCPConnectionIntent.SendMessage(message))
    override fun reconnect() = vm.sendUiIntent(TCPConnectionIntent.Reconnect)
    override fun disconnect() = vm.sendUiIntent(TCPConnectionIntent.Disconnect)
}

class TCPListenerHandler(private val vm: TCPListenerViewModel) : TcpNetworkHandler {
    override val uiState = vm.uiState
    override fun sendMessage(message: String) = vm.sendUiIntent(TCPListenerIntent.SendMessage(message))
    override fun reconnect() = vm.sendUiIntent(TCPListenerIntent.Reconnect)
    override fun disconnect() = vm.sendUiIntent(TCPListenerIntent.StopListening)
}

//tcp命令类，支持client或server单个连接
class TCPCommandViewModel<Command>(
    private val handler: TcpNetworkHandler,
    private val commandClass: KClass<Command>, // 命令枚举类型
): BaseViewModel<TCPCommandState, TCPCommandIntent, IUiEvent>()
        where Command : BaseCommand, Command : Enum<Command>{

    init {
        viewModelScope.launch {
            handler.uiState
                .map { state ->
                    when (state) {
                        is TCPConnectionState -> state.toCommandState(commandClass)
                        is TCPListenerState -> state.toCommandState(commandClass)
                        else -> TCPCommandState.Error("Unknown state type")
                    }
                }
                .collect { updateState { it } }
        }
    }

    override fun initUiState(): TCPCommandState = TCPCommandState.Idle

    override fun handleIntent(state: TCPCommandState, intent: TCPCommandIntent) {
        when (intent) {
            is TCPCommandIntent.SendCommand -> {
                if (state is TCPCommandState.Running) {
                    handler.sendMessage("${intent.command}: ${intent.value}")
                }
            }
            TCPCommandIntent.Reconnect -> handler.reconnect()
            TCPCommandIntent.Disconnect -> handler.disconnect()
        }
    }
}

class TCPCommandViewModelFactory<Command>(
    private val handler: TcpNetworkHandler,
    private val commandClass: KClass<Command> // 命令枚举类型
) : ViewModelProvider.Factory where Command : BaseCommand, Command : Enum<Command> {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TCPCommandViewModel::class.java)) {
            return TCPCommandViewModel(handler, commandClass) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
