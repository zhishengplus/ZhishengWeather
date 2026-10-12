package com.zhisheng.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin

/** Painted landscape, separate from the solid obstacles. Parallax uses flight distance only. */
internal fun DrawScope.drawSkyBirdLandscape(state: SkyBirdState, light: Boolean) {
    val w = size.width
    val h = size.height
    val travel = state.distance * w / SkyBirdEngine.WIDTH
    val scenery = SkyBirdJourney.scenery(state.score)
    val dark = !light || scenery == SkyBirdScenery.COSMOS
    val top = when (scenery) {
        SkyBirdScenery.VALLEY -> if (light) Color(0xFF91C9DC) else Color(0xFF102B49)
        SkyBirdScenery.PRAIRIE -> if (light) Color(0xFFC9CFB0) else Color(0xFF353547)
        SkyBirdScenery.PEAKS -> if (light) Color(0xFFABC8EB) else Color(0xFF252D56)
        SkyBirdScenery.COAST -> if (light) Color(0xFFABB9DB) else Color(0xFF273251)
        SkyBirdScenery.COSMOS -> Color(0xFF171E43)
    }
    val horizon = when (scenery) {
        SkyBirdScenery.VALLEY -> if (light) Color(0xFFEDE8CC) else Color(0xFF506C87)
        SkyBirdScenery.PRAIRIE -> if (light) Color(0xFFF5D7A0) else Color(0xFF937163)
        SkyBirdScenery.PEAKS -> if (light) Color(0xFFE5E4F2) else Color(0xFF727699)
        SkyBirdScenery.COAST -> if (light) Color(0xFFF3CCB9) else Color(0xFFAD827F)
        SkyBirdScenery.COSMOS -> Color(0xFF4B477C)
    }
    drawRect(Brush.verticalGradient(listOf(top, horizon, if (light) Color(0xFFADCBC5) else Color(0xFF284E60))))
    val sun = Offset(w * .79f, h * .16f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE8AF).copy(alpha = if (!dark) .52f else .16f),
        Color.Transparent), sun, w * .58f), w * .58f, sun)
    if (!dark) drawCircle(Color(0xFFFFF0C8), w * .058f, sun)
    if (dark) {
        val r = w * .068f
        val moon = Path().apply {
            moveTo(sun.x + r * .35f, sun.y - r * .94f)
            cubicTo(sun.x - r * 1.15f, sun.y - r * .86f,
                sun.x - r * 1.15f, sun.y + r * .86f, sun.x + r * .35f, sun.y + r * .94f)
            cubicTo(sun.x - r * .55f, sun.y + r * .37f,
                sun.x - r * .55f, sun.y - r * .37f, sun.x + r * .35f, sun.y - r * .94f)
            close()
        }
        drawPath(moon, Color(0xFFE4EFF4))
        repeat(if (scenery == SkyBirdScenery.COSMOS) 63 else 31) { i ->
            val at = Offset(w * ((i * 47 % 101) / 101f), h * (.04f + (i * 29 % 43) / 100f))
            drawCircle(Color(0xFFEAF5F6).copy(alpha = .3f + (i % 4) * .13f), w * (.0015f + i % 2 * .001f), at)
            if (i % 9 == 0) {
                drawLine(Color.White.copy(alpha = .23f), at - Offset(w * .012f, 0f), at + Offset(w * .012f, 0f), w * .001f)
                drawLine(Color.White.copy(alpha = .23f), at - Offset(0f, w * .012f), at + Offset(0f, w * .012f), w * .001f)
            }
        }
    }
    // Thin high clouds drift past the distant ridgeline instead of repeating a row of circles.
    repeat(7) { i ->
        val span = w * (.18f + (i % 3) * .065f)
        val x = wrap(i * w * .31f - travel * .06f, w * 1.45f) - w * .22f
        val y = h * (.15f + (i * 17 % 28) / 100f)
        val cloud = Path().apply {
            moveTo(x, y)
            cubicTo(x + span * .25f, y - h * .022f, x + span * .4f, y - h * .026f, x + span, y)
            cubicTo(x + span * .65f, y + h * .014f, x + span * .25f, y + h * .007f, x, y)
            close()
        }
        drawPath(cloud, Color.White.copy(alpha = if (light) .23f else .075f))
    }
    when (scenery) {
        SkyBirdScenery.VALLEY -> {
    ridge(w, h, .54f, .07f, 0f, if (light) Color(0xFF91B8BD) else Color(0xFF42637E), 1.3f)
    ridge(w, h, .63f, .095f, 0f, if (light) Color(0xFF6C9FA7) else Color(0xFF30576E), 2.7f)
    // Mist separates the mountains so the foreground never reads as a single flat silhouette.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, horizon.copy(alpha = .32f), Color.Transparent),
        h * .50f, h * .75f), Offset(0f, h * .50f), Size(w, h * .25f))
    ridge(w, h, .77f, .078f, 0f, if (light) Color(0xFF467F87) else Color(0xFF234958), 5.1f)
    val river = Path().apply {
        moveTo(w * .58f, h * .67f)
        cubicTo(w * .24f, h * .76f, w * .87f, h * .81f, w * .37f, h)
        lineTo(w * .66f, h)
        cubicTo(w * .96f, h * .82f, w * .40f, h * .75f, w * .61f, h * .67f)
        close()
    }
    drawPath(river, Brush.verticalGradient(listOf(if (light) Color(0xFFC0DAD6) else Color(0xFF4D7882),
        if (light) Color(0xFF82BABB) else Color(0xFF315E6F)), h * .67f, h))
    repeat(9) { i ->
        val y = h * (.80f + i * .021f)
        val x = w * (.57f + sin(i * 1.2f) * .07f)
        drawLine(Color(0xFFF5E8BA).copy(alpha = if (light) .36f else .14f),
            Offset(x - w * .023f, y), Offset(x + w * .038f, y), w * .003f)
    }
    repeat(16) { i ->
        val x = wrap(i * w * .105f, w * 1.7f) - w * .12f
        val y = h * (.83f + sin(i * 1.7f) * .025f)
        val treeH = h * (.028f + (i % 4) * .012f)
        val tree = Path().apply {
            moveTo(x, y - treeH); lineTo(x - treeH * .32f, y - treeH * .23f)
            lineTo(x - treeH * .18f, y - treeH * .23f); lineTo(x - treeH * .44f, y)
            lineTo(x + treeH * .44f, y); lineTo(x + treeH * .18f, y - treeH * .23f)
            lineTo(x + treeH * .32f, y - treeH * .23f); close()
        }
        drawPath(tree, if (light) Color(0xFF326C76).copy(alpha = .48f) else Color(0xFF183C4D).copy(alpha = .65f))
    }
        }
        SkyBirdScenery.PRAIRIE -> drawPrairie(w, h, light)
        SkyBirdScenery.PEAKS -> drawAlpinePeaks(w, h, light)
        SkyBirdScenery.COAST -> drawCoast(w, h, light)
        SkyBirdScenery.COSMOS -> drawStarRealm(w, h)
    }
    // Broad low cloudbank frames the valley; the brighter collision floor is drawn separately.
    val mist = Path().apply {
        moveTo(0f, h * .95f)
        cubicTo(w * .13f, h * .87f, w * .30f, h * .94f, w * .43f, h * .92f)
        cubicTo(w * .66f, h * .86f, w * .88f, h * .92f, w, h * .88f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(mist, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (light) .38f else .12f), Color.Transparent),
        h * .9f, h))
    repeat(3) { i ->
        val x = wrap(w * (.24f + i * .27f) - travel * .12f, w * 1.2f)
        val y = h * (.35f + i * .034f)
        val flock = Path().apply {
            moveTo(x - w * .013f, y); quadraticTo(x - w * .006f, y - w * .008f, x, y)
            quadraticTo(x + w * .006f, y - w * .008f, x + w * .013f, y)
        }
        drawPath(flock, (if (light) Color(0xFF457B8A) else Color(0xFFACCBD1)).copy(alpha = .28f), style = Stroke(w * .0025f))
    }
}

