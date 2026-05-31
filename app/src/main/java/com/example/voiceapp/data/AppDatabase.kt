package com.example.voiceapp.data

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom

class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "voice_archive.db"
        private const val DATABASE_VERSION = 1
        private const val PREFS_NAME = "db_secrets"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun getOrCreatePassphrase(context: Context): String {
            val masterKey = MasterKeys.AES256_GCM_SPEC.let {
                MasterKeys.getOrCreate(it)
            }

            val prefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
            if (existing != null) return existing

            val random = SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            val newPassphrase = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_DB_PASSPHRASE, newPassphrase).apply()
            return newPassphrase
        }
    }

    init {
        SQLiteDatabase.loadLibs(context)
        val passphrase = getOrCreatePassphrase(context)
        writableDatabase.rawExecSQL("PRAGMA key = '$passphrase';")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE person_profile (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                avatar_uri TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE voiceprint (
                id TEXT PRIMARY KEY,
                person_id TEXT NOT NULL,
                feature_vector BLOB NOT NULL,
                model_version TEXT NOT NULL,
                audio_sample_uri TEXT,
                confidence REAL DEFAULT 0.0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (person_id) REFERENCES person_profile(id) ON DELETE CASCADE
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE temporary_speaker (
                id TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                feature_vector BLOB,
                first_seen_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE conversation (
                id TEXT PRIMARY KEY,
                title TEXT,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                message_count INTEGER DEFAULT 0
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE conversation_participant (
                conversation_id TEXT NOT NULL,
                person_id TEXT NOT NULL,
                PRIMARY KEY (conversation_id, person_id),
                FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
                FOREIGN KEY (person_id) REFERENCES person_profile(id) ON DELETE CASCADE
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE message (
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                sender_person_id TEXT,
                temp_speaker_id TEXT,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                confidence REAL DEFAULT 0.0,
                audio_chunk_uri TEXT,
                FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
                FOREIGN KEY (sender_person_id) REFERENCES person_profile(id) ON DELETE SET NULL,
                FOREIGN KEY (temp_speaker_id) REFERENCES temporary_speaker(id) ON DELETE SET NULL
            );
        """.trimIndent())

        // FTS5 虚拟表，全文检索消息内容
        db.execSQL("""
            CREATE VIRTUAL TABLE message_fts USING fts5(
                content,
                content=message,
                content_rowid=rowid
            );
        """.trimIndent())

        // 触发器：插入时同步 FTS 索引
        db.execSQL("""
            CREATE TRIGGER message_ai AFTER INSERT ON message BEGIN
                INSERT INTO message_fts(rowid, content) VALUES (new.rowid, new.content);
            END;
        """.trimIndent())

        // 触发器：删除时同步 FTS 索引
        db.execSQL("""
            CREATE TRIGGER message_ad AFTER DELETE ON message BEGIN
                INSERT INTO message_fts(message_fts, rowid, content) VALUES('delete', old.rowid, old.content);
            END;
        """.trimIndent())

        // 触发器：更新时同步 FTS 索引
        db.execSQL("""
            CREATE TRIGGER message_au AFTER UPDATE ON message BEGIN
                INSERT INTO message_fts(message_fts, rowid, content) VALUES('delete', old.rowid, old.content);
                INSERT INTO message_fts(rowid, content) VALUES (new.rowid, new.content);
            END;
        """.trimIndent())

        // 性能索引
        db.execSQL("CREATE INDEX idx_message_conv ON message(conversation_id, timestamp);")
        db.execSQL("CREATE INDEX idx_message_sender ON message(sender_person_id);")
        db.execSQL("CREATE INDEX idx_voiceprint_person ON voiceprint(person_id);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 原型阶段，直接重建
        db.execSQL("DROP TABLE IF EXISTS message_fts;")
        db.execSQL("DROP TABLE IF EXISTS message;")
        db.execSQL("DROP TABLE IF EXISTS conversation_participant;")
        db.execSQL("DROP TABLE IF EXISTS conversation;")
        db.execSQL("DROP TABLE IF EXISTS voiceprint;")
        db.execSQL("DROP TABLE IF EXISTS temporary_speaker;")
        db.execSQL("DROP TABLE IF EXISTS person_profile;")
        onCreate(db)
    }

    fun getReadableDb(): SQLiteDatabase = readableDatabase
    fun getWritableDb(): SQLiteDatabase = writableDatabase
}