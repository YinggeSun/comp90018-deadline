package com.comp90018.deadline.testing

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Optional device screenshot evidence: enable with instrumentation argument qaScreenshotPrefix. */
object UiScreenshot {
    fun capture(
        rule: ComposeTestRule,
        name: String,
    ) {
        val prefix = InstrumentationRegistry.getArguments().getString("qaScreenshotPrefix") ?: return
        require(prefix.matches(Regex("[a-zA-Z0-9_-]+")))
        require(name.matches(Regex("[a-zA-Z0-9_-]+")))
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        // Let the Android window present the frame before UiAutomation reads it.
        Thread.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$prefix-$name.png")
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/${file.name}"),
        ).use { it.readBytes() }
    }
}
