package com.zhisheng.weather.data
import com.zhisheng.weather.model.City
import org.junit.Assert.*
import org.junit.Test
class LocatedSlotTest {
 private fun city(key:String,lat:Double=31.0)=City("测试", "", lat,121.0,key)
 @Test fun travelPreservesFavoriteAtItsOriginalLocation() {
  val manual=city("manual")
  val old=city("old").copy(isFavorite=true)
  val result=replaceLocatedCity(listOf(manual,old),"old",city("new",40.0))
  assertEquals(listOf("new","manual","old"),result.map{it.locationKey})
  assertFalse(result.first().isFavorite)
  assertEquals(old,result.first{it.locationKey=="old"})
  assertEquals(1,result.count{it.isFavorite})
 }
 @Test fun returningToSavedCityDoesNotDuplicateIt() {
  val result=replaceLocatedCity(listOf(city("old"),city("new")),"old",city("new"))
  assertEquals(1,result.size)
 }
 @Test fun firstFixDoesNotRemoveManualCities() {
  assertEquals(2,replaceLocatedCity(listOf(city("manual")),null,city("new")).size)
 }
 @Test fun reverseGeocoderFailureKeepsUsableCoordinates() {
  val c=coordinateLocation(31.23,121.47)
  assertEquals(31.23,c.latitude,0.00001)
  assertEquals(121.47,c.longitude,0.00001)
  assertEquals("当前位置",c.name)
 }
}
