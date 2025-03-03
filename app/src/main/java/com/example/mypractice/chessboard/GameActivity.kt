package com.example.mypractice.chessboard

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.example.mypractice.R
import com.example.mypractice.chat.MessageChatViewModel
import com.example.mypractice.chat.MessageChatViewModelFactory
import com.example.mypractice.chat.MessageCommand
import com.example.mypractice.ui.theme.MyPracticeTheme
import com.example.mypractice.ui.theme.chessBoardColor
import com.example.mypractice.utils.BaseComponentActivity
import com.example.mypractice.utils.ImageLoader
import com.example.mypractice.utils.TCPCombineCommandViewModel
import com.example.mypractice.utils.TCPCombineCommandViewModelFactory
import com.example.mypractice.utils.TCPConnectorViewModel
import com.example.mypractice.utils.TCPListenerViewModel

class GameActivity: BaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用 ViewModelProvider 获取实例
        val clientViewModel: TCPConnectorViewModel by viewModels()
        val serverViewModel: TCPListenerViewModel by viewModels()

        val combineViewModel: TCPCombineCommandViewModel<MessageCommand> by viewModels {
            TCPCombineCommandViewModelFactory(
                tcpConnectorViewModel = clientViewModel,
                tcpListenerViewModel = serverViewModel,
                commandClass = MessageCommand::class
            )
        }
        val viewModel: GameViewModel by viewModels {
            GameViewModelFactory(
                OnlineState.Local,
                null
            )
        }

        val imageLoader: ImageLoader = ImageLoader(this)

        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    GameMainUi(imageLoader, viewModel)
                }
            }
        }
    }
}

