package com.example.nooraai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.nooraai.databinding.ActivityMainBinding
import com.example.nooraai.ui.home.HomeFragment
import com.example.nooraai.ui.compass.KiblatoverlayFragment
import com.example.nooraai.util.LocationHelper

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_AUTH_RESULT = "auth_result"
        private const val TAG = "MainActivity"
        private const val REQ_POST_NOTIF = 1001
    }

    private lateinit var binding: ActivityMainBinding

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateNextPrayerFromSchedule()
            refreshHandler.postDelayed(this, 60_000L)
        }
    }

    private var todayPrayers: List<Prayer> = emptyList()

    override fun getLayoutId(): Int = R.layout.activity_main
    override fun getNavIndex(): Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val childRoot = getChildRootView()
        binding = ActivityMainBinding.bind(childRoot)

        // Edge-to-edge flag (kept, BaseActivity should handle insets)
        enableEdgeToEdge()

        // Initialize location chip (we will fill it via LocationHelper)
        val tvLocationText = binding.root.findViewById<TextView>(R.id.tvLocationText)
        LocationHelper.fetchAndFillLocation(this, tvLocationText) { lat, lon ->
            // callback when location saved - you can trigger API Qibla or notify fragments here
            Log.d(TAG, "location saved: $lat, $lon")
        }

        // Hook compass icon (use binding.root to find included header view)
        val ivCompass = binding.root.findViewById<ImageView?>(R.id.ivCompass)
        ivCompass?.setOnClickListener {
            KiblatoverlayFragment().show(supportFragmentManager, "kiblat")
        }
        val ivBellHeader = binding.root.findViewById<ImageView?>(R.id.ivBell) // atau R.id.ivBellHeader jika kamu ganti id
        ivBellHeader?.setOnClickListener {
            // show overlay (DialogFragment)
            com.example.nooraai.ui.prayer.PrayerTimesOverlayFragment().show(supportFragmentManager, "prayer_overlay")
        }

        // Do NOT set another onApplyWindowInsetsListener here.
        // Let BaseActivity handle status/nav insets for consistency.

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        intent?.getStringExtra(EXTRA_AUTH_RESULT)?.let {
            Log.d(TAG, "Auth result: $it")
            Toast.makeText(this, "Auth: $it", Toast.LENGTH_SHORT).show()
        }

        binding.tvGreeting.text = "Ayo Belajar Mengaji"

        todayPrayers = loadDummyPrayerSchedule()
        updateNextPrayerFromSchedule()
        refreshHandler.postDelayed(refreshRunnable, 60_000L)

        // Request POST_NOTIFICATIONS permission on Android 13+ (best practice)
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIF
                )
            }
        }
    }

    // handle result of permission requests (forward to LocationHelper for location requests)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // existing handling for POST_NOTIF
        if (requestCode == REQ_POST_NOTIF) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Toast.makeText(this, "Notifikasi dinonaktifkan — aktifkan permission agar alarm bekerja.", Toast.LENGTH_LONG).show()
            }
        }

        // Forward to LocationHelper in case it requested location permissions
        val tvLocationText = binding.root.findViewById<TextView>(R.id.tvLocationText)
        LocationHelper.onRequestPermissionsResult(requestCode, permissions, grantResults, this, tvLocationText) { lat, lon ->
            Log.d(TAG, "location after permission: $lat,$lon")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshHandler.removeCallbacksAndMessages(null)
    }

    private fun loadDummyPrayerSchedule(): List<Prayer> {
        return listOf(
            Prayer("Subuh", "04:30", "Selamat menjalankan ibadah Subuh"),
            Prayer("Dzuhur", "12:05", "Istirahat sejenak, saatnya Dzuhur"),
            Prayer("Ashar", "15:15", "Waktu Ashar memberikan kesejukan"),
            Prayer("Maghrib", "18:00", "Maghrib tiba, syukur dan berkumpul"),
            Prayer("Isya", "19:06", "Sholat isya memberi makna malammu")
        )
    }

    private fun updateNextPrayerFromSchedule() {
        if (todayPrayers.isEmpty()) return

        val now = Calendar.getInstance()
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        var next: Prayer? = null
        for (p in todayPrayers) {
            try {
                val d: Date = sdf.parse(p.time) ?: continue
                val cal = Calendar.getInstance().apply {
                    time = d
                    val today = Calendar.getInstance()
                    set(Calendar.YEAR, today.get(Calendar.YEAR))
                    set(Calendar.MONTH, today.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
                }
                if (cal.timeInMillis > now.timeInMillis) {
                    next = p
                    break
                }
            } catch (t: Throwable) {
                Log.w(TAG, "parse prayer time failed: ${t.message}")
            }
        }

        if (next == null) next = todayPrayers.first()

        runOnUiThread {
            try {
                binding.tvPrayerTime.text = "${next.name} ${next.time}"
                binding.tvGreeting.text = next.message
            } catch (t: Throwable) {
                Log.w(TAG, "failed to update prayer UI: ${t.message}")
            }
        }
    }

    data class Prayer(val name: String, val time: String, val message: String)
}