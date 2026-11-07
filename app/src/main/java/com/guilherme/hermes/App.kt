package com.guilherme.hermes

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
    }
}
