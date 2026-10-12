package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalPlaceIndexTest {
    private val fixture = """{"schema":1,"places":[
        {"id":"1","name":"金川区","affiliation":"甘肃省·金昌市","lat":38.5,"lon":102.2,"aliases":["金川","jin chuan qu","Jinchuan Qu"],"level":"ADM3"},
        {"id":"2","name":"金川县","affiliation":"四川省·阿坝州","lat":31.5,"lon":102.0,"aliases":["金川","jin chuan xian"],"level":"ADM3"},
        {"id":"3","name":"镇原县","affiliation":"甘肃省·庆阳市","lat":35.7,"lon":107.2,"aliases":["镇原","zhen yuan xian"],"level":"ADM3"}
    ]}"""

    @Test fun exactCountyRanksBeforeNamesThatOnlyShareCharacters() {
        val results = LocalPlaceIndex.parse(fixture.toByteArray()).search("金川区")
        assertEquals("金川区", results.first().name)
        assertEquals("甘肃省·金昌市", results.first().affiliation)
    }

    @Test fun fullPinyinAndInitialsFindTheSameCounty() {
        val index = LocalPlaceIndex.parse(fixture.toByteArray())
        assertEquals("镇原县", index.search("zhenyuan").first().name)
        assertEquals("镇原县", index.search("zyx").first().name)
    }

    @Test fun identicalShortNameRetainsBothRegionsAndStableIds() {
        val results = LocalPlaceIndex.parse(fixture.toByteArray()).search("金川")
        assertEquals(2, results.size)
        assertEquals(2, results.map { it.locationKey }.distinct().size)
        assertTrue(results.all { it.weatherLocationKey == null })
    }

    @Test fun regionQualifiedQueryDoesNotLoseMatchingCounty() {
        val results = LocalPlaceIndex.parse(fixture.toByteArray()).search("甘肃 金川")
        assertEquals("金川区", results.first().name)
        assertFalse(results.any { it.name == "金川县" })
    }

    @Test fun contiguousProvinceAndCountyQueryDoesNotRequireTypingEveryParent() {
        val index = LocalPlaceIndex.parse(fixture.toByteArray())
        assertEquals("金川区", index.search("甘肃金川").single().name)
        assertEquals("金川区", index.search("甘肃省金川区").single().name)
    }

    @Test fun malformedOrUnsupportedDatasetCannotCreatePhantomPlaces() {
        for (data in listOf("{}", """{"schema":99,"places":[]}""", fixture.replace("38.5", "999"))) {
            assertTrue(runCatching { LocalPlaceIndex.parse(data.toByteArray()) }.isFailure)
        }
    }

    @Test fun packagedDatasetAndManifestAgreeAndRepresentativeCountiesAreSearchable() {
        val root = File("src/main/assets/geo")
        val bytes = File(root, "local_places.json").readBytes()
        val index = LocalPlaceIndex.parseVerified(bytes, File(root, "local_places-manifest.json").readText())
        // Fixed exact-name samples cover all mainland provincial regions plus
        // representative records in each of the other three admitted exports.
        for (query in listOf("金川区", "镇原县", "建水县", "朝阳区", "兰州市", "成都市",
            "北京市", "天津市", "上海市", "重庆市", "河北省", "山西省", "辽宁省", "吉林省", "黑龙江省",
            "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省", "河南省", "湖北省", "湖南省",
            "广东省", "海南省", "四川省", "贵州省", "云南省", "陕西省", "甘肃省", "青海省",
            "内蒙古自治区", "广西壮族自治区", "西藏自治区", "宁夏回族自治区", "新疆维吾尔自治区",
            "離島區", "中西區", "大堂區", "台北市", "高雄市")) {
            assertTrue("$query missing", index.search(query).any { it.name == query })
        }
        assertTrue(index.search("朝阳区").map { it.affiliation }.distinct().size >= 2)
        assertFalse(index.search("肯定不存在的测试城市").isNotEmpty())
        assertTrue(runCatching { LocalPlaceIndex.parseVerified(bytes + 32, File(root, "local_places-manifest.json").readText()) }.isFailure)
    }
}
