package com.zhisheng.weather.ui.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AqiLayoutTest {
    @Test
    fun primaryPollutantHasItsOwnResponsiveColumn() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val home = File(
            projectDir,
            "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt",
        ).readText()

        assertTrue(home.contains("Column(Modifier.weight(1f))"))
        assertTrue(home.contains("modifier = Modifier.widthIn(max = 112.dp)"))
        assertTrue(home.contains("\"首要污染物\","))
        assertTrue(home.contains("textAlign = TextAlign.End"))
        assertFalse(home.contains("Text(\"首要污染物 \$it\""))
    }
}