private fun DrawScope.ridge(w: Float, h: Float, base: Float, amplitude: Float, drift: Float, color: Color, phase: Float) {
    val p = Path().apply {
        moveTo(0f, h)
        repeat(65) { i ->
            val x = w * i / 64f
            val t = (x + drift) / w
            val y = h * (base + (sin(t * 6.28f + phase) * .6f + sin(t * 13.4f + phase * 2f) * .4f) * amplitude)
            lineTo(x, y)
        }
        lineTo(w, h); close()
    }
    drawPath(p, Brush.verticalGradient(listOf(color, color.copy(alpha = .65f)), h * (base - amplitude), h))
}

private fun wrap(value: Float, span: Float) = ((value % span) + span) % span

private fun DrawScope.drawPrairie(w: Float, h: Float, light: Boolean) {
    ridge(w, h, .64f, .055f, 0f, if (light) Color(0xFFC0AD75) else Color(0xFF807663), 1.2f)
    ridge(w, h, .78f, .06f, 0f, if (light) Color(0xFFAB9256) else Color(0xFF655D4D), 3.5f)
    ridge(w, h, .91f, .055f, 0f, if (light) Color(0xFF8D8854) else Color(0xFF454D41), 5f)
    repeat(7) { i ->
        val y = h * (.78f + i * .04f)
        val field = Path().apply {
            moveTo(0f, y)
            cubicTo(w * .36f, y - h * .055f, w * .68f, y + h * .06f, w, y - h * .015f)
        }
        drawPath(field, Color(0xFFFFE7B7).copy(alpha = if (light) .35f else .12f), style = Stroke(w * .006f))
    }
    repeat(18) { i ->
        val x = w * i / 17f
        val y = h * (.95f + sin(i * 1.1f) * .02f)
        val stem = h * (.027f + i % 3 * .013f)
        val gold = if (light) Color(0xFFEBCD8C) else Color(0xFFB29C75)
        drawLine(gold.copy(alpha = .5f), Offset(x, y), Offset(x + w * .009f, y - stem), w * .002f)
        drawOval(gold.copy(alpha = .6f), Offset(x + w * .005f, y - stem - h * .006f), Size(w * .012f, h * .014f))
    }
}

