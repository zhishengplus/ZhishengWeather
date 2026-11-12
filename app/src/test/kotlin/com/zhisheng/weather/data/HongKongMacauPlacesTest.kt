package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HongKongMacauPlacesTest {
    private fun index(): LocalPlaceIndex {
        val root = File("src/main/assets/geo")
        return LocalPlaceIndex.parseVerified(File(root, "local_places.json").readBytes(),
            File(root, "local_places-manifest.json").readText())
    }

    @Test fun allDistrictsAndParishesAcceptBothScriptsWithStableCoordinatesAndIds() {
        val index = index()
        assertEquals(18, index.areas("香港").size)
        assertEquals(8, index.areas("澳门").size)
        assertEquals(index.areas("澳门"), index.areas("澳門"))
        for ((id, names) in hongKongMacauPlaceNames) {
            val region = if (id.startsWith("HK:")) "香港" else "澳門"
            val hits = names.map { name ->
                index.search("$region $name").single { it.locationKey == "geo:local:geonames:$id" }
            }
            assertTrue(id, hits.all { it.name == names.first() })
            assertEquals(id, 1, hits.map { it.latitude to it.longitude }.distinct().size)
            assertTrue(id, hits.all { it.isPreciseLocation })
        }
        assertEquals("嘉模堂区", index.search("澳门氹仔").single().name)
        assertEquals("嘉模堂区", index.search("Macau Taipa").single().name)
        assertEquals("元朗区", index.search("Hong Kong Yuen Long District").single().name)
        assertTrue(index.search("香港 中西區").all { it.locationKey.contains(":HK:") })
    }

    @Test fun territoryForecastIsAvailableForDistrictsWithoutInventingLocalStations() = runBlocking {
        val provinces = listOf(NmcApi.NmcProvince("AXG", "香港特别行政区"), NmcApi.NmcProvince("AAM", "澳门特别行政区"))
        val index = index()
        for ((query, region, expected) in listOf(Triple("香港 中西区", "AXG", "香港"), Triple("澳門 氹仔", "AAM", "澳门"))) {
            val city = index.search(query).first()
            val ref = NmcSource.resolveCitySync(city, provinces) { code ->
                assertEquals(region, code)
                listOf(NmcApi.NmcCityEntry("other", city = "另一站点"), NmcApi.NmcCityEntry("parent", city = expected))
            }
            assertEquals(expected, ref!!.cityName)
            assertEquals("parent", ref.scode)
            assertNull(NmcSource.resolveCitySync(city, provinces) { listOf(NmcApi.NmcCityEntry("other", city = "另一站点")) })
        }
    }

    @Test fun macauBrazilAndUnknownCoordinatesCannotBeUsedForMacauChina() {
        val hits = listOf(
            XiaomiLocationResult(locationKey = "accu:40727", name = "澳门", latitude = "-5.113", longitude = "-36.635", status = 0),
            XiaomiLocationResult(locationKey = "accu:unknown", name = "澳门", status = 0),
        )
        assertNull(coordinateXiaomiKey(hits, 22.191, 113.543))
        val local = XiaomiLocationResult(locationKey = "accu:60090", name = "澳门", latitude = "22.191", longitude = "113.543", status = 0)
        assertEquals("accu:60090", coordinateXiaomiKey(hits + local, 22.191, 113.543))
    }

    @Test fun traditionalMacauNamesWorkButKnownForeignNamesNeverUseDomesticStations() = runBlocking {
        val provinces = listOf(NmcApi.NmcProvince("AAM", "澳门特别行政区"))
        val local = City("氹仔", "澳門特別行政區", 22.15, 113.56, "local", countryCode = "MO")
        val ref = NmcSource.resolveCitySync(local, provinces) {
            listOf(NmcApi.NmcCityEntry("parent", city = "澳门"))
        }
        assertEquals("parent", ref!!.scode)
        assertNull(NmcSource.resolveCitySync(local.copy(name = "澳门", countryCode = "BR"), provinces) {
            fail("Foreign place must not query a domestic station list")
            emptyList()
        })
    }
}
