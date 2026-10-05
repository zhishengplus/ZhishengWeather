package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

class SponsorBoardTest {
    @Test fun onlyFirstPlaceKeepsBadge() {
        assertEquals("👑", sponsorBadge(1))
        for (rank in 2..SponsorBoard.size) assertNull(sponsorBadge(rank))
    }
    @Test fun sponsorNamesAndOrderingRemainIntact() {
        assertEquals(56, SponsorBoard.size)
        assertEquals(listOf("披着牛皮的糖", "摆渡人", "Spark"), SponsorBoard.take(3))
        assertEquals(16, SponsorBoard.indexOf("半夏"))
        assertEquals(37, SponsorBoard.indexOf("十二"))
        assertEquals(listOf("十二", "2026", "偏我来时不逢春", "西伯利亚狼", "江湖暂过住", "亦世凡华", "llb", "屿光"),
            SponsorBoard.subList(SponsorBoard.indexOf("十二"), SponsorBoard.indexOf("屿光") + 1))
        assertEquals("bluebone", SponsorBoard[SponsorBoard.indexOf("Sh4d0W") + 15])
        assertEquals(listOf("&", "追光者"), SponsorBoard.subList(7, 9))
        assertEquals(1, SponsorBoard.count { it == "😀" })
        assertEquals(
            listOf("PickGear", "以后升级纯靠举报加经验太好玩了", "酷我哥哥", "李玄子", "果丽橙", "烟花易冷", "Ali", "如此而已"),
            SponsorBoard.takeLast(8),
        )
    }
    @Test fun sh4d0wKeepsTheSixthSeatForever() {
        assertEquals("Sh4d0W", SponsorBoard[5])
        assertEquals(1, SponsorBoard.count { it == "Sh4d0W" })
    }
}
