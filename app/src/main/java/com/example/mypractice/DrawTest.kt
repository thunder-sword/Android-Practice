package com.example.mypractice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.example.mypractice.ui.theme.MyPracticeTheme
import com.example.mypractice.ui.theme.chessBoardColor
import kotlinx.coroutines.delay
import androidx.compose.material.MaterialTheme as MaterialTheme1

class DrawTest : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用 ViewModelProvider 获取实例
        val viewModel = ViewModelProvider(
            this,
            GameViewModelFactory(applicationContext)
        )[GameViewModel::class.java]

        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme1.colors.background
                ) {
                    DrawMain(viewModel)
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
    fadeDuration: Int = 1000, // 动画持续1秒
    onFaded: (() -> Unit)? = null
) {
    var isVisible by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = fadeDuration)
    )

    LaunchedEffect(Unit) {
        delay(fadeDelay)
        isVisible = false
        onFaded?.invoke()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceChatButton(
    isTalking: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSetting: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // 设置背景和圆角效果，使其看起来像一个按钮
            .background(MaterialTheme1.colors.primary, shape = RoundedCornerShape(30.dp))
            // 使用 combinedClickable 同时处理短按和长按事件
            .combinedClickable(
                onClick = { if (isTalking) onStop() else onStart() },
                onLongClick = onSetting
            )
    ) {
        Text(if (isTalking) "关" else "开")
    }
}

