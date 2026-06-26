package com.wifimanager.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
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
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ip       = intent.getStringExtra(EXTRA_IP)       ?: "192.168.1.1"
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: "admin"
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""

        var baseUrl = "https://$ip"
        var httpsFailed = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.removeAllCookies(null)

        // ── Build layout ──────────────────────────────────────────────────────
        val root = FrameLayout(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }

        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        // ── "Continue" button ─────────────────────────────────────────────────
        val continueBtn = Button(this).apply {
            text = "✓  دخلت بنجاح — تابع للتطبيق"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setPadding(0, 24, 0, 24)
        }

        column.addView(banner)
        column.addView(progress)
        column.addView(webView)
        column.addView(continueBtn)
        root.addView(column)
        setContentView(root)

        // ── WebView settings ──────────────────────────────────────────────────
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString   = "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36"
            mixedContentMode  = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        var prefilled = false

        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.proceed()
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE

                // Auto-detect login success via cookie or stok URL token
                val cookies = cookieManager.getCookie(url)?.takeIf { it.isNotEmpty() }
                    ?: cookieManager.getCookie(baseUrl) ?: ""
                val stok = Regex("stok=([a-fA-F0-9]+)")
                    .find(url)?.groupValues?.getOrElse(1) { "" } ?: ""

                if (cookies.contains("sysauth") || stok.isNotEmpty()) {
                    returnSuccess(cookies, stok)
                    return
                }

                // Pre-fill form fields once
                if (!prefilled) {
                    prefilled = true
                    view.postDelayed({
                        view.evaluateJavascript(buildFillJs(username, password), null)
                    }, 700)
                }
            }

            override fun onReceivedError(
                view: WebView, errorCode: Int, description: String, failingUrl: String
            ) {
                if (!httpsFailed && failingUrl.startsWith("https://")) {
                    httpsFailed = true
                    baseUrl = "http://$ip"
                    prefilled = false
                    banner.text = "⬇ جاري الاتصال بـ http — سجّل دخولك ثم اضغط الزر الأخضر"
                    view.loadUrl(baseUrl)
                }
            }
        }

        // ── Manual continue button ────────────────────────────────────────────
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
        val result = Intent().apply {
            putExtra(RESULT_COOKIES, cookies)
            putExtra(RESULT_STOK, stok)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun buildFillJs(username: String, password: String): String {
        val u = username.replace("\\", "\\\\").replace("'", "\\'")
        val p = password.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function() {
                var uf = ['username','luci_username','user','login_n','uname'];
                var pf = ['psd','password','luci_password','passwd','pass'];
                var ue = null, pe = null;
                for (var i=0;i<uf.length;i++){ue=document.querySelector('input[name="'+uf[i]+'"],input[id="'+uf[i]+'"]');if(ue)break;}
                for (var i=0;i<pf.length;i++){pe=document.querySelector('input[name="'+pf[i]+'"],input[id="'+pf[i]+'"]');if(pe)break;}
                if(!ue) ue=document.querySelector('input[type="text"]');
                if(!pe) pe=document.querySelector('input[type="password"]');
                if(ue){ue.value='$u';ue.dispatchEvent(new Event('input',{bubbles:true}));}
                if(pe){pe.value='$p';pe.dispatchEvent(new Event('input',{bubbles:true}));}
            })();
        """.trimIndent()
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}
