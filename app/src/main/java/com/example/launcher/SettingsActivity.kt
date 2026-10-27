package com.example.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(16), dp(20), dp(32))

        status = TextView(this)
        status.textSize = 17f
        box.addView(status)

        addButton(box, "Establecer como launcher predeterminado") { requestHome() }
        addButton(box, "Abrir ajustes de apps predeterminadas") { openDefaultSettings() }

        addTitle(box, "Apariencia")
        addSlider(box, "Columnas del cajón (3 a 6)", 3, prefs.getInt("cols", 4) - 3) {
            prefs.edit().putInt("cols", it + 3).apply()
        }
        addSlider(box, "Tamaño de íconos", 32, prefs.getInt("icon", 52) - 40) {
            prefs.edit().putInt("icon", it + 40).apply()
        }
        addSlider(box, "Opacidad del fondo del cajón", 200, prefs.getInt("alpha", 150) - 40) {
            prefs.edit().putInt("alpha", it + 40).apply()
        }

        addTitle(box, "Datos")
        addButton(box, "Apps ocultas...") { showHidden() }
        addButton(box, "Restablecer dock") {
            prefs.edit().remove("dock").apply()
            Toast.makeText(this, "Dock restablecido", Toast.LENGTH_SHORT).show()
        }
        addButton(box, "Reiniciar \"Más usadas\"") {
            val e = prefs.edit()
            for (k in prefs.all.keys) {
                if (k.startsWith("c_")) e.remove(k)
            }
            e.apply()
            Toast.makeText(this, "Listo", Toast.LENGTH_SHORT).show()
        }

        val scroll = ScrollView(this)
        scroll.addView(box)
        setContentView(scroll)

        // La primera vez que se abre, pide ser el launcher predeterminado
        if (!isDefault() && !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            requestHome()
        }
    }

    override fun onResume() {
        super.onResume()
        status.text = if (isDefault()) "Estado: este es tu launcher predeterminado"
        else "Estado: aún NO es tu launcher predeterminado"
    }

    private fun isDefault(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == packageName
    }

    private fun requestHome() {
        if (isDefault()) {
            Toast.makeText(this, "Ya es tu launcher predeterminado", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
                    return
                } catch (e: Exception) {
                    // si el sistema no lo permite, se usa el método de ajustes
                }
            }
        }
        openDefaultSettings()
    }

    private fun openDefaultSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Abre Ajustes > Apps > Apps predeterminadas", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addTitle(box: LinearLayout, text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 20f
        t.setPadding(0, dp(26), 0, 0)
        box.addView(t)
    }

    private fun addButton(box: LinearLayout, text: String, action: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(10)
        box.addView(b, lp)
    }

    private fun addSlider(box: LinearLayout, title: String, maxV: Int, startV: Int, onChange: (Int) -> Unit) {
        val label = TextView(this)
        label.text = title
        label.setPadding(0, dp(14), 0, 0)
        val bar = SeekBar(this)
        bar.max = maxV
        bar.progress = startV
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(label)
        box.addView(bar)
    }

    private fun showHidden() {
        val pm = packageManager
        val hidden = HashSet<String>(prefs.getStringSet("hidden", null) ?: emptySet<String>())
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.second in hidden }
            .distinctBy { it.second }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No hay apps ocultas", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = BooleanArray(apps.size) { true }
        AlertDialog.Builder(this)
            .setTitle("Apps ocultas (desmarca para mostrar)")
            .setMultiChoiceItems(apps.map { it.first }.toTypedArray(), checked) { _, i, c ->
                checked[i] = c
            }
            .setPositiveButton("Guardar") { _, _ ->
                apps.forEachIndexed { i, a -> if (!checked[i]) hidden.remove(a.second) }
                prefs.edit().putStringSet("hidden", hidden).apply()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
