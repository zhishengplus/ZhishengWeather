package com.zhisheng.weather.ui

import android.provider.Settings
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.data.WeatherReminderNotification
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Android intents: an app-wide denial must not be routed to a disabled category. */
class ReminderNotificationIntentTest {
    @get:Rule val paparazzi = Paparazzi(theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun deniedAppOpensWholeApplicationWithoutCategoryRouting() {
        val intent = reminderNotificationSettingsIntent(ReminderNotificationAccess.APP_BLOCKED, BuildConfig.APPLICATION_ID)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(BuildConfig.APPLICATION_ID, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertFalse("A channel extra incorrectly routes recovery to the category screen",
            intent.hasExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test fun categoryRecoveryAndAllowedDisplaySettingsTargetWeatherCategory() {
        for (access in listOf(ReminderNotificationAccess.CHANNEL_BLOCKED, ReminderNotificationAccess.ALLOWED)) {
            val intent = reminderNotificationSettingsIntent(access, BuildConfig.APPLICATION_ID)
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
            assertEquals(BuildConfig.APPLICATION_ID, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
            assertEquals(WeatherReminderNotification.CHANNEL, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        }
    }
}
