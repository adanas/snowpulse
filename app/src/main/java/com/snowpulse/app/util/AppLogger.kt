package com.snowpulse.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogItem(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * SnowPulse アプリ内リアルタイムイベントロガー
 * 最新100件の内部ログを循環保持し、デバッグ画面のライブコンソールに配信します。
 */
object AppLogger {
    private const val MAX_LOGS = 100
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.JAPAN)

    private val _logs = MutableStateFlow<List<LogItem>>(emptyList())
    val logs: StateFlow<List<LogItem>> = _logs.asStateFlow()

    init {
        i("SYSTEM", "SnowPulse アプリロガー初期化完了")
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String) {
        val item = LogItem(
            timestamp = timeFormat.format(Date()),
            tag = tag,
            message = message,
            level = level
        )
        val current = _logs.value.toMutableList()
        current.add(item)
        if (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _logs.value = current

        try {
            android.util.Log.println(
                when (level) {
                    LogLevel.DEBUG -> android.util.Log.DEBUG
                    LogLevel.INFO -> android.util.Log.INFO
                    LogLevel.WARN -> android.util.Log.WARN
                    LogLevel.ERROR -> android.util.Log.ERROR
                },
                tag,
                message
            )
        } catch (_: Throwable) {
            // JVM環境等でのフォールバック
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getAllLogsText(): String {
        return _logs.value.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message}" }
    }
}
