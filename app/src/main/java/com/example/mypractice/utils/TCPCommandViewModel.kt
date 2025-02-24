package com.example.mypractice.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

//命令抽象接口
interface BaseCommand

// 聊天界面的状态
sealed class TCPCommandState : IUiState {
    object Idle : TCPCommandState()
    data class Running(
        val isServer: Boolean,
        val command: BaseCommand?,
        val value: String
    ) : TCPCommandState()
    data class Error(val message: String): TCPCommandState()
    data class Reconnecting(val attempt: Int, val status: String): TCPCommandState()
    object Disconnected : TCPConnectionState() // 主动断开
}

// 聊天界面的意图
sealed class TCPCommandIntent : IUiIntent {
    data class SendCommand(val command: BaseCommand, val value: String) : TCPCommandIntent()
    object Reconnect: TCPCommandIntent()
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
                isServer = false,
                command = command,
                value = value
            )
        }
        else -> TCPCommandState.Idle
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
                isServer = true,
                command = command,
                value = value
            )
        }
        else -> TCPCommandState.Idle
    }
}


class TCPCommandViewModel<Command>(
    val tcpConnectorViewModel: TCPConnectorViewModel?,
    val tcpListenerViewModel: TCPListenerViewModel?,
    private val commandClass: KClass<Command>, // 命令枚举类型
): BaseViewModel<TCPCommandState, TCPCommandIntent>()
        where Command : BaseCommand, Command : Enum<Command>{
    init {
        //合并两个状态流
        val connectorFlow = tcpConnectorViewModel?.uiState ?: flowOf(TCPConnectionState.Idle)
        val listenerFlow = tcpListenerViewModel?.uiState ?: flowOf(TCPListenerState.Idle)

        viewModelScope.launch {
            combine(connectorFlow, listenerFlow) { connectionState, listenerState ->
                // 两个都连接则提示断开一个连接
                when {
                    connectionState is TCPConnectionState.Connected && listenerState is TCPListenerState.Connected ->
                        TCPCommandState.Error("Can't use two connections at the same time!")
                    connectionState is TCPConnectionState.Connected ->
                        connectionState.toCommandState(commandClass)
                    listenerState is TCPListenerState.Connected ->
                        listenerState.toCommandState(commandClass)
                    else ->
                        TCPCommandState.Idle
                }
            }.collect { chatState ->
                updateState { chatState }
            }
        }
    }

    override fun initUiState(): TCPCommandState = TCPCommandState.Idle

    override fun handleIntent(state: TCPCommandState, intent: TCPCommandIntent) {
        when(intent){
            is TCPCommandIntent.SendCommand -> {
                //因为直接调用网络连接器的intent，所有没有对状态的判断，只能在这里判断
                if(state is TCPCommandState.Running) {
                    val message = "${intent.command}: ${intent.value}"
                    if (state.isServer)
                        tcpListenerViewModel!!.sendUiIntent(TCPListenerIntent.SendMessage(message))
                    else
                        tcpConnectorViewModel!!.sendUiIntent(TCPConnectionIntent.SendMessage(message))
                }
            }

            TCPCommandIntent.Reconnect -> TODO()
        }
    }
}

class TCPCommandViewModelFactory<Command>(
    private val tcpConnectorViewModel: TCPConnectorViewModel,
    private val tcpListenerViewModel: TCPListenerViewModel,
    private val commandClass: KClass<Command> // 命令枚举类型
) : ViewModelProvider.Factory where Command : BaseCommand, Command : Enum<Command> {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TCPCommandViewModel::class.java)) {
            return TCPCommandViewModel(tcpConnectorViewModel, tcpListenerViewModel, commandClass) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
