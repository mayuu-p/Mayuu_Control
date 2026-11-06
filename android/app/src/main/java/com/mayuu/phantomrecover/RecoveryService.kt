package com.mayuu.phantomrecover

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class RecoveryService : Service() {
    companion object {
        private const val CHANNEL_ID = "phantom_protection"
        private const val NOTIFICATION_ID = 4301
        private const val API_URL = "https://ewvirpfovwruxshzzlaa.supabase.co/functions/v1/phantom-web"
        private const val PUBLISHABLE_KEY = "sb_publishable_Uc0KqgR1D4orcedVlswdRw_HYq1IZJk"
        private const val ACTION_STOP = "com.mayuu.phantomrecover.STOP_PROTECTION"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val heartbeatLoop = object : Runnable {
        override fun run() {
            executor.execute { sendHeartbeat(); pollCommands() }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate() { super.onCreate(); createChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handler.removeCallbacks(heartbeatLoop); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        if (DeviceSecrets.loadSession(this).isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(heartbeatLoop); handler.post(heartbeatLoop)
        return START_NOT_STICKY
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "PHANTOM Protection", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Visible status for user-enabled device recovery protection"; setShowBadge(false)
                }
            )
        }
    }
    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, RecoveryService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 4302, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openPending = PendingIntent.getActivity(this, 4303, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.phantom_logo).setContentTitle("PHANTOM Protection is active")
            .setContentText("Heartbeat and disclosed telemetry · Tap Stop to pause")
            .setColor(Color.rgb(25, 217, 245)).setOngoing(true).setContentIntent(openPending)
            .addAction(0, "Stop Protection", stopPending).build()
    }
    private fun sendHeartbeat() {
        val secret = DeviceSecrets.loadSession(this) ?: return
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val chargingStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING || chargingStatus == BatteryManager.BATTERY_STATUS_FULL
        val payload = JSONObject().put("action", "device_heartbeat")
            .put("battery_percent", level ?: JSONObject.NULL).put("charging", charging)
            .put("network_type", networkType()).put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL).put("android_version", Build.VERSION.RELEASE).put("app_version", "0.3.0")
        try { post(payload, secret) } catch (_: Exception) { /* retry on next interval; never log session material */ }
    }
    private fun networkType(): String {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return "offline"
        val caps = manager.getNetworkCapabilities(network) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            else -> "other"
        }
    }
    private fun pollCommands() {
        val secret = DeviceSecrets.loadSession(this) ?: return
        try {
            val response = post(JSONObject().put("action", "device_poll"), secret)
            val commands: JSONArray = response.optJSONArray("commands") ?: JSONArray()
            for (i in 0 until commands.length()) {
                val command = commands.optJSONObject(i) ?: continue
                val id = command.optString("id")
                val type = command.optString("command_type")
                val payload = command.optJSONObject("payload") ?: JSONObject()
                val result = when (type) {
                    "ring" -> { ring(); "completed" }
                    "lost_mode" -> { notifyOwnerRequest("Lost Mode request", "The owner requested Lost Mode. Open the app to review your device."); "acknowledged" }
                    "recovery_message" -> { notifyOwnerRequest("Recovery message", payload.optString("message", "The owner sent a recovery message.")); "acknowledged" }
                    "screen_share_request" -> { notifyOwnerRequest("Screen-share request", "The owner requested screen sharing. No capture starts unless you open the app and approve a future session."); "acknowledged" }
                    else -> "failed"
                }
                if (id.isNotBlank()) try {
                    post(JSONObject().put("action", "device_ack").put("command_id", id).put("status", result), secret)
                } catch (_: Exception) { /* server expires commands without acknowledgment */ }
            }
        } catch (_: Exception) { /* offline; retry at next interval */ }
    }
    private fun ring() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 1200)
            handler.postDelayed({ tone.release() }, 1400)
        } catch (_: Exception) { }
    }
    private fun notifyOwnerRequest(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.phantom_logo).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
    private fun post(payload: JSONObject, secret: String): JSONObject {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 12000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("x-device-session", secret); setRequestProperty("Cache-Control", "no-store")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result = JSONObject(body)
            if (connection.responseCode !in 200..299 || !result.optBoolean("ok", true)) {
                if (connection.responseCode == 401 || connection.responseCode == 403) {
                    DeviceSecrets.clearSession(this)
                    handler.post {
                        handler.removeCallbacks(heartbeatLoop)
                        notifyOwnerRequest("PHANTOM session ended", "This device session expired or was revoked. Open the app to enroll again if needed.")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                throw IllegalStateException(result.optString("message", "API request failed"))
            }
            return result
        } finally { connection.disconnect() }
    }
    override fun onDestroy() { handler.removeCallbacks(heartbeatLoop); executor.shutdownNow(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
