package com.zhisheng.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VistaCopyTest {
    @Test fun providerProgressMarkersCannotBeParsedAsWrappedButtonLabels() {
        listOf("[ ]", "[..]", "[!]", "[完成]", "[", "]", "", "[]", "[ ", " ]").forEach {
            assertEquals(it, vistaLabel(it))
        }
        assertEquals("", vistaLabel("[  ]"))
        assertEquals("验证", vistaLabel("[ 验证 ]"))
    }
    @Test fun usesEverydayNamesForSharedControls() {
        assertEquals("天气详情", vistaLabel("遥测数据"))
        assertEquals("昨天天气", vistaLabel("昨日复盘"))
        assertEquals("天气时钟", vistaLabel("气象中枢"))
        assertEquals("下一步", vistaLabel("[ 下一步 ]"))
    }
    @Test fun neverChangesWeatherValuesOrSourceIdentifiers() {
        listOf("24℃", "16°", "东风 5 km/h", "Open-Meteo", "GPS 精确定位",
            "09-05 10:45", "> 500", "未来2小时无降水").forEach {
            assertEquals(it, vistaLabel(it))
        }
    }

    @Test fun classicTerminalKeepsItsPromptAndBracketDecoration() {
        // 经典终端的 "> " 提示符、"[/]" 选中括号与 "[新增]" 版本前缀都是美术元素，
        // 人话化只替换词典词条，不能顺手清掉它们。
        listOf("> 恢复自动旋转", "> 赞助榜单 · 20 位", "[ 城市 ]", "[ 精确地址 ]",
            "[新增] 组件重做", "CITY DECK // 城市切换", "TIPS //").forEach {
            assertEquals(it, classicLabel(it))
        }
    }

    @Test fun classicTerminalUsesTheSameEverydayNames() {
        assertEquals("天气详情", classicLabel("遥测数据"))
        assertEquals("昨天天气", classicLabel("昨日复盘"))
        assertEquals("天气时钟", classicLabel("气象中枢"))
        assertEquals("未来五天", classicLabel("逐日预报"))
        assertEquals("月亮亮面比例", classicLabel("月面照明"))
        assertEquals("24℃", classicLabel("24℃"))
    }
}
