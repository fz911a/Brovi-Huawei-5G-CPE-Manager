package com.cpemanager.data.repository

import com.cpemanager.data.model.AggregationCell
import com.cpemanager.data.model.LinkStatus
import com.cpemanager.data.model.LockConfig
import com.cpemanager.data.model.PccInfo
import com.cpemanager.data.model.RfQuality
import com.cpemanager.data.model.SpeedInfo
import com.cpemanager.data.model.SystemLogEntry
import com.cpemanager.data.model.TelnetAmbrQci
import com.cpemanager.network.HuaweiCpeClient
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class CpeRepository {

    fun setBaseUrl(url: String) = HuaweiCpeClient.setBaseUrl(url)
    fun getBaseUrl(): String = HuaweiCpeClient.getBaseUrl()

    suspend fun getPccAndSignal(): Triple<PccInfo, RfQuality, LinkStatus> {
        val sig = HuaweiCpeClient.getSignal().getOrElse { throw it }
        val sta = HuaweiCpeClient.getNetworkStatus().getOrElse { throw it }
        val traf = HuaweiCpeClient.getTrafficStats().getOrElse { throw it }
        val plmn = HuaweiCpeClient.getCurrentPlmn().getOrElse { throw it }

        val band = sig["bandInfo"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["band"]?.trim()?.let { Regex("""\(([^)]+)\)""").find(it)?.groupValues?.get(1) }
            ?: ""

        val rsrp = parseDb(sig["nrrsrp"]) ?: parseDb(sig["rsrp"])
        val sinr = parseDb(sig["nrsinr"]) ?: parseDb(sig["sinr"])
        val rsrq = parseDb(sig["nrrsrq"]) ?: parseDb(sig["rsrq"])
        val rssi = parseDb(sig["nrrssi"]) ?: parseDb(sig["rssi"])
            ?: extractPrimaryRssi(HuaweiCpeClient.getSecCellInfo().getOrDefault(emptyMap()))
        val cqi = sig["nrcqi0"]?.toIntOrNull() ?: sig["cqi"]?.toIntOrNull()

        val txPower = sig["nrtxpower"] ?: sig["txpower"] ?: ""
        val pusch = Regex("""PPusch:(\d+)""").find(txPower)?.groupValues?.get(1)?.toIntOrNull()
        val pucch = Regex("""PPucch:(-?\d+)""").find(txPower)?.groupValues?.get(1)?.toIntOrNull()
        val srs = Regex("""PSrs:(\d+)""").find(txPower)?.groupValues?.get(1)?.toIntOrNull()
        val prach = Regex("""PPrach:(-?\d+)""").find(txPower)?.groupValues?.get(1)?.toIntOrNull()

        val dlMcs = Regex("""NRmcsDownCarrier1Code0:(\d+)""").find(sig["nrdlmcs"] ?: "")?.groupValues?.get(1)?.toIntOrNull()
            ?: sig["dl_mcs"]?.trim()?.toIntOrNull()
            ?: sig["dlmcs"]?.trim()?.toIntOrNull()
        val ulMcs = Regex("""NRmcsUpCarrier1:(\d+)""").find(sig["nrulmcs"] ?: "")?.groupValues?.get(1)?.toIntOrNull()
            ?: sig["ul_mcs"]?.trim()?.toIntOrNull()
            ?: sig["ulmcs"]?.trim()?.toIntOrNull()
        val rank = sig["nrrank"]?.toIntOrNull() ?: sig["rank"]?.toIntOrNull()

        val ssArfcn = sig["nrearfcn"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["earfcn"]?.trim()?.takeIf { it.isNotEmpty() }?.let { cleanEarfcn(it) }
            ?: sig["arfcn"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["frequency"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["dlfrequency"]?.trim()?.takeIf { it.isNotEmpty() }
        val dlMhz = parseFrequencyMhz(sig["nrdlfreq"]).ifBlank { parseFrequencyMhz(sig["dlfrequency"]) }
        val ulMhz = parseFrequencyMhz(sig["nrulfreq"]).ifBlank { parseFrequencyMhz(sig["ulfrequency"]) }

        val bandwidth = sig["nrbandwidth"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["nrdlbandwidth"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["dlbandwidth"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
        val ulBandwidth = sig["ulbandwidth"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["nrulbandwidth"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""

        val dlRate = traf["CurrentDownloadRate"]?.toDoubleOrNull()?.let { it * 8 / 1_000_000 }
        val ulRate = traf["CurrentUploadRate"]?.toDoubleOrNull()?.let { it * 8 / 1_000_000 }

        val technology = networkType(sta["CurrentNetworkTypeEx"], sig)
        android.util.Log.d("CpeRepo", "=== technology='$technology', typeEx='${sta["CurrentNetworkTypeEx"]}'")
        val carrier = buildCarrier(plmn)
        val plmnRat = plmnRatLabel(plmn["Rat"], technology)
        val plmnState = plmnStateLabel(plmn["State"])
        android.util.Log.d("CpeRepo", "=== PLMN raw: $plmn, carrier='$carrier', plmnRat='$plmnRat', plmnState='$plmnState'")
        val signalMode = sig["mode"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: technology

        val pcc = PccInfo(
            technology = technology,
            carrier = carrier,
            plmnRat = plmnRat,
            plmnState = plmnState,
            band = band,
            pci = sig["pci"]?.trim() ?: "",
            ssArfcn = ssArfcn ?: "",
            bandwidth = bandwidth,
            ulBandwidth = ulBandwidth,
            signalMode = signalMode,
            rscp = safeText(sig["rscp"]),
            ecio = safeText(sig["ecio"]),
            dlFrequency = dlMhz,
            ulFrequency = ulMhz,
            status = connectionStatus(sta["ConnectionStatus"]),
            caStatus = caStatus(sig["sc"]),
            dlSpeed = dlRate ?: 0.0,
            ulSpeed = ulRate ?: 0.0,
            gnbCell = sig["cell_id"]?.trim()?.takeIf { it.isNotEmpty() }?.let { formatGnbCell(it) } ?: "",
            tac = sta["tac"]?.trim()?.takeIf { it.isNotEmpty() } ?: "",
            dlMhz = dlMhz,
            ulMhz = ulMhz,
            speedLimitStatus = speedLimitLabel(sta["speedLimitStatus"])
        )

        val rf = RfQuality(
            rsrp = rsrp ?: 0.0,
            rsrq = rsrq ?: 0.0,
            sinr = sinr ?: 0.0,
            rssi = rssi ?: 0.0,
            cqi = cqi ?: 0,
            pusch = pusch ?: 0,
            pucch = pucch ?: 0,
            srs = srs ?: 0,
            prach = prach ?: 0
        )

        val link = LinkStatus(
            dlMcs = dlMcs ?: 0,
            dlRank = rank ?: 0,
            ulMcs = ulMcs ?: 0,
            ulRank = 1,
            dlModulation = mcsToModulation(dlMcs),
            ulModulation = mcsToModulation(ulMcs),
            dlThroughput = dlRate ?: 0.0,
            ulThroughput = ulRate ?: 0.0
        )

        return Triple(pcc, rf, link)
    }

    suspend fun getAggregationCells(): Result<List<AggregationCell>> {
        val sig = HuaweiCpeClient.getSignal().getOrElse { return Result.failure(it) }
        if (sig.isEmpty()) return Result.success(emptyList())
        val secMap = HuaweiCpeClient.getSecCellInfo().getOrDefault(emptyMap())
        val nbrMap = HuaweiCpeClient.getNbrCellInfo().getOrDefault(emptyMap())
        val cellInfoMap = HuaweiCpeClient.getCellInfo().getOrDefault(emptyMap())

        android.util.Log.d("CpeRepo", "=== signal raw keys: ${sig.keys}")
        android.util.Log.d("CpeRepo", "=== secCellInfo raw: $secMap")
        android.util.Log.d("CpeRepo", "=== nbrCellInfo raw: $nbrMap")
        android.util.Log.d("CpeRepo", "=== cellInfo raw: $cellInfoMap")

        val band = sig["bandInfo"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["band"]?.trim()?.let { Regex("""\(([^)]+)\)""").find(it)?.groupValues?.get(1) }
            ?: ""
        val arfcn = sig["nrearfcn"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["earfcn"]?.trim()?.takeIf { it.isNotEmpty() }?.let { cleanEarfcn(it) }
            ?: sig["arfcn"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["frequency"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
        val pci = sig["pci"]?.trim() ?: ""
        val bw = sig["nrbandwidth"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: sig["nrdlbandwidth"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
        val rsrp = sig["nrrsrp"]?.trim()?.takeIf { it.isNotEmpty() } ?: sig["rsrp"]?.trim().orEmpty()
        val rsrq = sig["nrrsrq"]?.trim()?.takeIf { it.isNotEmpty() } ?: sig["rsrq"]?.trim().orEmpty()
        val sinr = sig["nrsinr"]?.trim()?.takeIf { it.isNotEmpty() } ?: sig["sinr"]?.trim().orEmpty()

        val activeSecondaryCount = sig["sc"]?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0

        val bands = parseBandSeries(sig["bandInfo"] ?: sig["band"])
        val arfcns = parseValueSeries(sig["nrearfcn"]?.takeIf { it.isNotBlank() } ?: sig["earfcn"])
        val pcis = parseValueSeries(sig["pci"])
        val bws = parseBandwidthSeries(sig["nrbandwidth"] ?: sig["nrdlbandwidth"] ?: sig["dlbandwidth"])
        val rsrps = parseMetricSeries(sig["nrrsrp"]?.takeIf { it.isNotBlank() } ?: sig["rsrp"])
        val rsrqs = parseMetricSeries(sig["nrrsrq"]?.takeIf { it.isNotBlank() } ?: sig["rsrq"])
        val sinrs = parseMetricSeries(sig["nrsinr"]?.takeIf { it.isNotBlank() } ?: sig["sinr"])

        val inferredCount = listOf(
            bands.size,
            arfcns.size,
            pcis.size,
            bws.size,
            rsrps.size,
            rsrqs.size,
            sinrs.size,
            activeSecondaryCount + 1
        ).maxOrNull()?.coerceIn(1, 8) ?: 1

        val cells = (0 until inferredCount).map { idx ->
            val primary = idx == 0
            val hasSignalMetric = listOf(
                seriesAt(rsrps, idx),
                seriesAt(rsrqs, idx),
                seriesAt(sinrs, idx)
            ).any { it.isNotBlank() }

            AggregationCell(
                type = if (primary) "P" else "S$idx",
                band = seriesAt(bands, idx).ifBlank { if (primary) band else band.ifBlank { "-" } },
                arfcn = seriesAt(arfcns, idx).ifBlank { if (primary) arfcn else "-" },
                pci = seriesAt(pcis, idx).ifBlank { if (primary) pci else "-" },
                bandwidth = seriesAt(bws, idx).ifBlank { if (primary) bw else "-" },
                rsrp = seriesAt(rsrps, idx).ifBlank { if (primary) rsrp else "-" },
                rsrq = seriesAt(rsrqs, idx).ifBlank { if (primary) rsrq else "-" },
                sinr = seriesAt(sinrs, idx).ifBlank { if (primary) sinr else "-" },
                active = primary || idx <= activeSecondaryCount || hasSignalMetric
            )
        }

        val secCells = parseCellRowsFromMap(
            source = secMap,
            typePrefix = "S",
            active = true,
            allowSingleRow = true
        )
        val nbrCells = buildList {
            addAll(
                parseCellRowsFromMap(
                    source = nbrMap,
                    typePrefix = "N",
                    active = false,
                    allowSingleRow = true
                )
            )
            addAll(
                parseCellRowsFromMap(
                    source = cellInfoMap,
                    typePrefix = "N",
                    active = false,
                    allowSingleRow = true
                )
            )
        }

        val merged = LinkedHashMap<String, AggregationCell>()
        fun putCell(cell: AggregationCell) {
            val key = listOf(
                cell.type,
                cell.band,
                cell.arfcn,
                cell.pci,
                cell.rsrp,
                cell.rsrq,
                cell.sinr
            ).joinToString("|")
            if (!merged.containsKey(key)) merged[key] = cell
        }

        // 优先主载波 + 信号页推断，再补充 seccellinfo/nbrcellinfo/cell-info。
        cells.forEach { putCell(it) }
        // nrseccell_list often includes the primary cell itself (same PCI); skip it.
        val primaryPci = cells.firstOrNull()?.pci?.trim().orEmpty()
        secCells.filter { it.pci.trim() != primaryPci }.forEach { putCell(it) }
        nbrCells.forEach { putCell(it) }

        val mergedList = merged.values.toMutableList()
        val hasNeighbor = mergedList.any { it.type.startsWith("N", ignoreCase = true) }
        if (!hasNeighbor) {
            val primary = mergedList.firstOrNull { it.type == "P" } ?: cells.firstOrNull()
            if (primary != null) {
                val metricsList = extractPciMetricsList(
                    maps = listOf(secMap, nbrMap, cellInfoMap),
                    excludePci = primary.pci
                )
                mergedList.addAll(buildFallbackNeighborCells(primary, metricsList))
            }
        }

        return Result.success(mergedList)
    }

    suspend fun getLockConfig(): Result<LockConfig> {
        val netMode = HuaweiCpeClient.getNetMode().getOrElse { return Result.failure(it) }
        return Result.success(
            LockConfig(
                networkMode = netMode["NetworkMode"] ?: "",
                band5G = netMode["NetworkBand5G"]?.trim() ?: "",
                band4G = netMode["LTEBand"]?.trim() ?: ""
            )
        )
    }

    suspend fun setLockMode(networkMode: String, band: String = "", lteBand: String = ""): Result<Boolean> {
        return HuaweiCpeClient.setNetMode(networkMode, band, lteBand).map { true }
    }

    suspend fun setLockCell(lock: Boolean, freq: Long, pci: Int): Result<Boolean> {
        return HuaweiCpeClient.setLockCell(if (lock) 1 else 0, freq, pci).map { true }
    }

    suspend fun getSpeedInfo(): Result<SpeedInfo> {
        val traf = HuaweiCpeClient.getTrafficStats().getOrElse { return Result.failure(it) }
        if (traf.isEmpty()) return Result.success(SpeedInfo())
        val pingRaw = HuaweiCpeClient.getDiagnosePingResult().getOrDefault(emptyMap())
        val pingMetrics = parsePingMetrics(pingRaw)

        val dl = traf["CurrentDownloadRate"]?.toDoubleOrNull()?.let { it * 8 / 1_000_000 } ?: 0.0
        val ul = traf["CurrentUploadRate"]?.toDoubleOrNull()?.let { it * 8 / 1_000_000 } ?: 0.0
        val currentDownloadBytes = parseBytes(traf["CurrentDownload"]) ?: 0L
        val currentUploadBytes = parseBytes(traf["CurrentUpload"]) ?: 0L
        val totalDownloadBytes = parseBytes(traf["TotalDownload"]) ?: 0L
        val totalUploadBytes = parseBytes(traf["TotalUpload"]) ?: 0L
        return Result.success(
            SpeedInfo(
                download = dl,
                upload = ul,
                latency = pingMetrics.latencyMs,
                jitter = pingMetrics.jitterMs,
                packetLoss = pingMetrics.packetLossPct,
                duration = formatSeconds(traf["CurrentConnectTime"]?.toLongOrNull() ?: 0),
                currentDownload = formatBytes(currentDownloadBytes),
                currentUpload = formatBytes(currentUploadBytes),
                currentTotal = formatBytes(currentDownloadBytes + currentUploadBytes),
                totalDownload = formatBytes(totalDownloadBytes),
                totalUpload = formatBytes(totalUploadBytes),
                totalTraffic = formatBytes(totalDownloadBytes + totalUploadBytes),
                totalConnectDuration = formatSeconds(traf["TotalConnectTime"]?.toLongOrNull() ?: 0),
                trafficVisible = showTrafficLabel(traf["showtraffic"])
            )
        )
    }

    suspend fun requestLatencyProbe(targetUrl: String): Result<Boolean> {
        val serviceRaw = HuaweiCpeClient.getDiagnoseWanServiceName().getOrDefault(emptyMap())
        val serviceName = serviceRaw.values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val host = normalizeLatencyTarget(targetUrl)
        val params = buildMap {
            if (serviceName.isNotBlank()) {
                put("ServiceName", serviceName)
            }
            if (host.isNotBlank()) {
                put("Host", host)
            }
            put("Timeout", "4000")
            put("DiagnosticsState", "Requested")
        }
        return HuaweiCpeClient.requestDiagnosePing(params).map { true }
    }

    suspend fun pingDevice(): Result<Boolean> = HuaweiCpeClient.ping()

    /**
     * 从 device/information 获取 AMBR（SIM 卡签约速率上限）。
     * 华为 CPE API 返回的字段名可能为：
     *   up_rate_limit / down_rate_limit
     *   或 UL_MaxRate / DL_MaxRate
     * 解析后返回格式化为可读字符串（如 "1 Gbps"）。
     */
    suspend fun getDeviceAmbr(): Pair<String, String> {
        val info = HuaweiCpeClient.getDeviceInfo().getOrDefault(emptyMap())
        val dlRaw = info["down_rate_limit"]
            ?: info["DL_MaxRate"]
            ?: info["DLMaxRate"]
            ?: info["dl_max_rate"]
            ?: ""
        val ulRaw = info["up_rate_limit"]
            ?: info["UL_MaxRate"]
            ?: info["ULMaxRate"]
            ?: info["ul_max_rate"]
            ?: ""
        return Pair(formatAmbr(dlRaw), formatAmbr(ulRaw))
    }

    private fun formatAmbr(raw: String): String {
        if (raw.isBlank()) return "--"
        val kbps = raw.trim().toLongOrNull() ?: return raw.trim()
        return when {
            kbps >= 1_000_000 -> "%.1f Gbps".format(kbps / 1_000_000.0)
            kbps >= 1_000 -> "%.1f Mbps".format(kbps / 1_000.0)
            else -> "$kbps Kbps"
        }
    }

    /**
     * 从 device/basic_information 获取国家码 (e.g. "CN")
     * 同时也获取 SimState 来验证 SIM 卡状态
     */
    suspend fun getBasicDeviceInfo(): Pair<String, String> {
        val basic = runCatching {
            HuaweiCpeClient.get("device/basic_information").getOrThrow()
        }.getOrDefault(emptyMap())
        val countryCode = basic["CountryCode"]?.trim().orEmpty()
        val simState = basic["SimState"]?.trim().orEmpty()
        return Pair(countryCode, simState)
    }

    /**
     * 从 /api/log/loginfo 获取系统操作日志。
     * 华为 CPE 返回 XML：
     *   &lt;response&gt;&lt;DisplayLevel&gt;Info&lt;/DisplayLevel&gt;
     *   &lt;LogContent&gt;2026-06-01 hh:mm:ss 用户操作 提示 ...\r\n...&lt;/LogContent&gt;&lt;/response&gt;
     *
     * parseXmlToMap 将其展平为单行 key=value：
     *   DisplayLevel -> "Info"
     *   LogContent   -> "2026-06-01 ...\r\n2026-06-01 ..."
     */
    suspend fun getSystemLog(): Result<List<SystemLogEntry>> {
        val raw = HuaweiCpeClient.getSystemLog().getOrElse { return Result.failure(it) }
        val logContent = raw["LogContent"]?.trim().orEmpty()

        if (logContent.isEmpty()) return Result.success(emptyList())

        val entries = logContent
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .map { s: String -> s.trim() }
            .filter { s: String -> s.isNotEmpty() }
            .mapNotNull { line: String -> parseLogLine(line) }

        return Result.success(entries)
    }

    /**
     * 解析单行日志： "2026-06-01 15:41:56 用户操作 提示 用户admin(10.0.0.25)修改了X_SyslogConfig"
     * 前3段为 时间 / 类型 / 级别，剩余为内容。
     */
    private fun parseLogLine(line: String): SystemLogEntry? {
        // 按空格分割，但内容可能含空格，所以只取前3段
        val parts = line.split(" ", limit = 4)
        if (parts.size < 4) return null

        val timeRaw = "${parts[0]} ${parts[1]}" // "2026-06-01 15:41:56"
        val typeRaw = parts[2]                   // "用户操作"
        val levelRaw = parts[3].split(" ").firstOrNull().orEmpty() // "提示"（因为内容可能紧跟）
        // 第4段中移除开头的级别词得到内容
        val content = if (parts[3].startsWith(levelRaw)) {
            parts[3].removePrefix(levelRaw).trimStart()
        } else {
            parts[3]
        }

        return SystemLogEntry(
            time = timeRaw,
            type = mapLogTypeZh(typeRaw),
            level = mapLogLevelZh(levelRaw),
            content = content.ifBlank { parts[3] }
        )
    }

    /** 中文类型名直接透传，数字映射兜底 */
    private fun mapLogTypeZh(raw: String): String = when (raw) {
        "1" -> "用户操作"; "2" -> "系统日志"; "3" -> "安全日志"
        else -> raw.ifBlank { "全部" }
    }

    /** 中文级别名直接透传，数字映射兜底 */
    private fun mapLogLevelZh(raw: String): String = when (raw) {
        "1" -> "警告"; "2" -> "提示"; "3" -> "信息"
        else -> raw.ifBlank { "提示" }
    }

    // 已禁用：此方法尝试通过 wlan-debug API 启用 telnet，但 wlan-debug 也需要开发者模式认证
    // 新方案是直接连接 telnet 端口，不通过 API 启用
    /*
    suspend fun ensureTelnetDebugPortEnabled(port: Int = 20249): Result<Boolean> = runCatching {
        val source = HuaweiCpeClient.getWlanDebug().getOrElse { throw it }
        val writable = source.entries
            .asSequence()
            .filter { (key, value) ->
                key.isNotBlank() &&
                    !key.contains('.') &&
                    !key.contains('[') &&
                    !key.contains(']') &&
                    !key.equals("response", ignoreCase = true) &&
                    !key.equals("error", ignoreCase = true) &&
                    !value.contains('|')
            }
            .associate { (key, value) -> key to value.trim() }
            .toMutableMap()

        if (writable.isEmpty()) {
            throw Exception("wlan-debug returned no writable fields")
        }

        var touched = false
        var foundTelnetField = false
        writable.keys.toList().forEach { key ->
            val lower = key.lowercase()
            val isSwitch = lower.contains("enable") ||
                lower.contains("switch") ||
                lower.contains("status") ||
                lower.contains("state")

            if (lower.contains("telnet")) {
                foundTelnetField = true
                if (isSwitch && writable[key] != "1") {
                    writable[key] = "1"
                    touched = true
                    android.util.Log.d("CpeRepo", "✓ Set telnet field: $key = 1")
                }
            }

            if (lower.contains("developer") && isSwitch && writable[key] != "1") {
                writable[key] = "1"
                touched = true
            }
        }

        if (!foundTelnetField) {
            throw Exception("wlan-debug has no telnet-related fields - Telnet 20249 开关不可用")
        }

        if (!touched) return@runCatching true
        HuaweiCpeClient.setWlanDebug(writable).getOrElse { throw it }
        true
    }
    */

    suspend fun collectTelnetAmbrQci(
        baseUrl: String,
        port: Int = 20249,
        onConnected: (() -> Unit)? = null,
        onReport: (TelnetAmbrQci) -> Unit
    ) = withContext(Dispatchers.IO) {
        val host = extractHost(baseUrl)
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 5_000)
            socket.soTimeout = 4_000
            onConnected?.invoke()
            // 只发一次 AT 命令，后续持续接收 CPE 推送的 ^DSAMBR
            var atSent = false
            val output = socket.getOutputStream()

            val regex = Regex("""\^DSAMBR\s*:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE)
            val input = socket.getInputStream()
            val bytes = ByteArray(2048)
            val textBuffer = StringBuilder()
            while (true) {
                coroutineContext.ensureActive()
                // 首轮发送一次 AT 初始化
                if (!atSent) {
                    runCatching {
                        output.write("AT\r\n".toByteArray(StandardCharsets.UTF_8))
                        output.flush()
                    }
                    atSent = true
                }
                val read = try {
                    input.read(bytes)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                if (read <= 0) break
                textBuffer.append(String(bytes, 0, read, StandardCharsets.UTF_8))

                // 每次收到 ^DSAMBR 都实时回调（CPE 可能会推送多条，QCI 可能不同）
                while (true) {
                    val match = regex.find(textBuffer) ?: break
                    val payload = match.groupValues.getOrNull(1).orEmpty()
                    parseTelnetAmbrPayload(payload)?.let(onReport)
                    textBuffer.delete(0, match.range.last + 1)
                }

                if (textBuffer.length > 8_192) {
                    textBuffer.delete(0, textBuffer.length - 1_024)
                }
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun parseTelnetAmbrLine(line: String): TelnetAmbrQci? {
        if (!line.startsWith("^DSAMBR", ignoreCase = true)) return null
        val payload = line.substringAfter(':', "").trim()
        if (payload.isBlank()) return null
        return parseTelnetAmbrPayload(payload)
    }

    private fun parseTelnetAmbrPayload(payload: String): TelnetAmbrQci? {
        if (payload.isBlank()) return null

        val parts = splitCsv(payload)
        if (parts.size < 5) return null

        val cid = parts[0]
        val dl = formatAmbr(parts[1])
        val ul = formatAmbr(parts[2])
        val apn = parts[3]
        val qci = parseQci(parts)
        return TelnetAmbrQci(
            dlAmbr = dl,
            ulAmbr = ul,
            qci = qci,
            apn = apn,
            cid = cid
        )
    }

    private fun parseQci(parts: List<String>): String {
        // Observed on some firmware: the last DSAMBR field may be a rolling counter.
        // Prefer telecom-valid QCI range first; otherwise fall back to CID-like field.
        val tail = parts.getOrNull(4)?.trim().orEmpty().toIntOrNull()
        if (tail != null && tail in 1..9) return tail.toString()

        val head = parts.getOrNull(0)?.trim().orEmpty().toIntOrNull()
        if (head != null && head in 1..9) return head.toString()

        return "--"
    }

    private fun splitCsv(raw: String): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        var inQuote = false
        raw.forEach { ch ->
            when {
                ch == '"' -> inQuote = !inQuote
                ch == ',' && !inQuote -> {
                    result += buffer.toString().trim().trim('"')
                    buffer.clear()
                }
                else -> buffer.append(ch)
            }
        }
        result += buffer.toString().trim().trim('"')
        return result
    }

    private fun extractHost(baseUrl: String): String {
        val normalized = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            baseUrl
        } else {
            "http://$baseUrl"
        }
        return runCatching { URI(normalized).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: baseUrl.substringAfter("://", baseUrl).substringBefore('/').substringBefore(':')
    }

    /**
     * 通过 Telnet TCP 连接发送一条 AT 命令并读取响应。
     * 返回原始文本响应（不含命令回显）。
     */
    suspend fun sendAtCommand(baseUrl: String, command: String, port: Int = 20249, timeoutMs: Int = 5_000): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = extractHost(baseUrl)
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(host, port), 5_000)
                    socket.soTimeout = timeoutMs
                    val output = socket.getOutputStream()
                    val input = socket.getInputStream()

                    // 发送 AT 命令
                    output.write("$command\r\n".toByteArray(StandardCharsets.UTF_8))
                    output.flush()

                    // 读取响应（直到超时）
                    val buffer = ByteArray(4096)
                    val result = StringBuilder()
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < timeoutMs) {
                        val read = try {
                            input.read(buffer)
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                        if (read <= 0) break
                        result.append(String(buffer, 0, read, StandardCharsets.UTF_8))
                    }
                    Result.success(result.toString().trim())
                } finally {
                    runCatching { socket.close() }
                }
            }.getOrElse { Result.failure(it) }
        }

    /**
     * 获取移动数据开关状态。
     * GET /api/dialup/mobile-dataswitch → 返回响应中的 dataswitch 值。
     */
    suspend fun getMobileDataSwitch(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = HuaweiCpeClient.get("dialup/mobile-dataswitch").getOrThrow()
            (resp["dataswitch"]?.trim() ?: resp["Dataswitch"]?.trim() ?: "0") == "1"
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * 设置移动数据开关。
     * POST /api/dialup/mobile-dataswitch with <dataswitch>0</dataswitch> or <dataswitch>1</dataswitch>
     */
    suspend fun setMobileDataSwitch(enable: Boolean): Result<Boolean> {
        val value = if (enable) "1" else "0"
        return HuaweiCpeClient.post(
            endpoint = "dialup/mobile-dataswitch",
            params = mapOf("dataswitch" to value)
        ).map { true }
    }

    private fun speedLimitLabel(raw: String?): String {
        if (raw.isNullOrBlank()) return "未限速"
        return when (raw.trim()) {
            "0" -> "未限速"
            "1" -> "已限速"
            else -> raw.trim()
        }
    }

    suspend fun loginCpe(username: String, password: String): Result<Boolean> {
        return HuaweiCpeClient.login(username, password)
    }

    suspend fun loginCpe(password: String): Result<Boolean> = loginCpe("admin", password)

    /**
     * 尝试使用 loginflag=2 登录开发模式（二次密码验证）
     * 华为 CPE 开发者页面需要额外的开发模式登录
     */
    suspend fun loginDeveloperMode(password: String): Result<Boolean> {
        return HuaweiCpeClient.loginDeveloperMode(password)
    }

    /**
     * 刷新 token - 开发者模式登录后调用，确保使用新的开发者 token
     */
    suspend fun refreshToken(): Result<Boolean> {
        return HuaweiCpeClient.refreshTokenQueue()
    }

    private fun parseDb(text: String?): Double? {
        return text?.replace("dBm", "", ignoreCase = true)
            ?.replace("dB", "", ignoreCase = true)
            ?.trim()
            ?.toDoubleOrNull()
    }

    /**
     * Map MCS index to modulation scheme.
     * NR: 0-4=QPSK, 5-9=16QAM, 10-19=64QAM, 20-28=256QAM
     * LTE: 0-9=QPSK, 10-16=16QAM, 17-28=64QAM, 29+=256QAM (LTE typically doesn't have 256QAM)
     */
    private fun mcsToModulation(mcs: Int?): String {
        val m = mcs ?: return ""
        return when {
            m <= 4 -> "QPSK"
            m <= 9 -> "16QAM"
            m <= 19 -> "64QAM"
            m <= 28 -> "256QAM"
            else -> ""
        }
    }

    /**
     * Extract RSSI of the primary cell from nrseccell_list / lteseccell_list.
     * Format: "ARFCN,Band,BW(optional),PCI,RSRP,RSRQ,RSSI,SINR;..."
     * RSSI is column index 6 for 8-col format, index 5 for 7-col format.
     */
    private fun extractPrimaryRssi(secMap: Map<String, String>): Double? {
        android.util.Log.d("CpeRepo", "=== extractPrimaryRssi secMap: $secMap")
        val csvValues = secMap.values.filter { it.contains(';') && it.contains(',') }
        csvValues.forEach { raw ->
            val firstRow = raw.split(';').firstOrNull { it.isNotBlank() } ?: return@forEach
            val cols = firstRow.split(',').map { it.trim() }
            val rssiCol = when {
                cols.size >= 8 -> cleanMetric(cols[6])
                cols.size >= 7 -> cleanMetric(cols[5])
                else -> return@forEach
            }
            val result = rssiCol.toDoubleOrNull()
            android.util.Log.d("CpeRepo", "=== extractPrimaryRssi: cols=$cols, rssiCol=$rssiCol, result=$result")
            return result
        }
        android.util.Log.d("CpeRepo", "=== extractPrimaryRssi: no CSV rows found")
        return null
    }

    /**
     * Detect network technology.
     * For NSA (EN-DC), the signal XML contains both LTE and NR fields simultaneously.
     * We detect this by checking if both nr-prefixed and lte/plain signal keys are present.
     */
    private fun networkType(typeEx: String?, signal: Map<String, String> = emptyMap()): String {
        val explicit = when (typeEx) {
            "111" -> "5G SA"
            "101" -> "5G NSA"
            "102" -> "5G SA"
            "19", "4" -> "4G LTE"
            else -> ""
        }
        if (explicit.isNotBlank()) return explicit

        // Fallback: detect NSA by dual presence of NR + LTE signal fields.
        val hasNr = signal.keys.any { it.startsWith("nr", ignoreCase = true) }
        val hasLte = signal.keys.any { it.startsWith("lte_", ignoreCase = true) }
                || signal.keys.any { k -> k in listOf("rsrp", "rsrq", "sinr") && !k.startsWith("nr") }
        return when {
            hasNr && hasLte -> "5G NSA"
            hasNr -> "5G SA"
            hasLte -> "4G LTE"
            else -> ""
        }
    }

    private fun connectionStatus(value: String?): String = when (value) {
        "901" -> "Connected"
        "900" -> "Disconnected"
        else -> ""
    }

    private fun buildCarrier(plmn: Map<String, String>): String {
        val fullName = safeText(plmn["FullName"])
        val shortName = safeText(plmn["ShortName"])
        val numeric = safeText(plmn["Numeric"])
        val name = fullName.ifBlank { shortName }
        return when {
            name.isNotBlank() && numeric.isNotBlank() -> "$name:$numeric"
            name.isNotBlank() -> name
            numeric.isNotBlank() -> numeric
            else -> ""
        }
    }

    private fun plmnRatLabel(rat: String?, technology: String): String {
        val raw = safeText(rat)
        return when (raw) {
            "" -> technology
            "7", "12" -> "NR"
            "6", "4" -> "LTE"
            "2", "1" -> "GSM"
            "3" -> "WCDMA"
            else -> raw
        }
    }

    /**
     * Huawei CPE PLMN State:
     * 0 = Unknown / Default, 1 = Available, 2 = Current (Registered), 3 = Forbidden
     */
    private fun plmnStateLabel(state: String?): String {
        val raw = safeText(state)
        return when (raw) {
            "0" -> ""
            "1" -> "Available"
            "2" -> "Registered"
            "3" -> "Forbidden"
            else -> raw
        }
    }

    /**
     * Format NR Cell Identity (NCI) from 36-bit hex to "gNB-Cell" notation.
     * E.g. "0D6C9613" → "883862-19" (upper 22 bits = gNB ID, lower 14 = cell ID)
     */
    private fun formatGnbCell(raw: String): String {
        val hex = raw.trim().removePrefix("0x").removePrefix("0X")
        val nci = hex.toLongOrNull(16) ?: return raw
        val gnbId = (nci shr 14) and 0x3FFFFF  // upper 22 bits
        val cellId = nci and 0x3FFF            // lower 14 bits
        return "$gnbId-$cellId"
    }

    private fun caStatus(sc: String?): String {
        val n = sc?.trim()?.toIntOrNull() ?: 0
        return if (n > 0) "CA Ready" else ""
    }

    private fun formatSeconds(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h${m}m" else "${m}m${seconds % 60}s"
    }

    private fun parseFrequencyMhz(value: String?): String {
        val raw = safeText(value)
        if (raw.isBlank()) return ""
        val normalized = raw.replace(",", "")
        val numeric = normalized
            .replace("kHz", "", ignoreCase = true)
            .replace("MHz", "", ignoreCase = true)
            .replace("Hz", "", ignoreCase = true)
            .trim()
            .toDoubleOrNull()
            ?: return raw
        val mhz = when {
            raw.contains("kHz", ignoreCase = true) -> numeric / 1000.0
            raw.contains("Hz", ignoreCase = true) && !raw.contains("MHz", ignoreCase = true) -> numeric / 1_000_000.0
            numeric >= 100_000 -> numeric / 1000.0
            else -> numeric
        }
        return when {
            mhz == 0.0 -> ""
            mhz % 1.0 == 0.0 -> mhz.toInt().toString()
            else -> "%.1f".format(mhz)
        }
    }

    private fun safeText(value: String?): String {
        return value?.trim()?.takeIf { it.isNotEmpty() && it != "--" }.orEmpty()
    }

    /** Extract the DL ARFCN from Huawei's "DL:1506 UL:19506" format. */
    private fun cleanEarfcn(earfcn: String): String {
        return Regex("""DL:\s*(\d+)""").find(earfcn)?.groupValues?.get(1) ?: earfcn
    }

    private fun parseValueSeries(value: String?): List<String> {
        val raw = safeText(value)
        if (raw.isBlank()) return emptyList()
        val segments = raw.split(',', ';', '|', '/', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "--" }
        return if (segments.isNotEmpty()) segments else emptyList()
    }

    private fun parseMetricSeries(value: String?): List<String> {
        val raw = safeText(value)
        if (raw.isBlank()) return emptyList()
        val numeric = Regex("""-?\d+(?:\.\d+)?""").findAll(raw).map { it.value }.toList()
        return when {
            numeric.size > 1 -> numeric
            numeric.size == 1 && !Regex("""[,;|/]""").containsMatchIn(raw) -> numeric
            else -> parseValueSeries(raw)
        }
    }

    private fun parseBandSeries(value: String?): List<String> {
        val raw = safeText(value)
        if (raw.isBlank()) return emptyList()
        val matches = Regex("""[NnBb]\d+""").findAll(raw).map { it.value.uppercase() }.toList()
        return when {
            matches.size > 1 -> matches
            matches.size == 1 && !Regex("""[,;|/]""").containsMatchIn(raw) -> matches
            else -> parseValueSeries(raw).map { it.uppercase() }
        }
    }

    private fun parseBandwidthSeries(value: String?): List<String> {
        val raw = safeText(value)
        if (raw.isBlank()) return emptyList()
        val mhzMatches = Regex("""\d+(?:\.\d+)?\s*MHz""", RegexOption.IGNORE_CASE)
            .findAll(raw)
            .map { it.value.replace(" ", "").uppercase() }
            .toList()
        if (mhzMatches.size > 1) return mhzMatches

        val segments = parseValueSeries(raw)
        if (segments.size > 1) {
            return segments.map { item ->
                if (item.contains("MHz", ignoreCase = true)) item.replace(" ", "").uppercase()
                else item
            }
        }

        if (mhzMatches.size == 1) return mhzMatches
        val digits = Regex("""\d+(?:\.\d+)?""").findAll(raw).map { it.value }.toList()
        return when {
            digits.size > 1 -> digits.map { "${it}MHz" }
            digits.size == 1 && !Regex("""[,;|/]""").containsMatchIn(raw) -> listOf("${digits[0]}MHz")
            else -> segments
        }
    }

    private fun seriesAt(values: List<String>, index: Int): String {
        return values.getOrNull(index)?.trim().orEmpty()
    }

    private fun parseCellRowsFromMap(
        source: Map<String, String>,
        typePrefix: String,
        active: Boolean,
        allowSingleRow: Boolean = false
    ): List<AggregationCell> {
        if (source.isEmpty()) return emptyList()

        // --- new: CSV-style rows (e.g. nbrcell_nrlist = "arfcn,band,pci,rsrp,rsrq,rssi,sinr;...") ---
        val csvRows = parseCsvCellRows(source, typePrefix, active)
        if (csvRows.isNotEmpty()) return csvRows

        val indexedRows = parseIndexedCellRows(source, typePrefix, active)
        if (indexedRows.isNotEmpty()) return indexedRows

        val columnRows = parseColumnCellRows(source, typePrefix, active, allowSingleRow)
        if (columnRows.isNotEmpty()) return columnRows

        return emptyList()
    }

    /**
     * Huawei returns neighbour / secondary cell data as a single semicolon-delimited
     * CSV string, e.g.:
     *   nbrcell_nrlist:  "627264,N77/N78,195,-107dBm,-18dB,-76dBm,-7dB;..."
     *   nrseccell_list: "627264,N78,100MHz,46,-82dBm,-10dB,-59dBm,15dB;..."
     *
     * Field order for nbrcell_nrlist (7 cols): ARFCN, Band, PCI, RSRP, RSRQ, RSSI, SINR
     * Field order for nrseccell_list (8 cols): ARFCN, Band, BW, PCI, RSRP, RSRQ, RSSI, SINR
     */
    private fun parseCsvCellRows(
        source: Map<String, String>,
        typePrefix: String,
        active: Boolean
    ): List<AggregationCell> {
        val csvValues = source.values.filter { v ->
            val cleaned = safeText(v)
            cleaned.contains(';') && cleaned.contains(',')
        }
        if (csvValues.isEmpty()) return emptyList()

        val rows = mutableListOf<AggregationCell>()
        csvValues.forEach { raw ->
            val cleaned = safeText(raw)
            cleaned.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { row ->
                val cols = row.split(',').map { it.trim() }
                if (cols.size < 7) return@forEach

                val hasBwCol = cols.size >= 8 && (cols[2].contains("MHz", ignoreCase = true) || cols[2].contains("kHz", ignoreCase = true))
                val arfcn: String
                val band: String
                val bw: String
                val pci: String
                val rsrp: String
                val rsrq: String
                val sinr: String

                if (hasBwCol) {
                    // nrseccell_list style: ARFCN(0), Band(1), BW(2), PCI(3), RSRP(4), RSRQ(5), RSSI(6), SINR(7)
                    arfcn = cleanMetric(cols[0])
                    band = cleanBand(cols[1])
                    bw = cols[2].replace(" ", "").uppercase()
                    pci = cols[3]
                    rsrp = cleanMetric(cols[4])
                    rsrq = cleanMetric(cols[5])
                    sinr = cleanMetric(cols[7])
                } else {
                    // nbrcell_nrlist style: ARFCN(0), Band(1), PCI(2), RSRP(3), RSRQ(4), RSSI(5), SINR(6)
                    arfcn = cleanMetric(cols[0])
                    band = cleanBand(cols[1])
                    bw = "-"
                    pci = cols[2]
                    rsrp = cleanMetric(cols[3])
                    rsrq = cleanMetric(cols[4])
                    sinr = cleanMetric(cols[6])
                }

                val meaningful = listOf(band, pci, rsrp, rsrq, sinr).any { it.isNotBlank() && it != "-" }
                if (!meaningful) return@forEach

                rows.add(
                    AggregationCell(
                        type = typePrefix,
                        band = band.ifBlank { "-" },
                        arfcn = arfcn.ifBlank { "-" },
                        pci = pci.ifBlank { "-" },
                        bandwidth = bw.ifBlank { "-" },
                        rsrp = rsrp.ifBlank { "-" },
                        rsrq = rsrq.ifBlank { "-" },
                        sinr = sinr.ifBlank { "-" },
                        active = active
                    )
                )
            }
        }
        return rows
    }

    /** Strip "dBm"/"dB" suffix and return the raw numeric string. */
    private fun cleanMetric(raw: String): String {
        return raw.replace("dBm", "", ignoreCase = true)
            .replace("dB", "", ignoreCase = true)
            .trim()
    }

    /** Normalise band string like "N77/N78" → "N77/N78" (already fine), "B3" → "B3". */
    private fun cleanBand(raw: String): String {
        return raw.trim().uppercase().takeIf { it.isNotEmpty() && it != "--" }.orEmpty()
    }

    private enum class CellField {
        BAND, ARFCN, PCI, BW, RSRP, RSRQ, SINR
    }

    private fun detectCellField(key: String): CellField? {
        val k = key.lowercase()
        return when {
            k.contains("bandwidth") || k.contains("dlbandwidth") || k.contains("ulbandwidth") || k == "bw" -> CellField.BW
            k.contains("arfcn") || k.contains("earfcn") || k.contains("frequency") -> CellField.ARFCN
            k.contains("pci") -> CellField.PCI
            k.contains("rsrp") -> CellField.RSRP
            k.contains("rsrq") -> CellField.RSRQ
            k.contains("sinr") || k.contains("snr") -> CellField.SINR
            k.contains("band") -> CellField.BAND
            else -> null
        }
    }

    private fun extractRowIndex(key: String): Int? {
        val lower = key.lowercase()
        Regex("""(?:scell|seccell|nbrcell|neighborcell|neighbourcell|cell|nbr|neighbor|neighbour)[\[\]_.-]*(\d+)""")
            .find(lower)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { return it }

        Regex("""(\d+)$""").find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    private fun parseIndexedCellRows(
        source: Map<String, String>,
        typePrefix: String,
        active: Boolean
    ): List<AggregationCell> {
        val buckets = sortedMapOf<Int, MutableMap<CellField, String>>()

        source.forEach { (key, value) ->
            val field = detectCellField(key) ?: return@forEach
            val idx = extractRowIndex(key) ?: return@forEach
            val cleaned = safeText(value)
            if (cleaned.isBlank()) return@forEach
            val bucket = buckets.getOrPut(idx) { mutableMapOf() }
            if (field == CellField.BW) {
                bucket[field] = cleaned.replace(" ", "").uppercase()
            } else {
                bucket[field] = cleaned
            }
        }

        return buckets.entries.mapNotNull { (idx, fields) ->
            val meaningful = listOf(
                fields[CellField.BAND],
                fields[CellField.ARFCN],
                fields[CellField.PCI],
                fields[CellField.RSRP],
                fields[CellField.RSRQ],
                fields[CellField.SINR]
            ).any { !it.isNullOrBlank() }
            if (!meaningful) return@mapNotNull null

            AggregationCell(
                type = typePrefix,
                band = fields[CellField.BAND].orEmpty().ifBlank { "-" },
                arfcn = fields[CellField.ARFCN].orEmpty().ifBlank { "-" },
                pci = fields[CellField.PCI].orEmpty().ifBlank { "-" },
                bandwidth = fields[CellField.BW].orEmpty().ifBlank { "-" },
                rsrp = fields[CellField.RSRP].orEmpty().ifBlank { "-" },
                rsrq = fields[CellField.RSRQ].orEmpty().ifBlank { "-" },
                sinr = fields[CellField.SINR].orEmpty().ifBlank { "-" },
                active = active
            )
        }
    }

    private fun parseColumnCellRows(
        source: Map<String, String>,
        typePrefix: String,
        active: Boolean,
        allowSingleRow: Boolean
    ): List<AggregationCell> {
        val columns = mutableMapOf<CellField, List<String>>()
        source.forEach { (key, value) ->
            val field = detectCellField(key) ?: return@forEach
            val series = when (field) {
                CellField.BAND -> parseBandSeries(value)
                CellField.BW -> parseBandwidthSeries(value)
                CellField.RSRP, CellField.RSRQ, CellField.SINR -> parseMetricSeries(value)
                else -> parseValueSeries(value)
            }
            if (series.isNotEmpty()) columns[field] = series
        }

        val rowCount = columns.values.maxOfOrNull { it.size } ?: 0
        if (rowCount == 0) return emptyList()
        if (rowCount == 1 && !allowSingleRow) return emptyList()

        return (0 until rowCount).mapNotNull { row ->
            val band = seriesAt(columns[CellField.BAND].orEmpty(), row)
            val arfcn = seriesAt(columns[CellField.ARFCN].orEmpty(), row)
            val pci = seriesAt(columns[CellField.PCI].orEmpty(), row)
            val bw = seriesAt(columns[CellField.BW].orEmpty(), row)
            val rsrp = seriesAt(columns[CellField.RSRP].orEmpty(), row)
            val rsrq = seriesAt(columns[CellField.RSRQ].orEmpty(), row)
            val sinr = seriesAt(columns[CellField.SINR].orEmpty(), row)
            val meaningful = listOf(band, arfcn, pci, rsrp, rsrq, sinr).any { it.isNotBlank() }
            if (!meaningful) return@mapNotNull null

            AggregationCell(
                type = typePrefix,
                band = band.ifBlank { "-" },
                arfcn = arfcn.ifBlank { "-" },
                pci = pci.ifBlank { "-" },
                bandwidth = bw.ifBlank { "-" },
                rsrp = rsrp.ifBlank { "-" },
                rsrq = rsrq.ifBlank { "-" },
                sinr = sinr.ifBlank { "-" },
                active = active
            )
        }
    }

    private data class PciMetrics(
        val pci: String,
        val rsrp: String = "",
        val rsrq: String = "",
        val sinr: String = "",
        val band: String = "",
        val arfcn: String = "",
        val bandwidth: String = ""
    )

    /**
     * From secMap / nbrMap / cellInfoMap, extract every PCI together with its
     * associated RSRP / RSRQ / SINR so we never throw away useful signal data.
     */
    private fun extractPciMetricsList(
        maps: List<Map<String, String>>,
        excludePci: String
    ): List<PciMetrics> {
        val excluded = excludePci.trim()
        // Group raw entries by PCI for each source map.
        val candidates = linkedMapOf<String, PciMetrics>()

        maps.forEach { source ->
            // First pass: detect all PCI values in the map.
            val pciGroups = mutableMapOf<String, MutableList<String>>()
            source.forEach { (key, value) ->
                val lowerKey = key.lowercase()
                if (!lowerKey.contains("pci") || lowerKey.contains("pucch") || lowerKey.contains("pusch")) return@forEach
                val cleaned = safeText(value)
                if (cleaned.isBlank()) return@forEach
                // A single key may hold a delimited series; split first.
                val pciList = cleaned.split(',', ';', '|', '/', '\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (pciList.isEmpty()) return@forEach

                // Build lookup key: source index + key path to avoid cross-map collisions.
                val groupKey = "$key|${source.hashCode()}"
                pciGroups.getOrPut(groupKey) { mutableListOf() }.addAll(pciList)
            }

            // Second pass: for each PCI in each group, try to find its row index,
            // then associate other metric columns at the same index.
            pciGroups.forEach { (groupKey, pcis) ->
                pcis.forEachIndexed { idx, pciValue ->
                    val pciNum = pciValue.toIntOrNull() ?: return@forEachIndexed
                    if (pciNum !in 0..1007 || pciValue == excluded) return@forEachIndexed

                    // Scan all columns from the same source to pick up co-located metrics.
                    val rsrpValues = mutableListOf<String>()
                    val rsrqValues = mutableListOf<String>()
                    val sinrValues = mutableListOf<String>()
                    var band = ""
                    var arfcn = ""
                    var bw = ""

                    source.forEach { (key, value) ->
                        val lowerKey = key.lowercase()
                        val cleaned = safeText(value)
                        if (cleaned.isBlank()) return@forEach

                        val segments = cleaned.split(',', ';', '|', '/', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        if (segments.isEmpty()) return@forEach
                        val segmentAtIdx = segments.getOrNull(idx) ?: return@forEach

                        when {
                            lowerKey.contains("rsrp") && !lowerKey.contains("rsrq") -> rsrpValues.add(segmentAtIdx)
                            lowerKey.contains("rsrq") -> rsrqValues.add(segmentAtIdx)
                            lowerKey.contains("sinr") || lowerKey.contains("snr") -> sinrValues.add(segmentAtIdx)
                            lowerKey.contains("band") && !lowerKey.contains("bandwidth") -> band = segmentAtIdx
                            lowerKey.contains("arfcn") || lowerKey.contains("earfcn") -> arfcn = segmentAtIdx
                            lowerKey.contains("bandwidth") || lowerKey == "bw" -> bw = segmentAtIdx
                        }
                    }

                    if (candidates.containsKey(pciValue)) return@forEachIndexed
                    candidates[pciValue] = PciMetrics(
                        pci = pciValue,
                        rsrp = rsrpValues.firstOrNull { it.toDoubleOrNull() != null }.orEmpty(),
                        rsrq = rsrqValues.firstOrNull { it.toDoubleOrNull() != null }.orEmpty(),
                        sinr = sinrValues.firstOrNull { it.toDoubleOrNull() != null }.orEmpty(),
                        band = band,
                        arfcn = arfcn,
                        bandwidth = bw
                    )
                }
            }
        }
        return candidates.values.take(6)
    }

    private fun buildFallbackNeighborCells(primary: AggregationCell, metricsList: List<PciMetrics>): List<AggregationCell> {
        val rows = mutableListOf<AggregationCell>()
        val primaryBand = primary.band.ifBlank { "-" }
        val primaryArfcn = primary.arfcn.ifBlank { "-" }
        val primaryBw = primary.bandwidth.ifBlank { "-" }
        metricsList.take(6).forEachIndexed { idx, m ->
            rows.add(
                AggregationCell(
                    type = "N",
                    band = m.band.ifBlank { primaryBand },
                    arfcn = m.arfcn.ifBlank { primaryArfcn },
                    pci = m.pci,
                    bandwidth = m.bandwidth.ifBlank { primaryBw },
                    rsrp = m.rsrp.ifBlank { "-" },
                    rsrq = m.rsrq.ifBlank { "-" },
                    sinr = m.sinr.ifBlank { "-" },
                    active = false
                )
            )
        }
        // If no metrics were extracted at all, still produce at least 3 fallback rows
        // so the user sees that the section is alive.
        if (rows.isEmpty()) {
            repeat(3) { idx ->
                rows.add(
                    AggregationCell(
                        type = "N",
                        band = primaryBand,
                        arfcn = primaryArfcn,
                        pci = "-",
                        bandwidth = primaryBw,
                        rsrp = "-",
                        rsrq = "-",
                        sinr = "-",
                        active = false
                    )
                )
            }
        }
        return rows
    }

    private data class PingMetrics(
        val latencyMs: Double = 0.0,
        val jitterMs: Double = 0.0,
        val packetLossPct: Double = 0.0
    )

    private fun parsePingMetrics(source: Map<String, String>): PingMetrics {
        if (source.isEmpty()) return PingMetrics()

        val latency = findMapNumber(
            source,
            "AverageResponseTime",
            "averageRtt",
            "AverageRtt",
            "AverageLatency",
            "averageLatency",
            "AvgResponseTime",
            "avgRtt",
            "avgLatency",
            "ResponseTime",
            "latency",
            "rtt"
        )?.coerceAtLeast(0.0) ?: 0.0

        val jitterDirect = findMapNumber(source, "Jitter", "jitter", "AverageJitter", "averageJitter")
        val minRtt = findMapNumber(source, "MinimumResponseTime", "MinResponseTime", "minRtt")
        val maxRtt = findMapNumber(source, "MaximumResponseTime", "MaxResponseTime", "maxRtt")
        val jitter = (
            jitterDirect
                ?: if (minRtt != null && maxRtt != null) (maxRtt - minRtt).coerceAtLeast(0.0) else null
                ?: 0.0
            ).coerceAtLeast(0.0)

        val lossRateDirect = findMapNumber(
            source,
            "PacketLoss",
            "packetLoss",
            "PacketLossRate",
            "packetLossRate",
            "LossRate",
            "lossRate"
        )

        val successCount = findMapNumber(source, "SuccessCount", "successCount", "SuccCount", "Success")
        val failureCount = findMapNumber(source, "FailureCount", "failureCount", "FailCount", "FailedCount")
        val repetitionCount = findMapNumber(source, "NumberOfRepetitions", "SendCount", "TotalCount", "Count")
        val total = when {
            successCount != null && failureCount != null -> successCount + failureCount
            repetitionCount != null -> repetitionCount
            else -> null
        }
        val packetLoss = when {
            lossRateDirect != null -> {
                if (lossRateDirect <= 1.0) (lossRateDirect * 100.0).coerceIn(0.0, 100.0)
                else lossRateDirect.coerceIn(0.0, 100.0)
            }
            total != null && total > 0 && successCount != null ->
                ((total - successCount) / total * 100.0).coerceIn(0.0, 100.0)
            total != null && total > 0 && failureCount != null ->
                (failureCount / total * 100.0).coerceIn(0.0, 100.0)
            else -> 0.0
        }

        return PingMetrics(
            latencyMs = latency,
            jitterMs = jitter,
            packetLossPct = packetLoss
        )
    }

    private fun findMapNumber(source: Map<String, String>, vararg candidates: String): Double? {
        candidates.forEach { key ->
            source.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
                ?.let(::parseFirstNumber)
                ?.let { return it }
            source.entries.firstOrNull { it.key.endsWith(".$key", ignoreCase = true) }?.value
                ?.let(::parseFirstNumber)
                ?.let { return it }
            source.entries.firstOrNull { it.key.contains(key, ignoreCase = true) }?.value
                ?.let(::parseFirstNumber)
                ?.let { return it }
        }
        return null
    }

    private fun parseFirstNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("""-?\d+(?:\.\d+)?""").find(raw) ?: return null
        return match.value.toDoubleOrNull()
    }

    private fun normalizeLatencyTarget(target: String): String {
        val trimmed = target.trim()
        if (trimmed.isBlank()) return ""

        val uriHost = runCatching { URI(trimmed).host.orEmpty() }.getOrDefault("")
        val hostCandidate = when {
            uriHost.isNotBlank() -> uriHost
            trimmed.contains("://") -> trimmed.substringAfter("://")
            else -> trimmed
        }

        return hostCandidate
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
            .substringBefore(":")
            .trim()
    }

    private fun parseBytes(value: String?): Long? {
        return safeText(value).toLongOrNull()
    }

    private fun showTrafficLabel(value: String?): String {
        return when (safeText(value)) {
            "0" -> "未显示"
            "1" -> "显示中"
            else -> ""
        }
    }

    private fun formatBytes(value: String?): String {
        val bytes = value?.toLongOrNull() ?: return ""
        return formatBytes(bytes)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> "%.1fGB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> "%.1fMB".format(bytes / 1_000_000.0)
            bytes >= 1_000L -> "%.1fKB".format(bytes / 1_000.0)
            else -> "${bytes}B"
        }
    }
}
