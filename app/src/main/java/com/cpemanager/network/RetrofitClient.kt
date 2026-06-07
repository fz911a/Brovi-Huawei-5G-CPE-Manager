package com.cpemanager.network

import android.util.Base64
import com.cpemanager.data.model.HuaweiSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HuaweiCpeClient {

    private const val DEFAULT_BASE_URL = "http://10.0.0.1"
    private val XML_MEDIA_TYPE = "application/xml".toMediaType()

    private var baseUrl: String = DEFAULT_BASE_URL
    private var sessionId: String = ""
    private var cookieHeader: String = ""
    private var token: String = ""
    private val tokenQueue: ArrayDeque<String> = ArrayDeque()
    private val developerTokenQueue: ArrayDeque<String> = ArrayDeque()  // 开发者模式专用 token 队列
    private val requestMutex = Mutex()
    private var rawTokenForLogin: String = ""

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun setBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        if (normalized != baseUrl) {
            baseUrl = normalized
            clearAuth()
        }
    }

    fun getBaseUrl(): String = baseUrl

    suspend fun authenticate(): Result<HuaweiSession> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/api/webserver/SesTokInfo")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val xml = parseXmlToMap(body)

                sessionId = xml["SesInfo"].orEmpty().trim()
                token = xml["TokInfo"].orEmpty().trim()
                response.header("Set-Cookie")?.let { setCookie ->
                    parseCookieFromSetCookie(setCookie)?.let { cookieHeader = it }
                }

                if (cookieHeader.isEmpty() && sessionId.isNotEmpty()) {
                    cookieHeader = if (sessionId.contains("=")) sessionId else "SessionID=$sessionId"
                }
                if (cookieHeader.isEmpty()) throw Exception("missing session cookie")

                seedToken(xml["TokInfo"].orEmpty())
                HuaweiSession(sessionId = sessionId, token = token)
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception("connect failed: ${it.message}")) }
        )
    }

    suspend fun get(endpoint: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        doGet(endpoint).recoverCatching {
            clearAuth()
            authenticate().getOrThrow()
            doGet(endpoint).getOrThrow()
        }
    }

    suspend fun post(endpoint: String, params: Map<String, String>): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            doPost(endpoint, params).recoverCatching {
                clearAuth()
                authenticate().getOrThrow()
                doPost(endpoint, params).getOrThrow()
            }
        }

    suspend fun getSignal(): Result<Map<String, String>> = get("device/signal")
    suspend fun getDeviceInfo(): Result<Map<String, String>> = get("device/information")
    suspend fun getNetworkStatus(): Result<Map<String, String>> = get("monitoring/status")
    suspend fun getTrafficStats(): Result<Map<String, String>> = get("monitoring/traffic-statistics")
    suspend fun getSystemLog(): Result<Map<String, String>> = get("log/loginfo")
    suspend fun getDiagnosePingResult(): Result<Map<String, String>> = get("diagnosis/diagnose_ping")
    suspend fun getCurrentPlmn(): Result<Map<String, String>> = get("net/current-plmn")
    suspend fun getNetMode(): Result<Map<String, String>> = get("net/net-mode")
    suspend fun getSecCellInfo(): Result<Map<String, String>> = get("device/seccellinfo")
    suspend fun getNbrCellInfo(): Result<Map<String, String>> = get("device/nbrcellinfo")
    suspend fun getCellInfo(): Result<Map<String, String>> = get("net/cell-info")
    suspend fun getNetModeList(): Result<Map<String, String>> = get("net/net-mode-list")
    suspend fun getPlmnList(): Result<Map<String, String>> = get("net/plmn-list")
    suspend fun getAntennaType(): Result<Map<String, String>> = get("device/antenna_type")
    suspend fun getAntennaSetType(): Result<Map<String, String>> = get("device/antenna_set_type")
    suspend fun getAntennaConfiguration(): Result<Map<String, String>> = get("net/antenna-configuration")
    suspend fun getWlanDebug(): Result<Map<String, String>> = get("wlan/wlan-debug")
    
    /**
     * 刷新 token 队列 - 开发者模式登录后调用，确保新的开发者权限生效
     * 重要：开发者模式登录已经从响应头中捕获了新的 token 和 SessionID
     * 这个方法只需要确保 token 队列已经被正确初始化
     */
    suspend fun refreshTokenQueue(): Result<Boolean> = withContext(Dispatchers.IO) {
        requestMutex.withLock {
            runCatching {
                // 如果 token 队列为空，调用 /api/webserver/token 获取新的 token
                if (tokenQueue.isEmpty() && token.isEmpty()) {
                    fetchToken().getOrThrow()
                }
                // 验证 token 不为空
                if (tokenQueue.isEmpty() && token.isEmpty()) {
                    throw Exception("No token available after refresh")
                }
                true
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(it) }
            )
        }
    }
    
    suspend fun getDeveloperEnable(): Result<Map<String, String>> = get("developermode/developer-mode")
    suspend fun getDeveloperItemEnable(): Result<Map<String, String>> = get("developermode/developer-item")

    suspend fun setNetMode(mode: String, band: String = "", lteBand: String = ""): Result<Map<String, String>> =
        post(
            endpoint = "net/net-mode",
            params = mapOf("NetworkMode" to mode, "NetworkBand" to band, "LTEBand" to lteBand)
        )

    suspend fun setLockCell(lock: Int, freq: Long, pci: Int): Result<Map<String, String>> =
        post(
            endpoint = "net/lock-cell",
            params = mapOf("LockCell" to lock.toString(), "Freq" to freq.toString(), "PCI" to pci.toString())
        )

    suspend fun setWlanDebug(params: Map<String, String>): Result<Map<String, String>> =
        post(
            endpoint = "wlan/wlan-debug",
            params = params
        )

    suspend fun enableTelnetDebugPort(): Result<Boolean> = withContext(Dispatchers.IO) {
        requestMutex.withLock {
            runCatching {
                ensureSession()
                // 使用开发者 Token 队列（不是普通 tokenQueue）
                val requestToken = if (developerTokenQueue.isNotEmpty()) {
                    consumeDevToken()
                } else {
                    // 降级：如果开发者队列为空，尝试用普通 token
                    if (tokenQueue.isEmpty()) fetchToken().getOrThrow()
                    consumeToken()
                }
                val params = mapOf("enable" to "1")
                val body = buildXmlRequest(params).toRequestBody(XML_MEDIA_TYPE)
                
                val request = Request.Builder()
                    .url("$baseUrl/api/developer/atport-status")
                    .apply {
                        if (cookieHeader.isNotEmpty()) header("Cookie", cookieHeader)
                        if (requestToken.isNotEmpty()) header("__RequestVerificationToken", requestToken)
                    }
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    // Telnet 端口启用响应不需要存到 token queue
                    val text = response.body?.string().orEmpty()
                    android.util.Log.d("HuaweiCpeClient", "enableTelnetDebugPort response text: $text")
                    val map = parseXmlToMap(text)
                    android.util.Log.d("HuaweiCpeClient", "enableTelnetDebugPort parsed map: $map")
                    
                    val code = map["code"]?.trim()
                    android.util.Log.d("HuaweiCpeClient", "enableTelnetDebugPort code: '$code'")
                    if (code != null && code != "0" && code != "" && code != "success") {
                        throw Exception("atport-status: code=$code")
                    }
                    
                    true
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(it) }
            )
        }
    }

    suspend fun ping(): Result<Boolean> = withContext(Dispatchers.IO) {
        get("device/information").fold(
            onSuccess = { Result.success(true) },
            onFailure = { Result.success(false) }
        )
    }

    suspend fun login(password: String): Result<Boolean> = login("admin", password)

    /**
     * 开发模式登录 — 使用 loginflag=2 的挑战认证
     * 华为进入 /#/developermode 时需要额外的二次认证
     * 前端在挑战登录时附加 loginflag=2 参数
     */
    suspend fun loginDeveloperMode(password: String): Result<Boolean> = loginDeveloperMode("admin", password)

    suspend fun loginDeveloperMode(username: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val safeUser = username.trim().ifEmpty { "admin" }
            val firstNonce = generateNonceHex(32)

            // 第一步：challenge_login（使用普通 doPost，暂不需要开发者 token）
            val challenge = doPost(
                endpoint = "user/challenge_login",
                params = mapOf(
                    "username" to safeUser,
                    "firstnonce" to firstNonce,
                    "mode" to "1",
                    "loginflag" to "2"
                )
            ).getOrElse { return@runCatching Result.failure(it) }

            val saltHex = challenge["salt"].orEmpty()
            val iterationText = challenge["iterations"].orEmpty()
            val serverNonce = challenge["servernonce"].orEmpty()
            val iterations = iterationText.toIntOrNull()
                ?: return@runCatching Result.failure(Exception("invalid challenge iterations: $iterationText"))

            val salt = hexToBytes(saltHex)
            val authMessage = "$firstNonce,$serverNonce,$serverNonce"
            val saltedPassword = pbkdf2HmacSha256(password, salt, iterations, 32)
            val clientKey = hmacSha256("Client Key".toByteArray(Charsets.UTF_8), saltedPassword)
            val storedKey = sha256(clientKey)
            val clientSignature = hmacSha256(authMessage.toByteArray(Charsets.UTF_8), storedKey)
            val clientProof = xorBytes(clientKey, clientSignature)

            // 第二步：authentication_login（响应头中的 Token 存入 developerTokenQueue）
            val auth = doPostForDeveloper(
                endpoint = "user/authentication_login",
                params = mapOf(
                    "clientproof" to bytesToHex(clientProof),
                    "finalnonce" to serverNonce,
                    "loginflag" to "2"
                )
            ).getOrElse { return@runCatching Result.failure(it) }

            Result.success(true)
        }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun login(username: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val safeUser = username.trim().ifEmpty { "admin" }
            val state = runCatching { doGet("user/state-login").getOrThrow() }.getOrDefault(emptyMap())
            val passwordType = state["password_type"].orEmpty().trim()
            val lockStatus = state["lockstatus"].orEmpty().trim()
            if (lockStatus == "1") {
                val waitMinutes = state["remainwaittime"].orEmpty().trim().ifBlank { "1" }
                throw Exception("login is temporarily locked, wait $waitMinutes minute(s)")
            }

        val attempts: List<suspend () -> Result<Boolean>> = if (passwordType == "0") {
            listOf(
                { loginWithBase64(safeUser, password) },
                { loginWithChallenge(safeUser, password) },
                { loginWithPasswordType4(safeUser, password) }
                )
            } else {
                listOf(
                    { loginWithChallenge(safeUser, password) },
                    { loginWithPasswordType4(safeUser, password) },
                    { loginWithBase64(safeUser, password) }
                )
            }

            var lastError: Throwable? = null
            for (attempt in attempts) {
                val result = attempt()
                if (result.isSuccess) {
                    return@runCatching true
                }
                lastError = result.exceptionOrNull()
                val failureText = lastError?.message.orEmpty()
                // Wrong-password / lock errors should stop retries immediately to avoid lock escalation.
                if (failureText.contains("code=108006") ||
                    failureText.contains("code=108007") ||
                    failureText.contains("code=125003")
                ) {
                    break
                }
            }
            throw (lastError ?: Exception("login failed"))
        }.fold(
            onSuccess = { Result.success(true) },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun loginWithBase64(username: String, password: String): Result<Boolean> {
        val payload = mapOf(
            "Username" to username,
            "Password" to encodeBase64(password),
            "password_type" to "0"
        )
        return doPost("user/login", payload).map { true }
    }

    private suspend fun loginWithPasswordType4(username: String, password: String): Result<Boolean> {
        if (token.isEmpty()) fetchToken().getOrElse { return Result.failure(it) }
        val requestToken = peekToken()
        if (requestToken.isBlank()) {
            return Result.failure(Exception("missing verification token"))
        }
        val hashToken = rawTokenForLogin.takeIf { it.isNotBlank() } ?: requestToken
        val pwdHash = encodeBase64(sha256(password.toByteArray(Charsets.UTF_8)))
        val loginHash = encodeBase64(
            sha256((username + pwdHash + hashToken).toByteArray(Charsets.UTF_8))
        )
        val payload = mapOf(
            "Username" to username,
            "Password" to loginHash,
            "password_type" to "4"
        )
        return doPost("user/login", payload).map { true }
    }

    private suspend fun loginWithChallenge(username: String, password: String): Result<Boolean> {
        val firstNonce = generateNonceHex(32)
        val challenge = doPost(
            endpoint = "user/challenge_login",
            params = mapOf("username" to username, "firstnonce" to firstNonce, "mode" to "1")
        ).getOrElse { return Result.failure(it) }

        val saltHex = challenge["salt"].orEmpty()
        val iterationText = challenge["iterations"].orEmpty()
        val serverNonce = challenge["servernonce"].orEmpty()
        val iterations = iterationText.toIntOrNull()
            ?: return Result.failure(Exception("invalid challenge iterations: $iterationText"))
        if (saltHex.isBlank() || serverNonce.isBlank()) {
            return Result.failure(Exception("challenge response is incomplete"))
        }

        val salt = hexToBytes(saltHex)
        val authMessage = "$firstNonce,$serverNonce,$serverNonce"
        val saltedPassword = pbkdf2HmacSha256(password, salt, iterations, 32)
        // Huawei WebUI's SCRAM helper passes HMAC parameters as (message, key) via CryptoJS.HmacSHA256.
        val clientKey = hmacSha256("Client Key".toByteArray(Charsets.UTF_8), saltedPassword)
        val storedKey = sha256(clientKey)
        val clientSignature = hmacSha256(authMessage.toByteArray(Charsets.UTF_8), storedKey)
        val clientProof = xorBytes(clientKey, clientSignature)
        val serverKey = hmacSha256("Server Key".toByteArray(Charsets.UTF_8), saltedPassword)
        val expectedServerSignature = bytesToHex(
            hmacSha256(authMessage.toByteArray(Charsets.UTF_8), serverKey)
        )

        val auth = doPost(
            endpoint = "user/authentication_login",
            params = mapOf(
                "clientproof" to bytesToHex(clientProof),
                "finalnonce" to serverNonce,
                "logintype" to "1"
            )
        ).getOrElse { return Result.failure(it) }

        val serverSignature = auth["serversignature"].orEmpty()
        if (serverSignature.isNotBlank() &&
            !serverSignature.equals(expectedServerSignature, ignoreCase = true)
        ) {
            return Result.failure(Exception("server signature verification failed"))
        }
        return Result.success(true)
    }

    private suspend fun doGet(endpoint: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        requestMutex.withLock {
            runCatching {
                ensureSession()
                if (tokenQueue.isEmpty() && token.isEmpty()) fetchToken().getOrThrow()
                // Huawei web UI keeps token queues for POSTs and does not attach them to normal GETs.
                val requestToken = if (tokenQueue.isEmpty()) token else ""
                val request = Request.Builder()
                    .url("$baseUrl/api/$endpoint")
                    .apply {
                        if (cookieHeader.isNotEmpty()) header("Cookie", cookieHeader)
                        if (requestToken.isNotEmpty()) header("__RequestVerificationToken", requestToken)
                    }
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    captureAuthFromResponse(
                        setCookie = response.header("Set-Cookie"),
                        responseToken = response.header("__RequestVerificationToken"),
                        responseTokenOne = response.header("__RequestVerificationTokenone"),
                        responseTokenTwo = response.header("__RequestVerificationTokentwo")
                    )
                    val body = response.body?.string().orEmpty()
                    val map = parseXmlToMap(body)
                    ensureNoApiError(endpoint, map)
                    map
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(Exception("request $endpoint failed: ${it.message}")) }
            )
        }
    }

    private suspend fun doPost(endpoint: String, params: Map<String, String>): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            requestMutex.withLock {
                runCatching {
                    ensureSession()
                    if (tokenQueue.isEmpty()) fetchToken().getOrThrow()
                    val requestToken = consumeToken()
                    val body = buildXmlRequest(params).toRequestBody(XML_MEDIA_TYPE)
                    val request = Request.Builder()
                        .url("$baseUrl/api/$endpoint")
                        .apply {
                            if (cookieHeader.isNotEmpty()) header("Cookie", cookieHeader)
                            if (requestToken.isNotEmpty()) header("__RequestVerificationToken", requestToken)
                        }
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        captureAuthFromResponse(
                            setCookie = response.header("Set-Cookie"),
                            responseToken = response.header("__RequestVerificationToken"),
                            responseTokenOne = response.header("__RequestVerificationTokenone"),
                            responseTokenTwo = response.header("__RequestVerificationTokentwo")
                        )
                        val text = response.body?.string().orEmpty()
                        val map = parseXmlToMap(text)
                        ensureNoApiError(endpoint, map)
                        map
                    }
                }.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { Result.failure(Exception("POST $endpoint failed: ${it.message}")) }
                )
            }
        }

    /**
     * 开发者模式专用 POST：响应头中的 Token 存入 developerTokenQueue，不影响普通 tokenQueue。
     * 仅在 loginDeveloperMode 的 authentication_login 步骤中使用。
     */
    private suspend fun doPostForDeveloper(endpoint: String, params: Map<String, String>): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            requestMutex.withLock {
                runCatching {
                    ensureSession()
                    // 使用普通 tokenQueue 完成请求（开发者认证需要有效的普通 token）
                    if (tokenQueue.isEmpty()) fetchToken().getOrThrow()
                    val requestToken = consumeToken()
                    val body = buildXmlRequest(params).toRequestBody(XML_MEDIA_TYPE)
                    val request = Request.Builder()
                        .url("$baseUrl/api/$endpoint")
                        .apply {
                            if (cookieHeader.isNotEmpty()) header("Cookie", cookieHeader)
                            if (requestToken.isNotEmpty()) header("__RequestVerificationToken", requestToken)
                        }
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        // 将响应头的 Token 存入 developerTokenQueue
                        captureDevAuthFromResponse(
                            setCookie = response.header("Set-Cookie"),
                            responseToken = response.header("__RequestVerificationToken"),
                            responseTokenOne = response.header("__RequestVerificationTokenone"),
                            responseTokenTwo = response.header("__RequestVerificationTokentwo")
                        )
                        val text = response.body?.string().orEmpty()
                        val map = parseXmlToMap(text)
                        ensureNoApiError(endpoint, map)
                        map
                    }
                }.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { Result.failure(Exception("POST(dev) $endpoint failed: ${it.message}")) }
                )
            }
        }

    private suspend fun ensureSession() {
        if (cookieHeader.isNotEmpty()) return
        authenticate().getOrThrow()
    }

    private suspend fun fetchToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (cookieHeader.isEmpty()) throw Exception("session cookie is missing")
            val request = Request.Builder()
                .url("$baseUrl/api/webserver/token")
                .header("Cookie", cookieHeader)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.header("Set-Cookie")?.let { setCookie ->
                    parseCookieFromSetCookie(setCookie)?.let { cookieHeader = it }
                }
                val body = response.body?.string().orEmpty()
                val map = parseXmlToMap(body)
                val newToken = map["token"].orEmpty().ifBlank { map["TokInfo"].orEmpty() }
                if (newToken.isEmpty()) throw Exception("missing token")
                seedPostTokens(newToken)
                token
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    private fun ensureNoApiError(endpoint: String, responseMap: Map<String, String>) {
        val code = responseMap["code"]?.trim()
        if (!code.isNullOrEmpty()) {
            val message = responseMap["message"].orEmpty()
            val detail = if (message.isBlank()) {
                if (code == "125003") {
                    "code=125003 (invalid verification token or wrong username/password)"
                } else if (code == "108007") {
                    val wait = responseMap["waittime"].orEmpty().ifBlank { "1" }
                    "code=108007 (login locked, wait $wait minute(s))"
                } else if (code == "108006") {
                    val remain = responseMap["remaincount"].orEmpty()
                    if (remain.isNotBlank()) {
                        "code=108006 (wrong password, remaining attempts: $remain)"
                    } else {
                        "code=108006 (wrong password)"
                    }
                } else {
                    "code=$code"
                }
            } else {
                "code=$code, message=$message"
            }
            throw Exception("CPE API $endpoint error: $detail")
        }
    }

    private fun captureAuthFromResponse(
        setCookie: String?,
        responseToken: String?,
        responseTokenOne: String?,
        responseTokenTwo: String?
    ) {
        if (!setCookie.isNullOrBlank()) {
            parseCookieFromSetCookie(setCookie)?.let { cookieHeader = it }
        }
        when {
            !responseTokenOne.isNullOrBlank() -> {
                val merged = mutableListOf<String>()
                merged.addAll(extractPostTokens(responseTokenOne))
                merged.addAll(extractPostTokens(responseTokenTwo))
                tokenQueue.clear()
                merged.forEach { tokenQueue.addLast(it) }
                if (tokenQueue.isNotEmpty()) {
                    rawTokenForLogin = responseTokenOne.substringBefore('#').trim()
                }
                token = tokenQueue.firstOrNull().orEmpty()
            }
            !responseToken.isNullOrBlank() -> {
                val tokens = extractPostTokens(responseToken)
                tokenQueue.clear()
                tokens.forEach { tokenQueue.addLast(it) }
                if (tokenQueue.isNotEmpty()) {
                    rawTokenForLogin = responseToken.substringBefore('#').trim()
                }
                token = tokenQueue.firstOrNull().orEmpty()
            }
        }
    }

    private fun parseCookieFromSetCookie(setCookie: String): String? {
        return setCookie.substringBefore(';').trim().takeIf { it.contains("=") }
    }

    private fun parseXmlToMap(xml: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            val tagStack = mutableListOf<String>()
            val indexedTagStack = mutableListOf<String>()
            // Track sibling indexes to preserve repeated nodes like cell[0], cell[1], ...
            val childCounters = mutableListOf<MutableMap<String, Int>>()
            childCounters.add(mutableMapOf())

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.orEmpty()
                        if (tag.isNotBlank()) {
                            val siblings = childCounters.last()
                            val idx = siblings[tag] ?: 0
                            siblings[tag] = idx + 1

                            tagStack.add(tag)
                            indexedTagStack.add("$tag[$idx]")
                            childCounters.add(mutableMapOf())
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty() && tagStack.isNotEmpty()) {
                            val leaf = tagStack.last()
                            if (leaf != "response" && leaf != "error" && leaf != "request") {
                                val plainPath = tagStack.joinToString(".")
                                val indexedPath = indexedTagStack.joinToString(".")
                                addXmlValue(result, leaf, text)
                                addXmlValue(result, plainPath, text)
                                addXmlValue(result, indexedPath, text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagStack.isNotEmpty()) tagStack.removeAt(tagStack.lastIndex)
                        if (indexedTagStack.isNotEmpty()) indexedTagStack.removeAt(indexedTagStack.lastIndex)
                        if (childCounters.size > 1) childCounters.removeAt(childCounters.lastIndex)
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun addXmlValue(result: MutableMap<String, String>, key: String, value: String) {
        if (key.isBlank()) return
        val old = result[key]
        result[key] = when {
            old.isNullOrBlank() -> value
            old == value -> old
            else -> "$old|$value"
        }
    }

    private fun buildXmlRequest(params: Map<String, String>): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.append("<request>")
        params.forEach { (key, value) ->
            sb.append('<').append(key).append('>')
                .append(escapeXml(value))
                .append("</").append(key).append('>')
        }
        sb.append("</request>")
        return sb.toString()
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun normalizeBaseUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "http://$trimmed"
    }

    private fun clearAuth() {
        sessionId = ""
        cookieHeader = ""
        token = ""
        tokenQueue.clear()
        developerTokenQueue.clear()
        rawTokenForLogin = ""
    }

    private fun seedToken(initialToken: String) {
        val t = initialToken.trim()
        tokenQueue.clear()
        if (t.isNotEmpty()) tokenQueue.addLast(t)
        token = t
        rawTokenForLogin = t
    }

    private fun seedPostTokens(rawToken: String) {
        val normalized = rawToken.trim()
        tokenQueue.clear()
        if (normalized.isEmpty()) {
            token = ""
            rawTokenForLogin = ""
            return
        }
        rawTokenForLogin = normalized.substringBefore('#').trim()
        val candidates = normalized.split('#')
            .mapNotNull { adaptPostToken(it) }
        if (candidates.isEmpty()) {
            token = ""
            rawTokenForLogin = ""
            return
        }
        candidates.forEach { tokenQueue.addLast(it) }
        token = tokenQueue.firstOrNull().orEmpty()
    }

    private fun adaptPostToken(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        // /api/webserver/token returns a 64-char token; Huawei web UI uses the tail 32 chars for POSTs.
        return if (v.length > 32) v.substring(32) else v
    }

    private fun peekToken(): String {
        return tokenQueue.firstOrNull() ?: token
    }

    private fun consumeToken(): String {
        val next = tokenQueue.pollFirst() ?: token
        token = tokenQueue.firstOrNull() ?: next
        return next
    }

    // --- 开发者 Token 队列：仅用于 enableTelnetDebugPort ---

    private fun consumeDevToken(): String {
        val next = developerTokenQueue.pollFirst() ?: token
        return next
    }

    private fun seedDevTokens(rawToken: String) {
        val normalized = rawToken.trim()
        developerTokenQueue.clear()
        if (normalized.isEmpty()) return
        val candidates = normalized.split('#')
            .mapNotNull { adaptPostToken(it) }
        candidates.forEach { developerTokenQueue.addLast(it) }
    }

    private fun captureDevAuthFromResponse(
        setCookie: String?,
        responseToken: String?,
        responseTokenOne: String?,
        responseTokenTwo: String?
    ) {
        // 开发者模式登录可能返回新的 SessionID
        if (!setCookie.isNullOrBlank()) {
            parseCookieFromSetCookie(setCookie)?.let { cookieHeader = it }
        }
        when {
            !responseTokenOne.isNullOrBlank() -> {
                val merged = StringBuilder()
                merged.append(responseTokenOne)
                if (!responseTokenTwo.isNullOrBlank()) merged.append('#').append(responseTokenTwo)
                seedDevTokens(merged.toString())
            }
            !responseToken.isNullOrBlank() -> {
                seedDevTokens(responseToken)
            }
        }
    }

    private fun extractPostTokens(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('#').mapNotNull { adaptPostToken(it) }
    }

    private fun encodeBase64(value: String): String {
        return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun encodeBase64(value: ByteArray): String {
        return Base64.encodeToString(value, Base64.NO_WRAP)
    }

    private fun generateNonceHex(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return bytesToHex(bytes)
    }

    private fun pbkdf2HmacSha256(
        password: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int
    ): ByteArray {
        require(iterations > 0) { "iterations must be > 0" }
        require(keyLengthBytes > 0) { "keyLengthBytes must be > 0" }

        val hLen = 32
        val blocks = (keyLengthBytes + hLen - 1) / hLen
        val derived = ByteArray(blocks * hLen)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)

        var offset = 0
        for (block in 1..blocks) {
            val blockIndex = byteArrayOf(
                ((block ushr 24) and 0xFF).toByte(),
                ((block ushr 16) and 0xFF).toByte(),
                ((block ushr 8) and 0xFF).toByte(),
                (block and 0xFF).toByte()
            )
            val initial = ByteArray(salt.size + 4)
            System.arraycopy(salt, 0, initial, 0, salt.size)
            System.arraycopy(blockIndex, 0, initial, salt.size, 4)

            var u = hmacSha256(passwordBytes, initial)
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = hmacSha256(passwordBytes, u)
                for (j in t.indices) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }

            System.arraycopy(t, 0, derived, offset, t.size)
            offset += t.size
        }
        return derived.copyOf(keyLengthBytes)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun xorBytes(left: ByteArray, right: ByteArray): ByteArray {
        val size = minOf(left.size, right.size)
        val out = ByteArray(size)
        for (i in 0 until size) {
            out[i] = (left[i].toInt() xor right[i].toInt()).toByte()
        }
        return out
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        bytes.forEach { b ->
            out.append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
        }
        return out.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "invalid hex string length" }
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            val high = Character.digit(clean[i], 16)
            val low = Character.digit(clean[i + 1], 16)
            if (high == -1 || low == -1) throw IllegalArgumentException("invalid hex string")
            out[i / 2] = ((high shl 4) or low).toByte()
            i += 2
        }
        return out
    }
}

object RetrofitClient {
    fun setBaseUrl(url: String) = HuaweiCpeClient.setBaseUrl(url)
    fun getBaseUrl(): String = HuaweiCpeClient.getBaseUrl()
}
