package com.zhisheng.weather.ui

import com.zhisheng.weather.ui.home.*
import com.zhisheng.weather.model.radarTileSample
import org.junit.Assert.*
import org.junit.Test

class RadarPreviewGeometryTest {
    @Test fun offscreenStrokesAreClippedAndOutsideSegmentsAreDropped() {
        val segment=clipRadarPreviewSegment(PreviewWorldPoint(-1000.0,0.0),PreviewWorldPoint(1000.0,0.0))!!
        assertEquals(-128.0,segment.first.x,.001);assertEquals(128.0,segment.second.x,.001)
        assertNull(clipRadarPreviewSegment(PreviewWorldPoint(200.0,400.0),PreviewWorldPoint(300.0,500.0)))
        assertNull(clipRadarPreviewSegment(PreviewWorldPoint(-8000.0,0.0),PreviewWorldPoint(8000.0,0.0)))
    }
    @Test fun geographyAndRadarUseTheSameMercatorPixelCoordinates() {
        for ((lat,lon) in listOf(38.5 to 102.2, 22.3 to 114.2, 51.5 to -0.1, -33.9 to 151.2, 0.0 to 179.99)) {
            val p=radarPreviewWorldPoint(lat,lon);val tile=radarTileSample(lat,lon,6)
            assertEquals(tile.tileX*256.0+tile.pixelX,p.x,1.01)
            assertEquals(tile.tileY*256.0+tile.pixelY,p.y,1.01)
        }
    }
    @Test fun datelineNeighboursStayAdjacentAndDoNotCrossTheWholeWorld() {
        val a=radarPreviewWorldPoint(0.0,179.9);val b=radarPreviewWorldPoint(0.0,-179.9)
        assertEquals(16384*.2/360,radarPreviewRelativeX(b.x,a.x),.0001)
        assertEquals(0.0,radarPreviewRelativeX(a.x,a.x),0.0)
    }
    @Test fun polesAreClampedToTheSameFiniteMapRangeAsRadarTiles() {
        for (lat in listOf(-90.0,90.0)) {
            val p=radarPreviewWorldPoint(lat,540.0)
            assertTrue(p.x.isFinite() && p.y.isFinite())
            assertTrue(p.y>=-.0001 && p.y<=16384.0001)
        }
    }
}
