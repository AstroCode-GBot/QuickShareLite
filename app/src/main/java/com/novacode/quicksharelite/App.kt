package com.novacode.quicksharelite

import android.app.Application
import com.novacode.quicksharelite.data.AppContainer

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
