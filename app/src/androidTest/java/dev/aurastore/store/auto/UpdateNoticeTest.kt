package dev.aurastore.store.auto

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aurastore.store.install.UpdateTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The update notification used to be a count. A count cannot answer "which app", and the shade
 * carried no app icon, so this test asks the platform what was actually posted: one card per update
 * with that app's own large icon, its name, and the version pair.
 *
 * The cards are ungrouped on purpose. A group collapses into one summary, and that summary shows a
 * single icon no matter how many apps are behind it, which is the one thing this notification exists
 * to avoid.
 *
 * The two packages are on every device this project is tested on, so the icons are real and the test
 * needs no network. [UpdateNotifications.clear] runs after each case, so a passing run leaves the
 * shade as it found it.
 */
@RunWith(AndroidJUnit4::class)
class UpdateNoticeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grantNotifications() {
        // API 33+ refuses notify() without it. Below that every app may post, so there is nothing to do.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tidy() {
        UpdateNotifications.clear(context)
    }

    @Test
    fun eachUpdateWearsItsOwnIconAndName() {
        val ours = context.packageName
        val other = "com.android.settings"
        UpdateNotifications.report(
            context = context,
            targets = listOf(target(ours, "Aura Store"), target(other, "Settings")),
            queued = setOf(ours),
            wifiOnly = false,
        )

        val posted = manager.activeNotifications.toList()
        val cards = posted.filter { it.tag?.startsWith("aurastore.update:") == true }
        assertEquals("one card per updated app", 2, cards.size)

        val mine = cards.first { it.tag == "aurastore.update:$ours" }.notification
        assertEquals("Aura Store", mine.extras.getCharSequence("android.title").toString())
        // The large icon is read from the extras: the platform keeps it there, and the deprecated
        // `Notification.largeIcon` field is null on the Android 16 device this ran on.
        assertNotNull("the app's own icon rides the card", mine.extras.get("android.largeIcon"))
        assertTrue(
            "the version pair names the change",
            mine.extras.getCharSequence("android.text").toString().contains("v1.0 → v2.0"),
        )
        assertTrue(
            "a queued app says it is downloading",
            mine.extras.getCharSequence("android.bigText").toString().contains("Downloading now"),
        )

        val theirs = cards.first { it.tag == "aurastore.update:$other" }.notification
        assertEquals("Settings", theirs.extras.getCharSequence("android.title").toString())
        assertNotNull("every card gets its own icon", theirs.extras.get("android.largeIcon"))
        assertFalse(
            "an app nothing queued is not called downloading",
            theirs.extras.getCharSequence("android.bigText").toString().contains("Downloading now"),
        )

        // A summary would collapse the icons into one card, so there must not be one.
        assertFalse(
            "nothing is grouped",
            posted.any { it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0 },
        )
    }

    @Test
    fun anAppThatIsNoLongerUpdatedLosesItsNotification() {
        val ours = context.packageName
        UpdateNotifications.report(context, listOf(target(ours, "Aura Store")), emptySet(), wifiOnly = false)
        assertTrue(manager.activeNotifications.any { it.tag == "aurastore.update:$ours" })

        // The next check finds nothing left to do for it, so a card must not keep claiming an update.
        val other = "com.android.settings"
        UpdateNotifications.report(context, listOf(target(other, "Settings")), emptySet(), wifiOnly = false)

        assertFalse(manager.activeNotifications.any { it.tag == "aurastore.update:$ours" })
        assertTrue(manager.activeNotifications.any { it.tag == "aurastore.update:$other" })
    }

    private fun target(packageName: String, title: String) = UpdateTarget(
        postId = 1L,
        packageName = packageName,
        title = title,
        catalogueVersion = "2.0",
        installedVersion = "1.0",
        autoUpdatable = true,
    )
}
