package com.example.voiceapp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.voiceapp.MainActivity
import com.example.voiceapp.R
import com.example.voiceapp.audio.AudioCaptureManager
import com.example.voiceapp.audio.VoiceActivityDetector
import com.example.voiceapp.network.WebSocketManager

class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.voiceapp.action.STOP_RECORDING"
        private const val TAG = "RecordingService"
    }

    // 音频组件
    private lateinit var audioCapture: AudioCaptureManager
    private lateinit var vad: VoiceActivityDetector

    // 网络组件（新增）
    private lateinit var wsManager: WebSocketManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initAudioComponents()
        initWebSocket()  // 新增：初始化 WebSocket 并连接
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        startRecording()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- 音频组件初始化（修改）----------
    private fun initAudioComponents() {
        audioCapture = AudioCaptureManager()
        vad = VoiceActivityDetector()

        vad.onSpeechStart = {
            Log.d(TAG, "语音开始")
            wsManager.sendStart()  // 新增：通知 ASR 新语音段开始
        }

        vad.onSpeechEnd = { pcmSegment ->
            Log.d(TAG, "语音结束，段长度: ${pcmSegment.size} 字节")
            wsManager.sendEnd()    // 新增：通知 ASR 语音段结束
        }

        audioCapture.onAudioFrame = { frame ->
            vad.processFrame(frame)
            // 新增：如果正在语音中，实时发送音频帧
            if (vad.isSpeaking()) {
                wsManager.sendAudioFrame(frame)
            }
        }

        audioCapture.onError = { error ->
            Log.e(TAG, "音频采集错误: $error")
        }
    }

    // ---------- WebSocket 初始化（新增）----------
    private fun initWebSocket() {
        //wsManager = WebSocketManager()  // 可传入自定义 URL 或 Token
        //wsManager = WebSocketManager(serverUrl = "ws://10.0.2.2:8080")
        //wsManager = WebSocketManager(serverUrl = "ws://localhost:8080")
        wsManager = WebSocketManager(serverUrl = "ws://10.213.205.71:8080")

        wsManager.onConnected = {
            Log.d(TAG, "ASR 服务已连接")
        }

        wsManager.onTranscription = { text, isFinal ->
            Log.d(TAG, "转写结果: $text (final=$isFinal)")
            // TODO: 后续任务包将把转写结果存入数据库并显示
        }

        wsManager.onError = { error ->
            Log.e(TAG, "WebSocket 错误: $error")
        }

        wsManager.onDisconnected = { reason ->
            Log.w(TAG, "WebSocket 断开: $reason")
        }

        wsManager.connect()
    }

    // ---------- 启动/停止录音方法 ----------
    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "没有录音权限，无法开始录音")
            return
        }
        audioCapture.start()
        Log.d(TAG, "录音服务已启动")
    }

    private fun stopRecording() {
        audioCapture.stop()
        wsManager.disconnect()  // 新增：断开 WebSocket 连接
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "录音服务已停止")
    }

    // ---------- 通知相关（保持不变）----------
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "录音状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "持续录音服务通知"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音归档运行中")
            .setContentText("正在监听对话...")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_mic, "停止", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}