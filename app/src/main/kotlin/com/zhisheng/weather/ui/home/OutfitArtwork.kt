package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal data class OutfitColors(val name: String, val coat: Color, val shirt: Color, val trousers: Color, val accent: Color)
internal val outfitColors = listOf(
    OutfitColors("青岚", Color(0xFF4B7775), Color(0xFFE9E9DC), Color(0xFF354B59), Color(0xFFCAAB78)),
    OutfitColors("沙丘", Color(0xFFB29A79), Color(0xFFF0EBDE), Color(0xFF525C58), Color(0xFF5B8580)),
    OutfitColors("夜航", Color(0xFF52617E), Color(0xFFBACBD5), Color(0xFF303F51), Color(0xFFC3A578)),
)

/** One native vector drawing. No WebView, texture downloads, or continuous animation. */
@Composable
internal fun OutfitFigure(gear: List<OutfitGarment>, colors: OutfitColors, selected: OutfitSlot?,
    extras: List<OutfitExtra>, light: Boolean, modifier: Modifier = Modifier) {
    val outer = gear.getOrNull(1) ?: OutfitGarment.NO_COAT
    val base = gear.firstOrNull() ?: OutfitGarment.TEE
    val pants = gear.getOrNull(2) ?: OutfitGarment.PANTS
    val shoes = gear.getOrNull(3) ?: OutfitGarment.SNEAKERS
    val silhouette = "搭配示意：" + gear.joinToString("、") { it.label }
    val figure = rememberGraphicsLayer()
    Spacer(modifier.semantics { contentDescription = silhouette }.drawWithCache {
        val viewport = size
        // Preserve the halo outside the figure bounds at native density.
        val apron = ceil(minOf(size.width / 180f, size.height / 312f) * 32f).toInt()
        figure.compositingStrategy = CompositingStrategy.Offscreen
        figure.record(size = IntSize(ceil(size.width).toInt() + apron * 2,
            ceil(size.height).toInt() + apron * 2)) {
            translate(apron.toFloat(), apron.toFloat()) {
        val scale = minOf(viewport.width / 180f, viewport.height / 312f)
        withTransform({ translate((viewport.width - 180f * scale) / 2f, (viewport.height - 312f * scale) / 2f); scale(scale, scale, Offset.Zero) }) {
            val ink = if (light) Color(0xFF1C403F) else Color(0xFFE3F0EC)
            val ceramic = if (light) Color(0xFFD6DFDA) else Color(0xFF899D9F)
            val shadow = Color(0xFF173F43)
            drawCircle(Brush.radialGradient(listOf(colors.coat.copy(alpha=.13f), Color.Transparent), Offset(90f,141f), 118f),118f, Offset(90f,141f))
            drawOval(shadow.copy(alpha = if(light) .1f else .25f), Offset(26f,284f),Size(132f,17f))
            drawOval(ink.copy(alpha=.14f),Offset(27f,283f),Size(131f,18f),style=Stroke(.6f))
            // Ceramic display form keeps the clothing central, without assuming age, gender or body details.
            drawRoundRect(ceramic, Offset(80f,48f),Size(20f,24f),CornerRadius(6f))
            drawOval(Brush.linearGradient(listOf(ceramic,Color(0xFFF3F0E4)),Offset(73f,12f),Offset(106f,59f)),Offset(73f,12f),Size(35f,45f))
            drawPath(Path().apply {moveTo(76f,43f);quadraticTo(91f,54f,105f,42f)},ceramic.copy(alpha=.45f),style=Stroke(1f))

            val shorts = pants == OutfitGarment.SHORTS
            val trouserPath = Path().apply {
                moveTo(62f,151f);quadraticTo(87f,146f,117f,153f)
                lineTo(117f,if(shorts) 218f else 272f);lineTo(96f,if(shorts) 220f else 273f)
                lineTo(88f,192f);lineTo(77f,if(shorts) 219f else 273f);lineTo(55f,if(shorts) 217f else 271f)
                lineTo(62f,151f);close()
            }
            if(shorts) {
                drawRoundRect(ceramic, Offset(57f,210f),Size(19f,68f),CornerRadius(7f))
                drawRoundRect(ceramic, Offset(97f,210f),Size(19f,68f),CornerRadius(7f))
            }
            drawPath(trouserPath,Brush.linearGradient(listOf(colors.trousers,colors.trousers.copy(red=(colors.trousers.red*.66f))),Offset(50f,160f),Offset(125f,268f)))
            drawPath(Path().apply {moveTo(89f,165f);lineTo(88f,192f);moveTo(69f,177f);quadraticTo(65f,211f,66f,if(shorts) 216f else 264f);moveTo(108f,178f);lineTo(107f,if(shorts) 217f else 264f)}, Color.White.copy(alpha=.11f),style=Stroke(.9f))
            drawLine(colors.trousers.copy(alpha=.6f),Offset(62f,158f),Offset(116f,158f),3f)
            drawLine(colors.accent.copy(alpha=.75f),Offset(83f,157f),Offset(93f,157f),2.2f)
            if(selected == OutfitSlot.BOTTOM)drawPath(trouserPath,colors.accent,style=Stroke(2f))

            val sleevesShort = base in setOf(OutfitGarment.TEE,OutfitGarment.POLO)
            val torso = Path().apply {
                moveTo(77f,63f);quadraticTo(90f,70f,103f,63f);lineTo(122f,72f)
                lineTo(if(sleevesShort) 136f else 145f,if(sleevesShort) 108f else 157f)
                lineTo(if(sleevesShort) 119f else 130f,if(sleevesShort) 115f else 163f)
                lineTo(116f,97f);lineTo(119f,155f);quadraticTo(89f,165f,59f,155f);lineTo(63f,97f)
                lineTo(if(sleevesShort) 58f else 49f,if(sleevesShort) 115f else 163f)
                lineTo(if(sleevesShort) 42f else 34f,if(sleevesShort) 108f else 157f)
                lineTo(57f,72f);close()
            }
            // Arms underneath short sleeves, cuffs underneath long ones.
            drawPath(Path().apply {moveTo(46f,103f);lineTo(58f,109f);lineTo(49f,166f);quadraticTo(47f,176f,40f,171f);lineTo(38f,161f);close()},ceramic)
            drawPath(Path().apply {moveTo(120f,108f);lineTo(132f,103f);lineTo(142f,162f);quadraticTo(144f,174f,136f,173f);lineTo(131f,164f);close()},ceramic)
            drawPath(torso,Brush.linearGradient(listOf(colors.shirt,colors.shirt.copy(red=colors.shirt.red*.79f,green=colors.shirt.green*.83f,blue=colors.shirt.blue*.85f)),Offset(55f,65f),Offset(125f,166f)))
            drawPath(Path().apply {moveTo(77f,64f);quadraticTo(90f,82f,103f,64f)},colors.trousers.copy(alpha=.25f),style=Stroke(2f))
            if(base == OutfitGarment.SHIRT || base == OutfitGarment.POLO) {
                drawPath(Path().apply {moveTo(77f,64f);lineTo(79f,82f);lineTo(90f,73f);lineTo(101f,82f);lineTo(104f,64f)},colors.shirt,style=Stroke(2f))
                drawLine(colors.trousers.copy(alpha=.2f),Offset(90f,76f),Offset(90f,if(base==OutfitGarment.POLO) 101f else 153f),.8f)
                for(y in 87..145 step 17)if(base==OutfitGarment.SHIRT || y<102)drawCircle(colors.trousers.copy(alpha=.45f),.9f,Offset(90f,y.toFloat()))
            } else if(base in setOf(OutfitGarment.KNIT,OutfitGarment.WARM_BASE)) {
                for(x in 67..112 step 5) drawLine(colors.trousers.copy(alpha=.09f),Offset(x.toFloat(),87f),Offset(x.toFloat(),151f),.7f)
            }
            if(selected == OutfitSlot.BASE)drawPath(torso,colors.accent,style=Stroke(2f))

            if(outer != OutfitGarment.NO_COAT) {
                val long = outer in setOf(OutfitGarment.DOWN,OutfitGarment.COAT)
                val hem = if(long) 205f else 161f
                val coat = Path().apply {
                    moveTo(76f,62f);lineTo(60f,67f);quadraticTo(53f,69f,49f,82f);lineTo(29f,157f)
                    quadraticTo(37f,164f,50f,164f);lineTo(63f,104f);lineTo(56f,hem)
                    quadraticTo(72f,hem+7f,87f,hem+2f);lineTo(89f,83f);lineTo(94f,hem+2f)
                    quadraticTo(111f,hem+7f,124f,hem);lineTo(115f,104f);lineTo(130f,165f)
                    quadraticTo(144f,164f,149f,157f);lineTo(130f,82f);quadraticTo(127f,69f,119f,67f)
                    lineTo(104f,62f);lineTo(96f,76f);lineTo(89f,82f);lineTo(82f,74f);close()
                }
                drawPath(coat,Brush.linearGradient(listOf(colors.coat.copy(red=(colors.coat.red*1.12f).coerceAtMost(1f)),colors.coat,colors.coat.copy(red=colors.coat.red*.67f,green=colors.coat.green*.72f,blue=colors.coat.blue*.76f)),Offset(43f,74f),Offset(132f,hem)))
                drawPath(Path().apply {moveTo(76f,62f);lineTo(67f,76f);lineTo(83f,93f);lineTo(89f,82f);moveTo(104f,62f);lineTo(112f,76f);lineTo(96f,93f);lineTo(89f,82f)},Color.White.copy(alpha=.29f),style=Stroke(1.2f))
                val padded=outer in setOf(OutfitGarment.PUFFER,OutfitGarment.DOWN,OutfitGarment.JACKET)
                if(padded)for(y in 103..(hem.toInt()-10) step 18) {
                    drawPath(Path().apply {moveTo(62f,y.toFloat());quadraticTo(73f,y+3f,86f,y+1f);moveTo(97f,y+1f);quadraticTo(109f,y+3f,119f,y.toFloat())},Color.Black.copy(alpha=.14f),style=Stroke(1f))
                }
                drawLine(Color.White.copy(alpha=.28f),Offset(90f,89f),Offset(90f,hem-5f),1.1f)
                drawCircle(colors.accent,1.8f,Offset(92f,97f))
                drawLine(Color.Black.copy(alpha=.22f),Offset(67f,130f),Offset(77f,137f),2f,StrokeCap.Round)
                drawLine(Color.White.copy(alpha=.22f),Offset(103f,137f),Offset(114f,131f),1.5f,StrokeCap.Round)
                drawLine(colors.coat.copy(alpha=.7f),Offset(33f,157f),Offset(49f,161f),4f)
                drawLine(colors.coat.copy(alpha=.7f),Offset(132f,161f),Offset(146f,157f),4f)
                if(selected == OutfitSlot.OUTER)drawPath(coat,colors.accent,style=Stroke(2f))
            }

            val boots=shoes == OutfitGarment.BOOTS
            val footwearColor=if(shoes == OutfitGarment.SNEAKERS) colors.shirt else colors.trousers
            for(right in listOf(false,true)) {
                val x=if(right) 98f else 55f
                val shoe=Path().apply {
                    moveTo(x,if(boots) 255f else 269f);lineTo(x+18f,if(boots) 255f else 269f)
                    lineTo(x+20f,275f);quadraticTo(x+31f,276f,x+33f,283f);lineTo(x+32f,289f)
                    lineTo(x-3f,289f);lineTo(x-4f,280f);close()
                }
                drawPath(shoe,footwearColor)
                drawLine(colors.accent.copy(alpha=.75f),Offset(x-3f,286f),Offset(x+31f,286f),3f,StrokeCap.Round)
                for(y in 273..281 step 3)drawLine(colors.shirt.copy(alpha=.65f),Offset(x+7f,y.toFloat()),Offset(x+17f,y+1f),.8f)
                if(selected == OutfitSlot.SHOES)drawPath(shoe,colors.accent,style=Stroke(1.8f))
            }
            if(extras.any {it.kind=="scarf"}) {
                drawPath(Path().apply {moveTo(77f,60f);quadraticTo(91f,70f,105f,60f);lineTo(106f,70f);lineTo(94f,76f);lineTo(95f,119f);lineTo(83f,115f);lineTo(84f,75f);lineTo(76f,70f);close()},colors.accent)
                drawLine(Color.White.copy(alpha=.27f),Offset(88f,82f),Offset(89f,111f),1.1f)
            }
            if(extras.any {it.kind=="sunhat"}) {
                drawPath(Path().apply {moveTo(75f,25f);lineTo(79f,9f);quadraticTo(91f,3f,102f,11f);lineTo(105f,25f);close()},colors.accent)
                drawOval(colors.accent,Offset(61f,21f),Size(56f,9f))
            }
            if(extras.any {it.kind=="warmhat"}) {
                drawPath(Path().apply {moveTo(72f,28f);quadraticTo(72f,7f,89f,7f);quadraticTo(108f,7f,109f,28f);close()},colors.coat)
                drawRoundRect(colors.coat.copy(red=(colors.coat.red*1.16f).coerceAtMost(1f)),Offset(71f,23f),Size(39f,9f),CornerRadius(3f))
                drawRoundRect(colors.coat,Offset(71f,29f),Size(6f,12f),CornerRadius(3f))
                drawRoundRect(colors.coat,Offset(104f,29f),Size(6f,12f),CornerRadius(3f))
            }
            if(extras.any {it.kind=="glasses"}) {
                drawRoundRect(colors.trousers.copy(alpha=.9f),Offset(78f,30f),Size(11f,7f),CornerRadius(2f))
                drawRoundRect(colors.trousers.copy(alpha=.9f),Offset(92f,30f),Size(11f,7f),CornerRadius(2f))
                drawLine(colors.trousers,Offset(88f,32f),Offset(93f,32f),1f)
            }
            if(extras.any {it.label.contains("手套")}) {
                drawOval(colors.trousers,Offset(37f,160f),Size(11f,15f))
                drawOval(colors.trousers,Offset(132f,160f),Size(11f,15f))
            }
            if(extras.any {it.kind=="umbrella"}) {
                drawLine(colors.trousers,Offset(155f,214f),Offset(151f,280f),1.2f)
                drawPath(Path().apply {moveTo(155f,210f);quadraticTo(146f,224f,147f,260f);lineTo(158f,261f);quadraticTo(162f,227f,155f,210f);close()},colors.accent)
                drawPath(Path().apply {moveTo(151f,278f);quadraticTo(151f,287f,159f,282f)},colors.trousers,style=Stroke(1.8f,cap=StrokeCap.Round))
            }
            drawCircle(colors.accent,2f,Offset(17f,288f));drawLine(ink.copy(alpha=.2f),Offset(23f,288f),Offset(29f,288f),.7f)
        }

            }
        }
        onDrawBehind {
            translate(-apron.toFloat(), -apron.toFloat()) { drawLayer(figure) }
        }
    })
}

