package com.example.voiceapp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.voiceapp.BuildConfig
import com.example.voiceapp.MainActivity
import com.example.voiceapp.R
import com.example.voiceapp.audio.AudioCaptureManager
import com.example.voiceapp.audio.VoiceActivityDetector
import com.example.voiceapp.data.ConversationRepository
import com.example.voiceapp.network.BaiduWebSocketManager
import com.example.voiceapp.voiceprint.VoiceprintManager

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

    // 网络组件（百度 WebSocket 管理器）
    private lateinit var wsManager: BaiduWebSocketManager

    // 声纹组件
    private lateinit var voiceprintManager: VoiceprintManager

    // 当前语音段识别出的说话人 ID
    private var currentSpeakerId: String? = null

    // 数据库仓库
    private lateinit var repository: ConversationRepository
    // 当前对话 ID
    private var conversationId: String? = null

    // ✅ 新增：音频焦点相关
    private lateinit var audioManager: AudioManager
    private var isRecordingPaused = false
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        repository = ConversationRepository()
        conversationId = repository.ensureActiveConversation()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager  // ✅ 获取音频管理器
        initAudioComponents()
        initWebSocket()
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

    // ---------- 音频组件初始化 ----------
    private fun initAudioComponents() {
        audioCapture = AudioCaptureManager()
        vad = VoiceActivityDetector()

        // 初始化声纹管理器并预注册测试说话人
        voiceprintManager = VoiceprintManager()
        voiceprintManager.registerVoiceprint("person_alice")
        voiceprintManager.registerVoiceprint("person_bob")

        vad.onSpeechStart = {
            Log.d(TAG, "语音开始")
            wsManager.sendStart()
        }

        vad.onSpeechEnd = { pcmSegment ->
            Log.d(TAG, "语音结束，段长度: ${pcmSegment.size} 字节")
            wsManager.sendEnd()

            // 声纹识别
            val speakerId = voiceprintManager.identifySpeaker(pcmSegment)
            if (speakerId != null) {
                currentSpeakerId = speakerId
                Log.d(TAG, "识别到说话人: $speakerId")
            } else {
                // 创建临时说话人
                val tempId = "temp_${System.currentTimeMillis()}"
                Log.d(TAG, "创建临时说话人: $tempId")
                voiceprintManager.registerVoiceprint(tempId, pcmSegment)
                currentSpeakerId = tempId
            }
        }

        audioCapture.onAudioFrame = { frame ->
            vad.processFrame(frame)
            if (vad.isSpeaking()) {
                wsManager.sendAudioFrame(frame)
            }
        }

        audioCapture.onError = { error ->
            Log.e(TAG, "音频采集错误: $error")
        }
    }

    // ---------- WebSocket 初始化（直接使用 API Key）----------
    private fun initWebSocket() {
        // 从 BuildConfig 读取百度语音识别凭证
        val appId = BuildConfig.BAIDU_APP_ID
        val apiKey = BuildConfig.BAIDU_API_KEY

        // 直接创建百度 WebSocket 管理器（不再需要 access_token）
        wsManager = BaiduWebSocketManager(appId = appId, apiKey = apiKey)

        // 设置回调
        wsManager.onConnected = {
            Log.d(TAG, "百度 ASR 已连接")
        }

        wsManager.onTranscription = { text, isFinal ->
            val speaker = currentSpeakerId ?: "unknown"
            Log.d(TAG, "转写结果: $text (final=$isFinal, speaker=$speaker)")

            // 存储到数据库
            if (isFinal && text.isNotBlank()) {
                if (speaker.startsWith("temp_")) {
                    repository.ensureTemporarySpeaker(speaker)
                }
                conversationId?.let { cid ->
                    repository.insertMessage(
                        conversationId = cid,
                        speakerId = speaker,
                        content = text,
                        confidence = 0.95
                    )
                }
            }
        }

        wsManager.onError = { error ->
            Log.e(TAG, "百度 WebSocket 错误: $error")
        }

        // 连接百度 ASR 服务
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
        // ✅ 请求音频焦点
        if (!requestAudioFocus()) {
            Log.e(TAG, "无法获取音频焦点，录音可能受影响")
        }
        audioCapture.start()
        Log.d(TAG, "录音服务已启动")
    }
    private fun stopRecording() {
        audioCapture.stop()
        wsManager.disconnect()
        conversationId?.let { repository.finishConversation(it) }
        repository.debugPrintAllMessages()
        abandonAudioFocus()  // ✅ 释放音频焦点
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "录音服务已停止")
    }

    // ✅ 新增方法：请求音频焦点
    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(afChangeListener)
                .build()
            audioFocusRequest = focusRequest
            return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            // API < 26 使用旧方法
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    // ✅ 新增方法：释放音频焦点
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(afChangeListener)
        }
    }

     // ✅ 新增：音频焦点变化监听器
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "音频焦点永久丢失，停止录音")
                stopRecording()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "暂时失去焦点，暂停录音")
                if (!isRecordingPaused) {
                    isRecordingPaused = true
                    audioCapture.stop()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂降低音量，录音继续但可以降低增益，此处简单忽略
                Log.d(TAG, "短暂降低音量，继续录音")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isRecordingPaused) {
                    Log.d(TAG, "重新获得焦点，恢复录音")
                    isRecordingPaused = false
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        audioCapture.start()
                    }
                }
            }
        }
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