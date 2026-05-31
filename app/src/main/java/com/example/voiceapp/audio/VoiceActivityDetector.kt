package com.example.voiceapp.audio

import kotlin.math.sqrt

class VoiceActivityDetector(
    private val sampleRate: Int = 16000,
    private val frameSize: Int = 320,
    private val energyThreshold: Double = 50.0,
    private val silenceFrames: Int = 15
) {
    companion object {
        private const val TAG = "VAD"
    }

    private var isSpeech = false
    private var silenceCount = 0
    private val speechBuffer = mutableListOf<ByteArray>()

    var onSpeechStart: (() -> Unit)? = null
    var onSpeechEnd: ((ByteArray) -> Unit)? = null

    fun processFrame(frame: ByteArray) {
        val energy = calculateEnergy(frame)

        if (energy > energyThreshold) {
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