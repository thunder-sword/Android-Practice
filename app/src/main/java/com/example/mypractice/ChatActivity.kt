package com.example.mypractice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mypractice.ui.theme.MyPracticeTheme
import com.example.mypractice.utils.BaseComponentActivity

class ChatActivity : BaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyPracticeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    ChatMain()
                }
            }
        }
    }
}

@Preview
@Composable
fun ChatMain(){
    val current = LocalContext.current
    //语音器
    val audioManager: AudioChatManager = remember { AudioChatManager() }
    // 保存是否已经获取了录音和蓝牙权限的状态
    var hasRecordAndBluetoothPermission by rememberSaveable { mutableStateOf(
        ActivityCompat.checkSelfPermission(
            current,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED &&
                // 对于 Android 12 及以上，需要检查 BLUETOOTH_CONNECT 权限，否则默认认为已获得权限
                (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    ActivityCompat.checkSelfPermission(
                        current,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                else true)
    ) }
    // 创建一个请求多个权限的 launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val bluetoothGranted =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
            } else {
                true
            }
        hasRecordAndBluetoothPermission = recordGranted && bluetoothGranted

        if (hasRecordAndBluetoothPermission) {
            // 用户同意所有权限后启动录音
            audioManager.startRecord(current)
        } else {
            Toast.makeText(current, "必须授予录音和蓝牙权限才能使用通话功能", Toast.LENGTH_SHORT).show()
        }
    }
    // 开始执行一次
    LaunchedEffect(Unit) {
        //自动播放远端音频
        audioManager.startAudioPlay()
        //自动申请权限并录音
        // 如果还没有权限，则申请权限
        if (!hasRecordAndBluetoothPermission) {
            val permissionsToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(permissionsToRequest)
        }else {
            // 权限已具备，则直接启动录音
            audioManager.startRecord(current)
        }
    }

    // 监听生命周期，在 onDestroy 时清理
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
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

    audioManager.Panel()
}