// 返回放大棋盘后的宽高
fun getBoardSize(screenWidth: Dp, screenHeight: Dp, percent: Float = 1f): Pair<Dp, Dp> {
    val maxWidth = screenWidth * percent
    val maxHeight = screenHeight * percent
    val aspectRatio = 10f / 9

    // 计算等比宽高
    val chessBoardWidth = if (maxWidth * aspectRatio <= maxHeight) {
        maxWidth
    } else {
        maxHeight / aspectRatio
    }
    val chessBoardHeight = chessBoardWidth * aspectRatio
    return Pair(chessBoardWidth, chessBoardHeight)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameMainUi(imageLoader: ImageLoader, viewModel: GameViewModel, clientViewModel: TCPConnectorViewModel? = null, serverViewModel: TCPListenerViewModel? = null) {
    val current = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // 提取子状态（自动触发重组）
    val _gamePlayUiState = state.gamePlayUiState
    val _networkState = state.networkState

    // 若需要防抖（distinctUntilChanged），需结合 remember
    val gamePlayState = remember(_gamePlayUiState) { _gamePlayUiState }
    val networkState = remember(_networkState) { _networkState }

    //初次运行
    LaunchedEffect(Unit){
        viewModel.sendUiIntent(GameUiIntent.StartGame)
    }

    when(networkState){
        NetworkState.Connecting -> {
            AlertDialog(
                onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
                title = { Text(text = "提示") },
                text = {
                    Text(text = "正在连接网络...")
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.onPrimary) // 加载指示器
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        //viewModel.sendUiIntent(GameUiIntent.)
                    }) {
                        Text("停止连接")
                    }
                }
            )
            return
        }
        NetworkState.Disconnected -> TODO()
        is NetworkState.Error -> TODO()
        NetworkState.Idle -> {}
        is NetworkState.Reconnecting -> TODO()
        is NetworkState.Running -> {}
        is NetworkState.Waiting -> TODO()
    }
    when(gamePlayState){
        GamePlayUiState.Ended -> {
            AlertDialog(
                onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
                title = { Text(text = "提示") },
                text = {
                    Text(text = "游戏结束")
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.onPrimary) // 加载指示器
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        //viewModel.sendUiIntent(GameUiIntent.)
                    }) {
                        Text("重新开始？")
                    }
                }
            )
            return
        }
        is GamePlayUiState.Error -> {
            AlertDialog(
                onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
                title = { Text(text = "提示") },
                text = {
                    Text(text = "发生错误: ${gamePlayState.message}")
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.onPrimary) // 加载指示器
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        //viewModel.sendUiIntent(GameUiIntent.)
                    }) {
                        Text("是")
                    }
                }
            )
            return
        }
        GamePlayUiState.Idle -> {
            return
        }
        is GamePlayUiState.Running -> {}
    }

    // 屏幕宽高
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    // 使用 derivedStateOf 和 remember，让其只有在屏幕大小发生变化时才更改棋盘宽高
    val chessBoardSize by remember (screenWidth, screenHeight) {
        derivedStateOf {
            getBoardSize(screenWidth, screenHeight)
        }
    }
    // 解构棋盘宽高
    val (chessBoardWidth, chessBoardHeight) = chessBoardSize

    //UI显示
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f) //使其不会被覆盖
    ) {
        //堆叠UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
                .pointerInteropFilter { false } // 返回 false 表示该层不消费事件，点击事件可以往下传
        ) {
            //不是本地时
            if (OnlineState.Local != gamePlayState.onlineState && networkState is NetworkState.Running) {
                //中间上部网络状态显示
                //让文本框支持向下滚动
                val scrollState1 = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState1)
                        .padding(8.dp, top = 50.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    SelectionContainer {
                        Text(networkState.info, Modifier.padding(8.dp))
                    }
                }

                //右上部显示聊天信息
//                if (gameManager.chatMessage.isNotEmpty()) {
//                    Box(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(8.dp, top = keyboardHeightDp + 85.dp), //加上键盘高度，使调出键盘时也能显示聊天内容
//                        contentAlignment = Alignment.TopEnd
//                    ) {
//                        SelectionContainer {
//                            ChatBubble(gameManager.chatMessage, isFromMe = false) {
//                                gameManager.chatMessage = ""
//                            }
//                        }
//                    }
//                }

//                if (showMyChatBubble.isNotEmpty()) {
//                    //左下部显示聊天信息
//                    Box(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(8.dp, bottom = 110.dp),
//                        contentAlignment = Alignment.BottomStart
//                    ) {
//                        SelectionContainer {
//                            ChatBubble(showMyChatBubble, isFromMe = true) {
//                                showMyChatBubble = ""
//                            }
//                        }
//                    }
//                }
            }
        }

        //象棋棋盘
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
            contentAlignment = Alignment.Center
        ){
            //象棋游戏主容器
            Row(
                modifier = Modifier
                    //.fillMaxSize()
                    .graphicsLayer {
                        // 当前玩家不是玩家1，则垂直对角翻转180度
                        if (0 != gamePlayState.localPlayer) {
                            scaleX = -1f
                            scaleY = -1f
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 绘制棋盘
                Canvas(
                    modifier = Modifier
                        .size(width = chessBoardWidth, height = chessBoardHeight)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                viewModel.sendUiIntent(GameUiIntent.TapBoard(offset))
                            }
                        }
                ) {
                    //绘制棋盘
                    viewModel.chessBoard.initialize(size)
                    viewModel.chessBoard.draw(this, imageLoader = imageLoader)

                    // 绘制棋子图片
                    for (piece in viewModel.alivePieces) {
                        //黑子的正面棋子需要旋转180度
                        val isRotate: Boolean = (piece.isFront && PieceCamp.Black == piece.camp)
                        piece.draw(
                            this,
                            imageLoader = imageLoader,
                            borderLeft = viewModel.chessBoard.borderLeft,
                            borderTop = viewModel.chessBoard.borderTop,
                            cellWidth = viewModel.chessBoard.cellWidth,
                            cellHeight = viewModel.chessBoard.cellHeight,
                            isRotate = isRotate
                        )
                    }

                    // 绘制可到达位置提示格
                    viewModel.drawBox(
                        this,
                        imageLoader = imageLoader,
                        borderLeft = viewModel.chessBoard.borderLeft,
                        borderTop = viewModel.chessBoard.borderTop,
                        cellWidth = viewModel.chessBoard.cellWidth,
                        cellHeight = viewModel.chessBoard.cellHeight
                    )
                }
            }
        }

        //不堆叠UI
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally  //居中显示
        ) {
            //上部分容器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween, // 关键：将子项分到两端
                verticalAlignment = Alignment.Top
            ) {
                //左上角重新开始按钮
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    Button(
                        onClick = {
                            TODO()
                        }
                    ) {
                        Text(text = "重新开始")
                    }
                }

                //右上角显示容器
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = -1f
                            scaleY = -1f
                        }, // 垂直对角翻转180度
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        if (0 == gamePlayState.localPlayer) "玩家2" else "玩家1",
                        color = if (0 == gamePlayState.localPlayer) Color.Blue else Color.Red,
                        fontSize = 40.sp
                    )
                    Text(
                        text = " ${viewModel.alivePieces.groupBy { it.camp }[viewModel.players[(gamePlayState.localPlayer+1) % viewModel.players.size]]?.size ?: 0}"
                                + if (gamePlayState.localPlayer != gamePlayState.currentPlayer) "【走】" else "",
                        color = if (0 == gamePlayState.localPlayer) Color.Blue else Color.Red,
                        fontSize = 30.sp
                    )
                }
            }

            //下部分容器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween, // 关键：将子项分到两端
            ) {
                //左下角的容器
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        if (0 != gamePlayState.localPlayer) "玩家2" else "玩家1",
                        color = if (0 != gamePlayState.localPlayer) Color.Blue else Color.Red,
                        fontSize = 40.sp,
                    )
                    Text(
                        text = " ${viewModel.alivePieces.groupBy { it.camp }[viewModel.players[gamePlayState.localPlayer]]?.size ?: 0}"
                                + if (gamePlayState.localPlayer == gamePlayState.currentPlayer) "【走】" else "",
                        color = if (0 != gamePlayState.localPlayer) Color.Blue else Color.Red,
                        fontSize = 30.sp
                    )
                }

                //右下角悔棋按钮
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Button(
                        onClick = {
                            TODO()
                        }
                    ) {
                        Text(text = "悔棋")
                    }
                }
            }

