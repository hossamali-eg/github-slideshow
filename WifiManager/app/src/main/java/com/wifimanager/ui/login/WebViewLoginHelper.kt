package com.wifimanager.ui.login

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class WebViewLoginHelper(
    private val context: Context,
    private val parent: ViewGroup? = null
) {

    data class LoginResult(
        val success: Boolean,
        val cookies: String = "",
        val stok: String = "",
        val errorMessage: String = ""
    )

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun login(
        ip: String,
        username: String,
        password: String
    ): LoginResult = withTimeout(30_000) {
        suspendCancellableCoroutine { continuation ->

            val baseUrl = "https://$ip"
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.removeAllCookies(null)

            val webView = WebView(context)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36"
            }

            // Attach to parent view so WebView has a proper window context
            parent?.addView(webView, ViewGroup.LayoutParams(1, 1))

            var loginAttempted = false
            var resumed = false

            fun safeResume(result: LoginResult) {
                if (!resumed && continuation.isActive) {
                    resumed = true
                    continuation.resume(result)
                }
            }

            webView.webViewClient = object : WebViewClient() {

                override fun onReceivedSslError(
                    view: WebView, handler: SslErrorHandler, error: SslError
                ) {
                    handler.proceed()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    // Read cookies from current URL and base URL
                    val cookies = cookieManager.getCookie(url)?.takeIf { it.isNotEmpty() }
                        ?: cookieManager.getCookie(baseUrl) ?: ""

                    // Success: sysauth cookie present (LuCI / ZTE standard)
                    if (cookies.contains("sysauth")) {
                        val stok = extractStok(url).ifEmpty { extractStok(cookies) }
                        safeResume(LoginResult(true, cookies, stok))
                        return
                    }

                    // Success: stok token in URL
                    val stok = extractStok(url)
                    if (stok.isNotEmpty()) {
                        safeResume(LoginResult(true, cookies, stok))
                        return
                    }

                    if (loginAttempted) {
                        // Determine if we're still on the login page or moved somewhere new
                        val urlNorm = url.trimEnd('/')
                        val baseNorm = baseUrl.trimEnd('/')
                        val isLoginPage = urlNorm == baseNorm ||
                            url.contains("login", ignoreCase = true) ||
                            url.contains("index", ignoreCase = true)

                        if (!isLoginPage) {
                            // Navigated away from login page — treat as success
                            safeResume(LoginResult(true, cookies, ""))
                        } else {
                            // Back on the same login page — wrong credentials
                            safeResume(LoginResult(false, errorMessage = "اسم المستخدم أو كلمة المرور غير صحيحة"))
                        }
                        return
                    }

                    // First page load: wait 600ms for the page JS to fully initialize, then fill form
                    loginAttempted = true
                    view.postDelayed({
                        if (!resumed && continuation.isActive) {
                            injectLoginJs(view, username, password)
                        }
                    }, 600)
                }

                override fun onReceivedError(
                    view: WebView, errorCode: Int, description: String, failingUrl: String
                ) {
                    if (failingUrl == "$baseUrl/" || failingUrl == baseUrl) {
                        safeResume(LoginResult(false, errorMessage = "تعذر الاتصال بالراوتر: $description"))
                    }
                }
            }

            continuation.invokeOnCancellation {
                parent?.removeView(webView)
                webView.destroy()
            }

            webView.loadUrl("$baseUrl/")
        }
    }

    private fun injectLoginJs(view: WebView, username: String, password: String) {
        val safeUser = username.replace("\\", "\\\\").replace("'", "\\'")
        val safePass = password.replace("\\", "\\\\").replace("'", "\\'")

        val js = """
            (function() {
                var userFields = ['username','luci_username','user','login_n','uname'];
                var passFields = ['psd','password','luci_password','passwd','pass','login_p','pwd'];

                var userEl = null, passEl = null;

                for (var i = 0; i < userFields.length; i++) {
                    userEl = document.querySelector(
                        'input[name="' + userFields[i] + '"], input[id="' + userFields[i] + '"]');
                    if (userEl) break;
                }
                for (var i = 0; i < passFields.length; i++) {
                    passEl = document.querySelector(
                        'input[name="' + passFields[i] + '"], input[id="' + passFields[i] + '"]');
                    if (passEl) break;
                }

                // Type-based fallbacks if named fields not found
                if (!userEl) userEl = document.querySelector('input[type="text"]:not([type="hidden"])');
                if (!passEl) passEl = document.querySelector('input[type="password"]');

                function fillField(el, val) {
                    if (!el) return;
                    el.removeAttribute('readonly');
                    el.removeAttribute('disabled');
                    el.value = val;
                    el.dispatchEvent(new Event('focus',  {bubbles: true}));
                    el.dispatchEvent(new Event('input',  {bubbles: true}));
                    el.dispatchEvent(new Event('change', {bubbles: true}));
                    el.dispatchEvent(new Event('blur',   {bubbles: true}));
                }

                fillField(userEl, '$safeUser');
                fillField(passEl, '$safePass');

                // Click submit button 300ms after filling (gives change events time to settle)
                setTimeout(function() {
                    var btn = document.querySelector(
                        'button[type="submit"], input[type="submit"], ' +
                        'button.login-btn, .login-btn, .btn-login, ' +
                        '#login_btn, #btnLogin, #submitBtn, ' +
                        'button[onclick], form button, form input[type="button"]'
                    );
                    if (btn) {
                        btn.click();
                    } else {
                        var frm = document.querySelector('form');
                        if (frm) {
                            var evt = new Event('submit', {bubbles: true, cancelable: true});
                            if (frm.dispatchEvent(evt)) frm.submit();
                        }
                    }
                }, 300);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun extractStok(text: String): String =
        Regex("stok=([a-fA-F0-9]+)").find(text)?.groupValues?.getOrElse(1) { "" } ?: ""
}
