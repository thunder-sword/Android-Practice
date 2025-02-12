package com.example.mypractice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioChatManager(
    val sampleRate: Int = 16000 // 采样率（Hz）
) {
    //导入库
    companion object {
        init {
            System.loadLibrary("opus-lib")	//这里需要和你的JNI文件名一致
        }
    }

    // JNI 方法声明：建议在开始录音前初始化
    external fun initOpusEncoder(sampleRate: Int, channels: Int, application: Int): Int
    external fun initOpusDecoder(sampleRate: Int, channels: Int): Int
    external fun encodeAudio(pcmData: ShortArray): ByteArray
    external fun decodeAudio(opusData: ByteArray): ShortArray
    external fun destroyOpus()

    // 可在 startRecord() 方法中调用初始化（例如采用 OPUS_APPLICATION_VOIP，即应用场景为语音通信，值为2048）
    init {
        val encoderResult = initOpusEncoder(sampleRate, 1, 2048)
        val decoderResult = initOpusDecoder(sampleRate, 1)
        if (encoderResult != 0 || decoderResult != 0) {
            // 处理错误
            error("Opus 初始化失败: encoder=$encoderResult, decoder=$decoderResult")
        } else {
            println("Opus 初始化成功")
        }
    }


    //使用TCP还是UDP
    var isTCP by mutableStateOf(true)
    //是否是服务器
    var isServer : Boolean? = null
    //TCP网络状态
    var connectionStatus by mutableStateOf("Not Connected")
    //UCP网络状态
    var udpConnectionStatus by mutableStateOf("Not Connected")
    //TCP的serverSocket
    var serverSocket: ServerSocket? = null
    var serverAddresses by mutableStateOf("")
    //TCP连接标志
    var isListen by mutableStateOf(false)
    var isConnect by mutableStateOf(false)
    //TCP读写对象
    private var socket: Socket? = null
    private var writer: OutputStream? = null
    private var reader: InputStream? = null
    //TCP超时时间
    private var connectTimeoutMillis: Int = 5000 //毫秒
    private var timeoutMillis: Int = 1000 //毫秒

    // 录音帧大小（例如 320 个采样点，16位即 320*2 字节）
    private val frameSize = 320

    // 网络配置（在使用前请先调用 connect() 建立 UDP Socket）
    var ip by mutableStateOf("")
    var port by mutableStateOf("4400")
    var listenPort by mutableStateOf("4400")
    var clientAddresses by mutableStateOf("")

    // 状态参数
    var isRecording by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var isSendRecording by mutableStateOf(false)
    var isUDPConnect by mutableStateOf(false)
    var isPause by mutableStateOf(false)

    // 配置音频参数
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // 保存 AudioRecord 和 AudioTrack 的引用，方便后续停止和释放资源
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var speakerAudioTrack: AudioTrack? = null
    var isSpeaker by mutableStateOf(true)

    // UDP 发送与接收 Socket（connect() 中初始化）
    private var sendSocket: DatagramSocket? = null
    private var receiveSocket: DatagramSocket? = null

    //重连相关变量
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3 // 最大重连次数
    private val reconnectInterval = 2000L // 重连间隔时间（毫秒）
    var isReconnecting by mutableStateOf(false)


    //@Preview(showBackground = true)
    @Composable
    fun Panel(){
        val current = LocalContext.current

        //监听用户切换语音连接方式
        LaunchedEffect(isTCP){
            //切换时自动关闭录音
            isSendRecording = false
        }

        val scrollState1 = rememberScrollState()

        Box(
            Modifier
                .fillMaxWidth()
                //.heightIn(max = 600.dp) // 最大高度
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState1)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically  // 设置垂直居中对齐
                ) {
                    Switch(
                        checked = isTCP,
                        onCheckedChange = {
                            isTCP = it // 当状态改变时会自动更新
                        }
                    )
                    Text("TCP")
                    Switch(
                        checked = !isPause,
                        onCheckedChange = {
                            isPause = !it // 当状态改变时会自动更新
                        }
                    )
                    Text("播放音频")
                    Switch(
                        checked = isSpeaker,
                        onCheckedChange = {
                            isSpeaker = it // 当状态改变时会自动更新
                        }
                    )
                    Text("扬声器")
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    SelectionContainer {
                        if(isTCP) {
                            Text(connectionStatus, Modifier.padding(8.dp))
                        } else{
                            Text(udpConnectionStatus, Modifier.padding(8.dp))
                        }
                    }
                }

                //UDP和TCP Server需要监听
                if ((isTCP && (false != isServer)) || !isTCP) {
                    Text(text = "请输入语音服务器监听端口")
                    TextField(
                        value = listenPort,
                        onValueChange = { listenPort = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        label = { Text("Enter Port") }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically  // 设置垂直居中对齐
                    ) {
                        Button(
                            modifier = Modifier.padding(4.dp),
                            onClick = {
                                listen(isTCP)
                            }) {
                            Text("监听")
                        }
                        Button(
                            modifier = Modifier.padding(4.dp),
                            onClick = {
                                if (isTCP) {
                                    stopListen()
                                } else {
                                    isUDPConnect = false
                                    sendSocket?.close()
                                    sendSocket = null
                                }
                            }) {
                            Text("停止监听")
                        }
                    }
                }

                //如果是UDP或者不是TCP服务器需要输入连接地址
                if (!isTCP || true != isServer) {
                    Text(text = "请输入语音服务器地址")
                    TextField(
                        value = ip,
                        onValueChange = { ip = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        label = { Text("Enter IP Address") }
                    )

                    TextField(
                        value = port,
                        onValueChange = { port = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        label = { Text("Enter Port") }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically  // 设置垂直居中对齐
                    ) {
                        Button(
                            modifier = Modifier.padding(4.dp),
                            onClick = {
                                connect(isTCP)
                            }) {
                            Text("连接")
                        }
                        Button(
                            modifier = Modifier.padding(4.dp),
                            onClick = {
                                disConnect()
                            }) {
                            Text("停止连接")
                        }
                    }

                }

                Row(
                    verticalAlignment = Alignment.CenterVertically  // 设置垂直居中对齐
                ) {
                    Switch(
                        checked = isSendRecording,
                        onCheckedChange = {
                            if((isTCP && !isConnect) || (!isTCP && !isUDPConnect)){ //检查是否开启网络连接
                                Toast.makeText(current, "连接语音服务器后才能开启录音", Toast.LENGTH_LONG).show()
                            } else
                                isSendRecording = it // 当状态改变时会自动更新
                        }
                    )
                    Text("开启录音")
                }
            }
        }
    }

    //作用：监听端口
    fun listen(tcp: Boolean){
        val portNumber = listenPort.toIntOrNull() ?: return

        if(!tcp){ //UDP
            // 初始化接收端：绑定到指定端口
            receiveSocket = DatagramSocket(portNumber)
        }else { //TCP
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    serverSocket = ServerSocket(portNumber, 0)    //设置监听队列大小为0
                    serverAddresses = getLocalIPAddresses().joinToString("\n") { "$it:$portNumber" }

                    withContext(Dispatchers.Main) {
                        connectionStatus = "Server running on\n$serverAddresses"
                    }

                    isListen = true

                    socket = serverSocket!!.accept()
                    socket!!.soTimeout = timeoutMillis
                    writer = socket!!.getOutputStream()
                    reader = socket!!.getInputStream()

                    isListen = false

                    clientAddresses = "[${socket?.inetAddress?.hostAddress}]:${socket?.port}"

                    withContext(Dispatchers.Main) {
                        isConnect = true
                        connectionStatus = "Client connected from $clientAddresses"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isConnect = false
                        isListen = false
                        isSendRecording = false
                        connectionStatus = "Error: ${e.message}"
                    }
                }
            }
        }
    }

    //作用：停止监听端口
    fun stopListen(){
        isSendRecording = false //关闭录音
        isConnect = false
        isListen = false
        reader?.close()
        writer?.close()
        socket?.close()
        serverSocket?.close()
        receiveSocket?.close()
        connectionStatus = "have close listening."
        udpConnectionStatus = "have close listening."
    }

    //作用：重试连接
    fun startReconnect(onReconnectSuccess: (() -> Unit)? = null) {
        connectionStatus = "attempt at $reconnectAttempts"
        println("当前重连次数：$reconnectAttempts")
        if (reconnectAttempts >= maxReconnectAttempts) {
            connectionStatus = "已超过最大重连次数，可以手动连接"
            isReconnecting = false
            reconnectAttempts = 0
            return
        }

        isReconnecting = true
        reconnectAttempts++

        //TCP客户端
        if(true!=isServer) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withContext(Dispatchers.Main) {
                        connectionStatus = "Reconnecting... Attempt $reconnectAttempts"
                    }

                    if (connect(true)) {
                        isConnect = true
                        isReconnecting = false
                        reconnectAttempts = 0

                        withContext(Dispatchers.Main) {
                            connectionStatus = "Reconnected to ${ip}:${port}"
                            onReconnectSuccess?.invoke() // 调用回调
                        }
                    } else {
                        delay(reconnectInterval)
                        startReconnect(onReconnectSuccess)
                    }
                } catch (e: Exception) {
                    startReconnect(onReconnectSuccess)
                }
            }
        }
        if(false!=isServer){//TCP服务端
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withContext(Dispatchers.Main) {
                        connectionStatus = "Server running on\n$serverAddresses"
                    }

                    if (!isConnect) {
                        isListen = true

                        socket = serverSocket!!.accept()
                        socket!!.soTimeout = timeoutMillis
                        writer = socket!!.getOutputStream()
                        reader = socket!!.getInputStream()

                        isListen = false

                        clientAddresses = "[${socket?.inetAddress?.hostAddress}]:${socket?.port}"

                        withContext(Dispatchers.Main) {
                            isConnect = true
                            connectionStatus = "Client reconnected from $clientAddresses"
                            onReconnectSuccess?.invoke() // 调用回调
                        }

                    } else {
                        delay(reconnectInterval)
                        startReconnect(onReconnectSuccess)
                    }
                } catch (e: Exception) {
                    startReconnect(onReconnectSuccess)
                }
            }
        }
    }

    /**
     * 建立 UDP 连接，初始化发送与接收 Socket
     * 调用此函数后，录音时发送的数据才会真正通过网络发送出去，
     * 播放时也可以从网络接收数据。
     */
    fun connect(tcp: Boolean): Boolean {
        val portNumber = listenPort.toIntOrNull()!!
        println("正在尝试连接$ip:$portNumber，是否TCP：$isTCP")
        try {
            if(!tcp) {//UDP
                // 初始化发送端（系统会随机分配本地端口）
                sendSocket = DatagramSocket()
                udpConnectionStatus = "UDP has connected"
                isUDPConnect = true
                println("UDP Socket 已初始化：发送端 ${sendSocket?.localPort}，接收端绑定在端口 $portNumber")
            }
            else{//TCP
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withContext(Dispatchers.Main) {
                            isConnect = false
                            connectionStatus = "Try connecting to ${ip}:$portNumber"
                        }

                        socket = Socket()
                        socket!!.connect(InetSocketAddress(ip, portNumber), connectTimeoutMillis)
                        //设置读取发送超时时间
                        socket!!.soTimeout = timeoutMillis
                        writer = socket!!.getOutputStream()
                        reader = socket!!.getInputStream()

                        withContext(Dispatchers.Main) {
                            isConnect = true
                            connectionStatus = "Connected to ${ip}:$portNumber"
                            println("TCP Socket 已初始化：发送端 ${socket?.localPort}，接收端绑定在端口 $portNumber")
                        }
                    } catch (e: ConnectException){
                        withContext(Dispatchers.Main) {
                            isConnect = false
                            isSendRecording = false
                            connectionStatus = "Failed to connected to ${ip}:$portNumber"
                            println("TCP Socket连接失败")
                        }
                    } catch (e: SocketTimeoutException){
                        withContext(Dispatchers.Main) {
                            isConnect = false
                            isSendRecording = false
                            connectionStatus = "Timeout failed to connected to ${ip}:$portNumber"
                            println("TCP Socket连接超时")
                        }
                    } catch (e: Exception){
                        withContext(Dispatchers.Main) {
                            isConnect = false
                            isSendRecording = false
                            connectionStatus = "Error: $e"
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    //作用：停止连接
    fun disConnect(){
        isSendRecording = false //关闭录音
        isConnect = false
        connectionStatus = "Connection is closed."
        udpConnectionStatus = "Connection is closed."
        writer?.close()
        reader?.close()
        socket?.close()
        sendSocket?.close()
        receiveSocket?.close()
    }

    /**
     * 开始本地录音（需要在 Compose 中调用以获取 LocalContext）
     * 录音过程中每采集一段数据就调用 sendAudioData() 将数据发送出去
     */
    fun startRecord(context: Context) {
        // 检查录音权限
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "无法获取录音权限", Toast.LENGTH_LONG).show()
            return
        }

        // 初始化 AudioRecord
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        // 检查设备是否支持回音消除
        if (AcousticEchoCanceler.isAvailable()) {
            val echoCanceler = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
            echoCanceler?.enabled = true
            if (echoCanceler != null && echoCanceler.enabled) {
                println("AcousticEchoCanceler 启用成功")
            } else {
                println("AcousticEchoCanceler 启用失败")
            }
        } else {
            println("该设备不支持 AcousticEchoCanceler")
        }

        // 可选：启用噪声抑制
        if (NoiseSuppressor.isAvailable()) {
            val noiseSuppressor = NoiseSuppressor.create(audioRecord!!.audioSessionId)
            noiseSuppressor?.enabled = true
        }

        // 可选：启用自动增益控制
        if (AutomaticGainControl.isAvailable()) {
            val agc = AutomaticGainControl.create(audioRecord!!.audioSessionId)
            agc?.enabled = true
        }

        audioRecord?.startRecording()
        isRecording = true

        // 在协程中读取录音数据并发送
        CoroutineScope(Dispatchers.IO).launch {
            val frameByteSize = frameSize * 2   // 录音协程：每次确保读到完整一帧数据再发送
            val buffer = ByteArray(frameByteSize)
            while (isRecording) {
                var offset = 0
                while (offset < frameByteSize) {
                    val bytesRead = audioRecord?.read(buffer, offset, frameByteSize - offset) ?: 0
                    if (bytesRead <= 0) break
                    offset += bytesRead
                }
                //如果没开启录音则不发送
                if(!isSendRecording) continue
                if (offset == frameByteSize) {
                    // 发送完整一帧数据
                    val shortCount = frameByteSize / 2
                    val pcmShortArray = ShortArray(shortCount)
                    ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcmShortArray)
                    // 调用 native 方法进行编码
                    val encodedData = encodeAudio(pcmShortArray)
                    // 发送编码后的数据
                    sendAudioData(encodedData)
                }

//                if (bytesRead > 0) {
//                    // 复制有效数据并发送
//                    val dataToSend = buffer.copyOf(bytesRead)
//                    sendAudioData(dataToSend)
//                }
            }
            withContext(NonCancellable) {
                try {
                    audioRecord?.stop()
                } catch (e: Exception) {
                    //e.printStackTrace()
                }
                audioRecord?.release()
            }
            audioRecord?.release()
        }
    }

    /**
     * 停止录音
     */
    fun stopRecord() {
        isSendRecording = false
        isRecording = false
    }

    /**
     * 开始播放接收到的音频数据
     * 播放时会不断调用 receiveFromNetwork() 从 UDP Socket 获取数据，并写入 AudioTrack 播放
     */
    fun startAudioPlay() {
        // 初始化 AudioTrack
        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) //使用USAGE_VOICE_COMMUNICATION就是听筒，使用USAGE_MEDIA是扬声器
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        speakerAudioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA) //使用USAGE_VOICE_COMMUNICATION就是听筒，使用USAGE_MEDIA是扬声器
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack?.play()
        speakerAudioTrack?.play()
        isPlaying = true

        // 启动后台协程不断接收数据并播放
        CoroutineScope(Dispatchers.IO).launch {
            while (isPlaying) {
                if(isPause) continue
                val receivedData = receiveFromNetwork(true)        //TCP读取数据线程
                //println("tcp-receivedData: $receivedData")
                if (receivedData != null && receivedData.isNotEmpty()) {
                    try {
                        // 解码得到 PCM 数据（short[]）
                        val decodedPCM = decodeAudio(receivedData)
                        // 将 short[] 转为 byte[]（AudioTrack 接受 byte[] 数据）
                        val byteBuffer =
                            ByteBuffer.allocate(decodedPCM.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        decodedPCM.forEach { byteBuffer.putShort(it) }
                        val pcmByteArray = byteBuffer.array()
                        if (isSpeaker)
                            speakerAudioTrack?.write(pcmByteArray, 0, pcmByteArray.size)
                        else
                            audioTrack?.write(pcmByteArray, 0, pcmByteArray.size)
                    } catch (e: Exception){
                        e.printStackTrace()
                    }
//                    if(isSpeaker)
//                        speakerAudioTrack?.write(receivedData, 0, receivedData.size)
//                    else
//                        audioTrack?.write(receivedData, 0, receivedData.size)
                }
            }
            try {
                audioTrack?.stop()
                speakerAudioTrack?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            audioTrack?.release()
            speakerAudioTrack?.release()
        }

        // 同时启动UDP读取线程
        CoroutineScope(Dispatchers.IO).launch {
            while (isPlaying) {
                if(isPause) continue
                val receivedData = receiveFromNetwork(false)        //UDP读取数据线程
                if (receivedData != null && receivedData.isNotEmpty()) {
                    try {
                        // 解码得到 PCM 数据（short[]）
                        val decodedPCM = decodeAudio(receivedData)
                        // 将 short[] 转为 byte[]（AudioTrack 接受 byte[] 数据）
                        val byteBuffer =
                            ByteBuffer.allocate(decodedPCM.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        decodedPCM.forEach { byteBuffer.putShort(it) }
                        val pcmByteArray = byteBuffer.array()
                        if (isSpeaker)
                            speakerAudioTrack?.write(pcmByteArray, 0, pcmByteArray.size)
                        else
                            audioTrack?.write(pcmByteArray, 0, pcmByteArray.size)
                    } catch (e: Exception){
                        e.printStackTrace()
                    }
//                    if(isSpeaker)
//                        speakerAudioTrack?.write(receivedData, 0, receivedData.size)
//                    else
//                        audioTrack?.write(receivedData, 0, receivedData.size)
                }
            }
            try {
                audioTrack?.stop()
                speakerAudioTrack?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            audioTrack?.release()
            speakerAudioTrack?.release()
        }
    }

    /**
     * 停止播放
     */
    fun stopAudioPlay() {
        isPlaying = false
    }

    /**
     * 将录音数据通过 UDP 发送出去
     * 如果需要，可在此处对数据进行编码（例如使用 Opus 编码）后再发送
     * 要求：在调用 startRecord() 前必须调用 connect() 初始化 sendSocket
     */
    private fun sendAudioData(data: ByteArray) {
        if(!isTCP) {//UDP
            if (sendSocket == null) {
                println("sendSocket 尚未初始化，请先调用 connect()")
                return
            }
            if (ip.isEmpty()) {
                println("目标 IP 为空，无法发送数据")
                return
            }
            try {
                // 内层封装：添加 2 字节长度头
                val innerHeader = ByteArray(2)
                innerHeader[0] = ((data.size shr 8) and 0xFF).toByte()
                innerHeader[1] = (data.size and 0xFF).toByte()
                val packetData = innerHeader + data
                // 将 ip 与 port 转为目标地址
                val portNumber = port.toIntOrNull()!!
                val address = InetAddress.getByName(ip)
                val packet = DatagramPacket(packetData, packetData.size, address, portNumber)
                sendSocket?.send(packet)
                //println("发送音频数据，大小: ${data.size}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        else{//TCP
            if (!isConnect || writer == null) {
                println("当前未连接，无法发送消息")
                isSendRecording = false
                connectionStatus = "Connection closed."
                //断连之后自动重试连接
                startReconnect { }
                return
            }
            try{
                // 内层封装
                val innerHeader = ByteArray(2)
                innerHeader[0] = ((data.size shr 8) and 0xFF).toByte()
                innerHeader[1] = (data.size and 0xFF).toByte()
                val innerPacket = innerHeader + data
                // 外层封装：添加整个内层包的长度头
                val outerHeader = ByteArray(2)
                outerHeader[0] = ((innerPacket.size shr 8) and 0xFF).toByte()
                outerHeader[1] = (innerPacket.size and 0xFF).toByte()
                writer?.write(outerHeader)
                writer?.write(innerPacket)
                writer?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
                isConnect = false
                isSendRecording = false
                connectionStatus = "Connection closed."
                //断连之后自动重试连接
                startReconnect { }
            }
        }
    }

    /**
     * 读取 TCP 数据包：
     * 先读取外层 2 字节长度头，再读取对应长度的内层包（格式：[内层长度头 + 数据]）
     */
    private fun readTcpData(): ByteArray? {
        try {
            val outerHeader = ByteArray(2)
            val headerBytes = reader?.read(outerHeader) ?: return null
            //println("headerBytes: $headerBytes")
            if (headerBytes != 2) return null
            val outerLength = ((outerHeader[0].toInt() and 0xFF) shl 8) or (outerHeader[1].toInt() and 0xFF)
            //println("头部长度：$outerLength")
            val innerPacket = ByteArray(outerLength)
            var offset = 0
            while (offset < outerLength) {
                val read = reader?.read(innerPacket, offset, outerLength - offset) ?: 0
                if (read <= 0) break
                offset += read
            }
            //println("innerPacket: $innerPacket")
            return if (offset == outerLength) innerPacket else null
        } catch (e: Exception) {
            return null
        }
    }

    // 读取 UDP 数据包
    private fun readUdpData(): ByteArray? {
        try {
            if (receiveSocket == null) return null
            val buffer = ByteArray(bufferSize)
            val packet = DatagramPacket(buffer, buffer.size)
            receiveSocket?.receive(packet)
            return packet.data.copyOf(packet.length)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 根据协议接收数据，并解析内层 2 字节长度头获得实际数据内容
     */
    private fun receiveFromNetwork(tcp: Boolean): ByteArray? {
        val rawData = if (tcp) readTcpData() else readUdpData()
        return rawData?.let {
            if (it.size < 2) return null
            val innerLength = ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF)
            if (it.size < 2 + innerLength) return null
            it.copyOfRange(2, 2 + innerLength)
        }
    }

    fun onDestroy() {
        destroyOpus()  // 对应 JNI 中的释放方法
        // 停止录音
        isRecording = false
        isSendRecording = false
        audioRecord?.let { try { it.stop() } catch (e: Exception) {} ; it.release() }
        audioRecord = null

        // 停止播放
        isPlaying = false
        audioTrack?.let { try { it.stop() } catch (e: Exception) {} ; it.release() }
        speakerAudioTrack?.let { try { it.stop() } catch (e: Exception) {} ; it.release() }
        audioTrack = null
        speakerAudioTrack = null

        // 关闭 UDP Socket
        sendSocket?.close()
        sendSocket = null

        receiveSocket?.close()
        receiveSocket = null

        isConnect = false
        isListen = false
        reader?.close()
        writer?.close()
        socket?.close()
        serverSocket?.close()
    }
}
