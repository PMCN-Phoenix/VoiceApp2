package com.example.voiceapp.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val serverUrl: String = "wss://your-asr-service.com/v1/asr"  // 替换为实际 ASR 地址
) {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES)     // 长连接，不超时
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 回调
    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((String) -> Unit)? = null
    var onTranscription: ((text: String, isFinal: Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * 建立 WebSocket 连接
     */
    fun connect() {
        if (isConnected) return

        val request = Request.Builder()
            .url(serverUrl)
            //.addHeader("Authorization", "Bearer your-api-token")  // 替换为实际 Token
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d(TAG, "WebSocket 已连接")
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到消息: $text")
                try {
                    val result = AsrProtocol.parseResult(text)
                    if (result.type == "result") {
                        onTranscription?.invoke(result.text, result.isFinal)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析 ASR 结果失败: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 正在关闭: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "WebSocket 已关闭: $reason")
                onDisconnected?.invoke(reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "WebSocket 连接失败: ${t.message}")
                onError?.invoke(t.message ?: "未知错误")
                scheduleReconnect()
            }
        })
    }

    /**
     * 发送语音段的开始标记
     */
    fun sendStart() {
        val message = AsrProtocol.toJson(AsrProtocol.StartMessage())
        webSocket?.send(message)
    }

    /**
     * 发送一帧 PCM 数据（Base64 编码）
     */
    fun sendAudioFrame(pcmData: ByteArray) {
        val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val message = AsrProtocol.toJson(AsrProtocol.DataMessage(audio = base64))
        webSocket?.send(message)
    }

    /**
     * 发送语音段的结束标记
     */
    fun sendEnd() {
        val message = AsrProtocol.toJson(AsrProtocol.EndMessage())
        webSocket?.send(message)
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        webSocket?.close(1000, "客户端主动断开")
        webSocket = null
        isConnected = false
    }

    /**
     * 断线重连
     */
    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!isConnected) {
                Log.d(TAG, "尝试重连...")
                connect()
            }
        }
    }
}