package com.zhisheng.weather.ui.home

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.zhisheng.weather.model.WeatherData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Owned above the coordinate-keyed city pages, never by a recycled list item. */
@Stable
internal class OutfitUiState {
    var windowCode by mutableIntStateOf(0)
    var feelingCode by mutableIntStateOf(1)
    var activityCode by mutableIntStateOf(0)
    var paletteIndex by mutableIntStateOf(0)
    var selectedSlot by mutableIntStateOf(1)
    private var variantKey by mutableStateOf("")
    private var variants by mutableStateOf(listOf(0,0,0,0))
    private fun context(level: Int?) = "$level/$windowCode/$feelingCode/$activityCode"
    fun variantsFor(level: Int?): List<Int> = if(variantKey==context(level)) variants else listOf(0,0,0,0)
    fun selectVariants(level: Int, values: List<Int>) {
        if(values.size!=4) return
        variantKey=context(level);variants=values.map {it.coerceIn(0,16)}
    }
    companion object {
        val Saver= listSaver<OutfitUiState,Any>(save={listOf(it.windowCode,it.feelingCode,it.activityCode,it.paletteIndex,it.selectedSlot,it.variantKey)+it.variants},
            restore={ saved -> runCatching {
                OutfitUiState().apply {
                    windowCode=(saved[0] as Int).coerceIn(0,1);feelingCode=(saved[1] as Int).coerceIn(0,2)
                    activityCode=(saved[2] as Int).coerceIn(0,2);paletteIndex=(saved[3] as Int).coerceIn(0,2)
                    selectedSlot=(saved[4] as Int).coerceIn(0,3);variantKey=(saved[5] as String).take(64)
                    variants=(6..9).map {(saved[it] as Int).coerceIn(0,16)}
                }
            }.getOrNull() })
    }
}

@Composable
internal fun rememberOutfitUiState(): OutfitUiState = rememberSaveable(saver=OutfitUiState.Saver) {OutfitUiState()}

private val outfitStateJson=Json {ignoreUnknownKeys=true;allowSpecialFloatingPointValues=true}
@Serializable
internal data class OutfitDialogRequest(val weather: WeatherData,val unit:String,val windUnit:String,val cityName:String) {
    companion object {
        // Keep saved activity state small, without location coordinates or unused rain/daily datasets.
        fun create(data:WeatherData,unit:String,windUnit:String,cityName:String,now:Long):OutfitDialogRequest =
            OutfitDialogRequest(WeatherData(current=data.current,
                hourly=data.hourly.filter {it.timeMillis in now..now+7*3_600_000L},
                alerts=data.alerts.map {it.copy(detail=null)},updateTime=data.updateTime,fetchedAt=data.fetchedAt,
                utcOffsetSeconds=data.utcOffsetSeconds,dataSource=data.dataSource,
                moduleEvidence=data.moduleEvidence["current"]?.let {mapOf("current" to it.copy(requestedLatitude=null,requestedLongitude=null))} ?: emptyMap()),
                unit,windUnit,cityName.take(100))
        val Saver:Saver<OutfitDialogRequest?,Any> = Saver(
            save={ request -> request?.let {runCatching {outfitStateJson.encodeToString(it).takeIf {text->text.length<=48_000}}.getOrNull()} },
            restore={ value -> val text=value as? String
                if(text==null || text.length>48_000) null else runCatching {outfitStateJson.decodeFromString<OutfitDialogRequest>(text)}.getOrNull() })
    }
}
