package com.example.networkguardian

import android.content.Context

/** Настройки хранятся в SharedPreferences и переживают перезапуск. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("guardian", Context.MODE_PRIVATE)

    /** Цели проверки, по одной на строку: "Название=адрес" или просто "адрес". */
    var targets: String
        get() = p.getString("targets", "Роутер 1=192.168.0.1\nРоутер 2=192.168.1.1\nИнтернет=8.8.8.8") ?: ""
        set(v) = p.edit().putString("targets", v).apply()

    var intervalSec: Int
        get() = p.getInt("intervalSec", 5)
        set(v) = p.edit().putInt("intervalSec", v.coerceAtLeast(1)).apply()

    var timeoutSec: Int
        get() = p.getInt("timeoutSec", 2)
        set(v) = p.edit().putInt("timeoutSec", v.coerceIn(1, 10)).apply()

    /** Сколько неудачных проверок подряд нужно для тревоги. */
    var failsToAlarm: Int
        get() = p.getInt("failsToAlarm", 2)
        set(v) = p.edit().putInt("failsToAlarm", v.coerceAtLeast(1)).apply()

    var repeatSec: Int
        get() = p.getInt("repeatSec", 30)
        set(v) = p.edit().putInt("repeatSec", v.coerceAtLeast(5)).apply()

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    fun parsedTargets(): List<Pair<String, String>> =
        targets.lines().map { it.trim() }.filter { it.isNotEmpty() }.map {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else it to it
        }.filter { it.second.isNotEmpty() }
}
