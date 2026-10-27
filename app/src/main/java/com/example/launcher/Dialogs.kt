package com.example.launcher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

private fun MainActivity.dlg(): AlertDialog.Builder =
    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

internal fun MainActivity.showMenu(app: App) {
    val inDock = dockPkgs().contains(app.pkg)
    val options = arrayOf(
        "Información de la app",
        "Desinstalar",
        "Ocultar",
        if (inDock) "Quitar del dock" else "Agregar al dock",
        "Cambiar categoría (" + app.cat + ")"
    )
    dlg().setTitle(app.label).setItems(options) { _, which ->
        when (which) {
            0 -> startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
            )
            1 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)))
            2 -> hideApp(app)
            3 -> toggleDock(app, inDock)
            4 -> pickCategory(app)
            else -> {}
        }
    }.show()
}

internal fun MainActivity.pickCategory(app: App) {
    val names = (Cat.assignable + "Automática").toTypedArray()
    dlg().setTitle("Categoría de " + app.label).setItems(names) { _, i ->
        val e = prefs.edit()
        if (i < Cat.assignable.size) {
            e.putString("cat_" + app.pkg, Cat.assignable[i])
        } else {
            e.remove("cat_" + app.pkg)
        }
        e.apply()
        loadApps()
    }.show()
}

internal fun MainActivity.hideApp(app: App) {
    val set = hiddenSet()
    set.add(app.pkg)
    prefs.edit().putStringSet("hidden", set).apply()
    refreshLists()
    Toast.makeText(this, app.label + " oculta (se restaura en Ajustes)", Toast.LENGTH_LONG).show()
}

internal fun MainActivity.showHidden() {
    val hidden = hiddenSet()
    val apps = everyApp.filter { it.pkg in hidden }
    if (apps.isEmpty()) {
        Toast.makeText(this, "No hay apps ocultas", Toast.LENGTH_SHORT).show()
        return
    }
    val checked = BooleanArray(apps.size) { true }
    dlg().setTitle("Apps ocultas (desmarca para mostrar)")
        .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, c ->
            checked[i] = c
        }
        .setPositiveButton("Guardar") { _, _ ->
            val set = hiddenSet()
            apps.forEachIndexed { i, a -> if (!checked[i]) set.remove(a.pkg) }
            prefs.edit().putStringSet("hidden", set).apply()
            refreshLists()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

internal fun MainActivity.expandNotifications() {
    try {
        val sbs = getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(sbs)
    } catch (e: Exception) {
        Toast.makeText(this, "No se pudo abrir las notificaciones", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.showSettings() {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(dp(20), dp(8), dp(20), 0)

    fun addSlider(title: String, maxV: Int, startV: Int, onChange: (Int) -> Unit) {
        val label = TextView(this)
        label.text = title
        label.setTextColor(Color.WHITE)
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

    addSlider("Columnas del cajón (3 a 6)", 3, cols - 3) {
        prefs.edit().putInt("cols", it + 3).apply()
    }
    addSlider("Tamaño de íconos", 32, iconDp - 40) {
        prefs.edit().putInt("icon", it + 40).apply()
    }
    addSlider("Opacidad del fondo del cajón", 200, bgAlpha - 40) {
        prefs.edit().putInt("alpha", it + 40).apply()
    }

    val hiddenBtn = Button(this)
    hiddenBtn.text = "Apps ocultas..."
    hiddenBtn.setOnClickListener { showHidden() }
    box.addView(hiddenBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

    val resetBtn = Button(this)
    resetBtn.text = "Restablecer categorías"
    resetBtn.setOnClickListener {
        val e = prefs.edit()
        for (k in prefs.all.keys) {
            if (k.startsWith("cat_")) e.remove(k)
        }
        e.apply()
        loadApps()
    }
    box.addView(resetBtn, LinearLayout.LayoutParams(-1, -2))

    dlg().setTitle("Ajustes del launcher")
        .setView(box)
        .setPositiveButton("Listo", null)
        .setOnDismissListener { applySettings() }
        .show()
}
