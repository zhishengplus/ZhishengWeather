package com.zhisheng.weather.data

// Chinese display/search aliases only. GeoNames IDs, coordinates and the verified
// source asset remain unchanged. Macau has seven parishes plus the Cotai area.
internal val hongKongMacauPlaceNames: Map<String, List<String>> = mapOf(
    "HK:1818224" to listOf("元朗区", "元朗區"),
    "HK:1818458" to listOf("荃湾区", "荃灣區"),
    "HK:1818672" to listOf("大埔区", "大埔區"),
    "HK:1819049" to listOf("西贡区", "西貢區"),
    "HK:1819708" to listOf("离岛区", "離島區"),
    "HK:7533598" to listOf("中西区", "中西區"),
    "HK:7533607" to listOf("湾仔区", "灣仔區"),
    "HK:7533608" to listOf("东区", "東區"),
    "HK:7533609" to listOf("南区", "南區"),
    "HK:7533610" to listOf("油尖旺区", "油尖旺區"),
    "HK:7533611" to listOf("深水埗区", "深水埗區"),
    "HK:7533612" to listOf("九龙城区", "九龍城區"),
    "HK:7533613" to listOf("黄大仙区", "黃大仙區"),
    "HK:7533614" to listOf("观塘区", "觀塘區"),
    "HK:7533615" to listOf("葵青区", "葵青區"),
    "HK:7533616" to listOf("屯门区", "屯門區"),
    "HK:7533617" to listOf("北区", "北區"),
    "HK:7533618" to listOf("沙田区", "沙田區"),
    "MO:11875154" to listOf("花地玛堂区", "花地瑪堂區"),
    "MO:11875155" to listOf("花王堂区", "花王堂區", "圣安多尼堂区", "聖安多尼堂區"),
    "MO:11875156" to listOf("望德堂区", "望德堂區"),
    "MO:11875157" to listOf("大堂区", "大堂區"),
    "MO:11875158" to listOf("风顺堂区", "風順堂區"),
    "MO:11875159" to listOf("嘉模堂区", "嘉模堂區", "氹仔", "凼仔", "Taipa"),
    "MO:11875160" to listOf("路氹填海区", "路氹填海區", "路氹", "路凼", "Cotai"),
    "MO:11875161" to listOf("圣方济各堂区", "聖方濟各堂區", "路环", "路環", "Coloane"),
)

internal fun hongKongMacauRegionNames(id: String): List<String> = when {
    id.startsWith("HK:") -> listOf("香港", "香港特别行政区", "香港特別行政區", "Hong Kong")
    id.startsWith("MO:") -> listOf("澳门", "澳門", "澳门特别行政区", "澳門特別行政區", "Macao", "Macau")
    else -> emptyList()
}
