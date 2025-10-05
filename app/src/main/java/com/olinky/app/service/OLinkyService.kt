package com.olinky.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OLinkyService : Service() {

    private val statusMutable = MutableStateFlow("Idle")
    val status: StateFlow<String> = statusMutable.asStateFlow()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        statusMutable.value = "Service initialized"
    }

    override fun onDestroy() {
        statusMutable.value = "Service destroyed"
        super.onDestroy()
    }
}
