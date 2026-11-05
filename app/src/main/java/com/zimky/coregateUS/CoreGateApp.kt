package com.zimky.coregateUS

import android.app.Application
import com.zimky.coregateUS.core.XposedScopeBridge

class CoreGateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedScopeBridge.initialize()
    }
}
