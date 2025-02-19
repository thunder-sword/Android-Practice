package com.example.mypractice.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mypractice.utils.BaseViewModel
import com.example.mypractice.utils.TCPConnectionIntent
import com.example.mypractice.utils.TCPConnectionState
import com.example.mypractice.utils.TCPConnectorViewModel
import kotlinx.coroutines.launch

class MessageChatViewModel(val tcpConnectorViewModel: TCPConnectorViewModel): BaseViewModel<TCPConnectionState, TCPConnectionIntent>() {
    init {
        // 订阅 TCPConnectorViewModel 的状态更新
        viewModelScope.launch {
            tcpConnectorViewModel.uiState.collect { state ->
                // 这里可以选择直接更新自己的状态，或者做一些额外的处理
                when(state){
                    //忽略其他指令，接收到SendMessage指令时显示信息
                    is TCPConnectionState.Connected -> {
                        //此处状态中的message参数含义变为SendMessage指令的值
                        if (state.message.startsWith("SendMessage: "))
                            updateState { TCPConnectionState.Connected(
                                ip = state.ip,
                                port = state.port,
                                message = state.message.removePrefix("SendMessage: "),
                                info = state.info)
                            }
                        else
                            updateState { TCPConnectionState.Connected(
                                ip = state.ip,
                                port = state.port,
                                message = "",
                                info = state.info)
                            }
                    }
                    else -> updateState { state }
                }
            }
        }
    }

    override fun initUiState(): TCPConnectionState = TCPConnectionState.Idle

    override fun handleIntent(intent: TCPConnectionIntent) {
        when(intent){
            is TCPConnectionIntent.SendMessage -> tcpConnectorViewModel.sendUiIntent(TCPConnectionIntent.SendMessage("SendMessage: ${intent.message}"))
            else -> tcpConnectorViewModel.sendUiIntent(intent)
        }
    }
}

class MessageChatViewModelFactory(
    private val tcpConnectorViewModel: TCPConnectorViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageChatViewModel::class.java)) {
            return MessageChatViewModel(tcpConnectorViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
