package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

class SponsorBoardTest {
    @Test fun onlyFirstPlaceKeepsBadge() {
        assertEquals("👑", sponsorBadge(1))
        for (rank in 2..SponsorBoard.size) assertNull(sponsorBadge(rank))
    }
    @Test fun sponsorNamesAndOrderingRemainIntact() {
        assertEquals(62, SponsorBoard.size)
        assertEquals(listOf("披着牛皮的糖", "摆渡人", "Spark"), SponsorBoard.take(3))
        assertEquals(16, SponsorBoard.indexOf("半夏"))
        assertEquals(32, SponsorBoard.indexOf("AiFuL"))
        assertEquals(listOf("AiFuL", "施工单位狗人力", "意难平", "钟福燕"),
            SponsorBoard.subList(SponsorBoard.indexOf("AiFuL"), SponsorBoard.indexOf("钟福燕") + 1))
        assertEquals(41, SponsorBoard.indexOf("十二"))
        assertEquals(listOf("十二", "2026", "偏我来时不逢春", "西伯利亚狼", "江湖暂过住", "亦世凡华", "llb", "屿光", "阿良"),
            SponsorBoard.subList(SponsorBoard.indexOf("十二"), SponsorBoard.indexOf("阿良") + 1))
        assertEquals("bluebone", SponsorBoard[SponsorBoard.indexOf("Sh4d0W") + 15])
        assertEquals(listOf("&", "追光者"), SponsorBoard.subList(7, 9))
        assertEquals(1, SponsorBoard.count { it == "😀" })
        assertEquals(
            listOf("以后升级纯靠举报加经验太好玩了", "酷我哥哥", "李玄子", "果丽橙", "烟花易冷", "Ali", "如此而已", "小豪科1212"),
            SponsorBoard.takeLast(8),
        )
    }
    @Test fun sh4d0wKeepsTheSixthSeatForever() {
        assertEquals("Sh4d0W", SponsorBoard[5])
        assertEquals(1, SponsorBoard.count { it == "Sh4d0W" })
    }
}
