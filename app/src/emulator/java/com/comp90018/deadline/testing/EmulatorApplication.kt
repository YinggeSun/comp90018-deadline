package com.comp90018.deadline.testing

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import android.os.SystemClock
import android.security.NetworkSecurityPolicy
import android.util.Log
import com.comp90018.deadline.app.AppContainer
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.data.remote.firebase.RemoteConfigSource
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Only packaged in the emulator build. Providers cannot initialize Firebase before this. */
class EmulatorApplication : DeadlineApp() {
    override fun onCreate() {
        check(packageName == "com.comp90018.deadline.emulator")
        check(FirebaseApp.getApps(this).isEmpty()) { "Firebase initialized before emulator isolation" }
        check(checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            "Installed emulator APK lacks INTERNET permission"
        }
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        Log.i("EmulatorStartup", "uid=${Process.myUid()} network=$network capabilities=${connectivity.getNetworkCapabilities(network)}")
        Log.i(
            "EmulatorStartup",
            "links=${connectivity.getLinkProperties(network)} backgroundRestriction=${connectivity.restrictBackgroundStatus}",
        )
        Log.i("EmulatorStartup", "localCleartext=${NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("10.0.2.2")}")
        // Application.onCreate runs on the main thread; probe on a worker and fail startup closed.
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                val failures = mutableListOf<Throwable>()
                for (port in listOf(9099, 8080)) {
                    val started = SystemClock.elapsedRealtime()
                    try {
                        Socket().use {
                            it.connect(InetSocketAddress("10.0.2.2", port), 2_000)
                            Log.i(
                                "EmulatorStartup",
                                "port=$port connected local=${it.localSocketAddress} elapsedMs=${SystemClock.elapsedRealtime() - started}",
                            )
                        }
                    } catch (error: Exception) {
                        Log.e("EmulatorStartup", "port=$port failed elapsedMs=${SystemClock.elapsedRealtime() - started}", error)
                        failures += error
                    }
                }
                check(failures.isEmpty()) {
                    "Local Firebase unreachable from uid=${Process.myUid()}; ${failures.joinToString { it.toString() }}"
                }
            }.get(6, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
        val app =
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setProjectId("demo-deadline")
                    .setApplicationId("1:1:android:1")
                    .setApiKey("emulator-only")
                    .build(),
            )
        app.setDataCollectionDefaultEnabled(false)
        FirebaseAuth.getInstance(app).useEmulator("10.0.2.2", 9099)
        FirebaseFirestore.getInstance(app).useEmulator("10.0.2.2", 8080)
        // Only now may DeadlineApp construct repositories and start background work.
        super.onCreate()
    }

    override fun createContainer() =
        AppContainer(
            this,
            remoteConfigSource =
                object : RemoteConfigSource {
                    override suspend fun activated(): Map<String, String> = emptyMap()

                    override suspend fun fetchAndActivate() = Unit
                },
        )
}
