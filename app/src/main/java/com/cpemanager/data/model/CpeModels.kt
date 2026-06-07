package com.cpemanager.data.model

/**
 * Huawei 5G CPE data models.
 *
 * The device API returns XML maps. The repository converts those maps into
 * these UI-friendly models so the Compose screens can stay focused on layout.
 */

data class HuaweiSession(
    val sessionId: String,
    val token: String
)

data class HuaweiDeviceInfo(
    val DeviceName: String = "",
    val SerialNumber: String = "",
    val Imei: String = "",
    val Imsi: String = "",
    val HardwareVersion: String = "",
    val SoftwareVersion: String = "",
    val WebUIVersion: String = "",
    val MacAddress1: String = "",
    val MacAddress2: String = "",
    val ProductFamily: String = "",
    val Classify: String = "",
    val supportmode: String = "",
    val workmode: String = ""
)

data class HuaweiSignal(
    val pci: String = "",
    val sc: String = "",
    val cell_id: String = "",
    val rsrp: String = "",
    val rsrq: String = "",
    val rssi: String = "",
    val sinr: String = "",
    val rscp: String = "",
    val ecio: String = "",
    val mode: String = "",
    val band: String = "",
    val dlbandwidth: String = "",
    val ulbandwidth: String = "",
    val txpower: String = "",
    val cqi: String = "",
    val ulfrequency: String = "",
    val dlfrequency: String = ""
)

data class HuaweiNetworkStatus(
    val ConnectionStatus: String = "",
    val WifiConnectionStatus: String = "",
    val SignalStrength: String = "",
    val SignalIcon: String = "",
    val CurrentNetworkType: String = "",
    val CurrentServiceDomain: String = "",
    val RoamingStatus: String = "",
    val BatteryStatus: String = "",
    val SimLockStatus: String = "",
    val WanIPAddress: String = "",
    val WanIPv6Address: String = "",
    val PrimaryDns: String = "",
    val SecondaryDns: String = "",
    val PrimaryIPv6Dns: String = "",
    val SecondaryIPv6Dns: String = "",
    val CurrentWifiUser: String = "",
    val TotalWifiUser: String = "",
    val CurrentTotalWifiUser: String = "",
    val ServiceStatus: String = "",
    val SimStatus: String = "",
    val WifiStatus: String = "",
    val CurrentNetworkTypeEx: String = "",
    val maxsignal: String = "",
    val wifiindooronly: String = "",
    val wififrequency: String = "",
    val classify: String = "",
    val flymode: String = "",
    val cellroaming: String = "",
    val cell_id: String = "",
    val band: String = "",
    val tac: String = ""
)

data class HuaweiTrafficStats(
    val CurrentConnectTime: String = "",
    val CurrentUpload: String = "",
    val CurrentDownload: String = "",
    val CurrentDownloadRate: String = "",
    val CurrentUploadRate: String = "",
    val TotalUpload: String = "",
    val TotalDownload: String = "",
    val TotalConnectTime: String = "",
    val showtraffic: String = ""
)

data class HuaweiPlmnInfo(
    val State: String = "",
    val FullName: String = "",
    val ShortName: String = "",
    val Numeric: String = "",
    val Rat: String = ""
)

data class HuaweiNetMode(
    val NetworkMode: String = "",
    val NetworkBand: String = "",
    val LTEBand: String = "",
    val NetworkBand5G: String = ""
)

data class NetModeRequest(
    val NetworkMode: String,
    val NetworkBand: String = "",
    val LTEBand: String = ""
)

data class LockCellRequest(
    val LockCell: Int,
    val Freq: Long,
    val PCI: Int
)

data class PccInfo(
    val technology: String = "",
    val carrier: String = "",
    val plmnRat: String = "",
    val plmnState: String = "",
    val band: String = "",
    val pci: String = "",
    val ssArfcn: String = "",
    val bandwidth: String = "",
    val ulBandwidth: String = "",
    val signalMode: String = "",
    val rscp: String = "",
    val ecio: String = "",
    val dlFrequency: String = "",
    val ulFrequency: String = "",
    val status: String = "",
    val caStatus: String = "",
    val dlSpeed: Double = 0.0,
    val ulSpeed: Double = 0.0,
    val gnbCell: String = "",
    val tac: String = "",
    val dlMhz: String = "",
    val ulMhz: String = "",
    // AMBR 信息（SIM 卡限速）
    val dlAmbr: String = "",
    val ulAmbr: String = "",
    val qci: String = "",
    // 速度限制状态 (from monitoring/status speedLimitStatus)
    val speedLimitStatus: String = "",
    // 国家码 (from device/basic_information CountryCode)
    val countryCode: String = ""
)

