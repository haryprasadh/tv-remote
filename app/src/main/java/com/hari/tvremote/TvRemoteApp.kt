package com.hari.tvremote

import android.app.Application
import android.content.Context
import com.hari.tvremote.connection.ConnectionManager
import com.hari.tvremote.remote.TvRemote
import com.hari.tvremote.remote.UsageClock
import com.hari.tvremote.ui.Toaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TvRemoteApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/** Process-wide singletons shared by the activity, share sheet, service and tile. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val usage = UsageClock()
    val connection = ConnectionManager(app, scope)
    val remote = TvRemote(connection, usage)

    @Volatile var uiVisible = false
        private set

    init {
        scope.launch {
            connection.tvErrors.collect { Toaster.show(app, "The TV couldn't do that") }
        }
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        usage.touch()
        connection.setUiVisible(visible)
    }
}

val Context.graph: AppGraph get() = (applicationContext as TvRemoteApp).graph
