package com.andika.watersupply.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

/** Sleep until local midnight; clock and timezone changes restart the wait immediately. */
fun Context.currentDateFlow() = callbackFlow {
    var midnight: kotlinx.coroutines.Job? = null
    fun restart() {
        midnight?.cancel()
        midnight = launch {
            while (isActive) {
                val now = ZonedDateTime.now()
                trySend(now.toLocalDate())
                val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
            }
        }
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = restart()
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
    }
    ContextCompat.registerReceiver(this@currentDateFlow, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    restart()
    awaitClose { midnight?.cancel(); unregisterReceiver(receiver) }
}.distinctUntilChanged()
