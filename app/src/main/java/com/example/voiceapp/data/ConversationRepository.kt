package com.example.voiceapp.data

import android.content.ContentValues
import android.util.Log
import com.example.voiceapp.VoiceApp
import net.sqlcipher.database.SQLiteDatabase
import java.util.UUID

class ConversationRepository {

    companion object {
        private const val TAG = "ConversationRepo"
    }

    // 修改点：通过 getWritableDb() 获取数据库连接，传入密码
    private val db: SQLiteDatabase
        get() = VoiceApp.instance.database.getWritableDb()

    private var activeConversationId: String? = null

    fun ensureActiveConversation(): String {
        activeConversationId?.let { return it }
        return createNewConversation()
    }

    fun createNewConversation(): String {
        val convId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", convId)
            put("title", "会话 ${formatTime(now)}")
            put("started_at", now)
        }
        db.insert("conversation", null, values)
        activeConversationId = convId
        Log.d(TAG, "创建新对话: $convId")
        return convId
    }

    fun insertMessage(
        conversationId: String,
        speakerId: String?,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        confidence: Double = 0.0
    ): String {
        val msgId = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", msgId)
            put("conversation_id", conversationId)
            if (speakerId != null) {
                if (speakerId.startsWith("temp_")) {
                    put("temp_speaker_id", speakerId)
                    put("sender_person_id", null as String?)
                } else {
                    put("sender_person_id", speakerId)
                    put("temp_speaker_id", null as String?)
                }
            }
            put("content", content)
            put("timestamp", timestamp)
            put("confidence", confidence)
        }
        db.insert("message", null, values)

        db.execSQL(
            "UPDATE conversation SET message_count = message_count + 1 WHERE id = ?",
            arrayOf(conversationId)
        )
        Log.d(TAG, "消息已存储: $msgId -> $content (speaker=$speakerId)")
        return msgId
    }

    fun finishConversation(conversationId: String) {
        val values = ContentValues().apply {
            put("ended_at", System.currentTimeMillis())
        }
        db.update("conversation", values, "id = ?", arrayOf(conversationId))
        activeConversationId = null
        Log.d(TAG, "对话结束: $conversationId")
    }

    fun ensureTemporarySpeaker(speakerId: String) {
        db.execSQL(
            "INSERT OR IGNORE INTO temporary_speaker(id, label, first_seen_at, last_seen_at) VALUES (?, ?, ?, ?)",
            arrayOf(speakerId, "说话人 ${speakerId.take(8)}", System.currentTimeMillis(), System.currentTimeMillis())
        )
    }

    // ========== 新增调试方法 ==========
    /**
     * 查询并打印所有消息记录（调试用）
     */
    fun debugPrintAllMessages() {
        val cursor = db.rawQuery("SELECT * FROM message", null)
        Log.d(TAG, "========== 数据库消息列表 ==========")
        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
            val convId = cursor.getString(cursor.getColumnIndexOrThrow("conversation_id"))
            val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
            val sender = cursor.getString(cursor.getColumnIndexOrThrow("sender_person_id"))
            val temp = cursor.getString(cursor.getColumnIndexOrThrow("temp_speaker_id"))
            Log.d(TAG, "消息: $id | 对话: $convId | 内容: $content | 正式说话人: $sender | 临时说话人: $temp")
        }
        cursor.close()
        Log.d(TAG, "====================================")
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}