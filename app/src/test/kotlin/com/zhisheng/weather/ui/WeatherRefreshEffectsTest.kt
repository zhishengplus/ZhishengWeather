package com.zhisheng.weather.ui

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherRefreshEffectsTest {
    @Test fun cancelledOldRefreshCannotOverwriteNewWeatherOrRescheduleReminders() = runBlocking {
        withTimeout(5_000) {
            for (cancelDuring in listOf("disk", "widgets")) {
                var displayed = "old"
                val entered = CompletableDeferred<Unit>()
                val effects = mutableListOf<String>()
                suspend fun effect(name: String) {
                    if (name == cancelDuring) { entered.complete(Unit); awaitCancellation() }
                    effects += name
                }
                val oldRequest = launch {
                    completeWeatherRefresh({ effect("disk") }, { effect("widgets") }, { effects += "reminders" })
                    displayed = "old response"
                }
                entered.await()
                displayed = "new response"
                oldRequest.cancelAndJoin()
                assertEquals("new response", displayed)
                assertEquals(if (cancelDuring == "disk") emptyList<String>() else listOf("disk"), effects)
            }
        }
    }

    @Test fun optionalWriteFailuresStillAllowTheSuccessfulWeatherToDisplay() = runBlocking {
        val effects = mutableListOf<String>()
        completeWeatherRefresh(
            { effects += "disk"; throw IOException("disk unavailable") },
            { effects += "widgets"; throw IOException("widget unavailable") },
            { effects += "reminders" },
        )
        effects += "display"
        assertEquals(listOf("disk", "widgets", "reminders", "display"), effects)
    }
}
