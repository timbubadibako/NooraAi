package com.example.nooraai.ui.prayer

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.R
import com.example.nooraai.network.RetrofitClient
import com.example.nooraai.util.CityMapper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

import androidx.lifecycle.lifecycleScope

class PrayerTimesOverlayFragment : BottomSheetDialogFragment() {

    private val TAG = "PrayerTimesOverlay"

    private var adapter: PrayerTimesAdapter? = null
    private var progress: ProgressBar? = null
    private var rv: RecyclerView? = null
    private var tvLocation: TextView? = null

    override fun getTheme(): Int {
        return com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // make sure this layout file exists: res/layout/fragment_prayer_times_overlay.xml
        return inflater.inflate(R.layout.fragment_prayer_times_overlay, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(requireContext(), theme).apply {
        setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_rounded)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                behavior.isDraggable = true
                behavior.skipCollapsed = false
            }
            // keep window transparent so rounded drawable shows
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.recyclerPrayerTimes)
        progress = view.findViewById(R.id.progressPrayer)
        tvLocation = view.findViewById(R.id.tvPrayerLocation)

        if (rv == null || progress == null || tvLocation == null) {
            Log.e(TAG, "Required views not found in inflated layout. rv=${rv != null}, progress=${progress != null}, tvLocation=${tvLocation != null}")
            Toast.makeText(requireContext(), "Layout error: missing views for prayer overlay (check layout file name/ids).", Toast.LENGTH_LONG).show()
            return
        }

        adapter = PrayerTimesAdapter(requireContext()) { item, pos ->
            Toast.makeText(requireContext(), "Alarm toggled for ${item.name}", Toast.LENGTH_SHORT).show()
        }

        rv?.layoutManager = LinearLayoutManager(requireContext())
        rv?.adapter = adapter

        // debug prefs values
        // DEBUG block: paste in onViewCreated after reading prefs and before loadPrayerTimes()
        val prefs = requireContext().getSharedPreferences("noora_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString("saved_city_id", null)
        val savedName = prefs.getString("saved_city_name", null)
        Log.d(TAG, "DEBUG_PREFS pre-resolve: saved_city_id=$savedId saved_city_name=$savedName")
        Toast.makeText(requireContext(), "DBG: id=${savedId ?: "null"} name=${savedName ?: "null"}", Toast.LENGTH_LONG).show()

// 1) is CityMapper loaded from prefs?
        val loaded = CityMapper.loadFromPrefs(requireContext())
        Log.d(TAG, "DEBUG_CITYMAPPER loadedFromPrefs=$loaded")

// 2) list some candidate matches for savedName (if exists)
        if (!savedName.isNullOrBlank()) {
            val candidates = CityMapper.findCandidates(savedName, limit = 20)
            Log.d(TAG, "DEBUG_CITYMAPPER candidates count=${candidates.size} for name='$savedName'")
            candidates.forEachIndexed { i, (id,name) ->
                Log.d(TAG, "CAND[$i] id=$id name='$name'")
            }
            // show quick toast of first candidate (or none)
            if (candidates.isNotEmpty()) Toast.makeText(requireContext(), "DBG: first candidate = ${candidates.first().second}", Toast.LENGTH_LONG).show()
            else Toast.makeText(requireContext(), "DBG: no candidates found for \"$savedName\"", Toast.LENGTH_LONG).show()
        }

//        val p = getSharedPreferences("noora_prefs", Context.MODE_PRIVATE)
//        p.edit().putString("saved_city_id", "1234").putString("saved_city_name","Kuningan").apply()


        loadPrayerTimes()
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    /**
     * Try to resolve saved_city_id automatically:
     * - If already stored -> return it
     * - If saved_city_name exists -> load CityMapper (from prefs or by fetching list) and try to find match
     * - If multiple candidates -> show dialog and wait for user pick (suspend)
     */
    private suspend fun resolveCityIdIfMissing(): String? {
        val prefs = requireContext().getSharedPreferences("noora_prefs", Context.MODE_PRIVATE)
        val existingId = prefs.getString("saved_city_id", null)
        if (!existingId.isNullOrBlank()) return existingId

        val name = prefs.getString("saved_city_name", null)
        if (name.isNullOrBlank()) return null

        // Try load cached mapping first
        var loaded = CityMapper.loadFromPrefs(requireContext())
        if (!loaded) {
            // Try network fetch (suspend)
            try {
                // ApiService.getAllSholatKota is suspend; call directly
                val resp = RetrofitClient.apiService.getAllSholatKota()
                if (resp.isSuccessful) {
                    val raw = resp.body()?.toString() ?: ""
                    if (raw.isNotBlank()) {
                        CityMapper.saveJsonToPrefs(requireContext(), raw)
                        CityMapper.buildFromJsonString(raw)
                        loaded = true
                    }
                } else {
                    Log.w(TAG, "getAllSholatKota failed: ${resp.code()} ${resp.message()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed fetching kota list", e)
            }
        }

        // ensure mapper built (if loadFromPrefs returned false but buildFromJsonString might have been called)
        // Try to find by name using CityMapper
        val (foundId, candidates) = CityMapper.findCityIdByName(name, returnCandidatesIfAmbiguous = true)
        if (!foundId.isNullOrBlank()) {
            prefs.edit().putString("saved_city_id", foundId).apply()
            return foundId
        }

        // If ambiguous, show dialog and suspend until user picks
        if (candidates.isNotEmpty()) {
            val names = candidates.map { it.second }.toTypedArray()
            val chosen = suspendCancellableCoroutine<String?> { cont ->
                // show dialog on main thread
                val dlg = AlertDialog.Builder(requireContext())
                    .setTitle("Pilih kota")
                    .setItems(names) { dialog, which ->
                        val sel = candidates[which]
                        prefs.edit().putString("saved_city_id", sel.first).putString("saved_city_name", sel.second).apply()
                        cont.resume(sel.first)
                        dialog.dismiss()
                    }
                    .setOnCancelListener {
                        cont.resume(null)
                    }
                    .create()
                dlg.show()
                // cleanup if coroutine cancelled
                cont.invokeOnCancellation { dlg.dismiss() }
            }
            return chosen
        }

        return null
    }

    private fun loadPrayerTimes() {
        lifecycleScope.launch {
            try {
                val prefs = requireContext().getSharedPreferences("noora_prefs", Context.MODE_PRIVATE)
                var cityId = prefs.getString("saved_city_id", null)
                val cityName = prefs.getString("saved_city_name", null)
                tvLocation?.text = cityName ?: "Lokasi tidak tersedia"

                if (cityId.isNullOrBlank()) {
                    // attempt to resolve automatically (suspend)
                    cityId = resolveCityIdIfMissing()
                }

                if (cityId.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "City id not set. Please save location first.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                progress?.visibility = View.VISIBLE
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                // fetch on IO dispatcher
                val list = withContext(Dispatchers.IO) { PrayerRepository.fetchForDate(cityId, today) }
                progress?.visibility = View.GONE
                if (list.isEmpty()) {
                    Toast.makeText(requireContext(), "Gagal memuat jadwal sholat", Toast.LENGTH_SHORT).show()
                } else {
                    adapter?.submitList(list)
                }
            } catch (e: Exception) {
                progress?.visibility = View.GONE
                Log.e(TAG, "Error in loadPrayerTimes", e)
                Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}