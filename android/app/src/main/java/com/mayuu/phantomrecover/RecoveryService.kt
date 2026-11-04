package com.mayuu.phantomrecover

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class RecoveryService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("phantom_device", Context.MODE_PRIVATE) }
    private val api = "https://ewvirpfovwruxshzzlaa.supabase.co/functions/v1/phantom-web"
    private val key = "sb_publishable_Uc0KqgR1D4orcedVlswdRw_HYq1IZJk"
    private var working = false
    private val loop = object : Runnable {
        override fun run() {
            if (!working) return
            sendHeartbeatAndCommands()
            handler.postDelayed(this, 30_000L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(4101, buildNotification("Protected device · heartbeat active"))
        working = true
        handler.post(loop)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.contains("device_id") || !prefs.contains("session_secret")) {
            stopSelf()
            return START_NOT_STICKY
        }
        working = true
        handler.removeCallbacks(loop)
        handler.post(loop)
        return START_STICKY
    }
    private fun sendHeartbeatAndCommands() {
        if (prefs.getString("device_id", null).isNullOrBlank()) return
        thread {
            try {
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                val network = when {
                    caps == null -> "offline"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    else -> "connected"
                }
                val base = credentials()
                post(JSONObject(base.toString()).put("action", "device_heartbeat")
                    .put("batteryPercent", battery).put("charging", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING)
                    .put("networkType", network))
                val commands = post(JSONObject(base.toString()).put("action", "device_poll")).optJSONArray("commands") ?: return@thread
                for (i in 0 until commands.length()) {
                    val command = commands.getJSONObject(i)
                    var success = false
                    try {
                        when (command.optString("command_type")) {
                            "ring" -> { ringForTwentySeconds(); notifyUser("Recovery alert", "Your enrolled owner requested this device to ring."); success = true }
                            "lost_mode" -> { notifyUser("Lost-device mode", "This device has been marked as lost by its owner."); success = true }
                            "recovery_message" -> {
                                val message = command.optJSONObject("payload")?.optString("message") ?: "This device is marked as lost."
                                notifyUser("Recovery message", message); success = true
                            }
                            "screen_share_request" -> {
                                notifyUser("Screen-share request", "Open PHANTOM RECOVER to review and approve or reject screen sharing."); success = true
                            }
                        }
                    } finally {
                        post(JSONObject(base.toString()).put("action", "device_ack")
                            .put("commandId", command.getString("id")).put("success", success))
                    }
                }
            } catch (_: Exception) {
                // Retry on the next foreground-service cycle after transient network errors.
            }
        }
    }
    private fun credentials() = JSONObject().put("deviceId", prefs.getString("device_id", ""))
        .put("sessionSecret", prefs.getString("session_secret", ""))
    private fun post(body: JSONObject): JSONObject {
        val c = (URL(api).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 12000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("apikey", key)
        }
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val raw = stream.bufferedReader().use { it.readText() }
        if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}: $raw")
        c.disconnect()
        return JSONObject(raw)
    }
    private fun ringForTwentySeconds() {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 20_000)
        handler.postDelayed({ try { tone.stopTone(); tone.release() } catch (_: Exception) {} }, 20_000)
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("phantom_recovery", "PHANTOM recovery", NotificationManager.IMPORTANCE_HIGH))
        }
    }
    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, "phantom_recovery").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("PHANTOM RECOVER is active").setContentText(text)
            .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    private fun notifyUser(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            NotificationCompat.Builder(this, "phantom_recovery").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true).build())
    }
    override fun onDestroy() {
        working = false
        handler.removeCallbacks(loop)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}