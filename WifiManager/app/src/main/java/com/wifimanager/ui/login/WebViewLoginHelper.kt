package com.wifimanager.ui.login

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.webkit.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Uses a real WebView (full browser engine + JavaScript) to authenticate
 * with the router. This works with any ZTE firmware regardless of how
 * it hashes the password, because the browser executes the router's own JS.
 */
class WebViewLoginHelper(private val context: Context) {

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
                userAgentString = "Mozilla/5.0 (Linux; Android 11; Mobile)"
            }

            var loginAttempted = false

            webView.webViewClient = object : WebViewClient() {

                override fun onReceivedSslError(
                    view: WebView, handler: SslErrorHandler, error: SslError
                ) {
                    handler.proceed() // Accept router's self-signed certificate
                }

                override fun onPageFinished(view: WebView, url: String) {
                    val cookies = cookieManager.getCookie(baseUrl) ?: ""

                    // Check success by cookie
                    if (cookies.contains("sysauth")) {
                        if (continuation.isActive) {
                            val stok = extractStok(url).ifEmpty { extractStok(cookies) }
                            continuation.resume(LoginResult(true, cookies, stok))
                        }
                        return
                    }

                    // Check success by URL stok token
                    val stok = extractStok(url)
                    if (stok.isNotEmpty()) {
                        if (continuation.isActive)
                            continuation.resume(LoginResult(true, cookies, stok))
                        return
                    }

                    // If already tried to login, we got a response but no session
                    if (loginAttempted) {
                        if (continuation.isActive)
                            continuation.resume(LoginResult(false, errorMessage = "اسم المستخدم أو كلمة المرور غير صحيحة"))
                        return
                    }

                    // First page load: inject JS to fill and submit login form
                    loginAttempted = true
                    val js = """
                        (function() {
                            // Try all common field names for ZTE routers
                            var userFields = ['username','luci_username','user','login_n','uname'];
                            var passFields = ['psd','password','luci_password','passwd','pass','login_p','pwd'];

                            for (var i = 0; i < userFields.length; i++) {
                                var u = document.querySelector('input[name="' + userFields[i] + '"]');
                                if (u) { u.value = '${username.replace("'", "\\'")}'; break; }
                            }
                            for (var i = 0; i < passFields.length; i++) {
                                var p = document.querySelector('input[name="' + passFields[i] + '"]');
                                if (p) { p.value = '${password.replace("'", "\\'")}'; break; }
                            }

                            // Submit the form
                            var btn = document.querySelector(
                                'button[type="submit"],input[type="submit"],button.login-btn,.login-btn'
                            );
                            if (btn) {
                                btn.click();
                            } else if (document.forms.length > 0) {
                                document.forms[0].submit();
                            }
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(js, null)
                }

                override fun onReceivedError(
                    view: WebView, errorCode: Int, description: String, url: String
                ) {
                    if (continuation.isActive)
                        continuation.resume(LoginResult(false, errorMessage = "تعذر الاتصال بالراوتر: $description"))
                }
            }

            continuation.invokeOnCancellation { webView.destroy() }
            webView.loadUrl("$baseUrl/")
        }
    }

    private fun extractStok(text: String): String =
        Regex("stok=([a-f0-9]+)").find(text)?.groupValues?.getOrElse(1) { "" } ?: ""
}
