package com.ffvcn.client

import android.app.Application
import com.ffvcn.client.vpn.CaBundle

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Root certificates have to be unpacked before the engine or a probe runs.
        CaBundle.prepare(this)
    }
}