//            //不是本地时
//            if (OnlineState.Local != onlineState) {
//                //中间下部发送消息聊天框
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth(),
//                    horizontalArrangement = Arrangement.End,
//                    verticalAlignment = Alignment.CenterVertically
//                ){
//                    // 语音聊天按钮
//                    VoiceChatButton(
//                        isTalking = audioManager.isSendRecording,
//                        onStart = {
//                            if((audioManager.isTCP && !audioManager.isConnect) || (!audioManager.isTCP && !audioManager.isUDPConnect)){ //检查是否开启网络连接
//                                Toast.makeText(current, "连接语音服务器后才能开启录音", Toast.LENGTH_LONG).show()
//                            } else
//                                audioManager.isSendRecording = true
//                        },
//                        onStop = {
//                            audioManager.isSendRecording = false
//                        },
//                        onSetting = {
//                            showAudioSettingDialog = true
//                        }
//                    )
//                    //信息输入框
//                    TextField(
//                        value = tcpConnector?.messageToSend ?: "",
//                        onValueChange = { tcpConnector?.messageToSend = it },
//                        modifier = Modifier
//                            .weight(1f)  // 分配剩余空间
//                            .padding(4.dp),
//                        label = { Text("Enter Message") },
//                        singleLine = false  // 允许内容换行
//                    )
//                    Button(onClick = {
//                        showMyChatBubble = tcpConnector?.messageToSend ?: ""
//                        tcpConnector?.send("chatMessage: ${tcpConnector.messageToSend}")
//                    }) {
//                        Text("发送")
//                    }
//                }
//            }
        }
    }

    // 将状态栏配色设为当前背景配色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            //设置状态栏颜色和背景颜色同步
            window.statusBarColor = chessBoardColor.toArgb()
            //设置状态栏图标为深色
            WindowCompat
                .getInsetsController(window, view)
                ?.isAppearanceLightStatusBars = true
        }
    }

    //背景图片
    Image(
        painter = painterResource(R.drawable.bg),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize() // 图片填充整个 Box
    )
}