package com.zhisheng.weather.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InstallRegistrationTest {
    private class Memory : InstallRegistrationStore {
        var state: InstallRegistrationState? = null
        override suspend fun read() = state
        override suspend fun write(state: InstallRegistrationState) { this.state = state }
    }
    @Test fun disabledNeverReadsIdentityOrSends() = runBlocking {
        val store = Memory()
        val reporter = InstallRegistration(store, { error("identity read") }, { _, _ -> error("sent") })
        assertFalse(reporter.report(false, 7)); assertNull(store.state)
    }
    @Test fun sameVersionPersistsAcrossRestartsAndUpgradeAndDowngradeSendOnce() = runBlocking {
        val store = Memory()
        val requests = mutableListOf<Pair<String, Int>>()
        val reporter = InstallRegistration(store, { "device" }, { id, version -> requests.add(id to version); true })
        assertTrue(reporter.report(true, 7)); assertTrue(reporter.report(true, 7))
        val restarted = InstallRegistration(store, { error("identity regenerated") }, { id, version -> requests.add(id to version); true })
        assertTrue(restarted.report(true, 7)); assertTrue(restarted.report(true, 8)); assertTrue(restarted.report(true, 7))
        assertEquals(listOf("device" to 7, "device" to 8, "device" to 7), requests)
    }
    @Test fun retryUsesCurrentVersionAndOnlySuccessIsPersisted() = runBlocking {
        val store = Memory(); val versions = mutableListOf<Int>()
        val reporter = InstallRegistration(store, { "device" }, { _, version -> versions.add(version); versions.size > 1 })
        assertFalse(reporter.report(true, 7)); assertNull(store.state?.version)
        assertTrue(reporter.report(true, 8)); assertEquals(listOf(7, 8), versions)
        assertEquals(8, store.state?.version)
    }
    @Test fun concurrentRequestsRegisterOnlyOnce() = runBlocking {
        val store = Memory(); var sent = 0
        val reporter = InstallRegistration(store, { "device" }, { _, _ -> delay(1); sent++; true })
        coroutineScope { (1..12).map { async { reporter.report(true, 7) } }.awaitAll() }
        assertEquals(1, sent)
    }
    @Test fun cancellationDoesNotMarkSuccess() = runBlocking {
        val store = Memory()
        val reporter = InstallRegistration(store, { "device" }, { _, _ -> throw CancellationException() })
        try { reporter.report(true, 7); fail("cancellation swallowed") } catch (_: CancellationException) { }
        assertNull(store.state?.version)
    }
}
