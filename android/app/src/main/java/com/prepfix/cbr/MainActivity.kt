package com.prepfix.cbr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.util.Calendar

/** Talks to the reading page (window.cbrAndroid). */
class Bridge(private val ctx: Context) {
    /** The page sends the lock time and the finished days every time something is saved. */
    @JavascriptInterface
    fun setState(json: String) {
        try {
            val o = JSONObject(json)
            val p = Prefs(ctx)
            val lt = o.optString("lock", "04:00")
            p.lockTime = if (Regex("^\\d{1,2}:\\d{2}$").matches(lt)) lt else "04:00"
            val fin = HashSet<String>()
            val f = o.optJSONObject("finished")
            if (f != null) {
                val keys = f.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = f.opt(k)
                    val truthy = when (v) {
                        null, JSONObject.NULL -> false
                        is Boolean -> v
                        is Number -> v.toDouble() != 0.0
                        is String -> v.isNotEmpty()
                        else -> true
                    }
                    if (truthy) fin.add(k)
                }
            }
            p.finished = fin
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun status(): String {
        val p = Prefs(ctx)
        val locked = LockLogic.shouldLock(p.lockTime, p.finished, Calendar.getInstance(), p.bypassUntil)
        return JSONObject().put("locked", locked).put("bypassUntil", p.bypassUntil).put("lockTime", p.lockTime).toString()
    }

    /** Correct code unlocks the phone for 30 minutes. */
    @JavascriptInterface
    fun emergency(code: String): Boolean {
        val p = Prefs(ctx)
        if (!p.hasCode() || code.trim() != p.code) return false
        p.bypassUntil = System.currentTimeMillis() + 30 * 60 * 1000L
        return true
    }
}

class MainActivity : Activity() {
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Setup.complete(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        LockService.start(this)

        val view = WebView(this)
        web = view
        setContentView(view)

        // Serves the bundled pages over https://appassets.androidplatform.net so fetch() and localStorage work offline.
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = false
        view.settings.allowFileAccess = false
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        view.addJavascriptInterface(Bridge(this), "cbrAndroid")
        view.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
        LockService.start(this)
        applyImmersive()
    }

    override fun onPause() {
        web?.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    /** While locked, hide the status and navigation bars. */
    private fun applyImmersive() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (LockLogic.isLockedNow(this)) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    @Deprecated("Back is disabled while the phone is locked")
    override fun onBackPressed() {
        if (LockLogic.isLockedNow(this)) return
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else moveTaskToBack(true)
    }
}
