package com.example.voiceapp.network

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object BaiduTokenManager {
    private const val TAG = "BaiduTokenManager"
    private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
    private var accessToken: String? = null
    private val client = OkHttpClient()

    suspend fun getToken(apiKey: String, secretKey: String): String {
        accessToken?.let {
            Log.d(TAG, "使用缓存的 access_token，前8位: ${it.take(8)}...")
            return it
        }
        return fetchToken(apiKey, secretKey)
    }

    private suspend fun fetchToken(apiKey: String, secretKey: String): String {
        val url = "$TOKEN_URL?grant_type=client_credentials" +
                "&client_id=$apiKey" +
                "&client_secret=$secretKey"
        Log.d(TAG, "开始获取 access_token，API Key 前8位: ${apiKey.take(8)}...")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Token接口 HTTP 状态码: ${response.code}")
                Log.d(TAG, "Token接口原始响应: ${body.take(500)}")

                val json = JSONObject(body)
                // 尝试获取 access_token，如果失败则打印错误信息
                val token = json.optString("access_token")
                if (token.isEmpty()) {
                    val errorDesc = json.optString("error_description", "未知错误")
                    val error = json.optString("error", "")
                    Log.e(TAG, "获取 token 失败: error=$error, description=$errorDesc")
                    throw Exception("获取百度 access_token 失败: $errorDesc")
                }
                accessToken = token
                Log.d(TAG, "获取百度 access_token 成功，前8位: ${token.take(8)}...，完整长度: ${token.length}")
                token
            } catch (e: Exception) {
                Log.e(TAG, "获取百度 access_token 异常: ${e.message}")
                throw e
            }
        }
    }
}