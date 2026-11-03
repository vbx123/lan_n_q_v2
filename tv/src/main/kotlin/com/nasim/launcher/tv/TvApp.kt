package com.nasim.launcher.tv

import android.app.Application
import com.nasim.launcher.common.ThemeStore

class TvApp : Application() {
    lateinit var server: RemoteServer

    override fun onCreate() {
        super.onCreate()
        server = RemoteServer(this, ThemeStore(this))
        server.start()
    }
}
