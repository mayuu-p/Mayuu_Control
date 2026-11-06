package com.mayuu.phantomrecover

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val apiUrl = "https://ewvirpfovwruxshzzlaa.supabase.co/functions/v1/phantom-web"
    private val publishableKey = "sb_publishable_Uc0KqgR1D4orcedVlswdRw_HYq1IZJk"
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var codeInput: EditText
    private lateinit var consent: CheckBox
    private lateinit var enrollButton: Button
    private val locationPermissionRequest = 7102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 11, 19)
        window.navigationBarColor = Color.rgb(7, 11, 19)
        render()
        refreshState()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7101)
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 30, 22, 24)
            setBackgroundColor(Color.rgb(7, 11, 19))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val logo = TextView(this).apply {
            text = "⬡  PHANTOM RECOVER"; textSize = 23f
            setTextColor(Color.rgb(25, 217, 245)); gravity = Gravity.CENTER_VERTICAL
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        content.addView(logo, params(LinearLayout.LayoutParams.MATCH_PARENT, 58))
        addText(content, "YOUR DEVICE. YOUR CONSENT. YOUR CONTROL.", 10f, Color.rgb(111, 147, 174))
        addText(content, "Device security and recovery", 21f, Color.WHITE, 18)
        status = addText(content, "Checking enrollment…", 13f, Color.rgb(177, 196, 216), 10)
        codeInput = EditText(this).apply {
            hint = "Paste 64-character enrollment code"
            setHintTextColor(Color.rgb(112, 130, 151)); setTextColor(Color.WHITE)
            setSingleLine(true); setPadding(14, 14, 14, 14)
            setBackgroundColor(Color.rgb(16, 29, 46))
        }
        content.addView(codeInput, params(LinearLayout.LayoutParams.MATCH_PARENT, 54).apply { topMargin = 16 })
        consent = CheckBox(this).apply {
            text = "I understand this device will be linked to the owner account. Heartbeat and disclosed device telemetry are sent while Protection is running. Location is one-shot and requires my permission."
            setTextColor(Color.rgb(176, 194, 215)); textSize = 12f
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(25, 217, 245))
        }
        content.addView(consent)
        enrollButton = addButton(content, "Enroll this device") { enroll() }
        addText(content, "RECOVERY PROTECTION", 10f, Color.rgb(25, 217, 245), 24)
        addButton(content, "Start Protection") { startProtection() }
        addButton(content, "Stop Protection") { stopProtection() }
        addButton(content, "Stop Session & Unenroll") { stopSession() }
        addText(content, "LOCATION", 10f, Color.rgb(25, 217, 245), 22)
        addButton(content, "Share Current Location Once") { requestOneShotLocation() }
        addText(content, "SENSITIVE CAPABILITIES", 10f, Color.rgb(25, 217, 245), 22)
        addText(content, "Camera: Not implemented\nMicrophone: Not implemented\nScreen mirror: Not implemented\nNative WebRTC: Not implemented\nNo media capture runs in the background.", 13f, Color.rgb(168, 187, 207), 5)
        addText(content, "Privacy: PHANTOM RECOVER does not collect private notifications, messages, calls, contacts, keystrokes or personal files. No hidden persistence or permission bypass.", 11f, Color.rgb(112, 132, 153), 24)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun params(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)
    private fun addText(parent: LinearLayout, value: String, size: Float, color: Int, top: Int = 4): TextView {
        val view = TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, top, 0, 4) }
        parent.addView(view, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return view
    }
    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(14, 91, 125))
            setOnClickListener { action() }
        }
        parent.addView(button, params(LinearLayout.LayoutParams.MATCH_PARENT, 52).apply { topMargin = 8 })
        return button
    }

    private fun refreshState() {
        val secret = DeviceSecrets.loadSession(this)
        val id = DeviceSecrets.deviceId(this)
        if (secret.isNullOrBlank()) {
            status.text = "Not enrolled. An owner-issued one-time code is required."
            enrollButton.isEnabled = true
        } else {
            status.text = "Enrolled · Device ID: ${id ?: "registered"}\nSession secret protected by Android Keystore AES-GCM."
            enrollButton.isEnabled = false
        }
    }

    private fun enroll() {
        val code = codeInput.text.toString().trim().lowercase()
        if (!Regex("^[0-9a-f]{64}$").matches(code)) { status.text = "Invalid code. Enter the full 64-character hexadecimal code."; return }
        if (!consent.isChecked) { status.text = "Consent required. Read and accept the disclosure before enrollment."; return }
        enrollButton.isEnabled = false; status.text = "Contacting PHANTOM RECOVER…"
        val payload = JSONObject()
            .put("action", "device_redeem").put("code", code)
            .put("install_id", DeviceSecrets.installationId(this))
            .put("device_label", "${Build.MANUFACTURER} ${Build.MODEL}".take(80))
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("android_version", Build.VERSION.RELEASE).put("app_version", "0.3.0")
            .put("consent", true)
        worker.execute {
            try {
                val response = post(payload, null)
                DeviceSecrets.saveSession(this, response.getString("session_secret"), response.getString("device_id"))
                runOnUiThread {
                    status.text = "Enrollment confirmed by server. Device ID: ${response.getString("device_id")}"
                    codeInput.text.clear(); refreshState()
                    Toast.makeText(this, "Device enrolled securely", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = e.message ?: "Network or enrollment error."; enrollButton.isEnabled = true }
            }
        }
    }

    private fun startProtection() {
        if (DeviceSecrets.loadSession(this).isNullOrBlank()) { status.text = "Enroll this device before starting Protection."; return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Notification permission is required so Protection remains visible. Allow notifications, then tap Start Protection again."
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7101)
            return
        }
        val intent = Intent(this, RecoveryService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, intent) else startService(intent)
        status.text = "Protection requested. Check the persistent notification for active status and Stop."
    }
    private fun stopProtection() {
        stopService(Intent(this, RecoveryService::class.java))
        status.text = "Protection stopped on this device. Server status becomes offline after heartbeat timeout."
    }

    private fun stopSession() {
        val secret = DeviceSecrets.loadSession(this)
        if (secret.isNullOrBlank()) {
            status.text = "No active device session."
            return
        }
        status.text = "Ending device session securely…"
        worker.execute {
            try {
                post(JSONObject().put("action", "device_session_stop"), secret)
                DeviceSecrets.clearSession(this)
                runOnUiThread {
                    stopService(Intent(this, RecoveryService::class.java))
                    status.text = "Server confirmed session termination. This device is unenrolled."
                    refreshState()
                }
            } catch (e: Exception) {
                val detail = e.message ?: "Network error"
                if (Regex("expired|revoked|session is invalid", RegexOption.IGNORE_CASE).containsMatchIn(detail)) {
                    DeviceSecrets.clearSession(this)
                    runOnUiThread {
                        stopService(Intent(this, RecoveryService::class.java))
                        status.text = "Server reports this session is no longer valid. Local session credentials cleared."
                        refreshState()
                    }
                } else {
                    runOnUiThread { status.text = "Could not confirm session termination: ${detail}" }
                }
            }
        }
    }

    private fun requestOneShotLocation() {
        if (DeviceSecrets.loadSession(this).isNullOrBlank()) { status.text = "Enroll this device before sharing location."; return }
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationPermissionRequest)
            return
        }
        captureOneShotLocation()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequest) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) captureOneShotLocation()
            else status.text = "Location permission denied. No location was collected."
        }
    }

    @Suppress("MissingPermission")
    private fun captureOneShotLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        status.text = "Requesting a fresh location fix…"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                manager.getCurrentLocation(provider, null, mainExecutor) { location ->
                    if (location == null) status.text = "Location unavailable or timed out." else uploadLocation(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) { manager.removeUpdates(this); uploadLocation(location) }
                    @Deprecated("Deprecated by Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                manager.requestSingleUpdate(provider, listener, mainLooper)
            }
        } catch (e: Exception) { status.text = "Location error: ${e.message}" }
    }

    private fun uploadLocation(location: Location) {
        val secret = DeviceSecrets.loadSession(this) ?: return
        val payload = JSONObject().put("action", "device_location")
            .put("latitude", location.latitude).put("longitude", location.longitude)
            .put("accuracy_m", location.accuracy.toDouble())
            .put("captured_at", java.time.Instant.ofEpochMilli(location.time).toString())
        worker.execute {
            try {
                post(payload, secret)
                runOnUiThread { status.text = "Location shared once. Accuracy: ${location.accuracy.toInt()} m." }
            } catch (e: Exception) { runOnUiThread { status.text = "Location upload failed: ${e.message}" } }
        }
    }

    private fun post(payload: JSONObject, session: String?): JSONObject {
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("apikey", publishableKey)
            setRequestProperty("Cache-Control", "no-store")
            if (session != null) setRequestProperty("x-device-session", session)
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result = JSONObject(body)
            if (connection.responseCode !in 200..299 || !result.optBoolean("ok", true))
                throw IllegalStateException(result.optString("message", "Request failed (HTTP ${connection.responseCode})."))
            return result
        } finally { connection.disconnect() }
    }

    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
