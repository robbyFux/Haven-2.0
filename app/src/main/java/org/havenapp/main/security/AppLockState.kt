package org.havenapp.main.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level lock state for the app PIN feature.
 *
 * This is a plain Kotlin object (not Hilt-injected) because it must be
 * initialized in [org.havenapp.main.HavenApplication.onCreate] before any Activity starts.
 * Compose UI observes [locked] via collectAsStateWithLifecycle.
 */
object AppLockState {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun lock() { _locked.value = true }
    fun unlock() { _locked.value = false }
}
