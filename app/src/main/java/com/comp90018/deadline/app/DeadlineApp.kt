package com.comp90018.deadline.app

import android.app.Application

class DeadlineApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.signInInBackground()
    }
}
