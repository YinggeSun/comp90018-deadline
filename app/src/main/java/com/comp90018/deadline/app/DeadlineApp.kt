package com.comp90018.deadline.app

import android.app.Application

class DeadlineApp : Application() {
    /** Created on first use so tests and previews that never touch storage stay cheap. */
    val container: AppContainer by lazy { AppContainer(this) }
}
