package dev.muxport.shared.core.ui.common

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * ISO-8601 文字列を相対時間文字列に変換する。
 *
 * 例: "just now", "5m ago", "3h ago", "2d ago"
 * 30日以上前の場合は "MM/DD HH:MM" 形式にフォールバックする。
 */
@OptIn(ExperimentalTime::class)
internal fun String.toRelativeTimeString(): String = toRelativeTimeString(Clock.System.now())

/**
 * 基準時刻を指定して相対時間文字列に変換する。
 * テスト用途で [Clock] を注入したい場合に利用する。
 */
@OptIn(ExperimentalTime::class)
internal fun String.toRelativeTimeString(now: Instant): String =
    runCatching {
        val eventTime = Instant.parse(this)
        val elapsed = now - eventTime

        when {
            elapsed < 60.seconds -> "just now"
            elapsed < 60.minutes -> "${elapsed.inWholeMinutes}m ago"
            elapsed < 24.hours -> "${elapsed.inWholeHours}h ago"
            elapsed < 30.days -> "${elapsed.inWholeDays}d ago"
            else -> this.toCompactIsoDateTime()
        }
    }.getOrElse { this }

@OptIn(ExperimentalTime::class)
internal fun String.toCompactIsoDateTime(): String =
    runCatching {
        val localDateTime = Instant.parse(this).toLocalDateTime(TimeZone.currentSystemDefault())

        @Suppress("DEPRECATION")
        val month = localDateTime.monthNumber.toString().padStart(2, '0')

        @Suppress("DEPRECATION")
        val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')
        val datePart = "$month/$day"
        val timePart = "$hour:$minute"
        "$datePart $timePart"
    }.getOrElse { this }
