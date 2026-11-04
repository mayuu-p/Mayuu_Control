package com.mayuu.phantomrecover

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("phantom_device", Context.MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var tokenInput: EditText
    private val locationRequestCode = 4102
    private val API = "https://ewvirpfovwruxshzzlaa.supabase.co/functions/v1/phantom-web"
    private val KEY = "sb_publishable_Uc0KqgR1D4orcedVlswdRw_HYq1IZJk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val installId = prefs.getString("install_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("install_id", it).apply()
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 40, 32, 32) }
        layout.addView(TextView(this).apply { text = "PHANTOM RECOVER"; textSize = 25f })
        layout.addView(TextView(this).apply {
            text = "Owner-authorized recovery agent. Heartbeat runs as a visible foreground service. Location is shared only after you grant permission and tap the location button. You can stop the service or revoke enrollment at any time."
            textSize = 14f; setPadding(0, 16, 0, 18)
        })
        tokenInput = EditText(this).apply { hint = "Paste one-time owner enrollment code (64 characters)" }
        layout.addView(tokenInput)
        layout.addView(Button(this).apply {
            text = "Enroll this device"
            setOnClickListener { enroll(installId) }
        })
        layout.addView(Button(this).apply {
            text = "Start / resume recovery heartbeat"
            setOnClickListener { startRecoveryService() }
        })
        layout.addView(Button(this).apply {
            text = "Share current location once"
            setOnClickListener { requestLocation() }
        })
        layout.addView(Button(this).apply {
            text = "Stop recovery heartbeat"
            setOnClickListener { stopService(Intent(this@MainActivity, RecoveryService::class.java)); status.text = "Heartbeat stopped by device user." }
        })
        status = TextView(this).apply { textSize = 14f; setPadding(0, 18, 0, 0) }
        layout.addView(status)
        setContentView(ScrollView(this).apply { addView(layout) })
        if (prefs.contains("device_id")) startRecoveryService()
        showStatus(installId)
    }

    private fun enroll(installId: String) {
        val token = tokenInput.text.toString().trim()
        if (!token.matches(Regex("[a-f0-9]{64}"))) { toast("Paste the 64-character one-time code from your owner dashboard."); return }
        thread {
            try {
                val info = JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                    .put("androidVersion", Build.VERSION.RELEASE).put("appVersion", "0.2.0")
                val response = post(JSONObject().put("action", "device_redeem").put("token", token)
                    .put("installId", installId).put("consent", true).put("deviceInfo", info))
                prefs.edit().putString("device_id", response.getString("deviceId"))
                    .putString("session_secret", response.getString("sessionSecret"))
                    .putString("device_label", response.optString("label", "My Android")).apply()
                runOnUiThread {
                    tokenInput.setText("")
                    toast("Device enrolled successfully.")
                    showStatus(installId)
                    startRecoveryService()
                }
            } catch (e: Exception) { runOnUiThread { toast("Enrollment failed: ${e.message}") } }
        }
    }

    private fun startRecoveryService() {
        if (!prefs.contains("device_id")) { toast("Enroll this device first."); return }
        val intent = Intent(this, RecoveryService::class.java)
        ContextCompat.startForegroundService(this, intent)
        status.text = "Starting visible recovery service…"
    }

    private fun requestLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationRequestCode)
        } else shareLastKnownLocation()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun shareLastKnownLocation() {
        if (!hasLocationPermission()) return
        thread {
            try {
                val manager = getSystemService(LOCATION_SERVICE) as LocationManager
                val loc = manager.getProviders(true).asSequence().mapNotNull { p ->
                    try { manager.getLastKnownLocation(p) } catch (_: SecurityException) { null }
                }.maxByOrNull { it.time } ?: throw Exception("No last-known location. Turn on device location and try again.")
                val id = prefs.getString("device_id", null) ?: throw Exception("Device is not enrolled.")
                val secret = prefs.getString("session_secret", null) ?: throw Exception("Session missing.")
                post(JSONObject().put("action", "device_location").put("deviceId", id).put("sessionSecret", secret)
                    .put("latitude", loc.latitude).put("longitude", loc.longitude).put("accuracy", loc.accuracy))
                runOnUiThread { toast("Location shared. Accuracy: ${loc.accuracy.toInt()} m") }
            } catch (e: Exception) { runOnUiThread { toast("Location not shared: ${e.message}") } }
        }
    }

    private fun showStatus(installId: String) {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val network = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            else -> "Connected"
        }
        status.text = "Install ID: $installId\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nBattery: ${battery}%\nNetwork: $network\nEnrollment: ${if (prefs.contains("device_id")) "Enrolled" else "Not enrolled"}\nHeartbeat: visible foreground service"
    }

    private fun post(body: JSONObject): JSONObject {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", KEY)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val result = stream.bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) throw Exception(JSONObject(result).optString("error", "HTTP ${conn.responseCode}"))
        conn.disconnect()
        return JSONObject(result)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode && hasLocationPermission()) shareLastKnownLocation()
        else if (requestCode == locationRequestCode) toast("Location permission was not granted.")
    }
}