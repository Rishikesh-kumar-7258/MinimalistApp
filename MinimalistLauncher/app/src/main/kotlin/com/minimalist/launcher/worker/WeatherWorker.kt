package com.minimalist.launcher.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minimalist.launcher.LauncherApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class WeatherWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = (applicationContext as LauncherApplication).preferencesRepository
        val apiKey = prefs.weatherApiKey.first()
        val city   = prefs.weatherCity.first()

        if (apiKey.isBlank() || city.isBlank()) return@withContext Result.success()

        var conn: HttpURLConnection? = null
        return@withContext try {
            val encoded = URLEncoder.encode(city, "UTF-8")
            val url = URL("https://api.openweathermap.org/data/2.5/weather?q=$encoded&appid=$apiKey&units=metric")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 15_000
            conn.requestMethod  = "GET"

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val json = conn.inputStream.bufferedReader().readText()
                val obj  = JSONObject(json)
                val temp = obj.getJSONObject("main").getDouble("temp").roundToInt()
                val desc = obj.getJSONArray("weather")
                    .getJSONObject(0)
                    .getString("description")
                    .replaceFirstChar { it.uppercase() }
                prefs.setWeatherCache("$temp°C  $desc")
            }
            // On non-200 (bad key, city not found, rate limit) keep existing cache.
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            conn?.disconnect()
        }
    }
}
