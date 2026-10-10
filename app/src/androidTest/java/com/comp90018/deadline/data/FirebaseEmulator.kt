package com.comp90018.deadline.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID

/**
 * Shared setup for tests that run against the local Firebase Emulator Suite on the
 * `demo-deadline` project, never the real one. Start the emulators on the host with
 * `firebase emulators:start --only auth,firestore --project demo-deadline`.
 */
object FirebaseEmulator {
    /** The host machine, as seen from the Android emulator. */
    const val HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
    const val PROJECT_ID = "demo-deadline"

    fun isRunning(): Boolean = reachable(FIRESTORE_PORT) && reachable(AUTH_PORT)

    /** Deletes every document in the emulator's Firestore, so tests start from an empty database. */
    fun clearFirestore() {
        val url = URL("http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/(default)/documents")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "DELETE"
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Clearing Firestore failed: ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Creates the document at [path] from Firestore REST [json] with the emulator's admin access,
     * bypassing the rules, to stand in for records that already exist (for example from before
     * the rules were tightened).
     */
    fun writeAsAdmin(
        path: String,
        json: String,
    ) {
        val collection = path.substringBeforeLast('/')
        val documentId = path.substringAfterLast('/')
        val url =
            URL("http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT_ID/databases/(default)/documents/$collection?documentId=$documentId")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(json.toByteArray()) }
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Admin write failed: ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
    }

    /** A separate Firebase app, i.e. a separate player, connected to the emulators. */
    fun newApp(context: Context): FirebaseApp {
        val app =
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(PROJECT_ID)
                    .setApplicationId("1:1:android:1")
                    .setApiKey("emulator-only")
                    .build(),
                "player-${UUID.randomUUID()}",
            )
        FirebaseAuth.getInstance(app).useEmulator(HOST, AUTH_PORT)
        FirebaseFirestore.getInstance(app).useEmulator(HOST, FIRESTORE_PORT)
        return app
    }

    private fun reachable(port: Int) = runCatching { Socket().use { it.connect(InetSocketAddress(HOST, port), 1_000) } }.isSuccess
}
