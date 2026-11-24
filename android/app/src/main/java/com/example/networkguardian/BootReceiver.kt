package com.example.networkguardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Settings(ctx).enabled) {
            ctx.startForegroundService(Intent(ctx, MonitorService::class.java))
        }
    }
}
