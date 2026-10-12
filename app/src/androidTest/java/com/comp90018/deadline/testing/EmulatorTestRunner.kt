package com.comp90018.deadline.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Reject a wrong target before its Application (and providers) can start. */
class EmulatorTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application {
        check(className == EmulatorApplication::class.java.name) { "Instrumentation requires the emulator build" }
        check(context.packageName == "com.comp90018.deadline.emulator")
        return super.newApplication(cl, className, context)
    }
}
