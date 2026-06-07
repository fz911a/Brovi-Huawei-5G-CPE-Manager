package com.cpemanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cpemanager.data.local.SettingsRepository
import com.cpemanager.data.local.DEFAULT_REFRESH_INTERVAL_SEC
import com.cpemanager.data.local.settingsDataStore
import com.cpemanager.data.model.AggregationCell
import com.cpemanager.data.model.BaseStationTrend
import com.cpemanager.data.model.LinkStatus
import com.cpemanager.data.model.LockConfig
import com.cpemanager.data.model.PccInfo
import com.cpemanager.data.model.RfQuality
import com.cpemanager.data.model.SpeedInfo
import com.cpemanager.data.model.StationTrendSample
import com.cpemanager.data.model.SystemLogEntry
import com.cpemanager.data.model.TelnetAmbrQci
import com.cpemanager.data.model.defaultAggregationCells
import com.cpemanager.data.repository.CpeRepository
import com.cpemanager.network.HuaweiCpeClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CpeUiState(
    val pccInfo: PccInfo = PccInfo(),
    val rfQuality: RfQuality = RfQuality(
        rsrpHistory = listOf(-85.0, -87.0, -83.0, -88.0, -90.0, -86.0, -84.0, -82.0, -89.0, -87.0, -85.0, -83.0, -88.0, -86.0, -84.0, -82.0, -91.0, -87.0, -85.0, -83.0),
        sinrHistory = listOf(15.0, 12.0, 18.0, 10.0, 8.0, 14.0, 17.0, 20.0, 9.0, 13.0, 16.0, 19.0, 11.0, 15.0, 18.0, 22.0, 7.0, 14.0, 17.0, 20.0)
    ),
    val linkStatus: LinkStatus = LinkStatus(),
    val aggregationCells: List<AggregationCell> = emptyList(),
    val baseStationTrends: List<BaseStationTrend> = emptyList(),
    val lockConfig: LockConfig = LockConfig(),
    val speedInfo: SpeedInfo = SpeedInfo(),
    val baseUrl: String = "http://10.0.0.1",
    val username: String = "admin",
    val password: String = "",
    val apiKey: String = "",
    val speedTestUrl: String = "https://speed.cloudflare.com/__down?bytes=10485760",
    val latencyTestUrl: String = "https://www.gstatic.com/generate_204",
    val autoLogin: Boolean = true,
    val rememberPassword: Boolean = true,
    val autoRelogin: Boolean = true,
    val autoRefresh: Boolean = true,
    val refreshIntervalSec: Int = DEFAULT_REFRESH_INTERVAL_SEC,
    val lastUpdated: String = "2026-05-29 16:04:00",
    val connectionMessage: String = "Dashboard refreshed",
    val isLoading: Boolean = false,
    val error: String? = null,
    val deviceOnline: Boolean = false,
    val telnetStatus: String = "未连接",
    // 系统操作日志
    val systemLogEntries: List<SystemLogEntry> = emptyList(),
    val systemLogLoading: Boolean = false,
    val systemLogTypeFilter: String = "全部",     // "全部" / "用户操作" / "系统日志" / "安全日志"
    val systemLogLevelFilter: String = "提示"      // "警告" / "提示" / "信息"
)

class CpeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CpeRepository()
    private val settingsRepo = SettingsRepository(app.settingsDataStore)
    private val lastUpdatedFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val _uiState = MutableStateFlow(CpeUiState())
    val uiState: StateFlow<CpeUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var telnetMonitorJob: Job? = null
    private var lastTelnetReport: TelnetAmbrQci? = null
    private var firstTelnetQci: String? = null
    private var telnetEnabledForBaseUrl: String = ""
    private var telnetEnableInProgress: Boolean = false

    // 标记是否由用户主动暂停（用于区分后台暂停和断线重连）
    private var pausedByUser: Boolean = false

    /** App 进入前台 — 恢复所有连接 */
    fun onAppInForeground() {
        if (!pausedByUser) return
        pausedByUser = false
        android.util.Log.d("CpeVM", "App 回到前台，恢复连接...")

        val state = _uiState.value
        if (state.password.isNotBlank()) {
            viewModelScope.launch {
                // 重新登录并恢复 Telnet + 刷新
                runCatching {
                    repository.setBaseUrl(state.baseUrl)
                    repository.loginCpe(state.username.ifBlank { "admin" }, state.password).getOrThrow()
                    runCatching { repository.loginDeveloperMode(state.password) }
                    _uiState.update { it.copy(deviceOnline = true) }
                    startTelnetMonitor()
                    startAutoRefresh()
                }.onFailure { e ->
                    android.util.Log.w("CpeVM", "前台恢复登录失败: ${e.message}")
                    _uiState.update { it.copy(deviceOnline = false, error = e.message) }
                }
            }
        }
    }

    /** App 进入后台 — 暂停连接节省资源 */
    fun onAppInBackground() {
        pausedByUser = true
        android.util.Log.d("CpeVM", "App 进入后台，暂停连接...")
        stopAutoRefresh()
        stopTelnetMonitor()
    }

    init {
        viewModelScope.launch {
            val saved = settingsRepo.settings.first()
            _uiState.update {
                it.copy(
                    baseUrl = saved.baseUrl,
                    username = saved.username,
                    password = saved.password,
                    rememberPassword = saved.rememberPassword,
                    autoLogin = saved.autoLogin,
                    autoRelogin = saved.autoRelogin,
                    autoRefresh = saved.autoRefresh,
                    refreshIntervalSec = saved.refreshIntervalSec,
                    speedTestUrl = saved.speedTestUrl,
                    latencyTestUrl = saved.latencyTestUrl
                )
            }
            repository.setBaseUrl(saved.baseUrl)
            if (saved.autoLogin && saved.password.isNotBlank()) {
                val password = saved.password
                runCatching {
                    repository.loginCpe(saved.username.ifBlank { "admin" }, password).getOrThrow()
                }.onSuccess {
                    _uiState.update {
                        it.copy(deviceOnline = true, connectionMessage = "Auto login succeeded")
                    }
                    // 自动开发者模式登录，获取 telnet 权限
                    runCatching {
                        repository.loginDeveloperMode(password).getOrThrow()
                    }.onSuccess {
                        android.util.Log.d("CpeVM", "Auto dev login succeeded")
                    }.onFailure { e ->
                        android.util.Log.w("CpeVM", "Auto dev login failed: ${e.message}")
                    }
                    startTelnetMonitor()
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            deviceOnline = false,
                            connectionMessage = "Auto login failed: ${e.message.orEmpty()}",
                            error = e.message,
                            telnetStatus = "未连接"
                        )
                    }
                    stopTelnetMonitor()
                }
            }
            if (saved.autoRefresh) startAutoRefresh()
        }
    }

    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshAll()
                delay((_uiState.value.refreshIntervalSec * 1000L).coerceAtLeast(1000L))
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun updateBaseUrl(url: String) {
        _uiState.update { it.copy(baseUrl = url) }
        repository.setBaseUrl(url)
        telnetEnabledForBaseUrl = ""
        lastTelnetReport = null
        firstTelnetQci = null
        _uiState.update { it.copy(telnetStatus = "未连接") }
        stopTelnetMonitor()
        // 不要在 baseUrl 改变时自动重启 telnet monitor - 避免重复启用
        // 只有登录成功时才启动 telnet monitor
        viewModelScope.launch { settingsRepo.saveBaseUrl(url) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
        viewModelScope.launch { settingsRepo.saveCredentials(_uiState.value.username, password) }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
        viewModelScope.launch { settingsRepo.saveCredentials(username, _uiState.value.password) }
    }

    fun updateSpeedTestUrl(url: String) {
        _uiState.update { it.copy(speedTestUrl = url) }
        viewModelScope.launch { settingsRepo.saveTestUrls(url, _uiState.value.latencyTestUrl) }
    }

    fun updateLatencyTestUrl(url: String) {
        _uiState.update { it.copy(latencyTestUrl = url) }
        viewModelScope.launch { settingsRepo.saveTestUrls(_uiState.value.speedTestUrl, url) }
    }

    fun updateRememberPassword(enabled: Boolean) {
        _uiState.update { it.copy(rememberPassword = enabled) }
        viewModelScope.launch { settingsRepo.saveRememberPassword(enabled) }
    }

    fun updateAutoLogin(enabled: Boolean) {
        _uiState.update { it.copy(autoLogin = enabled) }
        viewModelScope.launch { settingsRepo.saveAutoLogin(enabled) }
    }

    fun updateAutoRelogin(enabled: Boolean) {
        _uiState.update { it.copy(autoRelogin = enabled) }
        viewModelScope.launch { settingsRepo.saveAutoRelogin(enabled) }
    }

    fun updateAutoRefresh(enabled: Boolean) {
        _uiState.update { it.copy(autoRefresh = enabled) }
        viewModelScope.launch { settingsRepo.saveAutoRefresh(enabled) }
        if (enabled) startAutoRefresh() else stopAutoRefresh()
    }

    fun updateRefreshInterval(seconds: Int) {
        _uiState.update { it.copy(refreshIntervalSec = seconds) }
        viewModelScope.launch { settingsRepo.saveRefreshInterval(seconds) }
        if (_uiState.value.autoRefresh) {
            stopAutoRefresh()
            startAutoRefresh()
        }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.pingDevice()
            result.fold(
                onSuccess = { online ->
                    val message = if (online) {
                        "Connected. Dashboard refreshed."
                    } else {
                        "Device did not respond. Check base URL and login."
                    }
                    _uiState.update {
                        it.copy(
                            deviceOnline = online,
                            connectionMessage = message,
                            isLoading = false
                        )
                    }
                    onResult(online, message)
                },
                onFailure = { e ->
                    val message = "Connection failed: ${e.message.orEmpty()}"
                    _uiState.update {
                        it.copy(
                            deviceOnline = false,
                            connectionMessage = message,
                            error = message,
                            isLoading = false
                        )
                    }
                    onResult(false, message)
                }
            )
        }
    }

    fun loginAndConnect(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val username = _uiState.value.username.ifBlank { "admin" }
            val password = _uiState.value.password
            if (password.isBlank()) {
                val msg = "Login failed: password is empty"
                _uiState.update {
                    it.copy(
                        deviceOnline = false,
                        isLoading = false,
                        error = msg,
                        connectionMessage = msg,
                        telnetStatus = "未连接"
                    )
                }
                stopTelnetMonitor()
                onResult(false, msg)
                return@launch
            }
            repository.setBaseUrl(_uiState.value.baseUrl)

            val loginResult = repository.loginCpe(username, password)
            loginResult.fold(
                onSuccess = {
                    // 普通登录成功后，立即进行开发者模式登录以获得 telnet 权限
                    val devModeResult = repository.loginDeveloperMode(password)
                    devModeResult.fold(
                        onSuccess = {
                            // 开发者模式登录成功 — Token 已自动存入 developerTokenQueue
                            android.util.Log.d("CpeVM", "developer mode login succeeded, devTokenQueue ready")
                            _uiState.update { it.copy(deviceOnline = true, isLoading = false, error = null) }
                            // 立即开启 Telnet（此时 developerTokenQueue 已有开发者 token）
                            startTelnetMonitor()
                            refreshAll()
                            onResult(true, "Login succeeded (developer mode). Data refreshed.")
                        },
                        onFailure = { devError ->
                            // 开发者模式登录失败，但普通登录成功，继续尝试启用 telnet
                            _uiState.update { it.copy(deviceOnline = true, isLoading = false, error = null) }
                            startTelnetMonitor()
                            refreshAll()
                            onResult(true, "Login succeeded (developer mode login failed, but continuing: ${devError.message}).")
                        }
                    )
                },
                onFailure = { e ->
                    val msg = "Login failed: ${e.message.orEmpty()}"
                    _uiState.update {
                        it.copy(
                            deviceOnline = false,
                            isLoading = false,
                            error = msg,
                            connectionMessage = msg,
                            telnetStatus = "未连接"
                        )
                    }
                    stopTelnetMonitor()
                    onResult(false, msg)
                }
            )
        }
    }

    fun sendAtCommand(command: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val normalized = command.trim()
            if (normalized.isBlank()) {
                onResult(false, "AT 命令不能为空")
                return@launch
            }

            val finalCommand = if (normalized.startsWith("AT", ignoreCase = true)) {
                normalized
            } else {
                "AT$normalized"
            }

            repository.setBaseUrl(_uiState.value.baseUrl)
            repository.sendAtCommand(_uiState.value.baseUrl, finalCommand).fold(
                onSuccess = { onResult(true, it) },
                onFailure = { onResult(false, it.message.orEmpty().ifBlank { "发送失败" }) }
            )
        }
    }

    fun refreshPccInfo() {
        viewModelScope.launch {
            runCatching {
                val (pcc, rf, link) = repository.getPccAndSignal()
                val snapshot = _uiState.value
                val pccMerged = applyTelnetOverlay(pcc, snapshot.pccInfo)
                val mergedRsrp = (snapshot.rfQuality.rsrpHistory + rf.rsrp).takeLast(360)
                val mergedSinr = (snapshot.rfQuality.sinrHistory + rf.sinr).takeLast(360)
                val sampleTime = System.currentTimeMillis()
                val nextTrends = appendStationTrendSample(
                    previous = snapshot.baseStationTrends,
                    pcc = pcc,
                    rf = rf,
                    cells = snapshot.aggregationCells,
                    timestampMs = sampleTime,
                    maxHistory = 360
                )
                _uiState.update {
                    it.copy(
                        pccInfo = pccMerged,
                        rfQuality = rf.copy(rsrpHistory = mergedRsrp, sinrHistory = mergedSinr),
                        linkStatus = link,
                        baseStationTrends = nextTrends,
                        speedInfo = it.speedInfo.copy(download = pcc.dlSpeed, upload = pcc.ulSpeed),
                        deviceOnline = true,
                        lastUpdated = lastUpdatedFormatter.format(Date()),
                        connectionMessage = "Dashboard refreshed",
                        error = null
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message, deviceOnline = false) }
            }
        }
    }

    fun refreshAggregation() {
        viewModelScope.launch {
            repository.getAggregationCells().fold(
                onSuccess = { cells ->
                    _uiState.update { state ->
                        val mergedCells = cells.ifEmpty { defaultAggregationCells() }
                        state.copy(
                            aggregationCells = mergedCells,
                            baseStationTrends = appendStationTrendSample(
                                previous = state.baseStationTrends,
                                pcc = state.pccInfo,
                                rf = state.rfQuality,
                                cells = mergedCells,
                                timestampMs = System.currentTimeMillis(),
                                maxHistory = 360
                            ),
                            error = null
                        )
                    }
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
    }

    fun refreshLockConfig() {
        viewModelScope.launch {
            repository.getLockConfig().fold(
                onSuccess = { config -> _uiState.update { it.copy(lockConfig = config, error = null) } },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
    }

    fun applyNetMode(mode: String, band: String = "", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.setLockMode(mode, band).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            lockConfig = state.lockConfig.copy(
                                networkMode = mode,
                                band5G = band.ifBlank { state.lockConfig.band5G }
                            ),
                            isLoading = false
                        )
                    }
                    onResult(true, "Network mode updated")
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    onResult(false, "Failed to set network mode: ${e.message.orEmpty()}")
                }
            )
        }
    }

    fun applyCellLock(freq: Long, pci: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.setLockCell(true, freq, pci).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            lockConfig = state.lockConfig.copy(lockCell = 1, lockFreq = freq, lockPci = pci),
                            isLoading = false
                        )
                    }
                    onResult(true, "Cell lock applied")
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    onResult(false, "Failed to lock cell: ${e.message.orEmpty()}")
                }
            )
        }
    }

    fun clearCellLock(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.setLockCell(false, 0, 0).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            lockConfig = state.lockConfig.copy(lockCell = 0, lockFreq = 0, lockPci = 0),
                            isLoading = false
                        )
                    }
                    onResult(true, "Cell lock cleared")
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    onResult(false, "Failed to clear lock: ${e.message.orEmpty()}")
                }
            )
        }
    }

    fun refreshSpeedInfo() {
        viewModelScope.launch {
            repository.getSpeedInfo().fold(
                onSuccess = { speed ->
                    _uiState.update { state ->
                        if (state.isLoading) {
                            state.copy(error = null)
                        } else {
                            state.copy(
                                speedInfo = mergeMonitoringSpeed(state.speedInfo, speed),
                                error = null
                            )
                        }
                    }
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
    }

    // ==================== 澶氱嚎绋嬪苟鍙戞祴閫燂紙浠?net.netart.cn锛?====================

    /** 娴嬮€熺嚎绋嬫暟锛岄粯璁?8 */
    private var speedThreadCount: Int = 8

    fun updateThreadCount(count: Int) {
        speedThreadCount = count.coerceIn(1, 64)
    }

    fun runSpeedTest() {
        // 闃叉閲嶅鍚姩
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val state = _uiState.value
            val latencyUrl = state.latencyTestUrl.ifBlank { "https://www.gstatic.com/generate_204" }
            val speedUrl = state.speedTestUrl.ifBlank { "https://speed.cloudflare.com/__down?bytes=10485760" }

            // 重置测速状态
            _uiState.update { s ->
                s.copy(speedInfo = s.speedInfo.copy(
                    download = 0.0, upload = 0.0, latency = 0.0, jitter = 0.0, packetLoss = 0.0,
                    latencySeries = emptyList(), downloadSeries = emptyList(), uploadSeries = emptyList(),
                    currentDownload = "", currentUpload = "", currentTotal = ""
                ))
            }

            // ---- 闃舵 1: 寤惰繜娴嬭瘯锛? 娆?HEAD锛?----
            val latencyResults = mutableListOf<Double>()
            val pingClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            repeat(5) {
                runCatching {
                    val req = okhttp3.Request.Builder().url(latencyUrl).head().build()
                    val start = System.nanoTime()
                    pingClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val elapsed = (System.nanoTime() - start) / 1_000_000.0
                            if (elapsed < 2000) latencyResults.add(elapsed)
                        }
                    }
                }
                delay(200)
            }

            val avgLatency = if (latencyResults.isNotEmpty()) latencyResults.average() else 0.0
            val jitter = if (latencyResults.size >= 2)
                latencyResults.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average()
            else 0.0
            val packetLoss = if (latencyResults.size < 5) (5 - latencyResults.size) * 20.0 else 0.0

            // ---- 闃舵 2: 澶氱嚎绋嬪苟鍙戜笅杞斤紙浠?net.netart.cn锛?----
            val startTime = System.nanoTime()
            val bytesAtomic = java.util.concurrent.atomic.AtomicLong(0)
            val running = java.util.concurrent.atomic.AtomicBoolean(true)
            val maxBytes = 100_000_000L // 100MB 鎬婚噺涓婇檺
            val speedHistory = mutableListOf<Double>() // 姣忕閲囨牱

            // 启动 N 个下载线程
            val downloadJobs = (0 until speedThreadCount).map { threadId ->
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    while (running.get() && bytesAtomic.get() < maxBytes) {
                        runCatching {
                            val req = okhttp3.Request.Builder().url(speedUrl).build()
                            val client = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                            client.newCall(req).execute().use { resp ->
                                val body = resp.body ?: return@runCatching
                                val stream = body.source()
                                val buf = okio.Buffer()
                                while (running.get() && bytesAtomic.get() < maxBytes) {
                                    val read = stream.read(buf, 8192)
                                    if (read == -1L) break
                                    bytesAtomic.addAndGet(read)
                                }
                            }
                        }
                    }
                }
            }

            // 姣忕閲囨牱閫熷害
            val sampleJob = launch {
                var lastBytes = 0L
                while (running.get()) {
                    delay(1000)
                    val currentBytes = bytesAtomic.get()
                    val delta = currentBytes - lastBytes
                    lastBytes = currentBytes
                    val instantSpeed = delta * 8.0 / 1_000_000.0 // Mbps
                    speedHistory.add(instantSpeed)

                    // 瀹炴椂鏇存柊 UI
                    val elapsedSec = (System.nanoTime() - startTime) / 1_000_000_000.0
                    val avgSpeed = if (elapsedSec > 0) (currentBytes * 8.0 / elapsedSec) / 1_000_000.0 else 0.0
                    val totalMB = currentBytes / 1_000_000.0

                    _uiState.update { s ->
                        val maxH = 60
                        s.copy(
                            speedInfo = s.speedInfo.copy(
                                download = avgSpeed,
                                latency = avgLatency,
                                jitter = jitter,
                                packetLoss = packetLoss,
                                latencySeries = (s.speedInfo.latencySeries + avgLatency).takeLast(maxH),
                                downloadSeries = (s.speedInfo.downloadSeries + instantSpeed).takeLast(maxH),
                                uploadSeries = (s.speedInfo.uploadSeries + instantSpeed * 0.3).takeLast(maxH),
                                currentDownload = "%.1f MB".format(totalMB)
                            )
                        )
                    }

                    // 杈惧埌涓婇檺鑷姩鍋滄
                    if (currentBytes >= maxBytes) {
                        running.set(false)
                    }
                }
            }

            // 最多跑 15 秒
            delay(15_000)
            running.set(false)

            // 等待所有下载线程结束
            downloadJobs.forEach { it.join() }
            sampleJob.join()

            val totalBytes = bytesAtomic.get()
            val totalSec = (System.nanoTime() - startTime) / 1_000_000_000.0
            val finalSpeed = if (totalSec > 0) (totalBytes * 8.0 / totalSec) / 1_000_000.0 else 0.0

            _uiState.update { s ->
                s.copy(
                    speedInfo = s.speedInfo.copy(
                        download = finalSpeed,
                        latency = avgLatency,
                        jitter = jitter,
                        packetLoss = packetLoss,
                        currentDownload = "%.1f MB".format(totalBytes / 1_000_000.0),
                        currentUpload = "-",
                        currentTotal = "%.1f MB".format(totalBytes / 1_000_000.0)
                    ),
                    isLoading = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 获取移动数据开关状态 */
    fun getMobileDataSwitch(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.getMobileDataSwitch().fold(
                onSuccess = { onResult(it) },
                onFailure = { onResult(true) } // 默认开启
            )
        }
    }

    /** 设置移动数据开关 */
    fun setMobileDataSwitch(enable: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.setMobileDataSwitch(enable).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    onResult(true, "成功")
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    onResult(false, e.message ?: "操作失败")
                }
            )
        }
    }

    private suspend fun refreshAll() {
        val cycleTimestamp = System.currentTimeMillis()
        val connected = runCatching {
            val (pcc, rf, link) = repository.getPccAndSignal()
            val (dlAmbr, ulAmbr) = runCatching { repository.getDeviceAmbr() }.getOrDefault(Pair("--", "--"))
            val (countryCode, _) = runCatching { repository.getBasicDeviceInfo() }.getOrDefault(Pair("", ""))
            val previousState = _uiState.value
            val pccWithAmbr = applyTelnetOverlay(
                pcc.copy(dlAmbr = dlAmbr, ulAmbr = ulAmbr, countryCode = countryCode),
                previousState.pccInfo
            )
            val maxH = 360
            val newRsrp = (previousState.rfQuality.rsrpHistory + rf.rsrp).takeLast(maxH)
            val newSinr = (previousState.rfQuality.sinrHistory + rf.sinr).takeLast(maxH)
            val nextTrends = appendStationTrendSample(
                previous = previousState.baseStationTrends, pcc = pcc, rf = rf,
                cells = previousState.aggregationCells, timestampMs = cycleTimestamp, maxHistory = maxH
            )
            val events = mutableListOf<String>()
            val prev = previousState.pccInfo
            if (prev.band.isNotBlank() && pcc.band.isNotBlank() && prev.band != pcc.band) {
                events.add("频段切换: ${prev.band} → ${pcc.band}")
            }
            if (prev.pci.isNotBlank() && pcc.pci.isNotBlank() && prev.pci != pcc.pci) {
                events.add("PCI 变更: ${prev.pci} → ${pcc.pci}")
            }
            val prevRsrp = previousState.rfQuality.rsrpHistory.lastOrNull() ?: rf.rsrp
            if (kotlin.math.abs(rf.rsrp - prevRsrp) > 10.0 && rf.rsrp != prevRsrp) {
                events.add("${if (rf.rsrp > prevRsrp) "RSRP 上升" else "RSRP 骤降"}: ${prevRsrp.toInt()} → ${rf.rsrp.toInt()} dBm")
            }
            _uiState.update {
                it.copy(
                    pccInfo = pccWithAmbr, rfQuality = rf.copy(rsrpHistory = newRsrp, sinrHistory = newSinr, eventLog = events),
                    linkStatus = link, baseStationTrends = nextTrends,
                    speedInfo = it.speedInfo.copy(download = pcc.dlSpeed, upload = pcc.ulSpeed),
                    deviceOnline = true, lastUpdated = lastUpdatedFormatter.format(Date()),
                    connectionMessage = "Dashboard refreshed", error = null
                )
            }
            true
        }.recoverCatching { e ->
            if (shouldAutoRelogin(e)) {
                val username = _uiState.value.username.ifBlank { "admin" }
                val password = _uiState.value.password
                if (password.isNotBlank()) {
                    // 重新登录（普通 + 开发者模式），然后重启 Telnet
                    repository.loginCpe(username, password).getOrThrow()
                    runCatching { repository.loginDeveloperMode(password) }
                        .onSuccess { android.util.Log.d("CpeVM", "Re-login dev mode success") }
                        .onFailure { android.util.Log.w("CpeVM", "Re-login dev mode failed: ${it.message}") }
                    startTelnetMonitor()
                    val (pcc, rf, link) = repository.getPccAndSignal()
                    val (dlA, ulA) = runCatching { repository.getDeviceAmbr() }.getOrDefault(Pair("--", "--"))
                    val snapshot = _uiState.value
                    val reconnectedPcc = applyTelnetOverlay(
                        pcc.copy(dlAmbr = dlA, ulAmbr = ulA),
                        snapshot.pccInfo
                    )
                    val mergedRsrp = (snapshot.rfQuality.rsrpHistory + rf.rsrp).takeLast(360)
                    val mergedSinr = (snapshot.rfQuality.sinrHistory + rf.sinr).takeLast(360)
                    val nextTrends = appendStationTrendSample(
                        previous = snapshot.baseStationTrends,
                        pcc = pcc,
                        rf = rf,
                        cells = snapshot.aggregationCells,
                        timestampMs = cycleTimestamp,
                        maxHistory = 360
                    )
                    _uiState.update {
                        it.copy(
                            pccInfo = reconnectedPcc,
                            rfQuality = rf.copy(rsrpHistory = mergedRsrp, sinrHistory = mergedSinr),
                            linkStatus = link,
                            baseStationTrends = nextTrends,
                            speedInfo = it.speedInfo.copy(download = pcc.dlSpeed, upload = pcc.ulSpeed),
                            deviceOnline = true,
                            lastUpdated = lastUpdatedFormatter.format(Date()),
                            connectionMessage = "Re-login succeeded, data refreshed",
                            error = null
                        )
                    }
                    true
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }.getOrElse { e ->
            _uiState.update {
                it.copy(
                    deviceOnline = false,
                    connectionMessage = "Refresh failed: ${e.message ?: "Unknown error"}",
                    error = e.message,
                    aggregationCells = emptyList(),
                    telnetStatus = "未连接"
                )
            }
            false
        }

        if (connected) {
            // 注意：不要在 refreshAll 中调用 startTelnetMonitor
            // 只在登录成功时调用一次，避免重复启动
            android.util.Log.d("CpeVM", "refreshAll connected, fetching aggregation cells...")
            repository.getAggregationCells().onSuccess { cells ->
                android.util.Log.d("CpeVM", "aggregation cells count: ${cells.size}")
                cells.forEachIndexed { i, c -> android.util.Log.d("CpeVM", "  cell[$i]: $c") }
                _uiState.update { state ->
                    state.copy(
                        aggregationCells = cells,
                        baseStationTrends = appendStationTrendSample(
                            previous = state.baseStationTrends,
                            pcc = state.pccInfo,
                            rf = state.rfQuality,
                            cells = cells,
                            timestampMs = cycleTimestamp,
                            maxHistory = 360
                        )
                    )
                }
            }.onFailure { e ->
                android.util.Log.e("CpeVM", "aggregation cells failed: ${e.message}")
            }
            repository.getLockConfig().onSuccess { config ->
                _uiState.update { it.copy(lockConfig = config) }
            }
            repository.getSpeedInfo().onSuccess { speed ->
                _uiState.update { state ->
                    if (state.isLoading) {
                        state
                    } else {
                        state.copy(speedInfo = mergeMonitoringSpeed(state.speedInfo, speed))
                    }
                }
            }
        } else {
            stopTelnetMonitor()
            android.util.Log.w("CpeVM", "refreshAll NOT connected, skipping aggregation")
        }
    }

    private data class StationSnapshot(
        val key: String,
        val displayName: String,
        val type: String,
        val band: String,
        val arfcn: String,
        val pci: String,
        val bandwidth: String,
        val active: Boolean,
        val rsrp: Double,
        val rsrq: Double,
        val sinr: Double
    )

    private fun appendStationTrendSample(
        previous: List<BaseStationTrend>,
        pcc: PccInfo,
        rf: RfQuality,
        cells: List<AggregationCell>,
        timestampMs: Long,
        maxHistory: Int
    ): List<BaseStationTrend> {
        val snapshots = buildConnectedStationSnapshots(pcc, rf, cells)
        if (snapshots.isEmpty()) return previous

        val previousMap = previous.associateBy { it.key }
        return snapshots.map { snap ->
            val previousSamples = previousMap[snap.key]?.samples.orEmpty()
            val mergedSamples = appendSample(
                existing = previousSamples,
                sample = StationTrendSample(
                    timestampMs = timestampMs,
                    rsrp = snap.rsrp,
                    rsrq = snap.rsrq,
                    sinr = snap.sinr
                ),
                maxHistory = maxHistory
            )

            BaseStationTrend(
                key = snap.key,
                displayName = snap.displayName,
                type = snap.type,
                band = snap.band,
                arfcn = snap.arfcn,
                pci = snap.pci,
                bandwidth = snap.bandwidth,
                active = snap.active,
                latestRsrp = snap.rsrp,
                latestRsrq = snap.rsrq,
                latestSinr = snap.sinr,
                samples = mergedSamples
            )
        }
    }

    private fun appendSample(
        existing: List<StationTrendSample>,
        sample: StationTrendSample,
        maxHistory: Int
    ): List<StationTrendSample> {
        if (!sample.rsrp.isFinite()) return existing.takeLast(maxHistory)
        val capped = if (existing.lastOrNull()?.timestampMs == sample.timestampMs) {
            existing.dropLast(1)
        } else {
            existing
        }
        return (capped + sample).takeLast(maxHistory)
    }

    private fun buildConnectedStationSnapshots(
        pcc: PccInfo,
        rf: RfQuality,
        cells: List<AggregationCell>
    ): List<StationSnapshot> {
        val primaryBand = pcc.band.ifBlank { "--" }
        val primaryPci = pcc.pci.ifBlank { "--" }
        val primaryArfcn = pcc.ssArfcn.ifBlank { "--" }
        val primaryKey = stationKey("P", primaryBand, primaryArfcn, primaryPci)
        val primarySnapshot = StationSnapshot(
            key = primaryKey,
            displayName = "PCC / $primaryBand / PCI $primaryPci",
            type = "P",
            band = primaryBand,
            arfcn = primaryArfcn,
            pci = primaryPci,
            bandwidth = pcc.bandwidth.ifBlank { "--" },
            active = true,
            rsrp = rf.rsrp,
            rsrq = rf.rsrq,
            sinr = rf.sinr
        )

        val connectedCells = cells
            .filter { cell ->
                cell.active && (
                    cell.type.equals("P", ignoreCase = true) ||
                        cell.type.startsWith("S", ignoreCase = true)
                    )
            }
            .sortedWith(
                compareBy<AggregationCell>(
                    { stationTypeOrder(it.type) },
                    { stationTypeIndex(it.type) },
                    { it.pci }
                )
            )

        val secondarySnapshots = connectedCells.mapNotNull { cell ->
            val type = cell.type.ifBlank { "S" }.uppercase(Locale.ROOT)
            val band = cell.band.ifBlank { "--" }
            val pci = cell.pci.ifBlank { "--" }
            val arfcn = cell.arfcn.ifBlank { "--" }
            val key = stationKey(type, band, arfcn, pci)
            if (key == primaryKey) return@mapNotNull null

            val rsrpValue = parseSignalMetric(cell.rsrp)
            val rsrqValue = parseSignalMetric(cell.rsrq)
            val sinrValue = parseSignalMetric(cell.sinr)
            StationSnapshot(
                key = key,
                displayName = "$type / $band / PCI $pci",
                type = type,
                band = band,
                arfcn = arfcn,
                pci = pci,
                bandwidth = cell.bandwidth.ifBlank { "--" },
                active = true,
                rsrp = rsrpValue,
                rsrq = rsrqValue,
                sinr = sinrValue
            )
        }

        return listOf(primarySnapshot) + secondarySnapshots
    }

    private fun stationKey(type: String, band: String, arfcn: String, pci: String): String {
        return listOf(
            type.uppercase(Locale.ROOT),
            band.trim().ifBlank { "-" },
            arfcn.trim().ifBlank { "-" },
            pci.trim().ifBlank { "-" }
        ).joinToString("|")
    }

    private fun parseSignalMetric(raw: String): Double {
        return raw
            .replace("dBm", "", ignoreCase = true)
            .replace("dB", "", ignoreCase = true)
            .replace(" ", "")
            .trim()
            .toDoubleOrNull()
            ?: Double.NaN
    }

    private fun stationTypeOrder(type: String): Int {
        val upper = type.uppercase(Locale.ROOT)
        return when {
            upper == "P" -> 0
            upper.startsWith("S") -> 1
            else -> 2
        }
    }

    private fun stationTypeIndex(type: String): Int {
        val upper = type.uppercase(Locale.ROOT)
        if (!upper.startsWith("S")) return Int.MAX_VALUE
        return upper.removePrefix("S").toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun mergeMonitoringSpeed(previous: SpeedInfo, latest: SpeedInfo): SpeedInfo {
        val maxHistory = 120
        val hasFreshLatency = latest.latency > 0
        val latencyValue = if (hasFreshLatency) latest.latency else previous.latency
        val jitterValue = when {
            latest.jitter > 0 -> latest.jitter
            hasFreshLatency -> 0.0
            else -> previous.jitter
        }
        val packetLossValue = if (hasFreshLatency) {
            latest.packetLoss.coerceIn(0.0, 100.0)
        } else {
            previous.packetLoss
        }
        val latencySeries = if (latencyValue > 0) {
            (previous.latencySeries + latencyValue).takeLast(maxHistory)
        } else {
            previous.latencySeries
        }

        return latest.copy(
            latency = latencyValue,
            jitter = jitterValue,
            packetLoss = packetLossValue,
            latencySeries = latencySeries,
            downloadSeries = (previous.downloadSeries + latest.download).takeLast(maxHistory),
            uploadSeries = (previous.uploadSeries + latest.upload).takeLast(maxHistory)
        )
    }

    private fun startTelnetMonitor() {
        if (telnetMonitorJob?.isActive == true) return
        if (telnetEnableInProgress) {
            android.util.Log.w("CpeVM", "telnet monitor already in progress, skipping...")
            return
        }

        telnetMonitorJob = viewModelScope.launch {
            val baseUrl = _uiState.value.baseUrl
            _uiState.update { it.copy(telnetStatus = "连接中...") }
            repository.setBaseUrl(baseUrl)

            // 外层循环：Telnet 断线自动重连
            while (isActive) {
                telnetEnableInProgress = true
                try {
                    // 尝试启用 telnet 端口
                    android.util.Log.d("CpeVM", "telnet: 尝试启用端口...")
                    val enableResult = runCatching {
                        HuaweiCpeClient.enableTelnetDebugPort().getOrNull() ?: false
                    }

                    if (enableResult.isSuccess && enableResult.getOrNull() == true) {
                        android.util.Log.d("CpeVM", "telnet: ✓ 端口启用成功")
                        delay(800)
                    } else {
                        android.util.Log.w("CpeVM", "telnet: 启用未成功（可能无开发者权限），继续尝试连接...")
                    }

                    _uiState.update { it.copy(telnetStatus = "连接中") }
                    android.util.Log.d("CpeVM", "telnet: 连接到 TCP 20249...")

                    // 建立 Telnet 连接持续监听（阻塞直到断开）
                    val streamResult = runCatching {
                        repository.collectTelnetAmbrQci(
                            baseUrl = baseUrl,
                            onConnected = {
                                android.util.Log.d("CpeVM", "telnet: ✓ TCP 连接成功，等待数据...")
                                _uiState.update { state -> state.copy(telnetStatus = "已连接，等待DSAMBR") }
                            }
                        ) { report ->
                            val incomingQci = report.qci.takeIf { it.isNotBlank() && it != "--" }
                            if (firstTelnetQci.isNullOrBlank() && incomingQci != null) {
                                firstTelnetQci = incomingQci
                            }
                            val fixedQci = firstTelnetQci
                                ?: incomingQci
                                ?: _uiState.value.pccInfo.qci

                            lastTelnetReport = report.copy(qci = fixedQci)
                            _uiState.update { state ->
                                state.copy(
                                    pccInfo = state.pccInfo.copy(
                                        dlAmbr = report.dlAmbr,
                                        ulAmbr = report.ulAmbr,
                                        qci = fixedQci
                                    ),
                                    telnetStatus = "已收到DSAMBR"
                                )
                            }
                        }
                    }

                    streamResult.onFailure { e ->
                        android.util.Log.e("CpeVM", "telnet: TCP 连接断开 - ${e.message}")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("CpeVM", "telnet: 异常 - ${e.message}")
                } finally {
                    telnetEnableInProgress = false
                }

                // 断开后立即重连
                if (isActive) {
                    _uiState.update { it.copy(telnetStatus = "断开，立即重连...") }
                }
            }
        }
    }

    private fun stopTelnetMonitor() {
        telnetMonitorJob?.cancel()
        telnetMonitorJob = null
        firstTelnetQci = null
        _uiState.update { it.copy(telnetStatus = "未连接") }
    }

    private fun applyTelnetOverlay(pcc: PccInfo, fallback: PccInfo): PccInfo {
        val telnet = lastTelnetReport
        return pcc.copy(
            dlAmbr = telnet?.dlAmbr?.takeIf { it.isNotBlank() && it != "--" }
                ?: pcc.dlAmbr.takeIf { it.isNotBlank() && it != "--" }
                ?: fallback.dlAmbr,
            ulAmbr = telnet?.ulAmbr?.takeIf { it.isNotBlank() && it != "--" }
                ?: pcc.ulAmbr.takeIf { it.isNotBlank() && it != "--" }
                ?: fallback.ulAmbr,
            qci = firstTelnetQci
                ?: telnet?.qci?.takeIf { it.isNotBlank() && it != "--" }
                ?: fallback.qci
        )
    }

    override fun onCleared() {
        stopAutoRefresh()
        stopTelnetMonitor()
        super.onCleared()
    }

    // ==================== 系统操作日志 ====================

    /** 加载系统操作日志（从 /api/log/loginfo） */
    fun loadSystemLog() {
        viewModelScope.launch {
            _uiState.update { it.copy(systemLogLoading = true) }
            repository.getSystemLog().fold(
                onSuccess = { entries ->
                    _uiState.update {
                        it.copy(
                            systemLogEntries = entries,
                            systemLogLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            systemLogLoading = false,
                            error = e.message
                        )
                    }
                }
            )
        }
    }

    /** 日志类型筛选 */
    fun setSystemLogTypeFilter(type: String) {
        _uiState.update { it.copy(systemLogTypeFilter = type) }
    }

    /** 日志级别筛选 */
    fun setSystemLogLevelFilter(level: String) {
        _uiState.update { it.copy(systemLogLevelFilter = level) }
    }

    /** 获取筛选后的日志列表 */
    fun getFilteredLogEntries(): List<SystemLogEntry> {
        val state = _uiState.value
        return state.systemLogEntries.filter { entry ->
            (state.systemLogTypeFilter == "全部" || entry.type == state.systemLogTypeFilter) &&
            (state.systemLogLevelFilter == "全部" || entry.level == state.systemLogLevelFilter)
        }
    }

    private fun shouldAutoRelogin(error: Throwable): Boolean {
        val msg = error.message.orEmpty()
        // 只应在 session 过期时自动重登，密码错误/锁定时不应重试
        return _uiState.value.autoRelogin &&
            (msg.contains("code=100003") || msg.contains("100003")) &&
            !msg.contains("code=108006") &&
            !msg.contains("code=108007") &&
            !msg.contains("code=125003")
    }
}
