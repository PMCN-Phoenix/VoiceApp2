package com.example.voiceapp.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * ASR 通信协议定义
 */
object AsrProtocol {
    private val gson = Gson()

    // ---------- 客户端发送 ----------

    data class StartMessage(
        val type: String = "start",
        val sampleRate: Int = 16000,
        val encoding: String = "pcm_s16le",
        val channels: Int = 1
    )

    data class DataMessage(
        val type: String = "data",
        val audio: String  // Base64 编码的 PCM 数据
    )

    data class EndMessage(
        val type: String = "end"
    )

    // ---------- 服务端返回 ----------

    data class AsrResult(
        val type: String = "",
        val text: String = "",
        @SerializedName("is_final")
        val isFinal: Boolean = false,
        val confidence: Double = 0.0
    )

    // ---------- 序列化工具 ----------

    fun toJson(obj: Any): String = gson.toJson(obj)

    fun parseResult(json: String): AsrResult = gson.fromJson(json, AsrResult::class.java)
}