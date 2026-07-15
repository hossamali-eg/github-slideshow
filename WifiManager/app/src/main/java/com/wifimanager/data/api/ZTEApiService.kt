package com.wifimanager.data.api

import com.wifimanager.data.models.ConnectedDevice
import com.wifimanager.data.models.DeviceType
import com.wifimanager.data.models.NetworkStats
import com.wifimanager.data.models.RouterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * ZTE ZXHN H188A specific API handler.
 *
 * The H188A uses a CGI-based HTTP interface:
 *  - Login:         POST /cgi-bin/luci  (or /cgi-bin/login.cgi)
 *  - Session token: stored in cookie "sysauth"
 *  - Device list:   GET  /cgi-bin/luci/;stok=<token>/admin/network/clients
 *  - Block device:  POST /cgi-bin/luci/;stok=<token>/admin/network/mac_filter
 *  - Speed limit:   POST /cgi-bin/luci/;stok=<token>/admin/network/qos
 *
 * Some firmware versions expose a JSON-RPC API at /cgi-bin/gui.cgi.
 * We try JSON-RPC first, fall back to HTML scraping.
 */
@Singleton
class ZTEApiService @Inject constructor() {

    private var baseUrl = "https://192.168.1.1"
    private var sysauthToken = ""
    private var stok = ""

    private val cookieStore = mutableListOf<Cookie>()

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext = SSLContext.getInstance("TLS").also {
        it.init(null, trustAllCerts, SecureRandom())
    }

