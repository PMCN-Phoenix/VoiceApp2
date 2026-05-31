package com.example.voiceapp

import android.app.Application
import com.example.voiceapp.data.AppDatabase

class VoiceApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: VoiceApp
            private set
    }
}