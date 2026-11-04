package com.mayuu.phantomrecover

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val locationRequestCode = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("phantom_device", Context.MODE_PRIVATE)
        val installId = prefs.getString("install_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("install_id", it).apply()
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 48, 36, 36) }
        val title = TextView(this).apply { text = "PHANTOM RECOVER"; textSize = 24f }
        val disclosure = TextView(this).apply {
            text = "Owner-controlled device recovery. This preview reads basic device status locally. Location is accessed only after you tap the permission button. No data is uploaded."
            textSize = 14f
            setPadding(0, 18, 0, 20)
        }
        status = TextView(this).apply { textSize = 14f }
        val refresh = Button(this).apply { text = "Refresh device status"; setOnClickListener { showStatus(installId) } }
        val location = Button(this).apply { text = "Allow location and show last known position"; setOnClickListener { requestLocation() } }
        layout.addView(title); layout.addView(disclosure); layout.addView(status); layout.addView(refresh); layout.addView(location)
        setContentView(layout)
        showStatus(installId)
    }

    private fun showStatus(installId: String) {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val networkType = when {
            caps == null -> "Offline / unavailable"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected / other"
        }
        status.text = """
            Enrollment: Not connected to server
            Installation ID: $installId
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Battery: ${if (battery >= 0) "$battery%" else "Unavailable"}
            Network: $networkType
            Location: ${if (hasLocationPermission()) "Permission granted" else "Not granted"}
            IMEI: Not collected (restricted identifier)
            Backend heartbeat: Not configured yet
        """.trimIndent()
    }

    private fun requestLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationRequestCode)
        } else showLastKnownLocation()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun showLastKnownLocation() {
        if (!hasLocationPermission()) return
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = try {
            manager.getProviders(true).asSequence().mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
        } catch (_: SecurityException) { null }
        status.append(if (location == null) "\nLast known location: Unavailable" else "\nLast known location: ${location.latitude}, ${location.longitude} (timestamp ${location.time})")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode) {
            val prefs = getSharedPreferences("phantom_device", Context.MODE_PRIVATE)
            val id = prefs.getString("install_id", "not-generated") ?: "not-generated"
            showStatus(id)
            if (hasLocationPermission()) showLastKnownLocation()
        }
    }
}
