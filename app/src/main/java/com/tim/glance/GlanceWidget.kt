package com.tim.glance

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class GlanceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        val pending = goAsync()
        Thread {
            try {
                if (fetchWeather(context)) render(context, manager, ids)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, GlanceWidget::class.java))
            if (ids.isNotEmpty()) onUpdate(context, m, ids)
        }
    }

    companion object {
        private const val PREFS = "glance"

        fun updateAll(context: Context) {
            val intent = Intent(context, GlanceWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val m = AppWidgetManager.getInstance(context)
                putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    m.getAppWidgetIds(ComponentName(context, GlanceWidget::class.java))
                )
            }
            context.sendBroadcast(intent)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget)

            val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
                .replaceFirstChar { it.uppercase() }
            views.setTextViewText(R.id.date, date)

            if (prefs.contains("temp")) {
                val t = prefs.getInt("temp", 0)
                views.setTextViewText(R.id.temp, "$t°")
                views.setTextViewText(R.id.icon, iconFor(prefs.getInt("code", 0)))
            }

            val open = android.app.PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.date, open)

            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun lastLocation(context: Context): Location? {
            val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return null
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (p in lm.getProviders(true)) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
                if (l != null && (best == null || l.time > best.time)) best = l
            }
            return best
        }

        private fun fetchWeather(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val loc = lastLocation(context)
            if (loc != null) {
                prefs.edit()
                    .putFloat("lat", loc.latitude.toFloat())
                    .putFloat("lon", loc.longitude.toFloat())
                    .apply()
            }
            if (!prefs.contains("lat")) return false
            val lat = prefs.getFloat("lat", 0f)
            val lon = prefs.getFloat("lon", 0f)

            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&timezone=auto"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            return try {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val cur = json.getJSONObject("current")
                prefs.edit()
                    .putInt("temp", cur.getDouble("temperature_2m").roundToInt())
                    .putInt("code", cur.getInt("weather_code"))
                    .apply()
                true
            } finally {
                conn.disconnect()
            }
        }

        private fun iconFor(code: Int): String = when (code) {
            0 -> "☀️"
            1 -> "🌤️"
            2 -> "⛅"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..57 -> "🌦️"
            in 61..67, in 80..82 -> "🌧️"
            in 71..77, 85, 86 -> "🌨️"
            in 95..99 -> "⛈️"
            else -> "☁️"
        }
    }
}
