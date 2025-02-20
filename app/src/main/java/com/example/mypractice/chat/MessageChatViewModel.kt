package com.example.mypractice.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mypractice.utils.BaseViewModel
import com.example.mypractice.utils.IUiIntent
import com.example.mypractice.utils.IUiState
import com.example.mypractice.utils.TCPConnectionIntent
import com.example.mypractice.utils.TCPConnectionState
import com.example.mypractice.utils.TCPConnectorViewModel
import com.example.mypractice.utils.TCPListenerIntent
import com.example.mypractice.utils.TCPListenerState
import com.example.mypractice.utils.TCPListenerViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// 聊天界面的状态
sealed class MessageChatState : IUiState {
    data class Chatting(
        val isServer: Boolean,
        val message: String
    ) : MessageChatState()
    object Idle : MessageChatState()
    data class Error(val message: String): MessageChatState()
}

// 聊天界面的意图
sealed class MessageChatIntent : IUiIntent {
    data class SendMessage(val message: String) : MessageChatIntent()
}

fun TCPConnectionState.toChatState(): MessageChatState {
    return when (this) {
        is TCPConnectionState.Connected -> MessageChatState.Chatting(
            isServer = false,
            message = if (message.startsWith("SendMessage: "))
                message.removePrefix("SendMessage: ")
            else ""
        )
        else -> MessageChatState.Idle
    }
}

fun TCPListenerState.toChatState(): MessageChatState {
    return when (this) {
        is TCPListenerState.Connected -> MessageChatState.Chatting(
            isServer = true,
            message = if (receivedMessage.startsWith("SendMessage: "))
                receivedMessage.removePrefix("SendMessage: ")
            else ""
        )
        else -> MessageChatState.Idle
    }
}


class MessageChatViewModel(
    val tcpConnectorViewModel: TCPConnectorViewModel,
    val tcpListenerViewModel: TCPListenerViewModel
): BaseViewModel<MessageChatState, MessageChatIntent>() {

    init {
        // 合并两个状态流
        viewModelScope.launch {
            combine(
                tcpConnectorViewModel.uiState,
                tcpListenerViewModel.uiState
            ) {
              connectionState, listenerState ->
                    //两个都连接则提示断开一个连接
                    if(connectionState is TCPConnectionState.Connected && listenerState is TCPListenerState.Connected)
                        MessageChatState.Error("Can't use two connections at the same time!")
                    else if(connectionState is TCPConnectionState.Connected)
                        connectionState.toChatState()
                    else if(listenerState is TCPListenerState.Connected)
                        listenerState.toChatState()
                    else
                        MessageChatState.Idle
            }.collect{ chatState ->
                updateState { chatState }
            }
        }
    }

    override fun initUiState(): MessageChatState = MessageChatState.Idle

    override fun handleIntent(state: MessageChatState, intent: MessageChatIntent) {
        when(intent){
            is MessageChatIntent.SendMessage -> {
                //因为直接调用网络连接器的intent，所有没有对状态的判断，只能在这里判断
                if(state is MessageChatState.Chatting) {
                    val message = "SendMessage: ${intent.message}"
                    if (state.isServer)
                        tcpListenerViewModel.sendUiIntent(TCPListenerIntent.SendMessage(message))
                    else
                        tcpConnectorViewModel.sendUiIntent(TCPConnectionIntent.SendMessage(message))
                }
            }
        }
    }
}

class MessageChatViewModelFactory(
    private val tcpConnectorViewModel: TCPConnectorViewModel,
    private val tcpListenerViewModel: TCPListenerViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageChatViewModel::class.java)) {
            return MessageChatViewModel(tcpConnectorViewModel, tcpListenerViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
