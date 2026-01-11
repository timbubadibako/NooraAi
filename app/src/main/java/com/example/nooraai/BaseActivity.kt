package com.example.nooraai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge

/**
 * BaseActivity (centralized insets & bottom nav)
 */
abstract class BaseActivity : AppCompatActivity() {

    abstract fun getLayoutId(): Int
    abstract fun getNavIndex(): Int

    private var selectedIndex = -1
    private lateinit var childRootView: View

    protected fun getChildRootView(): View = childRootView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // make all activities edge-to-edge so insets represent system bars
        enableEdgeToEdge()

        setContentView(R.layout.activity_base)

        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
            ?: throw IllegalStateException("content_frame not found in activity_base.xml")

        // Inflate child view once (do NOT add twice)
        val childView = LayoutInflater.from(this).inflate(getLayoutId(), contentFrame, false)
        childRootView = childView

        // Capture original paddings once so we only add insets on top of them
        val topHeaderView = childView.findViewById<View?>(R.id.topHeader)
        val originalHeaderTop = topHeaderView?.paddingTop ?: 0
        val contentContainerView = childView.findViewById<View?>(R.id.content_container)
        val originalContentBottom = contentContainerView?.paddingBottom ?: 0

        // Centralized insets handling: add statusBar inset to original header top padding,
        // and navigationBar inset to original content bottom padding (preserve left/right)
        ViewCompat.setOnApplyWindowInsetsListener(childView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            topHeaderView?.let { th ->
                th.setPadding(th.paddingLeft, originalHeaderTop + sys.top, th.paddingRight, th.paddingBottom)
            }

            contentContainerView?.let { cc ->
                cc.setPadding(cc.paddingLeft, cc.paddingTop, cc.paddingRight, originalContentBottom + sys.bottom)
            }

            insets
        }

        // Add child and request insets
        contentFrame.addView(childView)
        ViewCompat.requestApplyInsets(childView)

