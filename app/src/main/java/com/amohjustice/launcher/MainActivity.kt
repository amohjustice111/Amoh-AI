package com.amohjustice.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.security.MessageDigest

data class LaunchableApp(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("amoh_launcher", MODE_PRIVATE) }
    private val hidden = mutableSetOf<String>()
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var contentBody: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hidden.addAll(prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet())
        showHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 16f, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(0xFF172033.toInt())
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

    private fun button(value: String, action: () -> Unit): Button =
        Button(this).apply { text = value; setOnClickListener { action() } }

    private fun page(heading: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            setBackgroundColor(0xFFF4F6FB.toInt())
        }
        title = text(heading, 25f, true)
        root.addView(title)
        val scroll = ScrollView(this)
        contentBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(contentBody)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun getApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null
                try {
                    LaunchableApp(info.loadLabel(packageManager).toString(), pkg, info.loadIcon(packageManager))
                } catch (_: Exception) { null }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun appRow(app: LaunchableApp, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val icon = ImageView(this).apply { setImageDrawable(app.icon) }
        row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(text(app.label, 16f, true), LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(8)
        contentBody.addView(row, lp)
    }

    private fun showHome() {
        page("AMOH AI")
        contentBody.addView(text("Your apps", 16f, true))
        contentBody.addView(button("Private Apps  •  PIN protected") { requestPin { showPrivateApps() } })
        getApps().filter { it.packageName !in hidden }.forEach { app ->
            appRow(app) { launch(app.packageName) }
        }
        contentBody.addView(text("Set AMOH AI as your default Home app in Android Settings to use it as your launcher.", 13f))
    }

    private fun requestPin(onSuccess: () -> Unit) {
        val saved = prefs.getString("pin_hash", null)
        val input = EditText(this).apply {
            hint = if (saved == null) "Create a 4–8 digit PIN" else "Enter your PIN"
            inputType = 2
        }
        AlertDialog.Builder(this)
            .setTitle(if (saved == null) "Create your private PIN" else "Unlock Private Apps")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (saved == null) "Save PIN" else "Unlock") { _, _ ->
                val pin = input.text.toString()
                if (!pin.matches(Regex("\\d{4,8}"))) {
                    toast("Use a PIN with 4 to 8 digits")
                } else if (saved == null) {
                    prefs.edit().putString("pin_hash", hash(pin)).apply()
                    toast("PIN created")
                    onSuccess()
                } else if (hash(pin) == saved) onSuccess()
                else toast("Incorrect PIN")
            }.show()
    }

    private fun showPrivateApps() {
        page("Private Apps")
        contentBody.addView(button("+  Add Apps") { showAppPicker() })
        contentBody.addView(button("Back to Home") { showHome() })
        val hiddenApps = getApps().filter { it.packageName in hidden }
        if (hiddenApps.isEmpty()) contentBody.addView(text("No private apps yet. Tap + Add Apps to choose apps."))
        hiddenApps.forEach { app ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val open = Button(this).apply { text = "Open " + app.label; setOnClickListener { launch(app.packageName) } }
            val restore = Button(this).apply {
                text = "Unhide"
                setOnClickListener {
                    hidden.remove(app.packageName)
                    saveHidden()
                    showPrivateApps()
                }
            }
            row.addView(open, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(restore)
            contentBody.addView(row)
        }
    }

    private fun showAppPicker() {
        val apps = getApps().filter { it.packageName !in hidden }
        if (apps.isEmpty()) { toast("No more apps to add"); return }
        val labels = apps.map { it.label }.toTypedArray()
        val selected = BooleanArray(apps.size)
        AlertDialog.Builder(this)
            .setTitle("Choose apps to hide in AMOH AI")
            .setMultiChoiceItems(labels, selected) { _, which, checked -> selected[which] = checked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Hide selected") { _, _ ->
                apps.forEachIndexed { i, app -> if (selected[i]) hidden.add(app.packageName) }
                saveHidden()
                showPrivateApps()
            }.show()
    }

    private fun saveHidden() {
        prefs.edit().putStringSet("hidden_apps", hidden.toSet()).apply()
    }

    private fun launch(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) startActivity(intent) else toast("Android cannot open this app")
        } catch (_: Exception) { toast("Could not open app") }
    }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
