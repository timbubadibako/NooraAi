package com.example.nooraai.ui.compass

import android.animation.ObjectAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.example.nooraai.R
import com.example.nooraai.util.LocationHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.*

/**
 * KiblatoverlayFragment
 *
 * BottomSheetDialogFragment that shows a rounded-top sheet containing a CardView with a compass.
 * - Adds UX: when device heading is within ALIGN_THRESHOLD degrees to qibla, tvDegrees turns primary color and the phone vibrates once.
 */
class KiblatoverlayFragment : BottomSheetDialogFragment(), SensorEventListener {

    companion object {
        private const val PREFS_NAME = "noora_compass_prefs"
        private const val PREF_KEY_OFFSET = "compass_offset_degrees"
        private const val KAABA_LAT = 21.422487
        private const val KAABA_LON = 39.826206

        // degrees tolerance to consider "aligned" with qibla
        private const val ALIGN_THRESHOLD = 3f
    }

    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null

    private var ivNeedle: ImageView? = null
    private var ivDial: ImageView? = null
    private var tvDegrees: TextView? = null
    private var tvCompassSubtitle: TextView? = null

    // qibla marker view (small dot) added programmatically into the same FrameLayout as the dial
    private var qiblaMarker: View? = null
    private var qiblaBearing: Double? = null

    // smoothing state
    private var smoothAzimuth: Float = 0f
    private var animator: ObjectAnimator? = null

    // smoothing factor (0..1). Larger = faster updates, smaller = smoother.
    private val SMOOTHING_ALPHA = 0.30f

    private lateinit var prefs: SharedPreferences

    // aligned state used to trigger single vibration when entering alignment
    private var wasAligned = false

    // vibrator instance (nullable)
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // get Vibrator in a backward-compatible way
        vibrator = ContextCompat.getSystemService(requireContext(), Vibrator::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(requireContext(), theme).apply {
        setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_rounded)

                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = true
                behavior.skipCollapsed = true
            }

