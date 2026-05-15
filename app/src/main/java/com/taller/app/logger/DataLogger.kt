package com.taller.app.logger

import android.util.Log

class DataLogger {

    fun logEvent(event: String) {
        Log.d("TallerLogger", event)
    }
}