package com.hari.tvremote.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.hari.tvremote.background.RemoteService
import com.hari.tvremote.graph
import com.hari.tvremote.ui.Toaster
import kotlinx.coroutines.launch

/** Share-sheet target ("Play on TV"): maps the shared link and opens it on the TV. No UI of its own. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val link = text?.let(LinkMapper::toAppLink)
        if (link == null) {
            Toaster.show(this, "TV can't open this link")
        } else {
            RemoteService.start(this)
            val app = applicationContext
            app.graph.scope.launch {
                val sent = app.graph.remote.openLinkWhenReady(link)
                Toaster.show(app, if (sent) "Opening on TV" else "Can't reach the TV")
            }
        }
        finish()
    }
}