        setupCustomBottomNav()
    }

    override fun onResume() {
        super.onResume()
        // refresh nav state safely (same as before)
        try {
            val icons = listOfNotNull(
                findViewById<ImageView?>(R.id.icon_0),
                findViewById<ImageView?>(R.id.icon_1),
                findViewById<ImageView?>(R.id.icon_2),
                findViewById<ImageView?>(R.id.icon_3)
            )
            val sels = listOfNotNull(
                findViewById<View?>(R.id.sel_0),
                findViewById<View?>(R.id.sel_1),
                findViewById<View?>(R.id.sel_2),
                findViewById<View?>(R.id.sel_3)
            )
            val selLabels = listOfNotNull(
                findViewById<TextView?>(R.id.label_0_sel),
                findViewById<TextView?>(R.id.label_1_sel),
                findViewById<TextView?>(R.id.label_2_sel),
                findViewById<TextView?>(R.id.label_3_sel)
            )
            val selIcons = listOfNotNull(
                findViewById<ImageView?>(R.id.icon_0_sel),
                findViewById<ImageView?>(R.id.icon_1_sel),
                findViewById<ImageView?>(R.id.icon_2_sel),
                findViewById<ImageView?>(R.id.icon_3_sel)
            )

            if (icons.size == 4 && sels.size == 4 && selLabels.size == 4 && selIcons.size == 4) {
                selectNavItemPill(
                    getNavIndex(),
                    icons as List<ImageView>,
                    sels as List<View>,
                    selLabels as List<TextView>,
                    selIcons as List<ImageView>,
                    animate = false
                )
            } else {
                Log.w(TAG, "onResume: bottom nav views not complete, skip refresh")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onResume: refresh bottom nav failed: ${t.message}")
        }
    }

    // -------------------------
    // Bottom nav setup & helpers (unchanged)
    // -------------------------
    private fun setupCustomBottomNav() {
        val item0 = findViewById<FrameLayout?>(R.id.item_0)
        val item1 = findViewById<FrameLayout?>(R.id.item_1)
        val item2 = findViewById<FrameLayout?>(R.id.item_2)
        val item3 = findViewById<FrameLayout?>(R.id.item_3)

        val icons = listOf(
            findViewById<ImageView?>(R.id.icon_0),
            findViewById<ImageView?>(R.id.icon_1),
            findViewById<ImageView?>(R.id.icon_2),
            findViewById<ImageView?>(R.id.icon_3)
        )

        val sels = listOf(
            findViewById<View?>(R.id.sel_0),
            findViewById<View?>(R.id.sel_1),
            findViewById<View?>(R.id.sel_2),
            findViewById<View?>(R.id.sel_3)
        )

        val selLabels = listOf(
            findViewById<TextView?>(R.id.label_0_sel),
            findViewById<TextView?>(R.id.label_1_sel),
            findViewById<TextView?>(R.id.label_2_sel),
            findViewById<TextView?>(R.id.label_3_sel)
        )

        val selIcons = listOf(
            findViewById<ImageView?>(R.id.icon_0_sel),
            findViewById<ImageView?>(R.id.icon_1_sel),
            findViewById<ImageView?>(R.id.icon_2_sel),
            findViewById<ImageView?>(R.id.icon_3_sel)
        )

        if (item0 == null || item1 == null || item2 == null || item3 == null) {
            Log.e(TAG, "setupCustomBottomNav: item containers missing - check include_bottom_navigation.xml")
            return
        }
        if (icons.any { it == null } || sels.any { it == null } || selLabels.any { it == null } || selIcons.any { it == null }) {
            Log.e(TAG, "setupCustomBottomNav: one or more nav views missing - abort setup")
            return
        }

        @Suppress("UNCHECKED_CAST")
        val iconViews = icons as List<ImageView>
        @Suppress("UNCHECKED_CAST")
        val selViews = sels as List<View>
        @Suppress("UNCHECKED_CAST")
        val selLabelViews = selLabels as List<TextView>
        @Suppress("UNCHECKED_CAST")
        val selIconViews = selIcons as List<ImageView>

        // Inactive icons should use primary color, selected icon/text will be white.
        val primaryColor = ContextCompat.getColor(this, R.color.primary_color)
        for ((idx, iv) in iconViews.withIndex()) {
            val orig = iv.drawable
            val toSet: Drawable? = orig?.constantState?.newDrawable()?.mutate() ?: orig
            toSet?.let {
                val wrapped = DrawableCompat.wrap(it)
                DrawableCompat.setTint(wrapped, primaryColor) // inactive icon uses primary color
                iv.setImageDrawable(wrapped)
            } ?: run {
                ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(primaryColor))
            }
            val selOrig = selIconViews[idx].drawable
            val selCopy: Drawable? = selOrig?.constantState?.newDrawable()?.mutate() ?: selOrig
            selCopy?.let {
                val wrappedSel = DrawableCompat.wrap(it)
                DrawableCompat.setTint(wrappedSel, Color.WHITE) // selected icon drawable tinted white
                selIconViews[idx].setImageDrawable(wrappedSel)
            } ?: run {
                ImageViewCompat.setImageTintList(selIconViews[idx], ColorStateList.valueOf(Color.WHITE))
            }
        }

        selectNavItemPill(getNavIndex(), iconViews, selViews, selLabelViews, selIconViews, animate = true)

        item0.setOnClickListener {
            if (getNavIndex() != 0) {
                val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) }
                val opts = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle()
                startActivity(intent, opts)
            }
        }
        item1.setOnClickListener {
            if (getNavIndex() != 1) {
                val intent = Intent(this, LearnActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) }
                val opts = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle()
                startActivity(intent, opts)
            }
        }
        item2.setOnClickListener {
            if (getNavIndex() != 2) {
                val intent = Intent(this, TadarusActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) }
                val opts = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle()
                startActivity(intent, opts)
            }
        }
        item3.setOnClickListener {
            if (getNavIndex() != 3) {
                val intent = Intent(this, ProfileActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) }
                val opts = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle()
                startActivity(intent, opts)
            }
        }
    }

    private fun selectNavItemPill(
        index: Int,
        icons: List<ImageView>,
        sels: List<View>,
        selLabels: List<TextView>,
        selIcons: List<ImageView>,
        animate: Boolean
    ) {
        val duration = if (animate) 220L else 0L
        val primaryColor = ContextCompat.getColor(this, R.color.primary_color)
        val white = Color.WHITE
        val decel = DecelerateInterpolator()

        for (i in icons.indices) {
            val icon = icons[i]
            val sel = sels[i]
            val selLabel = selLabels[i]
            val selIcon = selIcons[i]

            if (i == index) {
                // selected: show pill (sel) and make selected icon + label white
                sel.visibility = View.VISIBLE
                sel.alpha = 0f
                sel.animate().alpha(1f).setDuration(duration).setInterpolator(decel).start()
                icon.animate().alpha(0f).setDuration(duration).withEndAction { icon.visibility = View.INVISIBLE }.start()
                ImageViewCompat.setImageTintList(selIcon, ColorStateList.valueOf(white))
                selLabel.setTextColor(white)
            } else {
                // not selected: hide pill and make main icon use primary color, label use primary
                sel.animate().alpha(0f).setDuration(duration).withEndAction { sel.visibility = View.GONE }.start()
                icon.visibility = View.VISIBLE
                icon.alpha = 0f
                icon.animate().alpha(1f).setDuration(duration).setInterpolator(decel).start()
                ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(primaryColor))
                ImageViewCompat.setImageTintList(selIcon, ColorStateList.valueOf(Color.WHITE))
                selLabel.setTextColor(primaryColor)
            }
        }
        selectedIndex = index
    }

    companion object {
        private const val TAG = "BaseActivity"
    }
}