//画棋盘
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChessBoard(viewModel: GameViewModel, onlineState: OnlineState = OnlineState.Local, tcpConnector: TCPConnector? = null) {
    val current = LocalContext.current
    //点击协程实例
    val scope = rememberCoroutineScope()
    //初始化游戏管理器
    val gameManager = remember {
        GameManager(
            current = current,
            scope = scope,
            onlineState = onlineState,
            tcpConnector = tcpConnector
        )
    }
    //语音器
    val audioManager: UDPAudioChat = remember { UDPAudioChat() }
    //是否显示语音器设置地址Dialog
    var showAudioSettingDialog by remember { mutableStateOf(false) }

    // 开始执行一次
    LaunchedEffect(Unit) {
        //启动游戏
        gameManager.startGame()
        //如果不是本地则初始化语音器
        if(OnlineState.Local!=onlineState) {
            audioManager.ip = tcpConnector!!.ip
            audioManager.isServer = (OnlineState.Server == onlineState)
            //自动尝试连接
            if(audioManager.isServer){
                audioManager.listen()
            }else{
                audioManager.connect()
            }
            //自动播放远端音频
            audioManager.startAudioPlay()
        }
    }

    // 监听生命周期，在 onDestroy 时清理
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                gameManager.onDestroy()
                tcpConnector?.onDestroy()
                audioManager.onDestroy()
            }
        }

        // 添加观察者
        lifecycleOwner.lifecycle.addObserver(observer)

        // 在组件从 Composition 中移除时移除观察者
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 屏幕宽高
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    // 使用 derivedStateOf 和 remember，让其只有在屏幕大小发生变化时才更改棋盘宽高
    val chessBoardSize by remember(screenWidth, screenHeight) {
        derivedStateOf {
            gameManager.getBoardSize(screenWidth, screenHeight)
        }
    }
    // 解构棋盘宽高
    val (chessBoardWidth, chessBoardHeight) = chessBoardSize

    // 监听 gameManager.currentState 的状态变化
    LaunchedEffect(gameManager.currentState) {
        if (gameManager.currentState == GameState.Ended) {
            gameManager.blockQueryString = "是否要重新开始游戏？"
            gameManager.onBlockQueryYes = {
                gameManager.tryRestartGame()
            }
            gameManager.onBlockQueryNo = {
            }
        }
    }

    //如果有blockQuery信息则显示
    if (gameManager.blockQueryString.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
            title = { Text(text = "提示") },
            text = { Text(text = gameManager.blockQueryString) },
            confirmButton = {
                Button(onClick = {
                    gameManager.blockQueryString = ""
                    gameManager.onBlockQueryYes?.invoke()
                }) {
                    Text("是")
                }
            },
            dismissButton = {
                Button(onClick = {
                    // 关闭弹窗
                    gameManager.blockQueryString = ""
                    gameManager.onBlockQueryNo?.invoke()
                }) {
                    Text("否")
                }
            }
        )
    }

    //如果有block信息则显示【缺点是返回键无法退出Activity】
    if (gameManager.blockString.isNotEmpty()) {
        Dialog(
            onDismissRequest = { /* 不允许点击外部关闭弹窗 */ }
        ) {
            // 居中显示的加载提示框
            Surface(
                modifier = Modifier
                    .width(200.dp)
                    .height(150.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.Black) // 加载指示器
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(gameManager.blockString, color = Color.Black)
                }
            }
        }
    }

    //本用户聊天气泡显示字符串
    var showMyChatBubble by remember { mutableStateOf("") }

    val view = LocalView.current
    // 记录并监听键盘高度
    var keyboardHeightPx by remember { mutableStateOf(0) } // 以 px 计算
    val density = LocalDensity.current // 获取屏幕密度
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
    // 转换 px -> dp
    val keyboardHeightDp = with(density) { keyboardHeightPx.toDp() }

    //设置音频地址
    if (showAudioSettingDialog) {
        AlertDialog(
            onDismissRequest = { /* 不允许点击外部关闭弹窗 */ },
            title = { Text(text = "语音设置") },
            text = {
                    audioManager.Panel()
                },
            confirmButton = {
                Button(onClick = {
                    showAudioSettingDialog = false
                }) {
                    Text("关闭")
                }
            }
        )
    }

    //监听用户是否尝试开启录音
    LaunchedEffect(audioManager.isTryRecording){
        if(audioManager.isTryRecording){ //尝试开启录音
            if(!audioManager.isConnect){ //检查是否开启网络连接
                Toast.makeText(current, "连接语音服务器后才能开启录音", Toast.LENGTH_LONG).show()
                audioManager.isTryRecording = false
            } else{
                audioManager.startRecord(current)
            }
        }else{ //尝试关闭录音
            audioManager.stopRecord()
        }
    }

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
                .zIndex(2f)
                .pointerInteropFilter { false } // 返回 false 表示该层不消费事件，点击事件可以往下传
        ) {
            //不是本地时
            if (OnlineState.Local != onlineState) {
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
                        Text(tcpConnector!!.connectionStatus, Modifier.padding(8.dp))
                    }
                }

                //右上部显示聊天信息
                if (gameManager.chatMessage.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp, top = keyboardHeightDp + 85.dp), //加上键盘高度，使调出键盘时也能显示聊天内容
                        contentAlignment = Alignment.TopEnd
                    ) {
                        SelectionContainer {
                            ChatBubble(gameManager.chatMessage, isFromMe = false) {
                                gameManager.chatMessage = ""
                            }
                        }
                    }
                }

                if (showMyChatBubble.isNotEmpty()) {
                    //左下部显示聊天信息
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp, bottom = 110.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        SelectionContainer {
                            ChatBubble(showMyChatBubble, isFromMe = true) {
                                showMyChatBubble = ""
                            }
                        }
                    }
                }
            }
        }

        //不堆叠UI
        Column(
            modifier = Modifier.fillMaxSize()
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
                            gameManager.blockQueryString = "是否要重新开始游戏？"
                            gameManager.onBlockQueryYes = {
                                gameManager.tryRestartGame()
                            }
                            gameManager.onBlockQueryNo = {
                            }
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
                        if (0 == gameManager.localPlayer) "玩家2" else "玩家1",
                        color = if (0 == gameManager.localPlayer) Color.Blue else Color.Red,
                        fontSize = 40.sp
                    )
                    Text(
                        text = " ${gameManager.alivePieces.groupBy { it.camp }[gameManager.players[(gameManager.localPlayer+1) % gameManager.players.size]]?.size ?: 0}"
                                + if (gameManager.localPlayer != gameManager.currentPlayer) "【走】" else "",
                        color = if (0 == gameManager.localPlayer) Color.Blue else Color.Red,
                        fontSize = 30.sp
                    )
                }
            }

            //象棋游戏主容器
            Row(
                modifier = Modifier
                    //.fillMaxSize()
                    .graphicsLayer {
                        // 当前玩家不是玩家1，则垂直对角翻转180度
                        if (0 != gameManager.localPlayer) {
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
                                gameManager.handleTap(offset)
                            }
                        }
                ) {
                    //绘制棋盘
                    gameManager.chessBoard.initialize(size)
                    gameManager.chessBoard.draw(this, imageLoader = viewModel.imageLoader)

                    // 绘制棋子图片
                    for (piece in gameManager.alivePieces) {
                        //黑子的正面棋子需要旋转180度
                        val isRotate: Boolean = (piece.isFront && PieceCamp.Black == piece.camp)
                        piece.draw(
                            this,
                            imageLoader = viewModel.imageLoader,
                            borderLeft = gameManager.chessBoard.borderLeft,
                            borderTop = gameManager.chessBoard.borderTop,
                            cellWidth = gameManager.chessBoard.cellWidth,
                            cellHeight = gameManager.chessBoard.cellHeight,
                            isRotate = isRotate
                        )
                    }

                    // 绘制可到达位置提示格
                    gameManager.drawBox(
                        this,
                        imageLoader = viewModel.imageLoader,
                        borderLeft = gameManager.chessBoard.borderLeft,
                        borderTop = gameManager.chessBoard.borderTop,
                        cellWidth = gameManager.chessBoard.cellWidth,
                        cellHeight = gameManager.chessBoard.cellHeight
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
                        if (0 != gameManager.localPlayer) "玩家2" else "玩家1",
                        color = if (0 != gameManager.localPlayer) Color.Blue else Color.Red,
                        fontSize = 40.sp,
                    )
                    Text(
                        text = " ${gameManager.alivePieces.groupBy { it.camp }[gameManager.players[gameManager.localPlayer]]?.size ?: 0}"
                                + if (gameManager.localPlayer == gameManager.currentPlayer) "【走】" else "",
                        color = if (0 != gameManager.localPlayer) Color.Blue else Color.Red,
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
                            gameManager.blockQueryString = "是否真的要悔棋？"
                            gameManager.onBlockQueryYes = {
                                if (!gameManager.tryBackStep()) {
                                    Toast.makeText(current, "不能再悔棋了", Toast.LENGTH_LONG).show()
                                }
                            }
                            gameManager.onBlockQueryNo = {
                            }
                        }
                    ) {
                        Text(text = "悔棋")
                    }
                }
            }

            //不是本地时
            if (OnlineState.Local != onlineState) {
                //中间下部发送消息聊天框
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.Bottom
                ){
                    // 语音聊天按钮
                    VoiceChatButton(
                        isTalking = audioManager.isRecording,
                        onStart = {
                            audioManager.isTryRecording = true
                        },
                        onStop = {
                            audioManager.isTryRecording = false
                        },
                        onSetting = {
                            showAudioSettingDialog = true
                        }
                    )
                    //信息输入框
                    TextField(
                        value = tcpConnector?.messageToSend ?: "",
                        onValueChange = { tcpConnector?.messageToSend = it },
                        modifier = Modifier
                            .weight(1f)  // 分配剩余空间
                            .padding(4.dp),
                        label = { Text("Enter Message") },
                        singleLine = false  // 允许内容换行
                    )
                    Button(onClick = {
                        showMyChatBubble = tcpConnector?.messageToSend ?: ""
                        tcpConnector?.send("chatMessage: ${tcpConnector.messageToSend}", current)
                    }) {
                        Text("发送")
                    }
                }
            }
        }
    }

    // 将状态栏配色设为当前背景配色
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

@Composable
fun DrawMain(viewModel: GameViewModel) {
    ChessBoard(viewModel)
}

