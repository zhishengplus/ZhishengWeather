package com.zhisheng.weather.data.badge
import com.zhisheng.weather.ui.badge.BadgeHapticGate
import org.junit.Assert.*
import org.junit.Test

class BadgeHapticGateTest {
    @Test fun whitelistCooldownAndLifecycle() {
        val gate=BadgeHapticGate()
        assertFalse(gate.interaction("touch", 0))
        gate.active=true
        assertFalse(gate.interaction("success", 0))
        assertTrue(gate.interaction("touch", 0))
        assertFalse(gate.interaction("back", 80))
        assertTrue(gate.interaction("back", 700))
        gate.active=false
        assertFalse(gate.interaction("reset", 2000))
    }
    @Test fun settingsReducedMotionAndOneSuccess() {
        val gate=BadgeHapticGate().apply {active=true;enabled=false}
        assertFalse(gate.success("grant-a",0))
        gate.enabled=true
        assertFalse(gate.success("grant-a",1000))
        gate.reducedMotion=true
        assertFalse(gate.interaction("front",2000))
        assertTrue(gate.success("grant-b",2000))
        assertFalse(gate.success("grant-b",4000))
    }
    @Test fun backgroundSuccessNeverReplays() {
        val gate=BadgeHapticGate()
        assertFalse(gate.success("grant",100))
        gate.active=true
        assertFalse(gate.success("grant",200))
    }
}
