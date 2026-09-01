package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `language preference is stable and defaults to Chinese`() {
        assertEquals(AppLanguage.CHINESE, AppLanguage.from(null))
        assertEquals(AppLanguage.CHINESE, AppLanguage.from("unknown"))
        assertEquals(AppLanguage.CHINESE, AppLanguage.from("zh"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.from("ja"))
    }
}
