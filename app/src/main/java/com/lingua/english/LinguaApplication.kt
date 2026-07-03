package com.lingua.english

import android.app.Application
import com.lingua.english.data.AppContainer

class LinguaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