private fun DrawScope.drawAlpinePeaks(w: Float, h: Float, light: Boolean) {
    for (layer in 0..2) {
        val color = when (layer) {
            0 -> if (light) Color(0xFF9FAEC7) else Color(0xFF606B90)
            1 -> if (light) Color(0xFF7D99B0) else Color(0xFF465D7E)
            else -> if (light) Color(0xFF5C8299) else Color(0xFF304963)
        }
        repeat(4) { i ->
            val x = w * (-.1f + i * .36f + layer * .08f)
            val summit = h * (.43f + layer * .11f + (i % 2) * .055f)
            val base = h * (.88f + layer * .05f)
            val half = w * (.34f - layer * .025f)
            val mountain = Path().apply { moveTo(x - half, base); lineTo(x, summit); lineTo(x + half, base); close() }
            drawPath(mountain, Brush.verticalGradient(listOf(color, color.copy(alpha = .65f)), summit, base))
            val snow = Path().apply {
                moveTo(x, summit); lineTo(x + half * .25f, summit + (base - summit) * .25f)
                lineTo(x + half * .08f, summit + (base - summit) * .20f)
                lineTo(x, summit + (base - summit) * .27f)
                lineTo(x - half * .08f, summit + (base - summit) * .18f)
                lineTo(x - half * .25f, summit + (base - summit) * .25f); close()
            }
            drawPath(snow, Color(0xFFE7EDF3).copy(alpha = if (light) .68f else .26f))
        }
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFD3E0E6).copy(alpha = .30f)),
        h * .76f, h), Offset(0f, h * .76f), Size(w, h * .24f))
}

private fun DrawScope.drawCoast(w: Float, h: Float, light: Boolean) {
    val sea = if (light) Color(0xFF649CAA) else Color(0xFF436A84)
    drawRect(Brush.verticalGradient(listOf(sea, if (light) Color(0xFF386F86) else Color(0xFF24465F)),
        h * .61f, h), Offset(0f, h * .61f), Size(w, h * .39f))
    val island = Path().apply {
        moveTo(w * .16f, h * .665f)
        cubicTo(w * .27f, h * .62f, w * .34f, h * .65f, w * .40f, h * .62f)
        cubicTo(w * .44f, h * .59f, w * .52f, h * .65f, w * .61f, h * .665f); close()
    }
    drawPath(island, if (light) Color(0xFF6E8996) else Color(0xFF46566F))
    repeat(19) { i ->
        val x = w * ((i * 29 % 101) / 101f)
        val y = h * (.65f + i * .018f)
        drawLine(Color(0xFFFFDAB7).copy(alpha = if (light) .28f else .17f),
            Offset(x, y), Offset(x + w * (.025f + i % 4 * .019f), y), w * .003f)
    }
    val cliff = Path().apply {
        moveTo(0f, h * .74f); cubicTo(w * .17f, h * .76f, w * .23f, h * .88f, w * .36f, h)
        lineTo(0f, h); close()
    }
    drawPath(cliff, if (light) Color(0xFF577B78) else Color(0xFF2F4D57))
    val rock = Path().apply {
        moveTo(w * .89f, h * .94f); lineTo(w * .93f, h * .88f); lineTo(w * .99f, h * .92f)
        lineTo(w, h); lineTo(w * .84f, h); close()
    }
    drawPath(rock, if (light) Color(0xFF6F8990) else Color(0xFF3E5669))
}

private fun DrawScope.drawStarRealm(w: Float, h: Float) {
    repeat(3) { i ->
        val ribbon = Path().apply {
            moveTo(-w * .1f, h * (.38f + i * .05f))
            cubicTo(w * .35f, h * (.12f + i * .04f), w * .51f, h * (.53f + i * .05f), w * 1.1f, h * .27f)
            lineTo(w * 1.1f, h * .40f)
            cubicTo(w * .53f, h * (.67f + i * .02f), w * .25f, h * (.29f + i * .04f), -w * .1f, h * .57f)
            close()
        }
        drawPath(ribbon, Brush.verticalGradient(listOf(Color(0xFF9FCBBC).copy(alpha = .11f),
            Color(0xFFAD91D2).copy(alpha = .10f), Color.Transparent), h * .15f, h * .65f))
    }
    val points = listOf(Offset(w * .10f, h * .26f), Offset(w * .24f, h * .20f),
        Offset(w * .34f, h * .28f), Offset(w * .46f, h * .24f), Offset(w * .58f, h * .31f))
    points.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFFD0D4EE).copy(alpha = .22f), a, b, w * .002f) }
    points.forEach { drawCircle(Color(0xFFE7EBF4).copy(alpha = .65f), w * .004f, it) }
    ridge(w, h, .84f, .06f, 0f, Color(0xFF4A527F), 2.1f)
    ridge(w, h, .95f, .065f, 0f, Color(0xFF293D63), 4.8f)
}
