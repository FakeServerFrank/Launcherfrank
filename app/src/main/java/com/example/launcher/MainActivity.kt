package com.example.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    data class App(val label: String, val pkg: String, val icon: Drawable)

    private lateinit var prefs: SharedPreferences

    private var everyApp = listOf<App>()   // todas las apps
    private var allApps = listOf<App>()    // sin las ocultas
    private var shown = listOf<App>()      // resultado de la búsqueda
    private var loading = false
    private var drawerOpen = false

    private lateinit var root: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var search: EditText
    private lateinit var frequentLabel: TextView
    private lateinit var frequentRow: LinearLayout
    private lateinit var grid: GridView
    private var adapter: AppAdapter? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val cols: Int get() = prefs.getInt("cols", 4)
    private val iconDp: Int get() = prefs.getInt("icon", 52)
    private val bgAlpha: Int get() = prefs.getInt("alpha", 150)

    private fun pill(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    // ------------------------------------------------------------------
    //  Creación de la interfaz
    // ------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE)
        root = FrameLayout(this)

        // ---------- PANTALLA DE INICIO ----------
        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(24))
        }
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 68f
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 2f, Color.argb(170, 0, 0, 0))
            gravity = Gravity.CENTER
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 17f
            setTextColor(Color.WHITE)
            setShadowLayer(8f, 0f, 2f, Color.argb(170, 0, 0, 0))
            gravity = Gravity.CENTER
        }
        val swipeHint = View(this).apply {
            background = pill(Color.argb(150, 255, 255, 255), 3)
        }
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = pill(Color.argb(90, 0, 0, 0), 30)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        home.addView(clock, LinearLayout.LayoutParams(-1, -2))
        home.addView(date, LinearLayout.LayoutParams(-1, -2))
        home.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        home.addView(swipeHint, LinearLayout.LayoutParams(dp(40), dp(5)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(14)
        })
        home.addView(dock, LinearLayout.LayoutParams(-1, -2))

        val homeGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (Math.abs(vy) < Math.abs(vx)) return false
                if (vy < -800f) {
                    openDrawer()
                    return true
                }
                if (vy > 800f) {
                    expandNotifications()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                showSettings()
            }
        })
        home.setOnTouchListener { _, ev -> homeGestures.onTouchEvent(ev) }

        // ---------- CAJÓN DE APPS ----------
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            visibility = View.INVISIBLE
            setPadding(dp(12), dp(28), dp(12), dp(8))
        }

        val handle = FrameLayout(this)
        handle.addView(View(this).apply {
            background = pill(Color.argb(170, 255, 255, 255), 3)
        }, FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER))
        handle.addView(TextView(this).apply {
            text = "Ajustes"
            textSize = 13f
            setTextColor(Color.argb(220, 255, 255, 255))
            setPadding(dp(10), dp(8), dp(4), dp(8))
            setOnClickListener { showSettings() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))

        val handleGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                closeDrawer()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy > 500f) {
                    closeDrawer()
                    return true
                }
                return false
            }
        })
        handle.setOnTouchListener { _, ev -> handleGestures.onTouchEvent(ev) }

        search = EditText(this).apply {
            hint = "Buscar app..."
            setHintTextColor(Color.argb(190, 255, 255, 255))
            setTextColor(Color.WHITE)
            background = pill(Color.argb(70, 255, 255, 255), 24)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ ->
                shown.firstOrNull()?.let { launch(it) }
                true
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    filter(s.toString())
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }

        frequentLabel = TextView(this).apply {
            text = "Más usadas"
            textSize = 13f
            setTextColor(Color.argb(220, 255, 255, 255))
            setPadding(dp(6), dp(14), 0, dp(6))
        }
        frequentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 5f
        }

        grid = GridView(this).apply {
            verticalSpacing = dp(14)
            selector = ColorDrawable(Color.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            setPadding(0, dp(14), 0, 0)
            clipToPadding = false
        }
        grid.setOnItemClickListener { _, _, pos, _ ->
            if (pos in shown.indices) launch(shown[pos])
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos in shown.indices) showMenu(shown[pos])
            true
        }

        drawer.addView(handle, LinearLayout.LayoutParams(-1, dp(36)))
        drawer.addView(search, LinearLayout.LayoutParams(-1, -2))
        drawer.addView(frequentLabel, LinearLayout.LayoutParams(-1, -2))
        drawer.addView(frequentRow, LinearLayout.LayoutParams(-1, -2))
        drawer.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        root.post { drawer.translationY = root.height.toFloat() }

        applySettings()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeDrawer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        closeDrawer()
    }

    // ------------------------------------------------------------------
    //  Cajón: abrir y cerrar
    // ------------------------------------------------------------------
    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawer.visibility = View.VISIBLE
        drawer.translationY = root.height.toFloat()
        drawer.animate().translationY(0f).setDuration(220).start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
        search.setText("")
        drawer.animate()
            .translationY(root.height.toFloat())
            .setDuration(200)
            .withEndAction {
                if (!drawerOpen) drawer.visibility = View.INVISIBLE
            }
            .start()
    }

    // ------------------------------------------------------------------
    //  Carga de apps
    // ------------------------------------------------------------------
    private fun loadApps() {
        if (loading) return
        loading = true
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                loading = false
                everyApp = list
                refreshLists()
            }
        }.start()
    }

    private fun refreshLists() {
        val hidden = hiddenSet()
        allApps = everyApp.filter { it.pkg !in hidden }
        buildDock()
        buildFrequent()
        filter(search.text.toString())
    }

    private fun filter(q: String) {
        val query = q.trim()
        shown = if (query.isEmpty()) allApps
        else allApps.filter { it.label.contains(query, ignoreCase = true) }
        adapter?.notifyDataSetChanged()
        val showFrequent = query.isEmpty() && frequentRow.childCount > 0
        val vis = if (showFrequent) View.VISIBLE else View.GONE
        frequentLabel.visibility = vis
        frequentRow.visibility = vis
    }

    private fun applySettings() {
        drawer.background = ColorDrawable(Color.argb(bgAlpha, 0, 0, 0))
        grid.numColumns = cols
        adapter = AppAdapter()
        grid.adapter = adapter
        buildDock()
        buildFrequent()
        filter(search.text.toString())
    }

    // ------------------------------------------------------------------
    //  Dock
    // ------------------------------------------------------------------
    private fun pkgFor(i: Intent): String? {
        val pm = packageManager
        val r = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (r != null && r != "android") return r
        return pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull()?.activityInfo?.packageName
    }

    private fun defaultDock(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
        val list = intents.mapNotNull { pkgFor(it) }.distinct()
        saveDock(list)
        return list
    }

    private fun dockPkgs(): List<String> {
        val raw = prefs.getString("dock", null) ?: return defaultDock()
        return raw.split(",").filter { it.isNotBlank() }
    }

    private fun saveDock(l: List<String>) {
        prefs.edit().putString("dock", l.joinToString(",")).apply()
    }

    private fun buildDock() {
        dock.removeAllViews()
        val byPkg = everyApp.associateBy { it.pkg }
        val items = dockPkgs().mapNotNull { byPkg[it] }.take(5)
        for (app in items) {
            val img = ImageView(this)
            img.setImageDrawable(app.icon)
            img.setOnClickListener { launch(app) }
            img.setOnLongClickListener {
                showMenu(app)
                true
            }
            dock.addView(img, LinearLayout.LayoutParams(0, dp(iconDp), 1f))
        }
        dock.visibility = if (items.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    private fun toggleDock(app: App, inDock: Boolean) {
        val l = dockPkgs().toMutableList()
        if (inDock) {
            l.remove(app.pkg)
        } else {
            if (l.size >= 5) {
                Toast.makeText(this, "El dock está lleno (máximo 5)", Toast.LENGTH_SHORT).show()
                return
            }
            l.add(app.pkg)
        }
        saveDock(l)
        buildDock()
    }

    // ------------------------------------------------------------------
    //  Más usadas
    // ------------------------------------------------------------------
    private fun buildFrequent() {
        frequentRow.removeAllViews()
        val top = allApps
            .filter { prefs.getInt("c_" + it.pkg, 0) > 0 }
            .sortedByDescending { prefs.getInt("c_" + it.pkg, 0) }
            .take(5)
        for (app in top) {
            val cell = makeCell()
            bindCell(cell, app)
            cell.setOnClickListener { launch(app) }
            cell.setOnLongClickListener {
                showMenu(app)
                true
            }
            frequentRow.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    // ------------------------------------------------------------------
    //  Celdas de app
    // ------------------------------------------------------------------
    private fun makeCell(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(ImageView(this), LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
        val label = TextView(this)
        label.setTextColor(Color.WHITE)
        label.textSize = 11f
        label.maxLines = 1
        label.gravity = Gravity.CENTER
        label.ellipsize = TextUtils.TruncateAt.END
        label.setShadowLayer(4f, 0f, 1f, Color.BLACK)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(4)
        c.addView(label, lp)
        return c
    }

    private fun bindCell(c: View, app: App) {
        val g = c as ViewGroup
        (g.getChildAt(0) as ImageView).setImageDrawable(app.icon)
        (g.getChildAt(1) as TextView).text = app.label
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(p: Int): Any = shown[p]
        override fun getItemId(p: Int): Long = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
            val v = convert ?: makeCell()
            bindCell(v, shown[p])
            return v
        }
    }

    // ------------------------------------------------------------------
    //  Acciones
    // ------------------------------------------------------------------
    private fun launch(app: App) {
        val i = packageManager.getLaunchIntentForPackage(app.pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        prefs.edit().putInt("c_" + app.pkg, prefs.getInt("c_" + app.pkg, 0) + 1).apply()
        closeDrawer()
    }

    private fun showMenu(app: App) {
        val inDock = dockPkgs().contains(app.pkg)
        val options = arrayOf(
            "Información de la app",
            "Desinstalar",
            "Ocultar",
            if (inDock) "Quitar del dock" else "Agregar al dock"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                    )
                    1 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)))
                    2 -> hideApp(app)
                    3 -> toggleDock(app, inDock)
                    else -> {}
                }
            }
            .show()
    }

    private fun hiddenSet(): MutableSet<String> =
        HashSet<String>(prefs.getStringSet("hidden", null) ?: emptySet<String>())

    private fun hideApp(app: App) {
        val set = hiddenSet()
        set.add(app.pkg)
        prefs.edit().putStringSet("hidden", set).apply()
        refreshLists()
        Toast.makeText(this, app.label + " oculta (se restaura en Ajustes)", Toast.LENGTH_LONG).show()
    }

    private fun showHidden() {
        val hidden = hiddenSet()
        val apps = everyApp.filter { it.pkg in hidden }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No hay apps ocultas", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = BooleanArray(apps.size) { true }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Apps ocultas (desmarca para mostrar)")
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

    private fun expandNotifications() {
        try {
            val sbs = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(sbs)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir las notificaciones", Toast.LENGTH_SHOR