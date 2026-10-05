package com.zhisheng.weather.data

import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable
internal data class InstallRegistrationState(val id: String, val version: Int? = null)
internal interface InstallRegistrationStore {
    suspend fun read(): InstallRegistrationState?
    suspend fun write(state: InstallRegistrationState)
}
internal class InstallRegistration(
    private val store: InstallRegistrationStore,
    private val createId: () -> String,
    private val send: suspend (String, Int) -> Boolean,
) {
    private val mutex = Mutex()
    suspend fun report(enabled: Boolean, version: Int): Boolean {
        if (!enabled) return false
        return mutex.withLock {
            val state = store.read() ?: InstallRegistrationState(createId()).also { store.write(it) }
            if (state.version == version) return@withLock true
            if (!send(state.id, version)) return@withLock false
            store.write(state.copy(version = version))
            true
        }
    }
}
