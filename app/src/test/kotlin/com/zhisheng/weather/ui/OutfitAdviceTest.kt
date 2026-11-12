package com.zhisheng.weather.ui

import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.home.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class OutfitAdviceTest {
    private val now=Instant.parse("2026-11-02T15:40:00Z").toEpochMilli()
    private val hour=3_600_000L
    private fun data(temperature: Double?=18.0, feels: Double?=temperature)=WeatherData(
        current=CurrentWeather(temperature=temperature,feelsLike=feels),fetchedAt=now,updateTime=now,utcOffsetSeconds=8*3600)
    private fun plan(data: WeatherData,window: OutfitWindow=OutfitWindow.NOW)=outfitAdvice(data,now,window).plan!!

    @Test fun officialTemperatureBandBoundariesHaveNoGapsOrRoundingJumps() {
        for((boundary,level) in listOf(29.0 to 0,24.0 to 1,21.0 to 2,18.0 to 3,15.0 to 4,11.0 to 5,6.0 to 6)) {
            assertEquals(level,outfitTemperatureLevel(boundary));assertEquals(level+1,outfitTemperatureLevel(boundary-.01))
        }
        assertEquals(7,outfitTemperatureLevel(-30.0))
    }
    @Test fun providerFeelsLikeIsNotWindChilledAgain() {
        val p=plan(data(15.0,5.0).copy(current=CurrentWeather(15.0,5.0,windSpeed=25.0)))
        assertEquals(5.0,p.temperature,0.0);assertEquals(7,p.level);assertTrue(p.usesFeelsLike);assertTrue(OutfitNeed.WIND in p.needs)
    }
    @Test fun missingFeelsLikeKeepsAirTemperatureDistinct() {
        val p=plan(data(18.0,null));assertFalse(p.usesFeelsLike);assertEquals(18.0,p.temperature,0.0)
        assertTrue(p.notes.any {it.contains("气温参考")})
    }
    @Test fun nonFiniteAndOutOfRangeMeasurementsNeverProduceAnOutfit() {
        for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,500.0,-500.0))assertNull(outfitAdvice(data(value,value),now).plan)
        val p=plan(data(16.0,Double.NaN));assertEquals(16.0,p.temperature,0.0)
    }
    @Test fun oldCurrentConditionsDoNotMasqueradeAsCurrentButNearForecastCanRecover() {
        val old=data().copy(updateTime=now-4*hour,hourly=listOf(HourlyWeather(now+20*60_000,12.0)))
        val p=plan(old);assertEquals(12.0,p.temperature,0.0);assertTrue(p.freshnessNote!!.contains("近期逐时"))
        assertNull(outfitAdvice(old.copy(hourly=emptyList()),now).plan)
    }
    @Test fun expiredWeatherDoesNotGenerateFalsePrecisionFromOldForecasts() {
        val old=data().copy(updateTime=now-13*hour,hourly=listOf(HourlyWeather(now+hour,15.0)))
        assertNull(outfitAdvice(old,now,OutfitWindow.SIX_HOURS).plan)
    }
    @Test fun forecastUsesActualTimestampsAcrossMidnightRatherThanArrayPositionOrDailyLow() {
        val d=data(20.0).copy(daily=listOf(DailyWeather(now,high=35.0,low=-20.0)),hourly=listOf(
            HourlyWeather(now+7*hour,-30.0),HourlyWeather(now-20*60_000,-35.0),HourlyWeather(now+2*hour,12.0),HourlyWeather(now+hour,16.0)))
        val p=plan(d,OutfitWindow.SIX_HOURS);assertEquals(12.0,p.low,0.0);assertEquals(20.0,p.high,0.0)
        assertEquals(2,p.forecastCount);assertEquals(now+2*hour,p.forecastUntil);assertTrue(p.notes.any {it.contains("冷暖变化")})
        assertEquals(p.low,plan(d.copy(utcOffsetSeconds=-10*3600),OutfitWindow.SIX_HOURS).low,0.0)
    }
    @Test fun aSparseForecastDoesNotClaimSixHoursOfCoverage() {
        val p=plan(data().copy(hourly=listOf(HourlyWeather(now+hour,17.0))),OutfitWindow.SIX_HOURS)
        assertEquals(1,p.forecastCount);assertEquals(now+hour,p.forecastUntil)
        val currentOnly=plan(data(),OutfitWindow.SIX_HOURS);assertEquals(0,currentOnly.forecastCount);assertNull(currentOnly.forecastUntil)
    }
    @Test fun contradictoryDuplicateHoursAreNotUsedAsAColdMinimum() {
        val p=plan(data().copy(hourly=listOf(HourlyWeather(now+hour,-10.0),HourlyWeather(now+hour,18.0))),OutfitWindow.SIX_HOURS)
        assertEquals(0,p.forecastCount);assertEquals(18.0,p.low,0.0)
    }
    @Test fun dailyUltravioletMaximumNeverTurnsIntoNightSunProtection() {
        val d=data(25.0).copy(current=CurrentWeather(25.0,25.0,WeatherCondition.CLEAR_NIGHT,uvIndex=0),daily=listOf(DailyWeather(now,uvIndex=12)))
        val p=plan(d);assertEquals(0,p.uv);assertFalse(OutfitNeed.SUN in p.needs);assertTrue(p.extras.none {it.kind=="sunhat"})
        assertFalse(OutfitNeed.SUN in plan(d.copy(current=d.current!!.copy(uvIndex=9))).needs)
    }
    @Test fun sunProtectionStartsAtThreeAndHotOutfitKeepsLegsCovered() {
        val d=data(31.0).copy(current=CurrentWeather(31.0,31.0,uvIndex=3))
        val p=plan(d);assertTrue(OutfitNeed.SUN in p.needs);assertTrue(p.extras.any {it.kind=="sunhat"})
        assertEquals(OutfitGarment.LIGHT_PANTS,p.gear.first {it.slot==OutfitSlot.BOTTOM}.choices.first())
        assertFalse(OutfitNeed.SUN in plan(d.copy(current=d.current!!.copy(uvIndex=2))).needs)
    }
    @Test fun strongWindAndRainChooseRainwearRatherThanAnUmbrella() {
        val d=data(12.0).copy(current=CurrentWeather(12.0,12.0,WeatherCondition.RAIN,windSpeed=42.0))
        val p=plan(d);assertTrue(p.extras.any {it.kind=="raincoat"});assertTrue(p.extras.none {it.kind=="umbrella"})
        assertTrue(p.notes.any {it.contains("雨衣")})
    }
    @Test fun modelWindUnitsAreKilometresPerHourNotMetresPerSecond() {
        assertFalse(OutfitNeed.WIND in plan(data().copy(current=CurrentWeather(18.0,18.0,windSpeed=8.0))).needs)
        assertTrue(OutfitNeed.WIND in plan(data().copy(current=CurrentWeather(18.0,18.0,windSpeed=24.0))).needs)
    }
    @Test fun gustDrivenRainwearKeepsTheGustAvailableForExplanation() {
        val p=plan(data().copy(current=CurrentWeather(18.0,18.0,WeatherCondition.RAIN,windSpeed=8.0,windGust=45.0)))
        assertTrue(p.extras.any {it.kind=="raincoat"});assertEquals(8.0,p.windKmh);assertEquals(45.0,p.gustKmh)
    }
    @Test fun snowCallsForGripAndWarmFootwearWithoutAnAutomaticUmbrella() {
        val p=plan(data(-5.0).copy(current=CurrentWeather(-5.0,-5.0,WeatherCondition.SNOW)))
        assertTrue(OutfitNeed.GRIP in p.needs);assertEquals(OutfitGarment.BOOTS,p.gear.last().choices.first());assertTrue(p.extras.none {it.kind=="umbrella"})
    }
    @Test fun personalChoicesChangeLayersWithoutRewritingTemperatures() {
        val d=data(16.0);val ordinary=plan(d)
        val cold=outfitAdvice(d,now,feeling=OutfitFeeling.COLD).plan!!;val warm=outfitAdvice(d,now,feeling=OutfitFeeling.WARM).plan!!
        assertEquals(ordinary.temperature,cold.temperature,0.0);assertEquals(ordinary.temperature,warm.temperature,0.0)
        assertTrue(cold.level>ordinary.level);assertTrue(warm.level<ordinary.level)
        assertEquals(7,outfitAdvice(data(-8.0),now,feeling=OutfitFeeling.WARM,activity=OutfitActivity.WALK).plan!!.level)
    }
    @Test fun missingOrInvalidUvRainWindAreNotInventedAsZeroOrSafe() {
        val p=plan(data().copy(current=CurrentWeather(18.0,18.0,windSpeed=Double.NaN,uvIndex=-1),hourly=listOf(HourlyWeather(now+hour,18.0,precipProb=200))))
        assertNull(p.windKmh);assertNull(p.uv);assertNull(p.rainProbability)
    }
    @Test fun futureRainProbabilityDoesNotAlterTheNowOutfit() {
        val d=data(24.0).copy(hourly=listOf(HourlyWeather(now+3*hour,20.0,precipProb=70)))
        assertFalse(OutfitNeed.RAIN in plan(d).needs);val future=plan(d,OutfitWindow.SIX_HOURS)
        assertTrue(OutfitNeed.RAIN in future.needs);assertEquals(70,future.rainProbability)
    }
    @Test fun thunderstormAdvicePrioritizesShelterOverEquipment() {
        val p=plan(data(24.0).copy(current=CurrentWeather(24.0,24.0,WeatherCondition.THUNDERSTORM)))
        assertTrue(p.caution!!.contains("安全室内"));assertEquals(p.caution,p.summary)
    }
    @Test fun coldSunnyWeatherKeepsHeadWarmInsteadOfSuggestingOnlyASunhat() {
        val p=plan(data(-12.0).copy(current=CurrentWeather(-12.0,-12.0,uvIndex=6)))
        assertTrue(p.extras.any {it.kind=="warmhat"});assertTrue(p.extras.any {it.kind=="glasses"})
        assertFalse(p.extras.any {it.kind=="sunhat"})
    }
    @Test fun clothingSuggestionsDoNotMutateWeatherOrBorrowSupplierDressIndex() {
        val d=data(30.0).copy(extraIndices=listOf(LifeIndexExtra("穿衣","DRESS","极冷")))
        val before=d.copy();assertEquals(0,plan(d).level);assertEquals(before,d)
    }
}
