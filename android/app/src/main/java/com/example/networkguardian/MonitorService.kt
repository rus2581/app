package com.example.networkguardian

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import java.util.Locale

class MonitorService : Service(), TextToSpeech.OnInitListener {

    private lateinit var settings: Settings
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        tts = TextToSpeech(this, this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "guardian:monitor").apply { acquire() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale("ru", "RU"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            // Играет как будильник: слышно даже при выключенном звуке медиа
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            ttsReady = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat("Мониторинг запущен")
        if (!running) {
            running = true
            worker = Thread { loop() }.also { it.start() }
        }
        return START_STICKY
    }

    private fun loop() {
        val fails = HashMap<String, Int>()
        var lastSpoken = 0L
        var lastKey = ""

        while (running) {
            val targets = settings.parsedTargets()
            val bad = mutableListOf<String>()
            val lines = mutableListOf<String>()

            for ((name, addr) in targets) {
                val ok = ping(addr, settings.timeoutSec)
                val n = if (ok) 0 else (fails[addr] ?: 0) + 1
                fails[addr] = n
                lines += (if (ok) "✔ " else "✖ ") + name + " (" + addr + ")"
                if (n >= settings.failsToAlarm) bad += name
            }
            fails.keys.retainAll(targets.map { it.second }.toSet())

            val key = bad.joinToString("|")
            val now = System.currentTimeMillis()
            if (bad.isNotEmpty()) {
                if (key != lastKey || now - lastSpoken >= settings.repeatSec * 1000L) {
                    speak("Внимание! Нет связи: " + bad.joinToString(", "))
                    lastSpoken = now
                }
                notify("Нет связи: " + bad.joinToString(", "), alarm = true)
            } else {
                if (lastKey.isNotEmpty()) speak("Связь восстановлена")
                notify("Всё в порядке", alarm = false)
                lastSpoken = 0
            }
            lastKey = key
            MainActivity.lastStatus = lines.joinToString("\n")

            try { Thread.sleep(settings.intervalSec * 1000L) } catch (_: InterruptedException) { }
        }
    }

    /** Обычный ping системной утилитой (root не нужен). */
    private fun ping(host: String, timeoutSec: Int): Boolean = try {
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", timeoutSec.toString(), host)
            .redirectErrorStream(true).start()
        val done = p.waitFor((timeoutSec + 2).toLong(), java.util.concurrent.TimeUnit.SECONDS)
        if (!done) { p.destroyForcibly(); false } else p.exitValue() == 0
    } catch (e: Exception) { false }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alarm")
    }

    // ---- уведомления ----
    private fun channel(id: String, name: String, importance: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) nm.createNotificationChannel(NotificationChannel(id, name, importance))
    }

    private fun build(text: String, alarm: Boolean): Notification {
        val id = if (alarm) "alarm" else "status"
        channel(id, if (alarm) "Авария" else "Статус", if (alarm) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW)
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, id)
            .setContentTitle("Network Guardian")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun notify(text: String, alarm: Boolean) {
        getSystemService(NotificationManager::class.java).notify(1, build(text, alarm))
    }

    private fun startForegroundCompat(text: String) {
        val n = build(text, false)
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        tts?.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
