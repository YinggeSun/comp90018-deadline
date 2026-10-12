package com.comp90018.deadline.app

import android.app.Application

open class DeadlineApp : Application() {
    val container: AppContainer by lazy { createContainer() }

    protected open fun createContainer(): AppContainer = AppContainer(this)

    override fun onCreate() {
        super.onCreate()
        container.signInInBackground()
        container.startProgressSync()
        container.refreshConfigInBackground()
    }
}