            // make dialog window transparent so our rounded sheet background shows properly
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_kiblat_overlay, container, false)

        ivNeedle = root.findViewById(R.id.ivNeedle)
        ivDial = root.findViewById(R.id.ivDial)
        tvDegrees = root.findViewById(R.id.tvDegrees)
        tvCompassSubtitle = root.findViewById(R.id.tvCompassSubtitle)

        // ensure needle uses our vector (if not set in XML)
        ivNeedle?.setImageResource(R.drawable.ic_compass_needle)

        // ensure pivot is center after layout
        ivNeedle?.doOnLayout {
            ivNeedle?.pivotX = (ivNeedle?.width ?: 0) / 2f
            ivNeedle?.pivotY = (ivNeedle?.height ?: 0) / 2f
        }

        // create qibla marker (small circular view) and add it into the dial's parent
        qiblaMarker = View(requireContext()).apply {
            setBackgroundResource(R.drawable.bg_qibla_marker)
            visibility = View.INVISIBLE
        }

        (ivDial?.parent as? FrameLayout)?.let { parent ->
            val sizePx = dpToPx(requireContext(), 10) // 10dp marker
            val lp = FrameLayout.LayoutParams(sizePx, sizePx)
            parent.addView(qiblaMarker, lp)
        }

        // Setup calibration long-press on degrees
        tvDegrees?.setOnLongClickListener {
            val current = smoothAzimuthNormalized()
            prefs.edit().putFloat(PREF_KEY_OFFSET, current).apply()
            Toast.makeText(requireContext(), "Kalibrasi tersimpan (offset ${current.roundToInt()}°)", Toast.LENGTH_SHORT).show()
            true
        }

        tvDegrees?.setOnClickListener {
            val saved = prefs.getFloat(PREF_KEY_OFFSET, 0f)
            Toast.makeText(requireContext(), "Offset kalibrasi: ${saved.roundToInt()}°", Toast.LENGTH_SHORT).show()
        }

        // compute qibla bearing from last saved coords (if available)
        val coords = LocationHelper.getLastSavedCoords(requireContext())
        if (coords != null) {
            qiblaBearing = bearingToKaaba(coords.first, coords.second)
            tvCompassSubtitle?.text = "Arah kiblat: ${qiblaBearing?.roundToInt()}°"
        } else {
            tvCompassSubtitle?.text = getString(R.string.kiblat_location_unavailable)
            qiblaBearing = null
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        rotationVectorSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        animator?.cancel()
    }

    override fun onDestroyView() {
        ivNeedle = null
        ivDial = null
        tvDegrees = null
        tvCompassSubtitle = null
        animator = null
        qiblaMarker = null
        super.onDestroyView()
    }

    // SensorEventListener
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val type = event.sensor.type
        if (type != Sensor.TYPE_ROTATION_VECTOR && type != Sensor.TYPE_GAME_ROTATION_VECTOR) return

        // rotation vector -> rotation matrix
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // remap coordinate system based on device rotation (so azimuth is correct in all orientations)
        val remapped = FloatArray(9)
        val display = requireActivity().windowManager.defaultDisplay
        val rot = display.rotation
        when (rot) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, remapped)
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remapped)
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remapped)
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remapped)
            else -> System.arraycopy(rotationMatrix, 0, remapped, 0, 9)
        }

        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        // orientation[0] is azimuth in radians (-pi..pi)
        var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (azimuthDeg < 0) azimuthDeg += 360f

        // smoothing using shortest path (prevents big jumps near 0/360)
        val newSmooth = shortestPathInterpolate(smoothAzimuth, azimuthDeg, SMOOTHING_ALPHA)
        smoothAzimuth = newSmooth

        // apply stored calibration offset
        val offset = prefs.getFloat(PREF_KEY_OFFSET, 0f)
        val calibrated = (smoothAzimuth - offset + 360f) % 360f

        // Update degrees text with calibrated heading (what the device heading is)
        tvDegrees?.text = "${calibrated.roundToInt()}°"

        // Rotate needle so it points to North relative to device orientation:
        val needleTarget = calibrated  // use calibrated directly so small positive angles rotate right
        animateNeedleTo(needleTarget)

        // update qibla subtitle and reposition marker if we have qiblaBearing
        qiblaBearing?.let { qb ->
            // qb = absolute bearing from North to Kaaba (0..360)
            // relative to device heading (calibrated): angle you must turn device from current heading to face qibla
            val angleToQibla = ((qb.toFloat() - calibrated + 360f) % 360f)

            // compute shortest difference (0..180)
            val diff = min(angleToQibla, 360f - angleToQibla)

            // change tvDegrees color based on alignment
            val colorAligned = ContextCompat.getColor(requireContext(), R.color.primary_color)
            val colorNormal = ContextCompat.getColor(requireContext(), R.color.text_primary)
            if (diff <= ALIGN_THRESHOLD) {
                tvDegrees?.setTextColor(colorAligned)
                // vibrate once when entering aligned state
                if (!wasAligned) {
                    triggerVibration()
                    wasAligned = true
                }
            } else {
                tvDegrees?.setTextColor(colorNormal)
                wasAligned = false
            }

            tvCompassSubtitle?.text = "Arah kiblat: ${qb.roundToInt()}° (relatif ${angleToQibla.roundToInt()}°)"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // ignore
    }

    private fun animateNeedleTo(needleRotation: Float) {
        ivNeedle?.let { needle ->
            animator?.cancel()
            val start = normalizeAngle(needle.rotation)
            var end = needleRotation
            var delta = ((end - start + 540f) % 360f) - 180f
            end = start + delta

            animator = ObjectAnimator.ofFloat(needle, View.ROTATION, start, end).apply {
                duration = 200L
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun triggerVibration() {
        try {
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(80)
                }
            }
        } catch (t: Throwable) {
            // ignore vibration errors
        }
    }

    /**
     * Interpolate using shortest angular path
     */
    private fun shortestPathInterpolate(current: Float, target: Float, alpha: Float): Float {
        var delta = (target - current + 540f) % 360f - 180f
        return (current + alpha * delta + 360f) % 360f
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a < 0) a += 360f
        return a
    }

    // helper: current smooth azimuth normalized (0..360)
    private fun smoothAzimuthNormalized(): Float {
        return (smoothAzimuth % 360f + 360f) % 360f
    }

    /**
     * Calculate great-circle initial bearing from (lat1, lon1) -> (lat2, lon2) in degrees (0..360)
     */
    private fun bearingToKaaba(lat1: Double, lon1: Double): Double {
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(KAABA_LAT)
        val Δλ = Math.toRadians(KAABA_LON - lon1)

        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        var θ = Math.toDegrees(atan2(y, x))
        θ = (θ + 360.0) % 360.0
        return θ
    }

    /**
     * Position the qiblaMarker on top of the dial according to absolute qiblaBearing (0 = up/north).
     */
    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
    }
}