    private val client = OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore.removeAll { c -> cookies.any { it.name == c.name } }
                cookieStore.addAll(cookies)
                cookies.firstOrNull { it.name == "sysauth" }?.let { sysauthToken = it.value }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore
        })
        .build()

    fun configure(ip: String) {
        baseUrl = "https://$ip"
        cookieStore.clear()
        sysauthToken = ""
        stok = ""
    }

    fun configureWithSession(cookies: String, stokValue: String) {
        val match = Regex("""sysauth=([^;]+)""").find(cookies)
        sysauthToken = match?.groupValues?.getOrElse(1) { "" }?.trim() ?: ""
        if (stokValue.isNotEmpty()) stok = stokValue
        if (sysauthToken.isNotEmpty()) {
            val domain = baseUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
            cookieStore.clear()
            try {
                cookieStore.add(Cookie.Builder().name("sysauth").value(sysauthToken).domain(domain).build())
            } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Authentication
    // ─────────────────────────────────────────────────────────────

    suspend fun login(username: String, password: String): RouterStatus =
        withContext(Dispatchers.IO) {
            // Load login page once
            val pageResp = try {
                client.newCall(
                    Request.Builder().url("$baseUrl/").get()
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
                        .build()
                ).execute()
            } catch (e: Exception) {
                return@withContext RouterStatus(isConnected = false, isAuthenticated = false,
                    errorMessage = "تعذر الاتصال بالراوتر: ${e.message}")
            }
            val html = pageResp.body?.string() ?: ""
            val pageUrl = pageResp.request.url.toString()
            val postUrl = resolveFormAction(html, pageUrl)

            // ZTE H188A (WE Egypt) uses field 'psd' with sha256(password)
            // Try combinations in order most-likely-first
            val attempts = listOf(
                mapOf("username" to username, "psd" to sha256(password)),
                mapOf("username" to username, "psd" to password),
                mapOf("username" to username, "psd" to md5(password)),
                mapOf("username" to username, "password" to sha256(password)),
                mapOf("username" to username, "password" to password),
                mapOf("luci_username" to username, "luci_password" to password),
                mapOf("username" to username, "password" to password,
                    "luci_username" to username, "luci_password" to password)
            )

            for (fields in attempts) {
                val result = submitForm(postUrl, pageUrl, fields, html)
                if (result.isAuthenticated) return@withContext result
            }

            // Last resort: JSON-RPC
            val jsonResult = tryJsonRpcLogin(username, sha256(password))
            if (jsonResult.isAuthenticated) return@withContext jsonResult

            RouterStatus(isConnected = true, isAuthenticated = false,
                errorMessage = "اسم المستخدم أو كلمة المرور غير صحيحة")
        }

    private fun resolveFormAction(html: String, pageUrl: String): String {
        val raw = Regex("""<form[^>]+action=["']?([^"'\s>]+)["']?""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrElse(1) { "" } ?: ""
        return when {
            raw.startsWith("http") -> raw
            raw.startsWith("/")    -> "$baseUrl$raw"
            raw.isNotEmpty()       -> "$baseUrl/$raw"
            else                   -> pageUrl
        }
    }

    private suspend fun submitForm(
        postUrl: String, referer: String,
        fields: Map<String, String>, html: String
    ): RouterStatus {
        return try {
            // Include any hidden input fields from the page (CSRF tokens etc.)
            val hiddenRegex = Regex(
                """<input[^>]+type=["']?hidden["']?[^>]+name=["']([^"']+)["'][^>]+value=["']([^"']*)["']""",
                RegexOption.IGNORE_CASE
            )
            val formBuilder = FormBody.Builder()
            hiddenRegex.findAll(html).forEach { m ->
                formBuilder.add(m.groupValues[1], m.groupValues[2])
            }
            fields.forEach { (k, v) -> formBuilder.add(k, v) }

            val resp = client.newCall(
                Request.Builder().url(postUrl).post(formBuilder.build())
                    .addHeader("Referer", referer)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
                    .build()
            ).execute()
            val body = resp.body?.string() ?: ""
            val finalUrl = resp.request.url.toString()

            stok = extractStok(finalUrl).ifEmpty { extractStok(body) }

            // Success if: got sysauth cookie, stok in URL, or redirected away from login page
            val authenticated = sysauthToken.isNotEmpty() || stok.isNotEmpty() ||
                    (finalUrl != postUrl && !finalUrl.contains("login", ignoreCase = true) && body.length > 200)

            RouterStatus(isConnected = true, isAuthenticated = authenticated)
        } catch (e: Exception) {
            RouterStatus(isConnected = false, isAuthenticated = false)
        }
    }

    private suspend fun tryJsonRpcLogin(username: String, password: String): RouterStatus {
        return try {
            val body = JSONObject().apply {
                put("method", "login")
                put("params", JSONArray().apply {
                    put(username)
                    put(password)
                })
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/cgi-bin/gui.cgi")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Referer", "$baseUrl/")
                .build()

            val resp = client.newCall(req).execute()
            val text = resp.body?.string() ?: ""

            if (resp.isSuccessful && text.contains("result")) {
                val json = JSONObject(text)
                val result = json.optString("result", "")
                stok = result
                RouterStatus(isConnected = true, isAuthenticated = result.isNotEmpty())
            } else {
                RouterStatus(isConnected = resp.code != 0, isAuthenticated = false)
            }
        } catch (e: Exception) {
            RouterStatus(isConnected = false, isAuthenticated = false, errorMessage = e.message ?: "")
        }
    }

    private suspend fun tryLuciLogin(username: String, password: String): RouterStatus {
        return try {
            client.newCall(Request.Builder().url("$baseUrl/").get().build()).execute().close()

            val formBody = FormBody.Builder()
                .add("luci_username", username)
                .add("luci_password", password)
                .add("username", username)
                .add("password", password)
                .build()

            val postReq = Request.Builder()
                .url("$baseUrl/cgi-bin/luci")
                .post(formBody)
                .addHeader("Referer", "$baseUrl/")
                .build()

            val resp = client.newCall(postReq).execute()
            val body = resp.body?.string() ?: ""

            stok = extractStok(resp.request.url.toString()).ifEmpty { extractStok(body) }

            val authenticated = sysauthToken.isNotEmpty() || stok.isNotEmpty()

            RouterStatus(
                isConnected = true,
                isAuthenticated = authenticated,
                errorMessage = if (!authenticated) "" else ""
            )
        } catch (e: Exception) {
            RouterStatus(isConnected = false, isAuthenticated = false, errorMessage = e.message ?: "خطأ في الاتصال")
        }
    }

    private suspend fun tryZteFormLogin(username: String, password: String): RouterStatus {
        return try {
            // ZTE H188A typically uses this login endpoint
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("psd", password)
                .add("login_n", username)
                .add("login_p", password)
                .add("selectLang", "ar")
                .build()

            val endpoints = listOf(
                "$baseUrl/cgi-bin/login.cgi",
                "$baseUrl/login",
                "$baseUrl/cgi-bin/luci/",
            )

            for (url in endpoints) {
                try {
                    val resp = client.newCall(
                        Request.Builder().url(url).post(formBody)
                            .addHeader("Referer", "$baseUrl/").build()
                    ).execute()
                    val body = resp.body?.string() ?: ""
                    stok = extractStok(resp.request.url.toString()).ifEmpty { extractStok(body) }
                    if (sysauthToken.isNotEmpty() || stok.isNotEmpty()) {
                        return RouterStatus(isConnected = true, isAuthenticated = true)
                    }
                } catch (_: Exception) {}
            }

            RouterStatus(isConnected = true, isAuthenticated = false,
                errorMessage = "اسم المستخدم أو كلمة المرور خاطئة")
        } catch (e: Exception) {
            RouterStatus(isConnected = false, isAuthenticated = false, errorMessage = e.message ?: "خطأ")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Connected Devices
    // ─────────────────────────────────────────────────────────────

    suspend fun getConnectedDevices(): List<ConnectedDevice> = withContext(Dispatchers.IO) {
        // Try JSON-RPC first
        val jsonDevices = getDevicesViaJsonRpc()
        if (jsonDevices.isNotEmpty()) return@withContext jsonDevices

        // Try LuCI client list
        val luciDevices = getDevicesViaLuci()
        if (luciDevices.isNotEmpty()) return@withContext luciDevices

        // Fallback: scrape the main status page
        getDevicesViaStatusPage()
    }

    private suspend fun getDevicesViaJsonRpc(): List<ConnectedDevice> {
        return try {
            val body = JSONObject().apply {
                put("method", "getHostInfo")
                put("params", JSONArray())
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/cgi-bin/gui.cgi")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", "sysauth=$sysauthToken")
                .build()

            val resp = client.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            parseJsonRpcDevices(text)
        } catch (e: Exception) { emptyList() }
    }

    private fun parseJsonRpcDevices(json: String): List<ConnectedDevice> {
        val devices = mutableListOf<ConnectedDevice>()
        try {
            val obj = JSONObject(json)
            val result = obj.optJSONArray("result") ?: return emptyList()
            for (i in 0 until result.length()) {
                val d = result.getJSONObject(i)
                devices.add(ConnectedDevice(
                    macAddress = normalizeMac(d.optString("MACAddress", d.optString("mac", ""))),
                    ipAddress = d.optString("IPAddress", d.optString("ip", "")),
                    hostname = d.optString("HostName", d.optString("hostname", "Unknown")),
                    isOnline = d.optInt("Active", d.optInt("active", 1)) == 1,
                    downloadSpeed = d.optDouble("DownstreamRate", 0.0) / 1_000_000,
                    uploadSpeed = d.optDouble("UpstreamRate", 0.0) / 1_000_000,
                    deviceType = guessDeviceType(d.optString("HostName", ""))
                ))
            }
        } catch (_: Exception) {}
        return devices
    }

    private suspend fun getDevicesViaLuci(): List<ConnectedDevice> {
        return try {
            val url = if (stok.isNotEmpty())
                "$baseUrl/cgi-bin/luci/;stok=$stok/admin/network/wireless"
            else
                "$baseUrl/cgi-bin/luci/admin/network/wireless"

            val req = Request.Builder().url(url).get()
                .addHeader("Cookie", "sysauth=$sysauthToken")
                .build()

            val resp = client.newCall(req).execute()
            parseLuciDevices(resp.body?.string() ?: "")
        } catch (e: Exception) { emptyList() }
    }

    private fun parseLuciDevices(html: String): List<ConnectedDevice> {
        val devices = mutableListOf<ConnectedDevice>()
        // ZTE H188A HTML table rows contain MAC + IP + Hostname
        val rowRegex = Regex("""<tr[^>]*>.*?</tr>""", RegexOption.DOT_MATCHES_ALL)
        val macRegex = Regex("""([0-9A-Fa-f]{2}[:\-]){5}[0-9A-Fa-f]{2}""")
        val ipRegex = Regex("""\b(192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3})\b""")
        val nameRegex = Regex("""<td[^>]*>\s*([A-Za-z0-9\-_]{3,32})\s*</td>""")

        rowRegex.findAll(html).forEach { rowMatch ->
            val row = rowMatch.value
            val mac = macRegex.find(row)?.value ?: return@forEach
            val ip = ipRegex.find(row)?.value ?: ""
            val name = nameRegex.find(row)?.groupValues?.getOrElse(1) { "" } ?: ""
            if (mac.length >= 17) {
                devices.add(ConnectedDevice(
                    macAddress = normalizeMac(mac),
                    ipAddress = ip,
                    hostname = name.ifEmpty { "Unknown" },
                    isOnline = true
                ))
            }
        }
        return devices
    }

    private suspend fun getDevicesViaStatusPage(): List<ConnectedDevice> {
        return try {
            val pages = listOf(
                "$baseUrl/cgi-bin/luci/;stok=$stok/admin/network/dhcp_leases",
                "$baseUrl/status_clients.asp",
                "$baseUrl/connected_clients.asp"
            )
            for (url in pages) {
                val req = Request.Builder().url(url).get()
                    .addHeader("Cookie", "sysauth=$sysauthToken")
                    .build()
                val html = try { client.newCall(req).execute().body?.string() ?: "" } catch (_: Exception) { "" }
                val devices = parseLuciDevices(html)
                if (devices.isNotEmpty()) return devices
            }
            emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // ─────────────────────────────────────────────────────────────
    // Device Control
    // ─────────────────────────────────────────────────────────────

    suspend fun blockDevice(mac: String, block: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // ZTE H188A MAC filter via JSON-RPC
            val body = JSONObject().apply {
                put("method", if (block) "addMacFilter" else "delMacFilter")
                put("params", JSONArray().apply { put(mac) })
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/cgi-bin/gui.cgi")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", "sysauth=$sysauthToken")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) return@withContext true

            // Fallback: LuCI MAC filter form
            val form = FormBody.Builder()
                .add("mac", mac)
                .add("action", if (block) "add" else "remove")
                .build()

            val luciReq = Request.Builder()
                .url("$baseUrl/cgi-bin/luci/;stok=$stok/admin/network/mac_filter")
                .post(form)
                .addHeader("Cookie", "sysauth=$sysauthToken")
                .addHeader("Referer", baseUrl)
                .build()

            client.newCall(luciReq).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun setSpeedLimit(mac: String, downloadKbps: Int, uploadKbps: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("method", "setQosBandwidth")
                    put("params", JSONArray().apply {
                        put(mac)
                        put(downloadKbps)
                        put(uploadKbps)
                    })
                }.toString()

                val req = Request.Builder()
                    .url("$baseUrl/cgi-bin/gui.cgi")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("Cookie", "sysauth=$sysauthToken")
                    .build()

                client.newCall(req).execute().isSuccessful
            } catch (e: Exception) { false }
        }

    suspend fun setInternetEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("method", "setWanConnectStatus")
                put("params", JSONArray().apply { put(if (enabled) 1 else 0) })
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/cgi-bin/gui.cgi")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", "sysauth=$sysauthToken")
                .build()

            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun getNetworkStats(): NetworkStats = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("method", "getSystemInfo")
                put("params", JSONArray())
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/cgi-bin/gui.cgi")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", "sysauth=$sysauthToken")
                .build()

            val text = client.newCall(req).execute().body?.string() ?: ""
            parseNetworkStats(text)
        } catch (e: Exception) {
            NetworkStats()
        }
    }

    private fun parseNetworkStats(json: String): NetworkStats {
        return try {
            val obj = JSONObject(json).optJSONObject("result") ?: return NetworkStats()
            NetworkStats(
                isInternetEnabled = obj.optInt("wanStatus", 1) == 1,
                totalDownloadSpeed = obj.optDouble("downRate", 0.0) / 1_000_000,
                totalUploadSpeed = obj.optDouble("upRate", 0.0) / 1_000_000,
                totalDevices = obj.optInt("hostCount", 0),
                activeDevices = obj.optInt("activeCount", 0),
                pingMs = obj.optInt("ping", 0),
                ssid = obj.optString("ssid", "ZTE_H188A"),
                channel = obj.optInt("channel", 6),
                frequency = if (obj.optInt("band", 2) == 5) "5GHz" else "2.4GHz",
                signalStrength = obj.optInt("rssi", -65)
            )
        } catch (_: Exception) { NetworkStats() }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun extractStok(text: String): String {
        val regex = Regex("""stok=([a-f0-9]+)""")
        return regex.find(text)?.groupValues?.getOrElse(1) { "" } ?: ""
    }

    private fun normalizeMac(mac: String): String =
        mac.uppercase().replace("-", ":").trim()

    private fun guessDeviceType(name: String): DeviceType {
        val n = name.lowercase()
        return when {
            n.contains("iphone") || n.contains("android") || n.contains("phone") ||
                    n.contains("mobile") || n.contains("galaxy") || n.contains("pixel") -> DeviceType.PHONE
            n.contains("ipad") || n.contains("tablet") || n.contains("tab") -> DeviceType.TABLET
            n.contains("laptop") || n.contains("macbook") || n.contains("notebook") -> DeviceType.LAPTOP
            n.contains("desktop") || n.contains("pc") || n.contains("imac") -> DeviceType.DESKTOP
            n.contains("tv") || n.contains("smart") || n.contains("samsung") -> DeviceType.TV
            n.contains("xbox") || n.contains("playstation") || n.contains("ps") -> DeviceType.GAME_CONSOLE
            n.contains("cam") || n.contains("camera") -> DeviceType.CAMERA
            else -> DeviceType.UNKNOWN
        }
    }

    private fun md5(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).fold("") { s, b -> s + "%02x".format(b) }
    }

    private fun sha256(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).fold("") { s, b -> s + "%02x".format(b) }
    }
}
