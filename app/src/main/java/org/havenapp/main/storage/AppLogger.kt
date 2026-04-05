package org.havenapp.main.storage

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app ring buffer logger. Captures log entries as a [StateFlow] so the
 * Diagnostics screen can display and export them without relying on logcat,
 * which is restricted on modern Android.
 *
 * Each entry is also forwarded to [android.util.Log] so it still shows in
 * logcat during debugging.
 *
 * Max [MAX_ENTRIES] entries are kept; oldest are dropped when the buffer is full.
 */
@Singleton
class AppLogger @Inject constructor() {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    /** Controls in-app ring-buffer verbosity. DEBUG captures all levels; NORMAL drops DEBUG entries. */
    enum class LogLevel { NORMAL, DEBUG }

    @Volatile var currentLogLevel: LogLevel = LogLevel.NORMAL

    fun setLogLevel(level: LogLevel) { currentLogLevel = level }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun log(level: Level, tag: String, message: String) {
        if (level == Level.DEBUG && currentLogLevel == LogLevel.NORMAL) return
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO  -> Log.i(tag, message)
            Level.WARN  -> Log.w(tag, message)
            Level.ERROR -> Log.e(tag, message)
        }
        val current = _entries.value
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        _entries.value = if (current.size >= MAX_ENTRIES) {
            current.drop(1) + entry
        } else {
            current + entry
        }
    }

    fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Level.INFO,  tag, msg)
    fun w(tag: String, msg: String) = log(Level.WARN,  tag, msg)
    fun e(tag: String, msg: String) = log(Level.ERROR, tag, msg)

    fun clear() { _entries.value = emptyList() }

    companion object {
        private const val MAX_ENTRIES = 500
    }
}
