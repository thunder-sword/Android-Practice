package com.example.mypractice.chat

import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.ui.theme.MyPracticeTheme
import com.example.mypractice.utils.*
import kotlinx.coroutines.delay

class MessageChatActivity : BaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用 ViewModelProvider 获取实例
        val clientViewModel = ViewModelProvider(
            this
        )[TCPConnectorViewModel::class.java]
        val serverViewModel = ViewModelProvider(
            this
        )[TCPListenerViewModel::class.java]

        val viewModel = ViewModelProvider(
            this,
            MessageChatViewModelFactory(clientViewModel, serverViewModel)
        )[MessageChatViewModel::class.java]

        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MessageChatUI(viewModel)
                }
            }
        }
    }
}

//画对话气泡
@Composable
fun ChatBubble(
    message: String,
    isFromMe: Boolean = false,
    fadeDelay: Long = 5000,  // 5秒后触发
    fadeDuration: Int = 500, // 动画持续1秒
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = fadeDuration)
    )

    LaunchedEffect(message) {
        if(message.isNotEmpty()) {
            isVisible = true
            delay(fadeDelay)
            isVisible = false
        }
    }

    // 颜色
    val backgroundColor = if (!isFromMe) Color(0xFF4CAF50) else Color(0xFFE0E0E0)

    // 透明后删除气泡
    if (isVisible) {
        Box(
            modifier = Modifier
                .alpha(alpha)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Text(
                text = message,
                color = if (!isFromMe) Color.White else Color.Black,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun MessageChatUI(viewModel: MessageChatViewModel){
    val current = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    //连接成功后才显示对话界面
    val connectedState = state as? MessageChatState.Chatting ?: run {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //如果有错误信息则显示错误信息
            if(state is MessageChatState.Error){
                Text((state as MessageChatState.Error).message)
            }
            TCPServerLinkUI(viewModel.tcpListenerViewModel)
            TCPClientLinkUI(viewModel.tcpConnectorViewModel)
        }
        return
    }

    LaunchedEffect(Unit) { // 当首次进入连接状态时触发
        Toast.makeText(current, "可以聊天了~", Toast.LENGTH_SHORT).show()
    }

    var messageToSend by rememberSaveable { mutableStateOf("") }
    //本用户聊天气泡显示字符串
    var myChatBubbleMessage by rememberSaveable { mutableStateOf("") }

    // 记录并监听键盘高度
    val view = LocalView.current
    var keyboardHeightPx by remember { mutableStateOf(0) } // 以 px 计算
    val density = LocalDensity.current // 获取屏幕密度
    // 转换 px -> dp
    val keyboardHeightDp by remember {
        derivedStateOf {
            with(density) { keyboardHeightPx.toDp() }
        }
    }
    // 监听键盘高度变化（兼容 SDK 21+）
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            view.getWindowVisibleDisplayFrame(rect)

            val heightDiff = view.height - rect.bottom

            keyboardHeightPx = if (heightDiff > view.height * 0.15) { // 15% 以上视为键盘弹出
                heightDiff
            } else {
                0
            }
        }

        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        //右上部显示聊天信息
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp, top = keyboardHeightDp + 85.dp), //加上键盘高度，使调出键盘时也能显示聊天内容
            contentAlignment = Alignment.TopEnd
        ) {
            SelectionContainer {
                ChatBubble(connectedState.message, isFromMe = false)
            }
        }

        //左下部显示聊天信息
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp, bottom = 110.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            SelectionContainer {
                ChatBubble(myChatBubbleMessage, isFromMe = true)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 输入待发送消息
                TextField(
                    value = messageToSend,
                    onValueChange = { messageToSend = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    label = { Text("发送消息") }
                )
                Button(onClick = {
                    if (messageToSend.isNotBlank()) {
                        viewModel.sendUiIntent(MessageChatIntent.SendMessage(messageToSend))
                        myChatBubbleMessage = messageToSend
                        messageToSend = ""  // 发送后清空输入框
                    }
                }) {
                    Text("发送")
                }
            }
        }
    }
}