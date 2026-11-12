package com.zhisheng.weather.ui

import androidx.compose.runtime.saveable.SaverScope
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.home.*
import org.junit.Assert.*
import org.junit.Test

class OutfitStateTest {
    private val scope=object:SaverScope {override fun canBeSaved(value:Any)=true}
    private val now=1_793_645_000_000L
    @Test fun preferencesAndEquipmentSurviveSavedPageReplacement() {
        val state=OutfitUiState().apply {windowCode=1;feelingCode=0;activityCode=2;paletteIndex=2;selectedSlot=3;selectVariants(6,listOf(1,0,0,1))}
        val saved=with(OutfitUiState.Saver) {scope.save(state)}!!
        val restored=OutfitUiState.Saver.restore(saved)!!
        assertEquals(1,restored.windowCode);assertEquals(0,restored.feelingCode);assertEquals(2,restored.activityCode)
        assertEquals(2,restored.paletteIndex);assertEquals(3,restored.selectedSlot);assertEquals(listOf(1,0,0,1),restored.variantsFor(6))
    }
    @Test fun aDifferentThermalContextNeverReusesAnIncompatibleClothingChoice() {
        val state=OutfitUiState().apply {paletteIndex=1;selectVariants(2,listOf(1,1,0,1))}
        assertEquals(listOf(0,0,0,0),state.variantsFor(7));assertEquals(1,state.paletteIndex)
        state.feelingCode=0;assertEquals(listOf(0,0,0,0),state.variantsFor(2))
    }
    @Test fun dialogueSnapshotRetainsAdviceAndAllWarningPresenceButOmitsUnusedPrivateLocation() {
        val original=WeatherData(current=CurrentWeather(14.0,10.0,WeatherCondition.RAIN,windSpeed=24.0,uvIndex=0),
            hourly=List(20) {HourlyWeather(now+it*3_600_000L,temperature=14.0-it,feelsLike=12.0-it,precipProb=70)},
            daily=listOf(DailyWeather(now,high=30.0,low=-30.0)),alerts=List(12) {AlertInfo("预警$it",detail="详细正文")},
            updateTime=now,fetchedAt=now,utcOffsetSeconds=8*3600,
            moduleEvidence=mapOf("current" to WeatherModuleEvidence("公开天气",now,now,37.0,102.0)))
        val request=OutfitDialogRequest.create(original,"c","kmh","金川区",now)
        assertEquals(12,request.weather.alerts.size);assertTrue(request.weather.alerts.all {it.detail==null})
        assertTrue(request.weather.daily.isEmpty());assertNull(request.weather.moduleEvidence["current"]!!.requestedLatitude)
        assertEquals(outfitAdvice(original,now,OutfitWindow.SIX_HOURS),outfitAdvice(request.weather,now,OutfitWindow.SIX_HOURS))
    }
    @Test fun savedOpenDialogPreservesZeroReadingsForecastTimesAndUnits() {
        val request=OutfitDialogRequest.create(WeatherData(current=CurrentWeather(0.0,0.0,WeatherCondition.CLEAR_NIGHT,uvIndex=0),
            hourly=listOf(HourlyWeather(now+3_600_000L,0.0,uvIndex=0)),fetchedAt=now,utcOffsetSeconds=8*3600),"f","ms","香港東區",now)
        val saved=with(OutfitDialogRequest.Saver) {scope.save(request)}!!;val restored=OutfitDialogRequest.Saver.restore(saved)!!
        assertEquals(request,restored);assertEquals(0.0,restored.weather.current!!.temperature);assertEquals(0,restored.weather.current!!.uvIndex)
        assertEquals("f",restored.unit);assertEquals("ms",restored.windUnit)
    }
    @Test fun oversizedOrMalformedSavedStateCannotCrashActivityRestoration() {
        assertNull(OutfitDialogRequest.Saver.restore(123))
        assertNull(OutfitDialogRequest.Saver.restore("not-json"));assertNull(OutfitDialogRequest.Saver.restore("x".repeat(48_001)))
        val large=OutfitDialogRequest(WeatherData(current=CurrentWeather(weatherText="x".repeat(50_000))),"c","kmh","城市")
        assertNull(with(OutfitDialogRequest.Saver) {scope.save(large)})
    }
    @Test fun invalidPreferenceStateFallsBackWithoutThrowing() {
        assertNull(OutfitUiState.Saver.restore(listOf("bad")))
        val restored=OutfitUiState.Saver.restore(listOf(99,-5,9,99,99,"",0,0,0,0))!!
        assertEquals(1,restored.windowCode);assertEquals(0,restored.feelingCode);assertEquals(2,restored.activityCode)
        assertEquals(2,restored.paletteIndex);assertEquals(3,restored.selectedSlot)
    }
}
