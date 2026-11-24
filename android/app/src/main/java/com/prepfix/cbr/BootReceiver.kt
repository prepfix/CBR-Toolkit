package com.prepfix.cbr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the lock service after the phone restarts or after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LockService.start(context)
    }
}
