package com.farhannz.kaitou.helpers

import android.util.Log

class Logger (tag : String){
    private val LOG_TAG = tag

    fun DEBUG(msg : String) {
        android.util.Log.d(LOG_TAG, msg)
    }
    fun ERROR(msg : String) {
        android.util.Log.e(LOG_TAG, msg)
    }
    fun INFO(msg : String) {
        Log.i(LOG_TAG, msg)
    }

    fun WARNING(msg: String) {
        Log.w(LOG_TAG,msg)
    }
}