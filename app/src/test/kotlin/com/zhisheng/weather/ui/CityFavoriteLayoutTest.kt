package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CityFavoriteLayoutTest {
    @Test
    fun cityDrawerOffersAnAccessibleFavoriteControl() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val home = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()
        val viewModel = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/WeatherViewModel.kt").readText()

        assertTrue(home.contains("onToggleFavorite = viewModel::toggleCityFavorite"))
        assertTrue(home.contains("R.drawable.ph_star"))
        assertTrue(home.contains("CityRepository.MAX_FAVORITES"))
        assertTrue(viewModel.contains("最多收藏 6 个城市"))
        assertTrue(home.contains("uiText(\"取消收藏\")"))
    }
}
