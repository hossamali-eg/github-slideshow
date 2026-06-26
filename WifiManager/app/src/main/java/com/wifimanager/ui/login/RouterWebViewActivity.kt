package com.wifimanager.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Shows the router's actual admin page in a full-screen WebView.
 * Pre-fills username/password. User presses the login button themselves.
 * When session cookie or stok is detected, returns success to LoginActivity.
 */
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

        // Try HTTPS first — ZTE H188A label says https://
        var baseUrl = "https://$ip"
        var httpsFailed = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.removeAllCookies(null)

        // ── Layout ────────────────────────────────────────────────────────────
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val banner = TextView(this).apply {
            text = "تحميل صفحة الراوتر... أدخل بيانات الدخول ثم اضغط تسجيل الدخول"
            textSize = 13f
            setPadding(24, 16, 24, 8)
            setBackgroundColor(0xFF1565C0.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }

        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(banner)
        root.addView(progress)
        root.addView(webView)
        setContentView(root)

        // ── WebView settings ──────────────────────────────────────────────────
        webView.settings.apply {
            javaScriptEnabled  = true
            domStorageEnabled  = true
            userAgentString    = "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36"
            mixedContentMode   = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        var prefilled = false

        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.proceed()   // accept self-signed router certificate
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE

                // Check for successful login
                val cookies = cookieManager.getCookie(url)?.takeIf { it.isNotEmpty() }
                    ?: cookieManager.getCookie(baseUrl) ?: ""
                val stok = Regex("stok=([a-fA-F0-9]+)").find(url)
                    ?.groupValues?.getOrElse(1) { "" } ?: ""

                if (cookies.contains("sysauth") || stok.isNotEmpty()) {
                    val result = Intent().apply {
                        putExtra(RESULT_COOKIES, cookies)
                        putExtra(RESULT_STOK, stok)
                    }
                    setResult(RESULT_OK, result)
                    finish()
                    return
                }

                // Pre-fill username and password once
                if (!prefilled) {
                    prefilled = true
                    view.postDelayed({
                        view.evaluateJavascript(buildFillJs(username, password), null)
                        banner.text = "أدخل الباسوورد إذا لم يكن مملوءاً ثم اضغط زر الدخول"
                    }, 800)
                }
            }

            override fun onReceivedError(
                view: WebView, errorCode: Int, description: String, failingUrl: String
            ) {
                // If HTTPS fails, try HTTP
                if (!httpsFailed && failingUrl.startsWith("https://")) {
                    httpsFailed = true
                    baseUrl = "http://$ip"
                    prefilled = false
                    banner.text = "جاري المحاولة بـ HTTP..."
                    view.loadUrl(baseUrl)
                }
            }
        }

        webView.loadUrl(baseUrl)
    }

    private fun buildFillJs(username: String, password: String): String {
        val u = username.replace("\\", "\\\\").replace("'", "\\'")
        val p = password.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function() {
                var userFields = ['username','luci_username','user','login_n','uname'];
                var passFields = ['psd','password','luci_password','passwd','pass','login_p','pwd'];
                var uel = null, pel = null;
                for (var i=0; i<userFields.length; i++) {
                    uel = document.querySelector('input[name="'+userFields[i]+'"],input[id="'+userFields[i]+'"]');
                    if (uel) break;
                }
                if (!uel) uel = document.querySelector('input[type="text"]');
                for (var i=0; i<passFields.length; i++) {
                    pel = document.querySelector('input[name="'+passFields[i]+'"],input[id="'+passFields[i]+'"]');
                    if (pel) break;
                }
                if (!pel) pel = document.querySelector('input[type="password"]');
                if (uel) { uel.value = '$u'; uel.dispatchEvent(new Event('input',{bubbles:true})); }
                if (pel) { pel.value = '$p'; pel.dispatchEvent(new Event('input',{bubbles:true})); }
            })();
        """.trimIndent()
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}
