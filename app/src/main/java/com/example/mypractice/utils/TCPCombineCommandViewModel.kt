package com.example.mypractice.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

//tcp命令类，支持client或server同时监听，但还只有单个连接
class TCPCombineCommandViewModel<Command>(
    private val tcpConnectorViewModel: TCPConnectorViewModel?,
    private val tcpListenerViewModel: TCPListenerViewModel?,
    private val commandClass: KClass<Command>, // 命令枚举类型
): BaseViewModel<TCPCommandState, TCPCommandIntent>()
        where Command : BaseCommand, Command : Enum<Command>{

    private var isServer: Boolean? = null

    init {
        //合并两个状态流
        val connectorFlow = tcpConnectorViewModel?.uiState ?: flowOf(TCPConnectionState.Idle)
        val listenerFlow = tcpListenerViewModel?.uiState ?: flowOf(TCPListenerState.Idle)

        viewModelScope.launch {
            combine(connectorFlow, listenerFlow) { connectionState, listenerState ->
                // 两个都连接则提示断开一个连接
                when {
                    connectionState is TCPConnectionState.Connected && listenerState is TCPListenerState.Connected -> {
                        isServer = null
                        TCPCommandState.Error("Can't use two connections at the same time!")
                    }
                    connectionState is TCPConnectionState.Connected -> {
                        isServer = false
                        connectionState.toCommandState(commandClass)
                    }
                    listenerState is TCPListenerState.Connected -> {
                        isServer = true
                        listenerState.toCommandState(commandClass)
                    }
                    connectionState is TCPConnectionState.Idle && listenerState is TCPListenerState.Idle -> {
                        isServer = null
                        TCPCommandState.Idle
                    }
                    connectionState !is TCPConnectionState.Idle -> {
                        isServer = null
                        connectionState.toCommandState(commandClass)
                    }
                    else -> {
                        isServer = null
                        listenerState.toCommandState(commandClass)
                    }
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
                    if(true==isServer)
                        tcpListenerViewModel!!.sendUiIntent(TCPListenerIntent.SendMessage(message))
                    else if(false==isServer)
                        tcpConnectorViewModel!!.sendUiIntent(TCPConnectionIntent.SendMessage(message))
                }
            }
            TCPCommandIntent.Reconnect -> {
                if(true==isServer)
                    tcpListenerViewModel!!.sendUiIntent(TCPListenerIntent.Reconnect)
                else if(false==isServer)
                    tcpConnectorViewModel!!.sendUiIntent(TCPConnectionIntent.Reconnect)
            }
            TCPCommandIntent.Disconnect -> {
                if(true==isServer)
                    tcpListenerViewModel!!.sendUiIntent(TCPListenerIntent.StopListening)
                else if(false==isServer)
                    tcpConnectorViewModel!!.sendUiIntent(TCPConnectionIntent.Disconnect)
            }
        }
    }
}

class TCPCombineCommandViewModelFactory<Command>(
    private val tcpConnectorViewModel: TCPConnectorViewModel?,
    private val tcpListenerViewModel: TCPListenerViewModel?,
    private val commandClass: KClass<Command> // 命令枚举类型
) : ViewModelProvider.Factory where Command : BaseCommand, Command : Enum<Command> {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TCPCombineCommandViewModel::class.java)) {
            return TCPCombineCommandViewModel(tcpConnectorViewModel, tcpListenerViewModel, commandClass) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}