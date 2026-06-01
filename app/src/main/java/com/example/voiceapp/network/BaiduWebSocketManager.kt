package com.example.voiceapp.network

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class BaiduWebSocketManager(
    private val appId: String,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "BaiduWS"
        private const val SERVER_URL = "wss://vop.baidu.com/realtime_asr"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES)
        .build()

    private var webSocket: WebSocket? = null
    private var audioFrameCount = 0

    var onConnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onTranscription: ((text: String, isFinal: Boolean) -> Unit)? = null

    fun connect() {
        // 每次连接生成新的 sn
        val sn = UUID.randomUUID().toString()
        val url = "$SERVER_URL?sn=$sn"
        Log.d(TAG, "连接 URL: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "百度 ASR 连接成功")
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type", "")

                    when (type) {
                        "FIN_TEXT" -> {
                            val errNo = json.optInt("err_no", -1)
                            val result = json.optString("result", "")
                            if (errNo == 0 && result.isNotBlank()) {
                                onTranscription?.invoke(result, true)
                            }
                            // 百度在一个 FIN_TEXT 后会关闭连接，我们需要为下一句话准备新连接
                            Log.d(TAG, "收到最终结果，准备自动重连...")
                        }
                        "MID_TEXT" -> {
                            Log.d(TAG, "临时结果: ${json.optString("result")}")
                        }
                        "HEARTBEAT" -> { /* 忽略 */ }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析失败: ${e.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接关闭: code=$code, reason=$reason")
                // 服务器主动关闭后，自动重连以支持后续语音段
                Log.d(TAG, "准备自动重连...")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    connect()
                }, 500)  // 延迟 500ms 重连
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "连接失败: ${t.message}")
                onError?.invoke(t.message ?: "未知错误")
                // 失败后也自动重连
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    connect()
                }, 1000)
            }
        })
    }

    fun sendStart() {
        val msg = JSONObject().apply {
            put("type", "START")
            put("data", JSONObject().apply {
                put("appid", appId.toIntOrNull() ?: 0)
                put("appkey", apiKey)
                put("dev_pid", 15372)
                put("cuid", "android_voice_app")
                put("format", "pcm")
                put("sample", 16000)
            })
        }
        Log.d(TAG, "发送 START 帧")
        webSocket?.send(msg.toString())
    }

    fun sendAudioFrame(pcm: ByteArray) {
        audioFrameCount++
        if (audioFrameCount % 50 == 0) {
            Log.d(TAG, "已发送 $audioFrameCount 帧音频")
        }
        webSocket?.send(okio.ByteString.of(*pcm))
    }

    fun sendEnd() {
        Log.d(TAG, "发送 FINISH 帧，共 $audioFrameCount 帧")
        val msg = JSONObject().apply { put("type", "FINISH") }
        webSocket?.send(msg.toString())
        audioFrameCount = 0
    }

    fun disconnect() {
        webSocket?.close(1000, "客户端关闭")
    }
}