package com.example.wifimonitor

// ------------------------------------------------------------
// STEP 1: Add these to AndroidManifest.xml (above <application>):
//
//   <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
//   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
//   <uses-permission android:name="android.permission.INTERNET" />
//
// STEP 2: Make sure your project uses AppCompat (default in
// Android Studio "Empty Views Activity" template).
// ------------------------------------------------------------

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var infoText: TextView
    private val handler = Handler(Looper.getMainLooper())

    // Refresh the screen every 2 seconds
    private val updater = object : Runnable {
        override fun run() {
            updateWifiInfo()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple screen built in code (no XML needed)
        infoText = TextView(this).apply {
            textSize = 20f
            setPadding(48, 96, 48, 48)
            gravity = Gravity.START
        }
        setContentView(infoText)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Location permission is needed to read the WiFi name (SSID)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
            )
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updater)
    }

    @Suppress("DEPRECATION")
    private fun updateWifiInfo() {
        if (!wifiManager.isWifiEnabled) {
            infoText.text = "WiFi is turned off."
            return
        }

        val info = wifiManager.connectionInfo
        val rssi = info.rssi                 // signal strength in dBm (closer to 0 = better)
        val speed = info.linkSpeed           // Mbps (link speed, not real internet speed)
        val freq = info.frequency            // MHz
        val ssid = info.ssid.replace("\"", "")

        infoText.text = """
            Network: $ssid

            Signal: $rssi dBm  (${signalQuality(rssi)})

            Band: ${bandName(freq)}

            Link speed: $speed Mbps

            Tip: ${tip(rssi, freq)}
        """.trimIndent()
    }

    private fun bandName(freq: Int): String = when {
        freq in 2400..2500 -> "2.4 GHz (longer range, more crowded)"
        freq in 4900..5900 -> "5 GHz (faster, less crowded)"
        freq >= 5925 -> "6 GHz (fastest, shortest range)"
        else -> "Unknown"
    }

    private fun signalQuality(rssi: Int): String = when {
        rssi >= -50 -> "Excellent"
        rssi >= -60 -> "Good"
        rssi >= -70 -> "Fair"
        else -> "Weak"
    }

    private fun tip(rssi: Int, freq: Int): String = when {
        rssi < -70 -> "Signal is weak. Move closer to the router."
        freq in 2400..2500 -> "Try connecting to the 5 GHz network if the school has one."
        else -> "Your connection looks healthy."
    }
}
