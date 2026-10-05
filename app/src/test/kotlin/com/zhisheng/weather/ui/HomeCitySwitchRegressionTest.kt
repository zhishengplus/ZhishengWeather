package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCitySwitchRegressionTest {
    private fun homeSource(): String = sequenceOf(
        File("app/src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt"),
        File("src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt"),
    ).first { it.isFile }.readText()

    @Test
    fun outgoingCityFrameOwnsItsWeatherSnapshot() {
        val source = homeSource()
        assertTrue(source.contains("HomeContentSnapshot.Data"))
        assertTrue(source.contains("data = snapshot.weather"))
        assertFalse(
            "Crossfade exit frames must never force-read cleared ViewModel weather",
            source.contains("uiState.weather!!"),
        )
    }

    @Test
    fun crossfadeIdentityIncludesCityKey() {
        val source = homeSource()
        assertTrue(source.contains("data class Data(val cityKey: String) : HomeContentKey"))
        assertTrue(source.contains("cityContentSnapshots[cityKey] = HomeContentSnapshot.Data"))
        assertTrue(source.contains("androidx.compose.runtime.key(page.cityKey)"))
    }

    @Test
    fun upwardPushPinsAnInterruptibleDeck() {
        val source = homeSource()
        assertTrue(source.contains("156.dp.toPx()"))
        assertTrue(source.contains("upwardIntent"))
        assertTrue(source.contains("cityDeckVerticalDrag <= -pinThresholdPx"))
        assertTrue(source.contains("cityDeckPinned = true"))
        assertTrue(source.contains("haptic.performHapticFeedback(HapticFeedbackType.LongPress)"))
        assertTrue(source.contains("enabled = pinned"))
        assertTrue(source.contains("onPinnedDrag(dragAmount.x)"))
    }

    @Test
    fun longPressShowsDeckAndReleaseSelectsFocusedCity() {
        val source = homeSource()
        assertTrue(source.contains("detectDragGesturesAfterLongPress"))
        assertTrue(source.contains("cityDeckVisible = true"))
        assertTrue(source.contains("val targetKey = uiState.cities"))
        assertTrue(source.contains("viewModel.selectCity(targetKey)"))
        assertFalse(source.contains("普通的主页横向滑动仍不会触发城市切换。\n                                cityDeckVisible = false"))
    }

    @Test
    fun shortTapIsBoundedByLongPressTimeoutAndScrollsOnlyTheActiveCity() {
        val source = homeSource()
        assertTrue(source.contains("withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis)"))
        assertTrue(source.contains("waitForUpOrCancellation()"))
        assertTrue(source.contains("if (up != null && !cityDeckVisible) scrollToTopRequest++"))
        assertTrue(source.contains("page.cityKey == uiState.selectedCity?.locationKey"))
        assertTrue(source.contains("weatherListState.animateScrollToItem(0)"))
        assertTrue(source.contains("enabled = uiState.weather != null || uiState.cities.size > 1"))
    }

    @Test
    fun heldDeckCounterClearsTheBottomSensor() {
        val source = homeSource()
        assertTrue(source.contains("modifier = Modifier.padding(bottom = if (pinned) 0.dp else 64.dp)"))
    }

    @Test
    fun focusedCardUsesExplicitVendorIndependentGlowLayers() {
        val source = homeSource()
        assertTrue(source.contains("scaleXValue = scale * 1.045f"))
        assertTrue(source.contains("ZhishengCyan.copy(alpha = 0.09f)"))
        assertTrue(source.contains("label = \"city-card-edge-pulse\""))
    }

    @Test
    fun cityDeckCardsKeepRoundedOutlineWhileSliding() {
        val source = homeSource()
        assertTrue(source.contains("clip = true"))
        assertTrue(source.contains("compositingStrategy = CompositingStrategy.Offscreen"))
        assertTrue(source.contains(".clip(innerShape)"))
        assertTrue(source.contains(".background(borderColor)"))
        assertFalse(source.contains("border(7.dp, ZhishengCyan.copy(alpha = 0.09f)"))
        assertFalse(
            "Hairline Modifier.border on a rotated layer stair-steps; use a filled inset ring instead",
            source.contains("width = if (focused) 2.dp else 1.dp"),
        )
    }
}