data class TelnetAmbrQci(
    val dlAmbr: String = "--",
    val ulAmbr: String = "--",
    val qci: String = "--",
    val apn: String = "",
    val cid: String = ""
)

data class RfQuality(
    val rsrp: Double = 0.0,
    val rsrq: Double = 0.0,
    val sinr: Double = 0.0,
    val rssi: Double = 0.0,
    val cqi: Int = 0,
    val pusch: Int = 0,
    val pucch: Int = 0,
    val srs: Int = 0,
    val prach: Int = 0,
    val rsrpHistory: List<Double> = emptyList(),
    val sinrHistory: List<Double> = emptyList(),
    val eventLog: List<String> = emptyList()
)

data class LinkStatus(
    val dlMcs: Int = 0,
    val dlRank: Int = 0,
    val ulMcs: Int = 0,
    val ulRank: Int = 0,
    val dlModulation: String = "",
    val ulModulation: String = "",
    val dlThroughput: Double = 0.0,
    val ulThroughput: Double = 0.0
)

data class AggregationCell(
    val type: String = "",
    val band: String = "",
    val arfcn: String = "",
    val pci: String = "",
    val bandwidth: String = "",
    val rsrp: String = "",
    val rsrq: String = "",
    val sinr: String = "",
    val active: Boolean = false
)

data class StationTrendSample(
    val timestampMs: Long,
    val rsrp: Double = Double.NaN,
    val rsrq: Double = Double.NaN,
    val sinr: Double = Double.NaN
)

data class BaseStationTrend(
    val key: String = "",
    val displayName: String = "",
    val type: String = "",
    val band: String = "",
    val arfcn: String = "",
    val pci: String = "",
    val bandwidth: String = "",
    val active: Boolean = false,
    val latestRsrp: Double = Double.NaN,
    val latestRsrq: Double = Double.NaN,
    val latestSinr: Double = Double.NaN,
    val samples: List<StationTrendSample> = emptyList()
)

fun defaultAggregationCells(): List<AggregationCell> = listOf(
    AggregationCell("P", "N78", "627264", "46", "100", "-83", "-10", "17", true),
    AggregationCell("S1", "N78", "627264", "606", "-", "-92", "-14", "-2", true),
    AggregationCell("S2", "N78", "627264", "195", "-", "-97", "-16", "-5", true),
    AggregationCell("D", "N78", "627264", "607", "-", "-99", "-14", "-1"),
    AggregationCell("D", "N78", "627264", "182", "-", "-157", "-44", "-24"),
    AggregationCell("D", "N78", "627264", "454", "-", "-157", "-44", "-24"),
    AggregationCell("D", "N78", "627264", "107", "-", "-157", "-44", "-24")
)

data class SpeedInfo(
    val download: Double = 0.0,
    val upload: Double = 0.0,
    val latency: Double = 0.0,
    val jitter: Double = 0.0,
    val packetLoss: Double = 0.0,
    val duration: String = "0s",
    val currentDownload: String = "",
    val currentUpload: String = "",
    val currentTotal: String = "",
    val totalDownload: String = "",
    val totalUpload: String = "",
    val totalTraffic: String = "",
    val totalConnectDuration: String = "",
    val trafficVisible: String = "",
    val monthDownload: String = "",
    val monthUpload: String = "",
    val latencySeries: List<Double> = emptyList(),
    val downloadSeries: List<Double> = emptyList(),
    val uploadSeries: List<Double> = emptyList()
)

data class LockConfig(
    val networkMode: String = "",
    val lockCell: Int = 0,
    val lockFreq: Long = 0,
    val lockPci: Int = 0,
    val band5G: String = "",
    val band4G: String = ""
)

/** 系统操作日志条目，来自 /api/log/loginfo */
data class SystemLogEntry(
    val time: String = "",       // "2026-06-04 15:41:56"
    val type: String = "",       // "全部" / "用户操作" / "系统日志" / "安全日志"
    val level: String = "",      // "警告" / "提示" / "信息"
    val content: String = ""     // 日志内容
)
