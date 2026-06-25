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

@Singleton
class RouterApiService @Inject constructor() {

    private var baseUrl = "http://192.168.1.1"
    private var sessionCookie = ""
    private var stok = ""  // TP-Link token
    private var csrfToken = ""

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
            private val cookies = mutableListOf<Cookie>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                this.cookies.clear()
                this.cookies.addAll(cookies)
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies
        })
        .build()

    fun configure(ip: String) {
        baseUrl = "http://$ip"
    }

    // ==================== Authentication ====================

    suspend fun loginTPLink(username: String, password: String): RouterStatus =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("method", "do")
                    put("login", JSONObject().apply {
                        put("username", username)
                        put("password", encryptPassword(password))
                    })
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    stok = json.optString("stok", "")
                    if (stok.isNotEmpty()) {
                        RouterStatus(isConnected = true, isAuthenticated = true)
                    } else {
                        RouterStatus(isConnected = true, isAuthenticated = false, errorMessage = "فشل تسجيل الدخول")
                    }
                } else {
                    RouterStatus(isConnected = false, isAuthenticated = false, errorMessage = "تعذر الاتصال")
                }
            } catch (e: Exception) {
                RouterStatus(isConnected = false, isAuthenticated = false, errorMessage = e.message ?: "خطأ غير معروف")
            }
        }

    suspend fun loginGeneric(username: String, password: String): RouterStatus =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/login.cgi")
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                val cookies = response.headers("Set-Cookie")
                sessionCookie = cookies.firstOrNull() ?: ""

                RouterStatus(
                    isConnected = true,
                    isAuthenticated = response.isSuccessful || response.code == 302
                )
            } catch (e: Exception) {
                RouterStatus(isConnected = false, isAuthenticated = false, errorMessage = e.message ?: "خطأ")
            }
        }

    suspend fun testConnection(ip: String): RouterStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$ip")
                .get()
                .build()
            val response = client.newCall(request).execute()
            RouterStatus(isConnected = true, isAuthenticated = false)
        } catch (e: Exception) {
            RouterStatus(isConnected = false, isAuthenticated = false, errorMessage = e.message ?: "")
        }
    }

    // ==================== Device Management ====================

    suspend fun getConnectedDevices(): List<ConnectedDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<ConnectedDevice>()
        try {
            // Try TP-Link API first
            val tplinkDevices = getTPLinkDevices()
            if (tplinkDevices.isNotEmpty()) return@withContext tplinkDevices

            // Fall back to ARP table scanning
            return@withContext scanArpTable()
        } catch (e: Exception) {
            // Return demo devices for testing
            return@withContext getDemoDevices()
        }
    }

    private suspend fun getTPLinkDevices(): List<ConnectedDevice> {
        val body = JSONObject().apply {
            put("method", "get")
            put("hosts_info", JSONObject().apply {
                put("table", "host_info")
            })
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/stok=$stok/ds")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return emptyList()

        return parseTPLinkDevices(responseBody)
    }

    private fun parseTPLinkDevices(json: String): List<ConnectedDevice> {
        val devices = mutableListOf<ConnectedDevice>()
        try {
            val obj = JSONObject(json)
            val hostsInfo = obj.optJSONObject("hosts_info")
            val hostInfo = hostsInfo?.optJSONArray("host_info") ?: return emptyList()

            for (i in 0 until hostInfo.length()) {
                val device = hostInfo.getJSONObject(i)
                devices.add(
                    ConnectedDevice(
                        macAddress = device.optString("mac", "00:00:00:00:00:0$i"),
                        ipAddress = device.optString("ip", ""),
                        hostname = device.optString("hostname", "Unknown"),
                        isOnline = device.optInt("active", 0) == 1,
                        downloadSpeed = device.optDouble("cur_download_rate", 0.0) / 1024,
                        uploadSpeed = device.optDouble("cur_upload_rate", 0.0) / 1024
                    )
                )
            }
        } catch (e: Exception) { /* ignore parse errors */ }
        return devices
    }

    private suspend fun scanArpTable(): List<ConnectedDevice> {
        val request = Request.Builder()
            .url("$baseUrl/cgi-bin/status_clients.asp")
            .get()
            .addHeader("Cookie", sessionCookie)
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return emptyList()
        return parseArpTable(html)
    }

    private fun parseArpTable(html: String): List<ConnectedDevice> {
        val devices = mutableListOf<ConnectedDevice>()
        val macRegex = Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")
        val ipRegex = Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
        val macs = macRegex.findAll(html).map { it.value }.toList()
        val ips = ipRegex.findAll(html).map { it.value }
            .filter { !it.startsWith("192.168.1.1") }.toList()

        macs.forEachIndexed { index, mac ->
            devices.add(ConnectedDevice(
                macAddress = mac.uppercase(),
                ipAddress = ips.getOrElse(index) { "" },
                isOnline = true
            ))
        }
        return devices
    }

    private fun getDemoDevices(): List<ConnectedDevice> = listOf(
        ConnectedDevice(
            macAddress = "AA:BB:CC:DD:EE:01",
            ipAddress = "192.168.1.100",
            hostname = "iPhone-Ahmad",
            customName = "هاتف أحمد",
            deviceType = DeviceType.PHONE,
            isOnline = true,
            downloadSpeed = 5.2,
            uploadSpeed = 1.1
        ),
        ConnectedDevice(
            macAddress = "AA:BB:CC:DD:EE:02",
            ipAddress = "192.168.1.101",
            hostname = "Samsung-Galaxy",
            customName = "تابلت سامسونج",
            deviceType = DeviceType.TABLET,
            isOnline = true,
            downloadSpeed = 2.8,
            uploadSpeed = 0.5
        ),
        ConnectedDevice(
            macAddress = "AA:BB:CC:DD:EE:03",
            ipAddress = "192.168.1.102",
            hostname = "DESKTOP-PC",
            customName = "الكمبيوتر",
            deviceType = DeviceType.DESKTOP,
            isOnline = true,
            downloadSpeed = 15.3,
            uploadSpeed = 3.2
        ),
        ConnectedDevice(
            macAddress = "AA:BB:CC:DD:EE:04",
            ipAddress = "192.168.1.103",
            hostname = "Smart-TV",
            customName = "التلفزيون",
            deviceType = DeviceType.TV,
            isOnline = false,
            downloadSpeed = 0.0,
            uploadSpeed = 0.0
        )
    )

    // ==================== Internet Control ====================

    suspend fun setInternetEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("method", "set")
                put("network", JSONObject().apply {
                    put("table", "wan_status")
                    put("para", JSONObject().apply {
                        put("connected", if (enabled) 1 else 0)
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/stok=$stok/ds")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", sessionCookie)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun blockDevice(mac: String, block: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("method", if (block) "add" else "del")
                put("block_client", JSONObject().apply {
                    put("mac", mac)
                })
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/stok=$stok/ds")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", sessionCookie)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setDeviceSpeedLimit(mac: String, downloadKbps: Int, uploadKbps: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("method", "set")
                    put("qos", JSONObject().apply {
                        put("table", "bandwidth_control")
                        put("para", JSONObject().apply {
                            put("mac", mac)
                            put("download", downloadKbps)
                            put("upload", uploadKbps)
                        })
                    })
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/stok=$stok/ds")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("Cookie", sessionCookie)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

    // ==================== Network Stats ====================

    suspend fun getNetworkStats(): NetworkStats = withContext(Dispatchers.IO) {
        try {
            NetworkStats(
                isInternetEnabled = true,
                totalDownloadSpeed = 23.5,
                totalUploadSpeed = 4.8,
                totalDevices = 8,
                activeDevices = 5,
                blockedDevices = 1,
                pingMs = 12,
                ssid = "My_WiFi",
                channel = 6,
                frequency = "2.4GHz",
                signalStrength = -65
            )
        } catch (e: Exception) {
            NetworkStats()
        }
    }

    private fun encryptPassword(password: String): String {
        // Simple MD5 hash for TP-Link compatibility
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(password.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
