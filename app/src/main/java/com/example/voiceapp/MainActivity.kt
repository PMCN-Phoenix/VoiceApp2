package com.example.voiceapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.voiceapp.service.RecordingService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // 录音权限请求器
    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // 录音权限已允许，继续请求通知权限（如果需要）
                requestNotificationPermissionIfNeeded()
            } else {
                Log.w(TAG, "录音权限被拒绝，无法启动录音服务")
            }
        }

    // 通知权限请求器（Android 13+）
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // 通知权限已允许，所有必要权限齐，启动服务
                startRecordingService()
            } else {
                Log.w(TAG, "通知权限被拒绝，前台服务通知可能无法显示")
                // 即使没有通知权限，也启动服务（但通知不显示）
                startRecordingService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 检查并请求权限，然后启动服务
        checkAndStartService()
    }

    private fun checkAndStartService() {
        // 第一步：检查/请求录音权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 已有录音权限，继续请求通知权限
                    requestNotificationPermissionIfNeeded()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                    Log.d(TAG, "需要录音权限才能进行语音归档")
                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                else -> {
                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        } else {
            // API < 23，权限已预授权
            requestNotificationPermissionIfNeeded()
        }
    }

    /**
     * 如果运行在 Android 13+，请求通知权限
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 已有通知权限，直接启动服务
                    startRecordingService()
                }
                else -> {
                    // 请求通知权限
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 12 及以下不需要动态请求通知权限
            startRecordingService()
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, RecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d(TAG, "录音服务启动指令已发送")
    }
}