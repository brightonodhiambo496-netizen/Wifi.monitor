package com.example.wifimonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var infoText: TextView
    private lateinit var speedButton: Button
    private lateinit var speedText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {
        override fun run() {
            updateWifiInfo()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        infoText = TextView(this).apply { textSize = 20f }

        speedButton = Button(this).apply {
            text = "Test speed"
            setOnClickListener { runSpeedTest() }
        }

        speedText = TextView(this).apply {
            textSize = 20f
            text = "Tap the button to test your real speed."
        }

        layout.addView(infoText)
        layout.addView(speedButton)
        layout.addView(speedText)
        setContentView(layout)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

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

    private fun runSpeedTest() {
        speedButton.isEnabled = false
        speedText.text = "Testing... please wait"

        Thread {
            var result: String
            try {
                // 1) Response time (includes connection setup)
                val t0 = System.nanoTime()
                val p = URL("https://speed.cloudflare.com/__down?bytes=0")
                    .openConnection() as HttpURLConnection
                p.connectTimeout = 8000
                p.readTimeout = 8000
                p.inputStream.use { it.readBytes() }
                p.disconnect()
                val pingMs = (System.nanoTime() - t0) / 1_000_000

                // 2) Download 10 MB and time it
                val start = System.nanoTime()
                val c = URL("https://speed.cloudflare.com/__down?bytes=10000000")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 15000
                var total = 0L
                val buf = ByteArray(16384)
                c.inputStream.use { s ->
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        total += n
                    }
                }
                c.disconnect()
                val seconds = (System.nanoTime() - start) / 1_000_000_000.0
                val mbps = total * 8 / 1_000_000.0 / seconds

                val verdict = when {
                    mbps >= 25 -> "Fast"
                    mbps >= 10 -> "Good"
                    mbps >= 3 -> "Slow"
                    else -> "Very slow (network is crowded)"
                }

                result = String.format(
                    "Download: %.1f Mbps\nResponse time: %d ms\nVerdict: %s",
                    mbps, pingMs, verdict
                )
            } catch (e: Exception) {
                result = "Test failed. Check that you have internet."
            }

            runOnUiThread {
                speedText.text = result
                speedButton.isEnabled = true
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun updateWifiInfo() {
        if (!wifiManager.isWifiEnabled) {
            infoText.text = "WiFi is turned off."
            return
        }

        val info = wifiManager.connectionInfo
        val rssi = info.rssi
        val speed = info.linkSpeed
        val freq = info.frequency
        val ssid = info.ssid.replace("\"", "")

        infoText.text = """
            Network: $ssid

            Signal: $rssi dBm  (${signalQuality(rssi)})

            Band: ${bandName(freq)}

            Link speed: $speed Mbps

            Tip: ${tip(rssi, freq)}
        """.trimIndent() + "\n\n"
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
