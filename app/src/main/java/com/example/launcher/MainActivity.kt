package com.example.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

class MainActivity : Activity() {

    internal lateinit var prefs: SharedPreferences
    internal var everyApp = listOf<App>()
    private var allApps = listOf<App>()
    private var shown = listOf<App>()
    private var loading = false
    private var drawerOpen = false
    private var currentCat = Cat.ALL
    private var visibleCats = listOf<String>()
    private var accent = 0xFF4FC3F7.toInt()
    private var appliedKey = ""

    private lateinit var root: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var search: EditText
    private lateinit var calcRow: TextView
    private lateinit var webRow: TextView
    private lateinit var grid: GridView
    private lateinit var tabsScroll: HorizontalScrollView
    private lateinit var tabsRow: LinearLayout
    private lateinit var swipes: GestureDetector
    private var adapter: AppAdapter? = null

    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    internal val cols: Int get() = prefs.getInt("cols", 4)
    internal val iconDp: Int get() = prefs.getInt("icon", 52)
    internal val bgAlpha: Int get() = prefs.getInt("alpha", 150)

    private fun pill(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE)
        loadAccent()
        root = FrameLayout(this)
        buildHome()
        buildDrawer()
        setContentView(root)
        root.post { drawer.translationY = root.height.toFloat() }
        applySettings()
    }

    override fun onResume() {
        super.onResume()
        if (appliedKey != settingsKey()) applySettings()
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (drawerOpen) swipes.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun settingsKey(): String = "" + cols + "," + iconDp + "," + bgAlpha

    private fun loadAccent() {
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                val wc = WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                val c = wc?.primaryColor
                if (c != null) {
                    val argb = c.toArgb()
                    accent = Color.rgb(
                        (Color.red(argb) * 4 + 255 * 6) / 10,
                        (Color.green(argb) * 4 + 255 * 6) / 10,
                        (Color.blue(argb) * 4 + 255 * 6) / 10
                    )
                }
            }
        } catch (e: Exception) {
            // se queda el color por defecto
        }
    }

    // ---------------------------------------------------------------- inicio
    private fun buildHome() {
        val home = LinearLayout(this)
        home.orientation = LinearLayout.VERTICAL
        home.setPadding(dp(16), dp(56), dp(16), dp(24))

        val clock = TextClock(this)
        clock.format12Hour = "h:mm"
        clock.format24Hour = "HH:mm"
        clock.textSize = 68f
        clock.setTextColor(Color.WHITE)
        clock.setShadowLayer(10f, 0f, 2f, Color.argb(170, 0, 0, 0))
        clock.gravity = Gravity.CENTER

        val date = TextClock(this)
        date.format12Hour = "EEEE, d MMMM"
        date.format24Hour = "EEEE, d MMMM"
        date.textSize = 17f
        date.setTextColor(accent)
        date.setShadowLayer(8f, 0f, 2f, Color.argb(170, 0, 0, 0))
        date.gravity = Gravity.CENTER

        val searchPill = TextView(this)
        searchPill.text = "Buscar apps, calcular, web..."
        searchPill.textSize = 15f
        searchPill.setTextColor(Color.argb(230, 255, 255, 255))
        searchPill.background = pill(Color.argb(80, 0, 0, 0), 24)
        searchPill.setPadding(dp(20), dp(12), dp(20), dp(12))
        searchPill.setOnClickListener { openDrawer(true) }

        val hint = View(this)
        hint.background = pill(Color.argb(150, 255, 255, 255), 3)

        dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.background = pill(Color.argb(90, 0, 0, 0), 30)
        dock.setPadding(dp(10), dp(10), dp(10), dp(10))

        home.addView(clock, LinearLayout.LayoutParams(-1, -2))
        home.addView(date, LinearLayout.LayoutParams(-1, -2))
        val spLp = LinearLayout.LayoutParams(-1, -2)
        spLp.topMargin = dp(22)
        home.addView(searchPill, spLp)
        home.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        val hLp = LinearLayout.LayoutParams(dp(40), dp(5))
        hLp.gravity = Gravity.CENTER_HORIZONTAL
        hLp.bottomMargin = dp(14)
        home.addView(hint, hLp)
        home.addView(dock, LinearLayout.LayoutParams(-1, -2))

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
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
        home.setOnTouchListener { _, ev -> gestures.onTouchEvent(ev) }
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
    }

    // ----------------------------------------------------------------- cajón
    private fun buildDrawer() {
        drawer = LinearLayout(this)
        drawer.orientation = LinearLayout.VERTICAL
        drawer.isClickable = true
        drawer.visibility = View.INVISIBLE
        drawer.setPadding(dp(12), dp(28), dp(12), dp(8))

        val handle = FrameLayout(this)
        val bar = View(this)
        bar.background = pill(Color.argb(170, 255, 255, 255), 3)
        handle.addView(bar, FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER))
        val tvSettings = TextView(this)
        tvSettings.text = "Ajustes"
        tvSettings.textSize = 13f
        tvSettings.setTextColor(Color.argb(220, 255, 255, 255))
        tvSettings.setPadding(dp(10), dp(8), dp(4), dp(8))
        tvSettings.setOnClickListener { showSettings() }
        handle.addView(tvSettings, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        val tvHome = TextView(this)
        tvHome.text = "Inicio"
        tvHome.textSize = 13f
        tvHome.setTextColor(Color.argb(220, 255, 255, 255))
        tvHome.setPadding(dp(4), dp(8), dp(10), dp(8))
        tvHome.setOnClickListener { closeDrawer() }
        handle.addView(tvHome, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        val tap = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                closeDrawer()
                return true
            }
        })
        handle.setOnTouchListener { _, ev -> tap.onTouchEvent(ev) }

        search = EditText(this)
        search.hint = "Buscar app, calcular o buscar en la web"
        search.setHintTextColor(Color.argb(190, 255, 255, 255))
        search.setTextColor(Color.WHITE)
        search.background = pill(Color.argb(70, 255, 255, 255), 24)
        search.setPadding(dp(18), dp(10), dp(18), dp(10))
        search.setSingleLine()
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.imeOptions = EditorInfo.IME_ACTION_GO
        search.setOnEditorActionListener { _, _, _ ->
            val first = shown.firstOrNull()
            if (first != null) launch(first) else webSearch()
            true
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filter()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        calcRow = TextView(this)
        calcRow.textSize = 22f
        calcRow.setTextColor(accent)
        calcRow.setPadding(dp(10), dp(12), dp(10), dp(4))
        calcRow.visibility = View.GONE

        webRow = TextView(this)
        webRow.textSize = 15f
        webRow.setTextColor(Color.WHITE)
        webRow.setPadding(dp(10), dp(10), dp(10), dp(6))
        webRow.visibility = View.GONE
        webRow.setOnClickListener { webSearch() }

        grid = GridView(this)
        grid.verticalSpacing = dp(14)
        grid.selector = ColorDrawable(Color.TRANSPARENT)
        grid.overScrollMode = View.OVER_SCROLL_NEVER
        grid.isVerticalScrollBarEnabled = false
        grid.setPadding(0, dp(14), 0, 0)
        grid.clipToPadding = false
        grid.setOnItemClickListener { _, _, pos, _ ->
            if (pos in shown.indices) launch(shown[pos])
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos in shown.indices) showMenu(shown[pos])
            true
        }

        tabsRow = LinearLayout(this)
        tabsRow.orientation = LinearLayout.HORIZONTAL
        tabsScroll = HorizontalScrollView(this)
        tabsScroll.isHorizontalScrollBarEnabled = false
        tabsScroll.background = pill(Color.argb(70, 0, 0, 0), 22)
        tabsScroll.addView(tabsRow)

        swipes = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (Math.abs(vy) > Math.abs(vx) * 1.5f) {
                    if (vy > 1200f && !grid.canScrollVertically(-1)) {
                        closeDrawer()
                        return true
                    }
                } else if (Math.abs(vx) > 1500f && search.text.isEmpty()) {
                    val loc = IntArray(2)
                    tabsScroll.getLocationOnScreen(loc)
                    if (e1 != null && e1.rawY >= loc[1]) return false
                    stepCat(if (vx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })

        drawer.addView(handle, LinearLayout.LayoutParams(-1, dp(36)))
        drawer.addView(search, LinearLayout.LayoutParams(-1, -2))
        drawer.addView(calcRow, LinearLayout.LayoutParams(-1, -2))
        drawer.addView(webRow, LinearLayout.LayoutParams(-1, -2))
        drawer.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val tLp = LinearLayout.LayoutParams(-1, -2)
        tLp.topMargin = dp(8)
        drawer.addView(tabsScroll, tLp)
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
    }

    internal fun openDrawer(focusSearch: Boolean = false) {
        if (!drawerOpen) {
            drawerOpen = true
            drawer.visibility = View.VISIBLE
            drawer.translationY = root.height.toFloat()
            drawer.animate().translationY(0f).setDuration(220).start()
        }
        if (focusSearch) {
            search.postDelayed({
                search.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }, 300)
        }
    }

    internal fun closeDrawer() {
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
                if (!drawerOpen) {
                    drawer.visibility = View.INVISIBLE
                    selectCat(Cat.ALL)
                }
            }
            .start()
    }

    private fun webSearch() {
        val q = search.text.toString().trim()
        if (q.isEmpty()) return
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            // sin navegador
        }
        closeDrawer()
    }

    // ------------------------------------------------------------ categorías
    private fun count(a: App): Int = prefs.getInt("c_" + a.pkg, 0)

    private fun buildTabs() {
        tabsRow.removeAllViews()
        val cats = ArrayList<String>()
        if (allApps.any { count(it) > 0 }) cats.add(Cat.USED)
        cats.add(Cat.ALL)
        for (c in Cat.assignable) {
            if (allApps.any { it.cat == c }) cats.add(c)
        }
        if (currentCat !in cats) currentCat = Cat.ALL
        visibleCats = cats
        for (c in cats) {
            val t = TextView(this)
            val sel = c == currentCat
            t.text = c
            t.textSize = 14f
            t.setPadding(dp(14), dp(9), dp(14), dp(9))
            t.setTextColor(if (sel) accent else Color.argb(210, 255, 255, 255))
            t.setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
            if (sel) t.background = pill(Color.argb(70, 255, 255, 255), 18)
            t.setOnClickListener { selectCat(c) }
            tabsRow.addView(t)
        }
    }

    private fun selectCat(c: String) {
        currentCat = c
        buildTabs()
        filter()
        grid.setSelection(0)
    }

    private fun stepCat(d: Int) {
        val i = visibleCats.indexOf(currentCat)
        if (i < 0) return
        val n = (i + d).coerceIn(0, visibleCats.size - 1)
        if (n != i) selectCat(visibleCats[n])
    }

    internal fun filter() {
        val q = search.text.toString().trim()
        shown = if (q.isEmpty()) {
            when (currentCat) {
                Cat.ALL -> allApps
                Cat.USED -> allApps.filter { count(it) > 0 }.sortedByDescending { count(it) }.take(16)
                else -> allApps.filter { it.cat == currentCat }
            }
        } else {
            allApps.filter { it.label.contains(q, ignoreCase = true) }
                .sortedBy { if (it.label.startsWith(q, ignoreCase = true)) 0 else 1 }
        }
        adapter?.notifyDataSetChanged()
        tabsScroll.visibility = if (q.isEmpty()) View.VISIBLE else View.GONE
        val r = if (q.isEmpty()) null else Calc.eval(q)
        if (r != null) {
            calcRow.text = "= " + Calc.format(r)
            calcRow.visibility = View.VISIBLE
        } else {
            calcRow.visibility = View.GONE
        }
        if (q.isEmpty()) {
            webRow.visibility = View.GONE
        } else {
            webRow.text = "Buscar \"" + q + "\" en la web"
            webRow.visibility = View.VISIBLE
        }
    }

    // ------------------------------------------------------------------ apps
    internal fun hiddenSet(): MutableSet<String> =
        HashSet<String>(prefs.getStringSet("hidden", null) ?: emptySet<String>())

    internal fun loadApps() {
        if (loading) return
        loading = true
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .map {
                    val ai = it.activityInfo.applicationInfo
                    val pkg = it.activityInfo.packageName
                    val label = it.loadLabel(pm).toString()
                    val auto = Cat.classify(pkg, label, ai.category, (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
                    val cat = prefs.getString("cat_" + pkg, null) ?: auto
                    App(label, pkg, it.loadIcon(pm), cat)
                }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                loading = false
                everyApp = list
                refreshLists()
            }
        }.start()
    }

    internal fun refreshLists() {
        val hidden = hiddenSet()
        allApps = everyApp.filter { it.pkg !in hidden }
        buildDock()
        buildTabs()
        filter()
    }

    internal fun applySettings() {
        appliedKey = settingsKey()
        drawer.background = ColorDrawable(Color.argb(bgAlpha, 0, 0, 0))
        grid.numColumns = cols
        adapter = AppAdapter()
        grid.adapter = adapter
        buildDock()
        buildTabs()
        filter()
    }

    private fun launch(app: App) {
        val i = packageManager.getLaunchIntentForPackage(app.pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        prefs.edit().putInt("c_" + app.pkg, count(app) + 1).apply()
        closeDrawer()
    }

    // ------------------------------------------------------------------ dock
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

    internal fun dockPkgs(): List<String> {
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

    internal fun toggleDock(app: App, inDock: Boolean) {
        val l = dockPkgs().toMutableList()
        if (inDock) {
            l.remove(app.pkg)
        } else {
            if (l.size >= 5) {
                android.widget.Toast.makeText(this, "El dock está lleno (máximo 5)", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            l.add(app.pkg)
        }
        saveDock(l)
        buildDock()
    }

    // ----------------------------------------------------------------- celdas
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

    inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(p: Int): Any = shown[p]
        override fun getItemId(p: Int): Long = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
            val v = convert ?: makeCell()
            val g = v as ViewGroup
            (g.getChildAt(0) as ImageView).setImageDrawable(shown[p].icon)
            (g.getChildAt(1) as TextView).text = shown[p].label
            return v
        }
    }
}
