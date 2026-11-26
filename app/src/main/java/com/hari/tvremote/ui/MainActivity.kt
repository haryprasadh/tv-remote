package com.hari.tvremote.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.hari.tvremote.background.RemoteService
import com.hari.tvremote.graph

/**
 * The remote. Opening it is a user action (connect, waking the TV if needed); while it's visible the
 * background service is kept alive. The Quick Settings tile opens it with [EXTRA_POWER].
 */
class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val graph = graph
        setContent {
            TvRemoteTheme { RemoteScreen(graph.connection, graph.remote) }
        }
        if (savedInstanceState == null) handlePower(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePower(intent)
    }

    override fun onStart() {
        super.onStart()
        graph.setUiVisible(true)
        RemoteService.start(this)
        graph.connection.onAppOpened()
    }

    override fun onStop() {
        graph.setUiVisible(false)
        super.onStop()
    }

    private fun handlePower(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_POWER, false) != true) return
        intent.removeExtra(EXTRA_POWER)
        setIntent(intent)
        graph.remote.power()
    }

    companion object {
        const val EXTRA_POWER = "power"
    }
}
