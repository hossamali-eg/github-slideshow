package com.wifimanager.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class RouterWebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IP       = "ip"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val RESULT_COOKIES = "cookies"
        const val RESULT_STOK    = "stok"

        private const val AUTO_TIMEOUT_MS = 12_000L
    }

    private var returned = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ip       = intent.getStringExtra(EXTRA_IP)       ?: "192.168.1.1"
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: "admin"
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""

        var baseUrl = "https://$ip"
        var httpsFailed = false
        var loginAttempted = false
        var manualMode = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.removeAllCookies(null)

        // ── Root layout ───────────────────────────────────────────────────────
        val root = FrameLayout(this)

        // ── Loading overlay (shown first) ─────────────────────────────────────
        val loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A237E"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            @Suppress("DEPRECATION")
            indeterminateDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        }

        val loadingTitle = TextView(this).apply {
            text = "جاري تسجيل الدخول..."
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 0)
        }

        val loadingSubtitle = TextView(this).apply {
            text = "يتم الاتصال بالراوتر تلقائياً"
            textSize = 13f
            setTextColor(Color.parseColor("#90CAF9"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }

        loadingOverlay.addView(spinner)
        loadingOverlay.addView(loadingTitle)
        loadingOverlay.addView(loadingSubtitle)

        // ── Manual fallback layout (hidden unless auto-login times out) ───────
        val manualColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val banner = TextView(this).apply {
            text = "⬇ سجّل دخولك في صفحة الراوتر ثم اضغط الزر الأخضر"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(16, 14, 16, 14)
            setBackgroundColor(Color.parseColor("#1565C0"))
            setTextColor(Color.WHITE)
        }

        val pageProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }

        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val continueBtn = Button(this).apply {
            text = "✓  دخلت بنجاح — انتقل للوحة التحكم"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setPadding(0, 24, 0, 24)
        }

        manualColumn.addView(banner)
        manualColumn.addView(pageProgress)
        manualColumn.addView(webView)
        manualColumn.addView(continueBtn)

        root.addView(manualColumn)
        root.addView(loadingOverlay)
        setContentView(root)

        // ── WebView settings ──────────────────────────────────────────────────
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString   = "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36"
            mixedContentMode  = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // ── Auto-timeout: show manual mode if auto-login doesn't complete ─────
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!returned) {
                manualMode = true
                loadingOverlay.visibility = View.GONE
                manualColumn.visibility = View.VISIBLE
            }
        }
        handler.postDelayed(timeoutRunnable, AUTO_TIMEOUT_MS)

        // ── WebViewClient ─────────────────────────────────────────────────────
        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.proceed()
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (manualMode) pageProgress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (manualMode) pageProgress.visibility = View.GONE

                if (returned) return

                val cookies = cookieManager.getCookie(url)?.takeIf { it.isNotEmpty() }
                    ?: cookieManager.getCookie(baseUrl) ?: ""
                val stok = Regex("stok=([a-fA-F0-9]+)")
                    .find(url)?.groupValues?.getOrElse(1) { "" } ?: ""

                // Success via cookie or stok token
                if (cookies.contains("sysauth") || stok.isNotEmpty()) {
                    handler.removeCallbacks(timeoutRunnable)
                    returnSuccess(cookies, stok)
                    return
                }

                val normalizedUrl = url.trimEnd('/')
                val normalizedBase = baseUrl.trimEnd('/')

                if (!loginAttempted) {
                    loginAttempted = true
                    // First page load — attempt auto-fill + auto-submit
                    view.postDelayed({
                        view.evaluateJavascript(buildAutoLoginJs(username, password), null)
                    }, 800)
                } else {
                    // Subsequent page load — check if we moved away from login page
                    val stillOnLogin = normalizedUrl == normalizedBase ||
                        url.contains("login", ignoreCase = true) ||
                        url.endsWith("/index.html") ||
                        url == "$normalizedBase/"

                    if (!stillOnLogin) {
                        // Navigated away from login → treat as success
                        handler.removeCallbacks(timeoutRunnable)
                        returnSuccess(cookies, stok)
                    }
                }
            }

            override fun onReceivedError(
                view: WebView, errorCode: Int, description: String, failingUrl: String
            ) {
                if (!httpsFailed && failingUrl.startsWith("https://")) {
                    httpsFailed = true
                    loginAttempted = false
                    baseUrl = "http://$ip"
                    if (manualMode) {
                        banner.text = "⬇ جاري الاتصال بـ http — سجّل دخولك ثم اضغط الزر الأخضر"
                    }
                    view.loadUrl(baseUrl)
                }
            }
        }

        // ── Manual "continue" button ──────────────────────────────────────────
        continueBtn.setOnClickListener {
            val url = webView.url ?: baseUrl
            val cookies = cookieManager.getCookie(url)?.takeIf { it.isNotEmpty() }
                ?: cookieManager.getCookie(baseUrl) ?: ""
            val stok = Regex("stok=([a-fA-F0-9]+)")
                .find(url)?.groupValues?.getOrElse(1) { "" } ?: ""
            returnSuccess(cookies, stok)
        }

        webView.loadUrl(baseUrl)
    }

    private fun returnSuccess(cookies: String, stok: String) {
        if (returned) return
        returned = true
        val result = Intent().apply {
            putExtra(RESULT_COOKIES, cookies)
            putExtra(RESULT_STOK, stok)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun buildAutoLoginJs(username: String, password: String): String {
        val u = username.replace("\\", "\\\\").replace("'", "\\'")
        val p = password.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function() {
                var uf = ['username','luci_username','user','login_n','uname'];
                var pf = ['psd','password','luci_password','passwd','pass','login_p','pwd'];
                var ue = null, pe = null;
                for (var i=0;i<uf.length;i++){
                    ue=document.querySelector('input[name="'+uf[i]+'"],input[id="'+uf[i]+'"]');
                    if(ue) break;
                }
                for (var i=0;i<pf.length;i++){
                    pe=document.querySelector('input[name="'+pf[i]+'"],input[id="'+pf[i]+'"]');
                    if(pe) break;
                }
                if(!ue) ue=document.querySelector('input[type="text"]:not([type="hidden"])');
                if(!pe) pe=document.querySelector('input[type="password"]');

                function fill(el, val) {
                    if(!el) return;
                    el.removeAttribute('readonly');
                    el.removeAttribute('disabled');
                    el.value = val;
                    el.dispatchEvent(new Event('focus',  {bubbles:true}));
                    el.dispatchEvent(new Event('input',  {bubbles:true}));
                    el.dispatchEvent(new Event('change', {bubbles:true}));
                    el.dispatchEvent(new Event('blur',   {bubbles:true}));
                }

                fill(ue, '$u');
                fill(pe, '$p');

                setTimeout(function() {
                    var btn = document.querySelector(
                        'button[type="submit"], input[type="submit"], ' +
                        'button.login-btn, .login-btn, .btn-login, #login_btn, ' +
                        '#btnLogin, #submitBtn, button[onclick], ' +
                        'form button, form input[type="button"]'
                    );
                    if(btn) {
                        btn.click();
                    } else {
                        var frm = document.querySelector('form');
                        if(frm) {
                            var e = new Event('submit', {bubbles:true, cancelable:true});
                            if(frm.dispatchEvent(e)) frm.submit();
                        }
                    }
                }, 500);
            })();
        """.trimIndent()
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}
