package com.hari.tvremote.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Shows a toast from any thread. */
object Toaster {
    private val main = Handler(Looper.getMainLooper())

    fun show(context: Context, message: String) {
        val app = context.applicationContext
        main.post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
    }
}
