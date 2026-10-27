package com.example.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    data class App(val label: String, val pkg: String, val icon: Drawable)

    private var allApps = listOf<App>()
    private var shown = listOf<App>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            setPadding(dp(12), dp(40), dp(12), dp(12))
        }

        val search = EditText(this).apply {
            hint = "Buscar app..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine()
        }
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(16)
            selector = null
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        adapter = AppAdapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            packageManager.getLaunchIntentForPackage(shown[pos].pkg)?.let { startActivity(it) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s.toString())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps = packageManager.queryIntentActivities(intent, 0)
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.loadIcon(packageManager)) }
            .filter { it.pkg != packageName }
            .sortedBy { it.label.lowercase() }
        filter("")
    }

    private fun filter(q: String) {
        shown = if (q.isBlank()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
        adapter.notifyDataSetChanged()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* un launcher no se cierra */ }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
            val d = resources.displayMetrics.density
            val app = shown[p]
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(context).apply { setImageDrawable(app.icon) },
                    LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()))
                addView(TextView(context).apply {
                    text = app.label
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    maxLines = 1
                    gravity = Gravity.CENTER
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
    }
}
