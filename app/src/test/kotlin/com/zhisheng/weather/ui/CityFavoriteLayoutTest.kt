package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CityFavoriteLayoutTest {
    @Test
    fun cityDrawerOffersAnAccessibleFavoriteControl() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val home = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()

        assertTrue(home.contains("onToggleFavorite = viewModel::toggleCityFavorite"))
        assertTrue(home.contains("if (city.isFavorite) \"★\" else \"☆\""))
        assertTrue(home.contains("uiText(\"收藏优先\")"))
        assertTrue(home.contains("uiText(\"取消收藏\")"))
    }
}
