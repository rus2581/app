package com.example.networkguardian

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    companion object {
        @Volatile var lastStatus: String = "Мониторинг не запущен"
    }

    private lateinit var s: Settings
    private lateinit var statusView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            statusView.text = lastStatus
            handler.postDelayed(this, 1000)
        }
    }

    private fun field(root: LinearLayout, label: String, value: String, numeric: Boolean = false): EditText {
        root.addView(TextView(this).apply { text = label; setPadding(0, 24, 0, 0) })
        val e = EditText(this).apply {
            setText(value)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(e)
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Settings(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(TextView(this).apply { text = "Network Guardian"; textSize = 24f })

        statusView = TextView(this).apply { textSize = 18f; setPadding(0, 24, 0, 24); text = lastStatus }
        root.addView(statusView)

        root.addView(TextView(this).apply {
            text = "Что проверять (по одной строке: Название=адрес)"; setPadding(0, 24, 0, 0)
        })
        val targets = EditText(this).apply {
            setText(s.targets)
            minLines = 4
            gravity = android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(targets)
        val interval = field(root, "Проверка каждые, сек", s.intervalSec.toString(), true)
        val timeout = field(root, "Таймаут ping, сек", s.timeoutSec.toString(), true)
        val fails = field(root, "Неудач подряд до тревоги", s.failsToAlarm.toString(), true)
        val repeat = field(root, "Повтор голоса каждые, сек", s.repeatSec.toString(), true)

        fun save() {
            s.targets = targets.text.toString()
            s.intervalSec = interval.text.toString().toIntOrNull() ?: 5
            s.timeoutSec = timeout.text.toString().toIntOrNull() ?: 2
            s.failsToAlarm = fails.text.toString().toIntOrNull() ?: 2
            s.repeatSec = repeat.text.toString().toIntOrNull() ?: 30
        }

        root.addView(Button(this).apply {
            text = "Сохранить настройки"
            setOnClickListener { save(); Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show() }
        })

        root.addView(Button(this).apply {
            text = "Запустить мониторинг"
            setOnClickListener {
                save()
                s.enabled = true
                startForegroundService(Intent(context, MonitorService::class.java))
                // Перезапуск службы подхватит новые адреса
                Toast.makeText(context, "Запущено", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Остановить"
            setOnClickListener {
                s.enabled = false
                stopService(Intent(context, MonitorService::class.java))
                lastStatus = "Мониторинг остановлен"
            }
        })

        root.addView(TextView(this).apply {
            setPadding(0, 32, 0, 0)
            text = "Для работы при заблокированном экране: Настройки Android → Приложения → " +
                "Network Guardian → Батарея → «Без ограничений»."
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }
}
