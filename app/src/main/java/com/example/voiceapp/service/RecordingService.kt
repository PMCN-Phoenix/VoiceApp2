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

class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.voiceapp.action.STOP_RECORDING"
        private const val TAG = "RecordingService"
    }

    // ---------- 新增：音频组件 ----------
    private lateinit var audioCapture: AudioCaptureManager
    private lateinit var vad: VoiceActivityDetector

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initAudioComponents()  // 新增：初始化音频采集和 VAD
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()      // 改为调用停止录音方法
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        startRecording()            // 新增：启动录音
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- 新增：音频组件初始化 ----------
    private fun initAudioComponents() {
        audioCapture = AudioCaptureManager()
        vad = VoiceActivityDetector()

        // VAD 回调
        vad.onSpeechStart = {
            Log.d(TAG, "语音开始")
        }
        vad.onSpeechEnd = { pcmSegment ->
            Log.d(TAG, "语音结束，段长度: ${pcmSegment.size} 字节")
            // TODO: 后续任务包将把 pcmSegment 发送给云端 ASR
        }

        // 音频帧回调 → 送给 VAD 处理
        audioCapture.onAudioFrame = { frame ->
            vad.processFrame(frame)
        }

        audioCapture.onError = { error ->
            Log.e(TAG, "音频采集错误: $error")
        }
    }

    // ---------- 新增：启动/停止录音方法 ----------
    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "没有录音权限，无法开始录音")
            // 实际项目中应在 MainActivity 引导用户授权
            return
        }
        audioCapture.start()
        Log.d(TAG, "录音服务已启动")
    }

    private fun stopRecording() {
        audioCapture.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "录音服务已停止")
    }

    // ---------- 通知相关（保持不变） ----------
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