package com.example.mypractice.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

//标识UI状态（Model）的接口
interface IUiState

//标识用户意图（Intent）的接口
interface IUiIntent

//基础ViewModel
abstract class BaseViewModel<UiState: IUiState, UiIntent: IUiIntent>: ViewModel(){
    //用StateFlow构建UI State流
    private val _uiState = MutableStateFlow(this.initUiState())
    val uiState: StateFlow<UiState> = _uiState
    //用Channel来接收用户意图
    //原来设置了 Channel.UNLIMITED 缓冲区，这样可以避免因为短时间内的多次发送导致挂起。但这也要注意，如果意图数量过大可能会占用较多内存。
    //后来采用Channel.BUFFERED，并结合 trySend 避免挂起
    private val _uiIntent = Channel<UiIntent>(Channel.BUFFERED)
    val uiIntent: Flow<UiIntent> = _uiIntent.receiveAsFlow()

    //初始化状态
    protected abstract fun initUiState(): UiState

    //更新状态
    protected fun updateState(copy: UiState.() -> UiState){
        _uiState.update { copy(_uiState.value) }
    }

    //发送意图
    fun sendUiIntent(uiIntent: UiIntent){
        viewModelScope.launch {
            _uiIntent.trySend(uiIntent).isSuccess // 避免阻塞
        }
    }

    init {
        //子线程自动处理意图
        viewModelScope.launch {
            uiIntent.collect{ intent ->
                //这里是并行运行的，所以状态有可能不是最新状态，还没想到较好的解决方案
                handleIntent(_uiState.value, intent)
            }
        }
    }

    //抽象处理意图函数
    protected abstract fun handleIntent(state: UiState, intent: UiIntent)
}