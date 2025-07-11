package com.farhannz.kaitou

import android.app.Application
import com.farhannz.kaitou.helpers.DatabaseManager
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseManager.initialize(this)
    }
}