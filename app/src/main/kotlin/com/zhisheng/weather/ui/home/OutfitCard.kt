package com.zhisheng.weather.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.theme.*
import com.zhisheng.weather.ui.weatherPresentationTime

private fun outfitBasis(plan: OutfitPlan): String = when {
    plan.mixedTemperatureBasis -> "体感与气温参考"
    plan.usesFeelsLike -> "体感参考"
    else -> "气温参考"
}
private fun outfitRange(plan: OutfitPlan, unit: String): String =
    if (plan.high - plan.low < .5) "${Fmt.temp(plan.low,unit)}${Fmt.unitSuffix(unit)}"
    else "${Fmt.temp(plan.low,unit)}° 至 ${Fmt.temp(plan.high,unit)}${Fmt.unitSuffix(unit)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutfitCard(data: WeatherData, unit: String, modifier: Modifier = Modifier, windUnit: String = "kmh",
    state:OutfitUiState=rememberOutfitUiState(),cityName:String="",onOpen:((OutfitDialogRequest)->Unit)?=null) {
    val now = weatherPresentationTime()
    val savedWindow=state.windowCode;val savedFeeling=state.feelingCode;val savedActivity=state.activityCode
    val paletteIndex=state.paletteIndex
    val advice = remember(data, now / 60_000L, savedWindow, savedFeeling, savedActivity) {
        outfitAdvice(data, now, OutfitWindow.entries[savedWindow.coerceIn(0,1)], OutfitFeeling.entries[savedFeeling.coerceIn(0,2)], OutfitActivity.entries[savedActivity.coerceIn(0,2)])
    }
    val savedVariants=state.variantsFor(advice.plan?.level)
    var localRequest by rememberSaveable(stateSaver=OutfitDialogRequest.Saver) {mutableStateOf<OutfitDialogRequest?>(null)}
    fun openWardrobe() {
        val request=OutfitDialogRequest.create(data,unit,windUnit,cityName,now)
        if(onOpen!=null) onOpen(request) else localRequest=request
    }
    if(onOpen==null) localRequest?.let {OutfitWardrobeOverlay(it,state) {localRequest=null}}
    HudCard(modifier.fillMaxWidth()) {
        val plan = advice.plan
        if (plan == null) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutfitSlotGlyph(OutfitSlot.OUTER, ZhishengMint, Modifier.size(36.dp))
                Text("先看天气，再来搭配", style = MaterialTheme.typography.titleLarge, color = ZhishengText)
                Text(advice.unavailable.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
                val future=remember(data,now/60_000L) {outfitAdvice(data,now,OutfitWindow.SIX_HOURS)}
                if(future.plan!=null) TextButton(onClick = { state.windowCode=1;openWardrobe() }) { Text("看看接下来的搭配", color = ZhishengMint) }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(plan.title, style = MaterialTheme.typography.titleLarge, color = ZhishengText, fontWeight = FontWeight.SemiBold)
                    Text(plan.summary, style = MaterialTheme.typography.bodySmall, color = if(plan.caution!=null) ZhishengOrange else ZhishengTextSecondary)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if(plan.window==OutfitWindow.NOW) "${Fmt.temp(plan.temperature,unit)}${Fmt.unitSuffix(unit)}" else outfitRange(plan,unit),
                        style = if(plan.window==OutfitWindow.NOW) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium, color = ZhishengMint)
                    Text(if(plan.window==OutfitWindow.SIX_HOURS) "6小时参考" else if(plan.usesFeelsLike) "体感" else "气温", style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                }
            }
            BoxWithConstraints {
                val stacked = maxWidth < 285.dp || LocalDensity.current.fontScale > 1.3f
                val gear = plan.gear.mapIndexed { i,item -> item.choices[savedVariants.getOrElse(i){0}.coerceIn(item.choices.indices)] }
                val colors = outfitColors[paletteIndex.mod(outfitColors.size)]
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutfitFigure(gear, colors, null, plan.extras, LocalZhishengPalette.current.isLight,
                        Modifier.fillMaxWidth().height(225.dp))
                    OutfitGearGrid(plan, gear, null) { state.selectedSlot=it;openWardrobe() }
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutfitFigure(gear, colors, null, plan.extras, LocalZhishengPalette.current.isLight,
                        Modifier.weight(.91f).height(254.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        plan.gear.forEachIndexed { index, item -> OutfitEquipment(item, gear[index], false) { state.selectedSlot=index;openWardrobe() } }
                    }
                }
            }
            OutfitNeedTags(plan)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${outfitColors[paletteIndex.mod(outfitColors.size)].name}配色", Modifier.heightIn(min=48.dp).clickable(role=Role.Button) { state.paletteIndex=(paletteIndex+1)%outfitColors.size }.padding(vertical=12.dp),
                    style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                TextButton(onClick = { openWardrobe() }, contentPadding = PaddingValues(horizontal=14.dp, vertical=10.dp)) {
                    Text("搭配一下  ↗", color = ZhishengMint, style = MaterialTheme.typography.labelLarge)
                }
            }
            plan.freshnessNote?.let { Text(it,style=MaterialTheme.typography.labelSmall,color=ZhishengTextTertiary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutfitWardrobeOverlay(request:OutfitDialogRequest,state:OutfitUiState,onClose:()->Unit) {
    val now=weatherPresentationTime()
    val window=state.windowCode;val feeling=state.feelingCode;val activity=state.activityCode
    val plan=remember(request.weather,now/60_000L,window,feeling,activity) {
        outfitAdvice(request.weather,now,OutfitWindow.entries[window.coerceIn(0,1)],
            OutfitFeeling.entries[feeling.coerceIn(0,2)],OutfitActivity.entries[activity.coerceIn(0,2)]).plan
    }
    ScrollableModalBottomSheet(onDismissRequest=onClose,sheetState=rememberScrollableSheetState(skipPartiallyExpanded=true),
        containerColor=ZhishengSurface.copy(alpha=1f),tonalElevation=0.dp) {
        com.zhisheng.weather.ui.HighRefreshDialogContent()
        OutfitWardrobe(request.weather,request.unit,state.paletteIndex,{state.paletteIndex=it},state.selectedSlot,{state.selectedSlot=it},
            onClose=onClose,windUnit=request.windUnit,initialWindow=state.windowCode,initialFeeling=state.feelingCode,initialActivity=state.activityCode,
            onPreferencesChanged={w,f,a->state.windowCode=w;state.feelingCode=f;state.activityCode=a},
            initialVariants=state.variantsFor(plan?.level),onVariantsChanged={level,values->state.selectVariants(level,values)},cityName=request.cityName)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutfitNeedTags(plan: OutfitPlan) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        val labels = plan.needs.map { it.label } + plan.extras.map { it.label }
        labels.ifEmpty { listOf("舒适穿搭") }.distinct().forEach { label ->
            Text(label, Modifier.clip(RoundedCornerShape(8.dp)).background(ZhishengMint.copy(alpha=.07f)).padding(horizontal=10.dp,vertical=6.dp),
                style=MaterialTheme.typography.labelSmall,color=ZhishengMint)
        }
    }
}

@Composable
private fun OutfitEquipment(item: OutfitGear, garment: OutfitGarment, selected: Boolean, modifier: Modifier=Modifier, onClick: ()->Unit) {
    val shape=RoundedCornerShape(14.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(if(selected) ZhishengMint.copy(alpha=.13f) else ZhishengCard.copy(alpha=.34f))
        .border(if(selected) 1.dp else .7.dp,if(selected) ZhishengMint.copy(alpha=.6f) else ZhishengCardBorder.copy(alpha=.38f),shape)
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=10.dp,vertical=9.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutfitSlotGlyph(item.slot,if(selected) ZhishengMint else ZhishengTextSecondary,Modifier.size(25.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(item.slot.label,style=MaterialTheme.typography.labelSmall,color=ZhishengTextTertiary)
            Text(garment.label,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium,color=ZhishengText)
        }
    }
}

@Composable
private fun OutfitGearGrid(plan: OutfitPlan, gear: List<OutfitGarment>, selected: Int?, onSelect: (Int)->Unit) {
    val large=LocalDensity.current.fontScale>1.45f
    plan.gear.indices.toList().chunked(if(large) 1 else 2).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            row.forEach { index -> OutfitEquipment(plan.gear[index],gear[index],index==selected,Modifier.weight(1f)) {onSelect(index)} }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OutfitWardrobe(data: WeatherData, unit: String, paletteIndex: Int=0, onPalette: (Int)->Unit={},
    selectedSlot: Int=1, onSlot: (Int)->Unit={}, onClose: ()->Unit={}, windUnit: String="kmh",
    initialWindow: Int=0, initialFeeling: Int=1, initialActivity: Int=0, onPreferencesChanged:(Int,Int,Int)->Unit={_,_,_->},
    initialVariants:List<Int> = listOf(0,0,0,0),onVariantsChanged:(Int,List<Int>)->Unit={_,_->},cityName:String="") {
    var windowCode by rememberSaveable { mutableIntStateOf(initialWindow.coerceIn(0,1)) }
    var feelingCode by rememberSaveable { mutableIntStateOf(initialFeeling.coerceIn(0,2)) }
    var activityCode by rememberSaveable { mutableIntStateOf(initialActivity.coerceIn(0,2)) }
    var referencesOpen by rememberSaveable { mutableStateOf(false) }
    val window=OutfitWindow.entries[windowCode.coerceIn(0,1)]
    val feeling=OutfitFeeling.entries[feelingCode.coerceIn(0,2)]
    val activity=OutfitActivity.entries[activityCode.coerceIn(0,2)]
    val now=weatherPresentationTime()
    val advice=remember(data,now/60_000L,window,feeling,activity) {outfitAdvice(data,now,window,feeling,activity)}
    val plan=advice.plan
    var variants by rememberSaveable(plan?.level,windowCode,feelingCode,activityCode) { mutableStateOf(initialVariants.takeIf {it.size==4} ?: listOf(0,0,0,0)) }
    val haptic=LocalHapticFeedback.current
    val density=LocalDensity.current
    val colors=outfitColors[paletteIndex.mod(outfitColors.size)]
    val gear=remember(plan,variants) {plan?.gear?.mapIndexed { i,item -> item.choices[variants.getOrElse(i){0}.coerceIn(item.choices.indices)] }.orEmpty()}
    // Keep choices above the lazy items: leaving the viewport must never reset a selection.
    // Only visible sections (and the next prefetched section) need composition and layout.
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.94f),
        contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=32.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item(key="header") {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("天气穿搭",style=MaterialTheme.typography.headlineMedium,color=ZhishengText,fontWeight=FontWeight.Bold)
                Text(if(cityName.isBlank()) "选一套，看看怎么穿" else "$cityName · 参考打开时的天气",style=MaterialTheme.typography.bodySmall,color=ZhishengTextSecondary)
            }
            TextButton(onClick=onClose) { Text("关闭",color=ZhishengTextSecondary) }
        }
        }
        item(key="window") {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutfitWindow.entries.forEachIndexed { index,value ->
                FilterChip(selected=windowCode==index,onClick={windowCode=index;onPreferencesChanged(index,feelingCode,activityCode)},label={Text(value.label)},modifier=Modifier.weight(1f))
            }
        }
        }
        if(plan==null) {
            item(key="unavailable") {
            Text(advice.unavailable.orEmpty(),style=MaterialTheme.typography.titleMedium,color=ZhishengTextSecondary)
            }
        } else {
            item(key="summary") {
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(plan.title,style=MaterialTheme.typography.headlineSmall,color=ZhishengText,fontWeight=FontWeight.SemiBold)
                Text("${outfitBasis(plan)} · ${outfitRange(plan,unit)}",style=MaterialTheme.typography.bodyMedium,color=ZhishengMint)
                if(window==OutfitWindow.SIX_HOURS) Text(
                    plan.forecastUntil?.let { "参考${plan.forecastCount}条逐时预报 · 最晚 ${Fmt.stamp(it,data.utcOffsetSeconds)}" }
                        ?: "缺少接下来的逐时预报，暂按当前天气参考",
                    style=MaterialTheme.typography.labelSmall,color=ZhishengTextTertiary)
            }
            }
            item(key="stage") {
            val stageShape=RoundedCornerShape(26.dp)
            Column(Modifier.fillMaxWidth().clip(stageShape).background(ZhishengMint.copy(alpha=.045f)).border(.7.dp,ZhishengCardBorder.copy(alpha=.5f),stageShape).padding(14.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("${colors.name} · 当前搭配",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=ZhishengTextSecondary)
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        outfitColors.forEachIndexed { index,palette ->
                            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role=Role.Button) {onPalette(index);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
                                .semantics {contentDescription="${palette.name}配色${if(index==paletteIndex) "，已选" else ""}"},contentAlignment=Alignment.Center) {
                                Box(Modifier.size(24.dp).border(if(index==paletteIndex) 2.dp else .5.dp,if(index==paletteIndex) ZhishengMint else ZhishengCardBorder,CircleShape).padding(3.dp).background(palette.coat,CircleShape))
                            }
                        }
                    }
                }
                BoxWithConstraints {
                    if(maxWidth>=480.dp && density.fontScale<=1.3f) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                        OutfitFigure(gear,colors,OutfitSlot.entries[selectedSlot.coerceIn(0,3)],plan.extras,LocalZhishengPalette.current.isLight,Modifier.weight(.65f).height(170.dp))
                        Column(Modifier.weight(1.35f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutfitGearGrid(plan,gear,selectedSlot) {onSlot(it);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
                        }
                    } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        OutfitFigure(gear,colors,OutfitSlot.entries[selectedSlot.coerceIn(0,3)],plan.extras,LocalZhishengPalette.current.isLight,
                            Modifier.fillMaxWidth().height(if(density.fontScale>1.4f) 270.dp else 310.dp))
                        OutfitGearGrid(plan,gear,selectedSlot) {onSlot(it);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
                    }
                }
                val selected=plan.gear[selectedSlot.coerceIn(0,3)]
                Column(Modifier.padding(horizontal=3.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(selected.reason,style=MaterialTheme.typography.bodyMedium,color=ZhishengTextSecondary)
                    if(selected.choices.size>1) TextButton(onClick={
                        val index=selectedSlot.coerceIn(0,3);variants=variants.toMutableList().apply {this[index]=(this[index]+1)%selected.choices.size}
                        onVariantsChanged(plan.level,variants)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },contentPadding=PaddingValues(horizontal=0.dp)) {
                        Text(when(selected.slot) { OutfitSlot.BASE -> "换件上装  ↻"; OutfitSlot.OUTER -> "换件外搭  ↻"; OutfitSlot.BOTTOM -> "换条裤子  ↻"; OutfitSlot.SHOES -> "换双鞋  ↻" },color=ZhishengMint,style=MaterialTheme.typography.labelLarge)
                    }
                }
            }
            }
            item(key="needs") { OutfitNeedTags(plan) }
            plan.caution?.let { caution -> item(key="caution") {
                Text(caution,Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ZhishengOrange.copy(alpha=.09f)).padding(14.dp),
                    style=MaterialTheme.typography.bodyMedium,color=ZhishengOrange)
            } }
            item(key="preferences") {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("出门怎么安排",style=MaterialTheme.typography.titleSmall,color=ZhishengText)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    OutfitActivity.entries.forEachIndexed { i,value -> FilterChip(selected=activityCode==i,onClick={activityCode=i;onPreferencesChanged(windowCode,feelingCode,i)},label={Text(value.label)}) }
                }
                Text("你平时的冷暖感受",style=MaterialTheme.typography.titleSmall,color=ZhishengText)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    OutfitFeeling.entries.forEachIndexed { i,value -> FilterChip(selected=feelingCode==i,onClick={feelingCode=i;onPreferencesChanged(windowCode,i,activityCode)},label={Text(value.label)}) }
                }
            }
            }
            item(key="notes") {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("这套搭配的考虑",style=MaterialTheme.typography.titleSmall,color=ZhishengText)
                Text(plan.summary,style=MaterialTheme.typography.bodyMedium,color=ZhishengTextSecondary)
                plan.notes.forEach {Text("· $it",style=MaterialTheme.typography.bodyMedium,color=ZhishengTextSecondary)}
                plan.freshnessNote?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=ZhishengTextTertiary)}
            }
            }
            item(key="divider") { HorizontalDivider(color=ZhishengCardBorder.copy(alpha=.4f)) }
            item(key="references") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={referencesOpen=!referencesOpen},contentPadding=PaddingValues(0.dp)) {
                    Text(if(referencesOpen) "收起建议依据  ↑" else "建议依据与说明  ↓",color=ZhishengTextSecondary)
                }
                if(referencesOpen) {
                    Text("冷暖分级优先参考数据源提供的体感温度，风雨、紫外线和所选时段另作考虑。衣物是搭配示例，可用相近厚薄的现有衣服替换。",style=MaterialTheme.typography.bodySmall,color=ZhishengTextSecondary)
                    Text("${outfitBasis(plan)} ${outfitRange(plan,unit)} · 风速 ${Fmt.wind(plan.windKmh,windUnit) ?: "暂无"}${plan.gustKmh?.let { " · 阵风 ${Fmt.wind(it,windUnit)}" }.orEmpty()} · 紫外线 ${plan.uv?.toString() ?: "暂无"}",style=MaterialTheme.typography.bodySmall,color=ZhishengTextTertiary)
                    plan.rainProbability?.let {Text("所选预报时段降水概率最高 $it%",style=MaterialTheme.typography.bodySmall,color=ZhishengTextTertiary)}
                    val uri=LocalUriHandler.current;val context=LocalContext.current
                    outfitReferences.forEach { (title,url) -> Text(title,Modifier.fillMaxWidth().clickable(role=Role.Button) {
                        runCatching {uri.openUri(url)}.onFailure {Toast.makeText(context,"暂时无法打开链接",Toast.LENGTH_SHORT).show()}
                    }.heightIn(min=48.dp).padding(vertical=12.dp),style=MaterialTheme.typography.bodySmall,color=ZhishengMint) }
                    Text("建议用于日常出门参考，个人活动量与冷暖感受仍需自行调整。",style=MaterialTheme.typography.bodySmall,color=ZhishengTextTertiary)
                }
            }
            }
        }
    }
}
