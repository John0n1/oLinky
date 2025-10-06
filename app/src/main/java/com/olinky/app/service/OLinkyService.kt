package com.olinky.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.olinky.core.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

class OLinkyService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val statusMutable = MutableStateFlow<ServiceStatus>(ServiceStatus.Idle)
    val status: StateFlow<ServiceStatus> = statusMutable.asStateFlow()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        statusMutable.value = ServiceStatus.Starting
        serviceScope.launch {
            val hasRoot = RootShell.isRootAvailable()
            statusMutable.value = if (hasRoot) {
                ServiceStatus.Ready
            } else {
                ServiceStatus.Error("Root access denied. Please grant superuser permissions to oLinky.")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        statusMutable.value = ServiceStatus.Stopped
        serviceScope.cancel()
        super.onDestroy()
    }
}

sealed interface ServiceStatus {
    data object Idle : ServiceStatus
    data object Starting : ServiceStatus
    data object Ready : ServiceStatus
    data object Stopped : ServiceStatus
    data class Error(val message: String) : ServiceStatus
}
