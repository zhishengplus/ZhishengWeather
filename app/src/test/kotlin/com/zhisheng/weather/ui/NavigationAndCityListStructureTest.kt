package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndCityListStructureTest {
    private val projectDir = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun cityDrawerKeepsActionsVisibleAndCitiesScrollable() {
        val home = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()
        val drawer = home.substringAfter("private fun CityDrawer(").substringBefore("private fun formatAlertTime")

        assertTrue(drawer.contains("LazyColumn("))
        assertTrue(drawer.contains("Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(drawer.contains("itemsIndexed("))
        assertTrue(drawer.contains("Text(\"添加城市\""))
        assertTrue(drawer.contains("onBack: () -> Unit"))
        assertTrue(drawer.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(drawer.contains("IconButton(onClick = onBack"))
        assertFalse(drawer.contains("uiState.cities.forEachIndexed"))
    }

    @Test
    fun citySearchHasAnExplicitLabeledBackControl() {
        val search = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/SearchScreen.kt").readText()

        assertTrue(search.contains("onClickLabel = uiText(\"返回\")"))
        assertTrue(search.contains("Text(uiText(\"返回\")"))
        assertFalse(search.contains("IconButton(onClick = onBack)"))
    }
}
