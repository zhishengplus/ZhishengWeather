package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndCityListStructureTest {
    private val projectDir = File(requireNotNull(System.getProperty("user.dir")))
    @Test
    fun orientationRestoresWeatherButDoesNotRestoreAnOpenCityDrawer() {
        val home = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()
        assertTrue(home.contains("remember(drawerOrientation) { androidx.compose.material3.DrawerState(DrawerValue.Closed) }"))
        assertFalse(home.contains("val drawerState = rememberDrawerState("))
        val activity = File(projectDir, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        assertTrue(activity.contains("orientationState.SaveableStateProvider"))
    }

    @Test
    fun cityDrawerKeepsActionsVisibleAndCitiesScrollable() {
        val home = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()
        val drawer = home.substringAfter("private fun CityDrawer(").substringBefore("private fun formatAlertTime")

        assertTrue(drawer.contains("LazyColumn("))
        assertTrue(drawer.contains("Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(drawer.contains("itemsIndexed("))
        assertTrue(drawer.contains("Text(\"添加城市\""))
        assertTrue(drawer.contains("R.drawable.ph_crosshair"))
        assertTrue(drawer.contains("R.drawable.ph_star"))
        assertTrue(drawer.contains("R.drawable.ph_trash"))
        assertTrue(drawer.contains("\"定位当前位置\""))
        assertTrue(drawer.contains("navigationBarsPadding().padding(top = 8.dp, bottom = 12.dp)"))
        assertTrue(drawer.contains("RequestMultiplePermissions()"))
        assertTrue(drawer.contains("LocationSource.requestedPermissions(precise = preciseEnabled)"))
        assertFalse(drawer.contains("LocationSource.requestedPermissions(precise = true)"))
        assertTrue(drawer.contains("onBack: () -> Unit"))
        assertTrue(drawer.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(drawer.contains("IconButton(onClick = onBack"))
        assertFalse(drawer.contains("uiState.cities.forEachIndexed"))
    }

    @Test
    fun lastAlertDoesNotLeaveADuplicateBottomGap() {
        val home = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()
        val alerts = home.substringAfter("private fun AlertSection(").substringBefore("private fun hazardStripes")

        assertTrue(alerts.contains("alerts.forEachIndexed { index, alert ->"))
        assertTrue(alerts.contains("if (index == alerts.lastIndex) 0.dp else 8.dp"))
    }

    @Test
    fun citySearchHasAnExplicitLabeledBackControl() {
        val search = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/SearchScreen.kt").readText()

        assertTrue(search.contains("onClickLabel = uiText(\"返回\")"))
        assertTrue(search.contains("Text(uiText(\"返回\")"))
        // A labelled native icon button is valid; keep the classic text control as well.
        assertTrue(search.contains("PhosphorIcon(R.drawable.ph_arrow_left, \"返回\""))
    }
}
