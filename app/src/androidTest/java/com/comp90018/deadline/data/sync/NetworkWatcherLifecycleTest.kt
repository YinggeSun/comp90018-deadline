package com.comp90018.deadline.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NetworkWatcherLifecycleTest {
    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
        assertTrue("Network callback condition did not become true", condition())
    }

    @Test
    fun closingOneRegistrationTwiceStopsItsEventsAndPreservesOtherObservers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        val wifi = Settings.Global.getInt(context.contentResolver, Settings.Global.WIFI_ON, 0)
        val closedEvents = AtomicInteger()
        val liveEvents = AtomicInteger()
        var closedRegistration: AutoCloseable? = null
        var liveRegistration: AutoCloseable? = null
        val watcher = NetworkWatcher(context)
        try {
            assertTrue("Test requires an initially online emulator", connectivity.activeNetwork != null)
            closedRegistration = watcher.start { closedEvents.incrementAndGet() }
            liveRegistration = watcher.start { liveEvents.incrementAndGet() }
            await { closedEvents.get() >= 1 && liveEvents.get() >= 1 }
            closedRegistration.close()
            closedRegistration.close()
            val countAtClose = closedEvents.get()
            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable")
            await { connectivity.activeNetwork == null }
            val offlineLiveEvents = liveEvents.get()
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable")
            await { connectivity.activeNetwork != null && liveEvents.get() > offlineLiveEvents }
            assertEquals("Closed observers must not receive reconnection callbacks", countAtClose, closedEvents.get())
        } finally {
            try {
                closedRegistration?.close()
            } finally {
                try {
                    liveRegistration?.close()
                } finally {
                    try {
                        shell("cmd connectivity airplane-mode ${if (airplane == 0) "disable" else "enable"}")
                    } finally {
                        shell("svc wifi ${if (wifi == 1) "enable" else "disable"}")
                    }
                }
            }
        }
    }
}
