package com.example.nooraai.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Centralized helper to fetch device location, fill a location TextView (chip),
 * and persist the last coordinates for other parts of the app to reuse.
 *
 * Usage:
 * - Call LocationHelper.fetchAndFillLocation(activity, tvLocationText) from any Activity.
 * - Forward permission results to LocationHelper.onRequestPermissionsResult(...) from the Activity's onRequestPermissionsResult.
 * - Read saved coords anywhere with LocationHelper.getLastSavedCoords(context).
 *
 * Note: This helper uses ActivityCompat.requestPermissions (classic flow) so it works easily with existing onRequestPermissionsResult.
 * If you prefer ActivityResult API integration, we can provide an alternative implementation.
 */
object LocationHelper {
    private const val TAG = "LocationHelper"
    const val REQ_LOCATION = 2001 // public so callers can recognize if they want (optional)
    private const val PREFS = "noora_prefs"
    private const val KEY_LAT = "last_lat"
    private const val KEY_LON = "last_lon"
    private const val KEY_CITY_ID = "saved_city_id"
    private const val KEY_CITY_NAME = "saved_city_name"

    /**
     * Entry point: will check permission and either request it or fetch the location now.
     *
     * @param activity Activity (should be a FragmentActivity for coroutine lifecycle usage)
     * @param chipTextView TextView to fill with human-readable location label (city/province) or lat/lon fallback
     * @param onLocationSaved optional callback invoked with (lat, lon) after location is obtained and saved to prefs
     */
    fun fetchAndFillLocation(activity: Activity, chipTextView: TextView, onLocationSaved: ((Double, Double) -> Unit)? = null) {
        val hasFine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            fetchLocationOnce(activity, chipTextView, onLocationSaved)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
        }
    }

    /**
     * Forward permission result to this helper from Activity.onRequestPermissionsResult
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        activity: Activity,
        chipTextView: TextView,
        onLocationSaved: ((Double, Double) -> Unit)? = null
    ) {
        if (requestCode != REQ_LOCATION) return

        val granted = grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            fetchLocationOnce(activity, chipTextView, onLocationSaved)
        } else {
            Toast.makeText(activity, "Izin lokasi diperlukan untuk mendapatkan lokasi.", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("MissingPermission")
    private fun fetchLocationOnce(activity: Activity, chipTextView: TextView, onLocationSaved: ((Double, Double) -> Unit)?) {
        val fused = LocationServices.getFusedLocationProviderClient(activity)

        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                handleLocationObtained(activity, loc.latitude, loc.longitude, chipTextView, onLocationSaved)
            } else {
                requestSingleLocationUpdate(activity, fused, chipTextView, onLocationSaved)
            }
        }.addOnFailureListener { ex ->
            Log.w(TAG, "lastLocation failed: ${ex.message}")
            requestSingleLocationUpdate(activity, fused, chipTextView, onLocationSaved)
        }
    }

    @Suppress("MissingPermission")
    private fun requestSingleLocationUpdate(
        activity: Activity,
        fused: FusedLocationProviderClient,
        chipTextView: TextView,
        onLocationSaved: ((Double, Double) -> Unit)?
    ) {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdates(1)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val l = result.lastLocation
                if (l != null) {
                    handleLocationObtained(activity, l.latitude, l.longitude, chipTextView, onLocationSaved)
                } else {
                    Toast.makeText(activity, "Gagal mendapatkan lokasi", Toast.LENGTH_SHORT).show()
                }
                fused.removeLocationUpdates(this)
            }
        }
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
    }

    private fun handleLocationObtained(
        activity: Activity,
        lat: Double,
        lon: Double,
        chipTextView: TextView,
        onLocationSaved: ((Double, Double) -> Unit)?
    ) {
        // persist coords as floats
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_LAT, lat.toFloat()).putFloat(KEY_LON, lon.toFloat()).apply()

        // reverse geocode on IO and update UI using lifecycleScope if available
        val fragActivity = activity as? FragmentActivity
        if (fragActivity != null) {
            fragActivity.lifecycleScope.launch {
                val label = withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(activity, Locale.getDefault())
                        val list = geocoder.getFromLocation(lat, lon, 1)
                        if (!list.isNullOrEmpty()) {
                            val a = list[0]
                            val city = a.locality ?: a.subAdminArea ?: a.adminArea
                            val country = a.countryName
                            if (city != null) "$city${if (country != null) ", $country" else ""}" else a.getAddressLine(0)
                        } else null
                    } catch (t: Throwable) {
                        null
                    }
                } ?: String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon)

                chipTextView.text = label
                onLocationSaved?.invoke(lat, lon)
            }
        } else {
            // fallback synchronous attempt (rare)
            try {
                val geocoder = Geocoder(activity, Locale.getDefault())
                val list = geocoder.getFromLocation(lat, lon, 1)
                val label = if (!list.isNullOrEmpty()) {
                    val a = list[0]
                    val city = a.locality ?: a.subAdminArea ?: a.adminArea
                    val country = a.countryName
                    if (city != null) "$city${if (country != null) ", $country" else ""}" else a.getAddressLine(0)
                } else String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon)

                chipTextView.text = label
            } catch (t: Throwable) {
                chipTextView.text = String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon)
            }
            onLocationSaved?.invoke(lat, lon)
        }
    }

    /**
     * Read last saved coords from prefs (returns null if not present)
     */
    fun getLastSavedCoords(context: Context): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
        val lon = prefs.getFloat(KEY_LON, 0f).toDouble()
        return Pair(lat, lon)
    }

    // Simple name -> cityId mapping (extend this map with more entries as needed)
    private fun mapCityNameToCityId(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val n = name.trim().lowercase(Locale.getDefault())

        // NOTE: this is a tiny sample. Add more mappings or replace with a full JSON/CSV lookup.
        val map = mapOf(
            "jakarta pusat" to "1631",
            "jakarta selatan" to "1632",
            "jakarta barat" to "1633",
            "jakarta timur" to "1634",
            "jakarta utara" to "1635",
            "bandung" to "1101",
            "surabaya" to "3512",
            "medan" to "1671",
            "makassar" to "7371",
            "yogyakarta" to "3401"
            // tambahkan kota lain sesuai data API myquran (idealnya pakai daftar lengkap)
        )

        // try exact contains match (allows "kota kabupaten" etc.)
        val entry = map.entries.firstOrNull { n.contains(it.key) }
        return entry?.value
    }
}