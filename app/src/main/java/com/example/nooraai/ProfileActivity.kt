package com.example.nooraai

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.nooraai.databinding.ActivityProfileBinding

class ProfileActivity : BaseActivity() {
    private lateinit var binding: ActivityProfileBinding

    override fun getLayoutId(): Int = R.layout.activity_profile
    override fun getNavIndex(): Int = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // bind to child root provided by BaseActivity (reliable)
        val childRoot = getChildRootView()
        binding = ActivityProfileBinding.bind(childRoot)

        // safe window insets handling on the actual root of the child layout
        val rootView: View? = binding.root
        rootView?.let { v ->
            ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // populate dummy user info (if you have real data, replace here)
        val lastEmail = Prefs.getLastEmail(this) ?: "email@example.com"
        binding.tvEmail.text = lastEmail
        binding.tvName.text = Prefs.getUserDisplayName(this) ?: "Nama Pengguna" // if Prefs has this; fallback safe

        // wire logout button
        binding.btnLogout.setOnClickListener {
            showLogoutConfirm()
        }
    }

    private fun showLogoutConfirm() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Apakah kamu yakin ingin logout?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .show()
    }

    /**
     * performLogout:
     * - set logged-in flag false (Prefs.setLoggedIn)
     * - clear any saved sensitive data if needed (optional, add to Prefs)
     * - navigates back to LoginActivity clearing back stack
     */
    private fun performLogout() {
        // update app prefs/state
        try {
            Prefs.setLoggedIn(this, false)
            // if you have additional cleanup functions, call them here, e.g. Prefs.clearUserData(this)
        } catch (t: Throwable) {
            // ignore but log if necessary
            t.printStackTrace()
        }

        // navigate to LoginActivity as fresh start
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        Toast.makeText(this, "Kamu berhasil logout", Toast.LENGTH_SHORT).show()
        finish()
    }
}