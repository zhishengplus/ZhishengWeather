package com.zhisheng.weather.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class BriefingCopyTest {
    @Test
    fun naturalPunctuationWinsEvenAtEighteenCharacters() {
        assertEquals(
            BriefingCopy("外面有点风", "骑车时会比走路更有感觉。"),
            briefingCopy("外面有点风，骑车时会比走路更有感觉。"),
        )
    }

    @Test
    fun shortSentenceWithoutAUsefulBreakStaysOnOneLine() {
        assertEquals(
            BriefingCopy("晴天在线。", null),
            briefingCopy("晴天在线。"),
        )
    }
}