@Composable
internal fun OutfitSlotGlyph(slot: OutfitSlot, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        withTransform({ scale(size.width/32f,size.height/32f,Offset.Zero) }) {
            val p=Path()
            when(slot) {
                OutfitSlot.BASE,OutfitSlot.OUTER -> {
                    p.moveTo(11f,6f);p.quadraticTo(16f,11f,21f,6f);p.lineTo(29f,12f);p.lineTo(25f,19f);p.lineTo(22f,17f);p.lineTo(22f,28f);p.lineTo(10f,28f);p.lineTo(10f,17f);p.lineTo(7f,19f);p.lineTo(3f,12f);p.close()
                    if(slot==OutfitSlot.OUTER){p.moveTo(16f,11f);p.lineTo(16f,28f)}
                }
                OutfitSlot.BOTTOM -> {p.moveTo(8f,5f);p.lineTo(24f,5f);p.lineTo(25f,28f);p.lineTo(18f,28f);p.lineTo(16f,14f);p.lineTo(14f,28f);p.lineTo(7f,28f);p.close();p.moveTo(9f,9f);p.lineTo(23f,9f)}
                OutfitSlot.SHOES -> {p.moveTo(5f,11f);p.lineTo(12f,14f);p.lineTo(18f,12f);p.lineTo(22f,19f);p.quadraticTo(29f,19f,29f,25f);p.lineTo(3f,25f);p.lineTo(3f,17f);p.close();p.moveTo(4f,22f);p.lineTo(28f,22f);p.moveTo(15f,15f);p.lineTo(18f,18f)}
            }
            drawPath(p,color.copy(alpha=.12f));drawPath(p,color,style=Stroke(1.4f))
        }
    }
}
