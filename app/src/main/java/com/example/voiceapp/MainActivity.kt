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
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startRecordingService()
            } else {
                Log.w(TAG, "录音权限被拒绝，无法启动录音服务")
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

        // 检查并请求录音权限，然后启动服务
        checkAndStartService()
    }

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 已有权限，直接启动
                    startRecordingService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                    // 用户之前拒绝过，可以在这里解释为什么需要录音权限
                    Log.d(TAG, "需要录音权限才能进行语音归档")
                    // 仍然发起请求
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                else -> {
                    // 首次请求
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        } else {
            // API < 23，安装时已授权
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