package com.example.voiceapp.voiceprint

import android.util.Log
import kotlin.random.Random

/**
 * 声纹管理器（原型模拟版）
 * 负责声纹注册、实时说话人识别。
 * 当前使用模拟特征向量，后续可替换为真实声纹算法。
 */
class VoiceprintManager {

    companion object {
        private const val TAG = "VoiceprintManager"
        private const val SIMILARITY_THRESHOLD = 0.8  // 模拟相似度阈值
    }

    // 已注册的声纹库：声纹ID -> 特征向量（模拟）
    private val voiceprintDB = mutableMapOf<String, FloatArray>()

    // 当前说话人的声纹ID（用于连续语音段识别）
    private var currentSpeakerId: String? = null

    /**
     * 注册新声纹（通常在用户主动注册时调用）
     * @param personId 说话人档案 ID
     * @param audioData PCM 音频数据（模拟中未使用，真实实现会提取特征）
     */
    fun registerVoiceprint(personId: String, audioData: ByteArray? = null): Boolean {
        if (voiceprintDB.containsKey(personId)) {
            Log.w(TAG, "声纹已存在: $personId")
            return false
        }
        // 生成模拟特征向量（16维浮点数组）
        val features = FloatArray(16) { Random.nextFloat() }
        voiceprintDB[personId] = features
        Log.d(TAG, "声纹注册成功: $personId")
        return true
    }

    /**
     * 实时识别说话人
     * @param audioData 当前语音段的 PCM 数据
     * @return 识别出的说话人 ID，若未匹配则返回 null
     */
    fun identifySpeaker(audioData: ByteArray): String? {
        // 生成当前语音段的模拟特征向量
        val currentFeatures = FloatArray(16) { Random.nextFloat() }

        // 与已注册声纹逐一比对（余弦相似度模拟）
        var bestMatchId: String? = null
        var bestSimilarity = 0.0

        for ((id, features) in voiceprintDB) {
            val similarity = computeMockSimilarity(currentFeatures, features)
            Log.d(TAG, "比对 $id: 相似度 $similarity")
            if (similarity > bestSimilarity && similarity >= SIMILARITY_THRESHOLD) {
                bestSimilarity = similarity
                bestMatchId = id
            }
        }

        // 如果未匹配到任何注册声纹，返回 null（后续可创建临时说话人）
        if (bestMatchId != null) {
            Log.d(TAG, "识别结果: $bestMatchId (相似度: $bestSimilarity)")
            currentSpeakerId = bestMatchId
            return bestMatchId
        }
        Log.d(TAG, "未识别到已注册说话人")
        return null
    }

    /**
     * 计算两个模拟特征向量的余弦相似度（模拟版）
     */
    private fun computeMockSimilarity(a: FloatArray, b: FloatArray): Double {
        // 简单模拟：返回 0.6~0.95 之间的随机值
        return 0.6 + Random.nextDouble() * 0.35
    }

    /**
     * 获取当前说话人 ID
     */
    fun getCurrentSpeakerId(): String? = currentSpeakerId

    /**
     * 重置当前说话人（对话结束时调用）
     */
    fun resetSpeaker() {
        currentSpeakerId = null
    }
}