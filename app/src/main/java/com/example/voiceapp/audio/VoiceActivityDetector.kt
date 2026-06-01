package com.example.voiceapp.audio

import kotlin.math.sqrt

class VoiceActivityDetector(
    private val sampleRate: Int = 16000,
    private val frameSize: Int = 320,           // 20ms @ 16kHz
    private val energyThreshold: Double = 150.0, // 初始阈值（自适应启动后会被覆盖）
    private val silenceFrames: Int = 8,          // 连续静音帧数（80ms）
    private val adaptiveEnabled: Boolean = true, // 是否启用自适应阈值
    private val noiseEstimateFrames: Int = 50    // 用于估计底噪的初始静音帧数（1秒）
) {
    companion object {
        private const val TAG = "VAD"
        private const val DEFAULT_ALPHA = 10.0    // 阈值系数：语音能量需大于底噪的 alpha 倍
    }

    private var isSpeech = false
    private var silenceCount = 0
    private val speechBuffer = mutableListOf<ByteArray>()

    // 自适应相关
    private var noiseFloor: Double = energyThreshold / DEFAULT_ALPHA // 初始底噪估计
    private var isNoiseCalibrated = false
    private val noiseBuffer = mutableListOf<Double>() // 用于初始校准的底噪能量值
    private var actualThreshold: Double = energyThreshold

    var onSpeechStart: (() -> Unit)? = null
    var onSpeechEnd: ((ByteArray) -> Unit)? = null

    /**
     * 处理一帧 PCM 数据
     */
    fun processFrame(frame: ByteArray) {
        val energy = calculateEnergy(frame)

        // 自适应阈值：估计底噪
        if (adaptiveEnabled && !isNoiseCalibrated) {
            noiseBuffer.add(energy)
            if (noiseBuffer.size >= noiseEstimateFrames) {
                // 使用中位数作为底噪估计（比均值更抗干扰）
                val sorted = noiseBuffer.sorted()
                noiseFloor = sorted[sorted.size / 2]
                // 若中位数太小（死寂环境），给予一个最小值，避免阈值过低
                if (noiseFloor < 1.0) noiseFloor = 1.0
                actualThreshold = noiseFloor * DEFAULT_ALPHA
                isNoiseCalibrated = true
                noiseBuffer.clear() // 释放内存
            }
            // 校准期间不进行语音判断，直接返回
            return
        }

        // 使用 actualThreshold 进行 VAD 判断
        if (energy > actualThreshold) {
            silenceCount = 0
            if (!isSpeech) {
                isSpeech = true
                speechBuffer.clear()
                onSpeechStart?.invoke()
            }
            speechBuffer.add(frame)
        } else if (isSpeech) {
            silenceCount++
            speechBuffer.add(frame)
            if (silenceCount >= silenceFrames) {
                isSpeech = false
                val segment = mergeByteArrays(speechBuffer)
                speechBuffer.clear()
                onSpeechEnd?.invoke(segment)
            }
        }
    }

    fun isSpeaking(): Boolean = isSpeech

    /**
     * 手动重新校准底噪（例如用户切换环境时调用）
     */
    fun recalibrate() {
        isNoiseCalibrated = false
        noiseBuffer.clear()
    }

    /**
     * 获取当前实际使用的阈值（调试用）
     */
    fun getCurrentThreshold(): Double = actualThreshold

    private fun calculateEnergy(frame: ByteArray): Double {
        var sum = 0.0
        for (i in 0 until frame.size - 1 step 2) {
            val sample = ((frame[i + 1].toInt() shl 8) or (frame[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        return sum / (frame.size / 2)
    }

    private fun mergeByteArrays(arrays: List<ByteArray>): ByteArray {
        val totalSize = arrays.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (array in arrays) {
            System.arraycopy(array, 0, result, offset, array.size)
            offset += array.size
        }
        return result
    }
}