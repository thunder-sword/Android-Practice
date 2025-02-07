package com.example.mypractice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UDPAudioChat(
    val sampleRate: Int = 16000 // 采样率（Hz）
) {
    // 网络配置（在使用前请先调用 connect() 建立 UDP Socket）
    var ip by mutableStateOf("")
    var port by mutableStateOf("4399")
    var listenPort by mutableStateOf("4399")

    // 状态参数
    var isRecording by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)

    // 配置音频参数
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // 保存 AudioRecord 和 AudioTrack 的引用，方便后续停止和释放资源
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    // UDP 发送与接收 Socket（connect() 中初始化）
    private var sendSocket: DatagramSocket? = null
    private var receiveSocket: DatagramSocket? = null

    /**
     * 建立 UDP 连接，初始化发送与接收 Socket
     * 调用此函数后，录音时发送的数据才会真正通过网络发送出去，
     * 播放时也可以从网络接收数据。
     */
    fun connect() {
        val portNumber = listenPort.toIntOrNull()!!
        try {
            // 初始化发送端（系统会随机分配本地端口）
            sendSocket = DatagramSocket()
            // 初始化接收端：绑定到指定端口
            receiveSocket = DatagramSocket(portNumber)
            println("UDP Socket 已初始化：发送端 ${sendSocket?.localPort}，接收端绑定在端口 $portNumber")
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

        audioRecord?.startRecording()
        isRecording = true

        // 在协程中读取录音数据并发送
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    // 复制有效数据并发送
                    val dataToSend = buffer.copyOf(bytesRead)
                    sendAudioData(dataToSend)
                }
            }
            audioRecord?.stop()
            audioRecord?.release()
        }
    }

    /**
     * 停止录音
     */
    fun stopRecord() {
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
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
        isPlaying = true

        // 启动后台协程不断接收数据并播放
        CoroutineScope(Dispatchers.IO).launch {
            while (isPlaying) {
                val receivedData = receiveFromNetwork()
                if (receivedData != null && receivedData.isNotEmpty()) {
                    audioTrack?.write(receivedData, 0, receivedData.size)
                }
            }
            audioTrack?.stop()
            audioTrack?.release()
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
        if (sendSocket == null) {
            println("sendSocket 尚未初始化，请先调用 connect()")
            return
        }
        if (ip.isEmpty()) {
            println("目标 IP 为空，无法发送数据")
            return
        }
        try {
            // 将 ip 与 port 转为目标地址
            val portNumber = port.toIntOrNull()!!
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(data, data.size, address, portNumber)
            sendSocket?.send(packet)
            println("发送音频数据，大小: ${data.size}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 从网络接收数据，并返回接收到的音频数据（原始 PCM 数据）
     * 如果需要，可在此处对数据进行解码
     * 要求：在调用 startAudioPlay() 前必须调用 connect() 初始化 receiveSocket
     */
    private fun receiveFromNetwork(): ByteArray? {
        if (receiveSocket == null) {
            println("receiveSocket 尚未初始化，请先调用 connect()")
            return null
        }
        return try {
            val buffer = ByteArray(bufferSize)
            val packet = DatagramPacket(buffer, buffer.size)
            // 此方法会阻塞直到数据到达
            receiveSocket?.receive(packet)
            // 调用数据处理函数（例如解码），这里示例直接调用 handleReceivedData
            handleReceivedData(packet.data, packet.length)
            // 返回真正接收到的数据（去除缓冲区中无效部分）
            packet.data.copyOf(packet.length)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 处理接收到的数据，例如进行解码或记录日志
     * 如果数据经过编码（例如 Opus），可在此处进行解码后返回 PCM 数据
     * 当前示例假定数据为原始 PCM，故仅打印日志。
     *
     * @param data   接收到的完整数据缓冲区
     * @param length 有效数据长度
     */
    private fun handleReceivedData(data: ByteArray, length: Int) {
        // TODO：如果发送端对录音数据进行了编码，则在此处对数据进行解码
        println("接收到音频数据，长度: $length")
        // 这里可以将数据放入缓冲队列，或直接返回给播放线程
    }

    fun onDestroy() {
        // 停止录音
        isRecording = false
        audioRecord?.let { record ->
            try {
                record.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            record.release()
        }
        audioRecord = null

        // 停止播放
        isPlaying = false
        audioTrack?.let { track ->
            try {
                track.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            track.release()
        }
        audioTrack = null

        // 关闭 UDP Socket
        sendSocket?.close()
        sendSocket = null

        receiveSocket?.close()
        receiveSocket = null

        // 如果有后台协程，也需要取消（假设你有一个scope）
        // scope.cancel()
    }
}

/**
 * 请求录音和网络权限的 Composable
 * 使用 ActivityResult API 请求 RECORD_AUDIO 和 BLUETOOTH 权限
 */
@Composable
fun QueryAudioPermissions() {
    val context = LocalContext.current
    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 遍历权限结果，若有权限被拒绝则进行提示
        permissions.forEach { (permission, granted) ->
            if (!granted) {
                Toast.makeText(context, "权限 $permission 被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 使用 LaunchedEffect 在首次组合时启动权限请求
    LaunchedEffect(Unit) {
        requestPermissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH
            )
        )
    }
}
