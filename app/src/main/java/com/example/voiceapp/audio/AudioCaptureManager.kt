package com.example.voiceapp.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * 音频采集管理器：使用 AudioRecord 从麦克风实时读取 PCM 数据。
 * 通过回调输出音频帧和错误信息。
 *
 * 帧大小默认 5120 字节（16kHz, 16bit, 单声道下对应 160ms），
 * 符合百度实时语音识别最佳实践。
 */
class AudioCaptureManager(
    private val sampleRate: Int = 16000,      // 采样率 16kHz（ASR 标准）
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val bufferSizeInBytes: Int = 5120  // 160ms = 5120 bytes
) {
    companion object {
        private const val TAG = "AudioCapture"
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onAudioFrame: ((ByteArray) -> Unit)? = null   // 回调：音频帧
    var onError: ((String) -> Unit)? = null

    /**
     * 开始采集
     */
    fun start() {
        if (audioRecord != null) return  // 防止重复启动

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeInBytes
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord 初始化失败")
                }
                record.startRecording()
                Log.d(TAG, "录音已开始，bufferSize=$bufferSizeInBytes")
            }
        } catch (e: SecurityException) {
            onError?.invoke("缺少录音权限")
            return
        } catch (e: Exception) {
            onError?.invoke("AudioRecord 异常: ${e.message}")
            return
        }

        // 在协程中循环读取
        captureJob = scope.launch {
            val buffer = ByteArray(bufferSizeInBytes)
            while (isActive) {
                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (readBytes > 0) {
                    // 复制出实际读取的字节
                    val frame = buffer.copyOf(readBytes)
                    onAudioFrame?.invoke(frame)
                } else if (readBytes == AudioRecord.ERROR_INVALID_OPERATION) {
                    onError?.invoke("AudioRecord ERROR_INVALID_OPERATION")
                    break
                } else if (readBytes == AudioRecord.ERROR_BAD_VALUE) {
                    onError?.invoke("AudioRecord ERROR_BAD_VALUE")
                    break
                }
                // readBytes == 0 可忽略
            }
            Log.d(TAG, "录音循环退出")
        }
    }

    /**
     * 停止采集并释放资源
     */
    fun stop() {
        captureJob?.cancel()
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        Log.d(TAG, "录音已停止")
    }
}