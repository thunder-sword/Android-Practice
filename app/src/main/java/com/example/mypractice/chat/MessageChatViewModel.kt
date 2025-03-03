package com.example.mypractice.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mypractice.utils.BaseCommand
import com.example.mypractice.utils.BaseViewModel
import com.example.mypractice.utils.IUiEvent
import com.example.mypractice.utils.IUiIntent
import com.example.mypractice.utils.IUiState
import com.example.mypractice.utils.TCPCombineCommandViewModel
import com.example.mypractice.utils.TCPCommandIntent
import com.example.mypractice.utils.TCPCommandState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class MessageCommand: BaseCommand {
    SendMessage
}

// 聊天界面的状态
sealed class MessageChatState : IUiState {
    object Connecting: MessageChatState()
    object Disconnected: MessageChatState()
    data class Reconnecting(val attempt: Int): MessageChatState()
    data class Chatting(
        val message: String
    ) : MessageChatState()
    object Idle : MessageChatState()
    data class Error(val message: String): MessageChatState()
}

// 聊天界面的意图
sealed class MessageChatIntent : IUiIntent {
    data class SendMessage(val message: String) : MessageChatIntent()
}


class MessageChatViewModel(
    private val tcpCombineCommandViewModel: TCPCombineCommandViewModel<MessageCommand>
): BaseViewModel<MessageChatState, MessageChatIntent, IUiEvent>() {

    init {
        viewModelScope.launch {
            tcpCombineCommandViewModel.uiState.map { state ->
                when(state){
                    TCPCommandState.Connecting -> MessageChatState.Connecting
                    TCPCommandState.Disconnected -> MessageChatState.Disconnected
                    is TCPCommandState.Error -> MessageChatState.Error(state.message)
                    TCPCommandState.Idle -> MessageChatState.Idle
                    is TCPCommandState.Reconnecting -> MessageChatState.Reconnecting(state.attempt)
                    is TCPCommandState.Running -> MessageChatState.Chatting(if(state.command == MessageCommand.SendMessage) state.value else "")
                }
            }.collect{ updateState { it }}
        }
    }

    override fun initUiState(): MessageChatState = MessageChatState.Idle

    override fun handleIntent(state: MessageChatState, intent: MessageChatIntent) {
        when(intent){
            is MessageChatIntent.SendMessage -> {
                tcpCombineCommandViewModel.sendUiIntent(TCPCommandIntent.SendCommand(MessageCommand.SendMessage, intent.message))
            }
        }
    }
}

class MessageChatViewModelFactory(
    private val tcpCombineCommandViewModel: TCPCombineCommandViewModel<MessageCommand>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageChatViewModel::class.java)) {
            return MessageChatViewModel(tcpCombineCommandViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
