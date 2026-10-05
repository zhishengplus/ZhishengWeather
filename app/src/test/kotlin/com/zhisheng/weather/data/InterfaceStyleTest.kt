package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceStyleTest {
    @Test
    fun newInstallDefaultsToPhosphorVista() {
        assertEquals(InterfaceStyle.PHOSPHOR_VISTA, InterfaceStyle.from(null))
        assertEquals(InterfaceStyle.PHOSPHOR_VISTA, InterfaceStyle.from("unknown"))
    }

    @Test
    fun classicThemeRoundTripsWithoutMigration() {
        assertEquals(
            InterfaceStyle.CLASSIC_TERMINAL,
            InterfaceStyle.from(InterfaceStyle.CLASSIC_TERMINAL.key),
        )
    }
}

