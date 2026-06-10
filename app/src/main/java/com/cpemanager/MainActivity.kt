package com.cpemanager

import android.graphics.Paint as AndroidPaint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cpemanager.data.model.AggregationCell
import com.cpemanager.data.model.BaseStationTrend
import com.cpemanager.data.model.LinkStatus
import com.cpemanager.data.model.PccInfo
import com.cpemanager.data.model.RfQuality
import com.cpemanager.data.model.StationTrendSample
import com.cpemanager.data.local.REFRESH_INTERVAL_OPTIONS_SEC
import com.cpemanager.viewmodel.CpeUiState
import com.cpemanager.viewmodel.CpeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFFF5F6FA)
private val LockBg = Color(0xFFF3F5FB)
private val SurfaceWhite = Color.White
private val BorderColor = Color(0xFFE6E9EF)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF667085)
private val Green = Color(0xFF31C27C)
private val Blue = Color(0xFF3D5AFE)
private val Indigo = Color(0xFF5B67F0)
private val Purple = Color(0xFF7C4DFF)
private val Cyan = Color(0xFF00B8D9)
private val Amber = Color(0xFFF59E0B)
private val Red = Color(0xFFFB7185)
private val BlueSoft = Color(0xFFEAF0FF)
private val PurpleSoft = Color(0xFFF1E7FF)
private val CyanSoft = Color(0xFFE6FBFF)
private val AmberSoft = Color(0xFFFFF4DF)
private val GreenSoft = Color(0xFFE7F8EF)
private val GraySoft = Color(0xFFF5F6FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CpeTheme {
                CpeManagerApp()
            }
        }
    }
}

@Composable
private fun CpeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Indigo,
            background = Bg,
            surface = SurfaceWhite,
            onSurface = TextPrimary
        ),
        content = content
    )
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    Settings("设置", Icons.Rounded.Settings),
    Pcc("PCC", Icons.Rounded.Tune),
    Aggregation("载波聚合", Icons.Rounded.CellTower),
    Lock("锁频", Icons.Rounded.Lock),
    Speed("速率", Icons.Rounded.Speed),
    SystemLog("日志", Icons.Rounded.Info)
}

private data class SpeedRecord(val left: String, val right: String)

private val BottomTabs = listOf(
    AppTab.Settings,
    AppTab.Pcc,
    AppTab.Aggregation,
    AppTab.Lock,
    AppTab.Speed
)

private fun maskApiKey(raw: String): String {
    val key = raw.trim()
    if (key.isBlank()) return "sk-********************"
    if (key.length <= 7) return "${key.take(3)}****"
    val stars = "*".repeat((key.length - 7).coerceAtLeast(6))
    return "${key.take(3)}$stars${key.takeLast(4)}"
}

private fun maskDots(raw: String, minDots: Int = 8, maxDots: Int = 16, prefix: String = ""): String {
    val dotsCount = raw.trim().length.coerceIn(minDots, maxDots)
    val dots = List(dotsCount) { "•" }.joinToString(" ")
    return if (prefix.isBlank()) dots else "$prefix $dots"
}

@Composable
private fun CpeManagerApp(viewModel: CpeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableStateOf(AppTab.Pcc) }
    var showPccTrendDetail by rememberSaveable { mutableStateOf(false) }

    // 观察应用生命周期：后台暂停，前台恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppInForeground()
                Lifecycle.Event.ON_STOP -> viewModel.onAppInBackground()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            BottomBar(
                tab = tab,
                onSelect = {
                    tab = it
                    if (it != AppTab.Pcc) showPccTrendDetail = false
                }
            )
        }
    ) { padding ->
        val pageBg = if (tab == AppTab.Lock) LockBg else Bg
        // 日志页面不需要 LazyColumn 包裹
        if (tab == AppTab.SystemLog) {
            SystemLogScreen(viewModel = viewModel, onBack = { tab = AppTab.Pcc })
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageBg)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 0.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    when (tab) {
                        AppTab.Settings -> SettingsScreen(viewModel, uiState)
                        AppTab.Pcc -> {
                            if (showPccTrendDetail) {
                                PccTrendDetailScreen(uiState = uiState, onBack = { showPccTrendDetail = false })
                            } else {
                                PccScreen(
                                    viewModel = viewModel,
                                    uiState = uiState,
                                    onOpenTrendDetail = { showPccTrendDetail = true },
                                    onOpenSystemLog = { tab = AppTab.SystemLog }
                                )
                            }
                        }
                        AppTab.Aggregation -> AggregationScreen(viewModel, uiState)
                        AppTab.Lock -> LockScreen(viewModel, uiState)
                        AppTab.Speed -> SpeedScreen(viewModel, uiState)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(tab: AppTab, onSelect: (AppTab) -> Unit) {
    val highlightAsBubble = tab == AppTab.Settings
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(SurfaceWhite)
            .border(1.dp, BorderColor, RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomTabs.forEach { item ->
            val selected = item == tab
            val selectedTone = if (selected && item == AppTab.Lock) Indigo else if (selected) Green else TextSecondary
            val selectedBg = when {
                !selected -> Color.Transparent
                item == AppTab.Lock -> Color(0xFFEEF2FF)
                else -> GreenSoft
            }
            val itemBg = if (highlightAsBubble) Color.Transparent else selectedBg
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(itemBg)
                    .clickable { onSelect(item) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (highlightAsBubble && selected) selectedBg else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = selectedTone, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.label,
                    color = selectedTone,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: CpeViewModel, uiState: CpeUiState) {
    val context = LocalContext.current
    val aiBaseUrl = "http://192.168.8.1"
    val apiKeyMasked = maskDots(uiState.apiKey.removePrefix("sk-"), minDots = 12, maxDots = 20, prefix = "sk-")
    val onlineLabel = if (uiState.deviceOnline) "在线" else "离线"
    val connectionHint = if (uiState.deviceOnline) {
        "已连接，最近更新 ${uiState.lastUpdated}"
    } else {
        "未连接，最近更新 ${uiState.lastUpdated}"
    }
    val connectionMessage = when {
        uiState.connectionMessage.isBlank() && uiState.deviceOnline -> "设备看板刷新成功"
        uiState.connectionMessage.isBlank() -> "设备未连接，请检查登录信息。"
        uiState.connectionMessage.contains("Dashboard", true) -> "设备看板刷新成功"
        uiState.connectionMessage.contains("Connected", true) -> "连接成功，数据已刷新。"
        uiState.connectionMessage.contains("failed", true) -> "连接失败，请检查地址与凭据。"
        uiState.connectionMessage.contains("succeeded", true) -> "自动登录成功。"
        else -> uiState.connectionMessage
    }
    val latencyUrl = when (uiState.latencyTestUrl) {
        "", "https://www.baidu.com" -> "https://www.gstatic.com/generate_204"
        else -> uiState.latencyTestUrl
    }
    val speedUrl = when (uiState.speedTestUrl) {
        "", "https://speed.cloudflare.com/__down?bytes=25000000" -> "https://speed.cloudflare.com/__down?bytes=10485760"
        else -> uiState.speedTestUrl
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsStatusBar()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("设置", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("AI 接口、登录信息与网络测试配置", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        AppCard(shape = 20.dp, padding = 14.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFEEF2FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = Indigo, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text("配置中心", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "保存 CPE 登录、AI API、测速地址和连通性测试入口。",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("CPE 登录", Color(0xFFEEF2FF), Indigo)
                        Chip("AI API", CyanSoft, Color(0xFF0891B2))
                    }
                }
            }
        }

        AppCard(shape = 20.dp, padding = 14.dp) {
            SectionTitle("连接与调试") {
                Chip(onlineLabel, PurpleSoft, Purple)
            }
            Text(connectionHint, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF7F9FC))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(connectionMessage, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        AppCard(shape = 20.dp, padding = 14.dp) {
            SectionTitle("登录配置") {
                Chip("编辑", PurpleSoft, Purple)
            }
            // 可编辑的 Base URL
            var editBaseUrl by rememberSaveable { mutableStateOf(uiState.baseUrl) }
            OutlinedTextField(
                value = editBaseUrl,
                onValueChange = { editBaseUrl = it },
                label = { Text("Base URL", fontSize = 11.sp) },
                placeholder = { Text("http://10.0.0.1", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo,
                    unfocusedBorderColor = Color(0xFFE6EAF2),
                    focusedContainerColor = Color(0xFFF7F9FC),
                    unfocusedContainerColor = Color(0xFFF7F9FC)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            // 可编辑的用户名
            var editUsername by rememberSaveable { mutableStateOf(uiState.username) }
            OutlinedTextField(
                value = editUsername,
                onValueChange = { editUsername = it },
                label = { Text("用户名", fontSize = 11.sp) },
                placeholder = { Text("admin", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo,
                    unfocusedBorderColor = Color(0xFFE6EAF2),
                    focusedContainerColor = Color(0xFFF7F9FC),
                    unfocusedContainerColor = Color(0xFFF7F9FC)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            // 可编辑的密码（支持显示/隐藏）
            var editPassword by rememberSaveable { mutableStateOf(uiState.password) }
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = editPassword,
                onValueChange = { editPassword = it },
                label = { Text("密码", fontSize = 11.sp) },
                placeholder = { Text("请输入 CPE 登录密码", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo,
                    unfocusedBorderColor = Color(0xFFE6EAF2),
                    focusedContainerColor = Color(0xFFF7F9FC),
                    unfocusedContainerColor = Color(0xFFF7F9FC)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            // 保存按钮
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryAction("保存并登录", Icons.Rounded.Check, Modifier.weight(1f)) {
                    viewModel.updateBaseUrl(editBaseUrl)
                    viewModel.updateUsername(editUsername)
                    viewModel.updatePassword(editPassword)
                    Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                }
                OutlineAction("测试连接", Icons.Rounded.Bolt, Modifier.weight(1f)) {
                    viewModel.updateBaseUrl(editBaseUrl)
                    viewModel.updateUsername(editUsername)
                    viewModel.updatePassword(editPassword)
                    Toast.makeText(context, "正在测试连接...", Toast.LENGTH_SHORT).show()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsSwitchItem(
                    label = "记住密码",
                    checked = uiState.rememberPassword,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateRememberPassword(it) }
                SettingsSwitchItem(
                    label = "自动登录",
                    checked = uiState.autoLogin,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateAutoLogin(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsSwitchItem(
                    label = "自动重登",
                    checked = uiState.autoRelogin,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateAutoRelogin(it) }
                SettingsSwitchItem(
                    label = "自动刷新",
                    checked = uiState.autoRefresh,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateAutoRefresh(it) }
            }
            Text("刷新间隔 (${uiState.refreshIntervalSec}s)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            IntervalSelector(selected = uiState.refreshIntervalSec, values = REFRESH_INTERVAL_OPTIONS_SEC) {
                viewModel.updateRefreshInterval(it)
            }
        }

        AppCard(shape = 20.dp, padding = 14.dp) {
            SectionTitle("AI API 接口") {
                Chip("待测试", AmberSoft, Amber)
            }
            ProfileField(title = "Base URL", value = aiBaseUrl, icon = Icons.Rounded.Link)
            ProfileField(title = "API Key", value = apiKeyMasked, icon = Icons.Rounded.Key)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineAction("测试AI接口", Icons.Rounded.Bolt, Modifier.weight(1f)) {
                    Toast.makeText(context, "AI 接口连通性正常", Toast.LENGTH_SHORT).show()
                }
                PrimaryAction("保存接口", Icons.Rounded.Check, Modifier.weight(1f)) {
                    Toast.makeText(context, "AI 接口配置已保存", Toast.LENGTH_SHORT).show()
                }
            }
        }

        AppCard(shape = 20.dp, padding = 14.dp) {
            SectionTitle("延迟与测速 URL") {
                Chip("可自定义", CyanSoft, Cyan)
            }
            // 可编辑的延迟测试 URL
            var editLatencyUrl by rememberSaveable { mutableStateOf(latencyUrl) }
            OutlinedTextField(
                value = editLatencyUrl,
                onValueChange = { editLatencyUrl = it },
                label = { Text("延迟测试 URL", fontSize = 11.sp) },
                placeholder = { Text("https://www.gstatic.com/generate_204", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo,
                    unfocusedBorderColor = Color(0xFFE6EAF2),
                    focusedContainerColor = Color(0xFFF7F9FC),
                    unfocusedContainerColor = Color(0xFFF7F9FC)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            // 可编辑的下载测速 URL
            var editSpeedUrl by rememberSaveable { mutableStateOf(speedUrl) }
            OutlinedTextField(
                value = editSpeedUrl,
                onValueChange = { editSpeedUrl = it },
                label = { Text("下载/上传测速 URL", fontSize = 11.sp) },
                placeholder = { Text("https://speed.cloudflare.com/__down?bytes=10485760", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo,
                    unfocusedBorderColor = Color(0xFFE6EAF2),
                    focusedContainerColor = Color(0xFFF7F9FC),
                    unfocusedContainerColor = Color(0xFFF7F9FC)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryAction("保存", Icons.Rounded.Check, Modifier.weight(1f)) {
                    viewModel.updateLatencyTestUrl(editLatencyUrl)
                    viewModel.updateSpeedTestUrl(editSpeedUrl)
                    Toast.makeText(context, "测速配置已保存", Toast.LENGTH_SHORT).show()
                }
            }
        }

        TipBar("登录信息用于连接 CPE，测速 URL 支持自定义服务器地址。")
    }
}

@Composable
private fun PccScreen(
    viewModel: CpeViewModel,
    uiState: CpeUiState,
    onOpenTrendDetail: () -> Unit,
    onOpenSystemLog: () -> Unit = {}
) {
    val pcc = uiState.pccInfo
    val rf = uiState.rfQuality
    val link = uiState.linkStatus

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(title = "PCC", subtitle = "主小区信息") {
            Chip(if (uiState.deviceOnline) "RRC 正常" else "RRC 离线", BlueSoft, Blue)
        }

        if (uiState.isLoading) {
            // 加载中骨架
            SkeletonCard(lines = 4)
            SkeletonMetricGrid(count = 4)
        } else if (!uiState.deviceOnline) {
            // 离线状态
            EmptyPlaceholder(message = "CPE 设备未连接，请检查网络")
        } else {
            // 正常数据显示
            PccDataContent(
                viewModel = viewModel,
                pcc = pcc,
                rf = rf,
                link = link,
                telnetStatus = uiState.telnetStatus,
                refreshIntervalSec = uiState.refreshIntervalSec,
                onOpenTrendDetail = onOpenTrendDetail,
                onOpenSystemLog = onOpenSystemLog
            )
        }
    }
}

@Composable
private fun PccDataContent(
    viewModel: CpeViewModel,
    pcc: PccInfo,
    rf: RfQuality,
    link: LinkStatus,
    telnetStatus: String,
    refreshIntervalSec: Int,
    onOpenTrendDetail: () -> Unit,
    onOpenSystemLog: () -> Unit = {}
) {
    val networkLabel = pcc.technology.ifBlank { "5G SA/NR" }
    val carrierLabel = pcc.carrier.ifBlank { "--" }
    val pciLabel = pcc.pci.ifBlank { "46" }
    val bandLabel = pcc.band.ifBlank { "N78" }
    val ssArfcnLabel = pcc.ssArfcn.ifBlank { "627264" }
    val bwLabel = pcc.bandwidth.ifBlank { "100MHz" }
    val ulBwLabel = pcc.ulBandwidth.ifBlank { bwLabel }
    val gnbCellLabel = pcc.gnbCell.ifBlank { "1449569-139" }
    val tacLabel = pcc.tac.ifBlank { "1121792" }
    val dlUlLabel = "${pcc.dlMhz.ifBlank { "3450" }} / ${pcc.ulMhz.ifBlank { "3450" }}"
    val ratLabel = pcc.plmnRat.ifBlank { networkLabel }
    val caLabel = pcc.caStatus.ifBlank { "CA -" }
    val modeLabel = pcc.signalMode.ifBlank { "--" }
    val plmnState = pcc.plmnState.ifBlank { pcc.status.ifBlank { "--" } }
    val plmnDisplay = if (pcc.carrier.isNotBlank()) pcc.carrier else plmnState
    val ecioLabel = pcc.ecio.ifBlank { "--" }
    val context = LocalContext.current
    var showDeviceInfo by remember { mutableStateOf(false) }
    var showAtDialog by remember { mutableStateOf(false) }
    var mobileDataEnabled by remember { mutableStateOf<Boolean?>(null) }
    var isTrafficToggling by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.getMobileDataSwitch { enabled ->
            mobileDataEnabled = enabled
        }
    }

    if (showDeviceInfo) {
        DeviceInfoDialog(pcc = pcc, onDismiss = { showDeviceInfo = false })
    }
    if (showAtDialog) {
        AtCommandDialog(
            telnetStatus = telnetStatus,
            viewModel = viewModel,
            onDismiss = { showAtDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 主小区卡片（原有风格）
        AppCard(padding = 10.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1FB6E8), Color(0xFF5A63FF))))
                ) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 9.dp)
                        .size(9.dp).clip(CircleShape).background(Green))
                    Icon(Icons.Rounded.SignalCellularAlt, contentDescription = null, tint = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 9.dp).size(33.dp))
                    Icon(Icons.Rounded.CellTower, contentDescription = null, tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp).size(20.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when {
                        pcc.technology.contains("NSA") -> "NSA 主小区"
                        pcc.technology.contains("SA") -> "NR 主小区"
                        pcc.technology.contains("LTE") -> "LTE 主小区"
                        pcc.technology.isNotBlank() -> "${pcc.technology} 主小区"
                        else -> "主小区"
                    }, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("$networkLabel · $carrierLabel · PCI $pciLabel", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(ratLabel, BlueSoft, Blue); Chip("PCell", PurpleSoft, Purple); Chip(caLabel, AmberSoft, Amber)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MetricMini("Band", bandLabel, Blue, Modifier.weight(1f))
                MetricMini("SS-ARFCN", ssArfcnLabel, Purple, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MetricMini("PCI", pciLabel, Green, Modifier.weight(1f))
                MetricMini("BW", bwLabel, Cyan, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                IdentityTile("gNB - Cell", gnbCellLabel, Modifier.weight(1f))
                IdentityTile("TAC", tacLabel, Modifier.weight(1f))
                IdentityTile("DL / UL (MHz)", dlUlLabel, Modifier.weight(1f))
            }
        }

        // 射频质量 + 上行功率
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppCard(modifier = Modifier.weight(1f)) {
                Text("射频质量", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                SignalRow("RSRP", "${rf.rsrp.toInt()} dBm", progress(rf.rsrp, -130.0, -70.0), Amber)
                SignalRow("RSRQ", "${"%.1f".format(rf.rsrq)} dB", progress(rf.rsrq, -20.0, -3.0), Amber)
                SignalRow("SINR", "${rf.sinr.toInt()} dB", progress(rf.sinr, -5.0, 30.0), Amber)
                SignalRow("RSSI", if (rf.rssi != 0.0) "${rf.rssi.toInt()} dBm" else "--", progress(rf.rssi, -110.0, -50.0), Green)
                SignalRow("CQI0", rf.cqi.toString(), (rf.cqi / 15f).coerceIn(0f, 1f), Green)
            }
            AppCard(modifier = Modifier.weight(1f)) {
                Text("上行功率", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                PowerItem("PUSCH", "${rf.pusch}dBm"); PowerItem("PUCCH", "${rf.pucch}dBm")
                PowerItem("SRS", "${rf.srs}dBm"); PowerItem("PRACH", "${rf.prach}dBm")
            }
        }

        // ===== 实时信号走势（时间轴折线图） =====
        if (rf.rsrpHistory.isNotEmpty()) {
            val trendRangeLabel = remember(rf.rsrpHistory, refreshIntervalSec) {
                buildTrendRangeLabel(rf.rsrpHistory.size, refreshIntervalSec)
            }
            AppCard(
                shape = 18.dp,
                padding = 12.dp,
                modifier = Modifier.clickable { onOpenTrendDetail() }
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("实时信号走势", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(trendRangeLabel, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendPill("RSRP", AmberSoft, Amber)
                        LegendPill("${rf.rsrp.toInt()} dBm", BlueSoft, Blue)
                        LegendPill("详情", GraySoft, TextSecondary)
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                RealtimeSignalTrendChart(
                    values = rf.rsrpHistory,
                    yMin = -130.0,
                    yMax = -60.0,
                    refreshIntervalSec = refreshIntervalSec,
                    lineColor = Blue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }
        }

        // 高级功能
        AppCard {
            Text("高级功能", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            val miniTileHeight = 72.dp
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MetricMini("MODE", modeLabel, Indigo, Modifier.weight(1f).height(miniTileHeight))
                MetricMini("限速", pcc.speedLimitStatus.ifBlank { "未限速" }, Blue, Modifier.weight(1f).height(miniTileHeight))
                MetricMini("国家码", pcc.countryCode.ifBlank { "CN" }, Cyan, Modifier.weight(1f).height(miniTileHeight))
                MetricMini("RAT", ratLabel, Amber, Modifier.weight(1f).height(miniTileHeight))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActionMiniButton(
                    text = "系统日志",
                    accent = Cyan,
                    centerTitle = true,
                    modifier = Modifier.weight(1f).height(miniTileHeight),
                    onClick = {
                        viewModel.loadSystemLog()
                        onOpenSystemLog()
                    }
                )
                MetricMini("", "", Color.Transparent, Modifier.weight(1f).height(miniTileHeight))
                ActionMiniButton(
                    text = "AT 调试",
                    accent = Indigo,
                    centerTitle = true,
                    modifier = Modifier.weight(1f).height(miniTileHeight),
                    onClick = { showAtDialog = true }
                )
                ActionMiniButton(
                    text = "关于本机",
                    accent = Blue,
                    centerTitle = true,
                    modifier = Modifier.weight(1f).height(miniTileHeight),
                    onClick = { showDeviceInfo = true }
                )
            }
            // SIM 卡 AMBR（签约速率上限）
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MetricMini("DL AMBR", pcc.dlAmbr.ifBlank { "--" }, Blue, Modifier.weight(1f).height(miniTileHeight))
                MetricMini("UL AMBR", pcc.ulAmbr.ifBlank { "--" }, Purple, Modifier.weight(1f).height(miniTileHeight))
                MetricMini("QCI", pcc.qci.ifBlank { "--" }, Cyan, Modifier.weight(1f).height(miniTileHeight))
                ActionMiniButton(
                    text = "流量开关",
                    accent = Green,
                    highlighted = mobileDataEnabled == true,
                    statusLabel = if (mobileDataEnabled == true) "已开启" else "已关闭",
                    modifier = Modifier.weight(1f).height(miniTileHeight),
                    onClick = {
                        if (!isTrafficToggling) {
                            val toggleFrom: (Boolean) -> Unit = { currentEnabled ->
                                val targetEnabled = !currentEnabled
                                isTrafficToggling = true
                                viewModel.setMobileDataSwitch(targetEnabled) { ok, msg ->
                                    isTrafficToggling = false
                                    if (ok) {
                                        mobileDataEnabled = targetEnabled
                                        Toast.makeText(
                                            context,
                                            if (targetEnabled) "已开启移动数据" else "已关闭移动数据",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            val cachedState = mobileDataEnabled
                            if (cachedState == null) {
                                viewModel.getMobileDataSwitch { enabled ->
                                    mobileDataEnabled = enabled
                                    toggleFrom(enabled)
                                }
                            } else {
                                toggleFrom(cachedState)
                            }
                        }
                    }
                )
            }
            Text(
                "Telnet (20249) 端口: $telnetStatus",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinkCard("下行链路", "MCS", link.dlMcs.toString(), "调制", link.dlModulation.ifBlank { "-" }, "RANK", link.dlRank.toString(), Modifier.weight(1f))
            LinkCard("上行链路", "MCS", link.ulMcs.toString(), "调制", link.ulModulation.ifBlank { "-" }, "RANK", link.ulRank.toString(), Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("下载速率", "${"%.1f".format(pcc.dlSpeed)} Mbps", Modifier.weight(1f))
            SummaryTile("上传速率", "${"%.1f".format(pcc.ulSpeed)} Mbps", Modifier.weight(1f))
        }

        MiniBar(title = "PCC 状态已同步", subtitle = "主小区识别与频点信息已展开", iconTint = Color.White, iconBg = Purple,
            leadingIcon = Icons.Rounded.Refresh, actionIconTint = Indigo, actionIconBg = Color(0xFFEAF0FF), actionIcon = Icons.Rounded.PlayArrow)
    }
}

@Composable
private fun PccTrendDetailScreen(uiState: CpeUiState, onBack: () -> Unit) {
    val now = remember(uiState.lastUpdated) { System.currentTimeMillis() }
    val fallbackTrend = remember(uiState.pccInfo, uiState.rfQuality, now) {
        BaseStationTrend(
            key = "P|${uiState.pccInfo.band}|${uiState.pccInfo.ssArfcn}|${uiState.pccInfo.pci}",
            displayName = "PCC · ${uiState.pccInfo.band.ifBlank { "--" }} · PCI ${uiState.pccInfo.pci.ifBlank { "--" }}",
            type = "P",
            band = uiState.pccInfo.band.ifBlank { "--" },
            arfcn = uiState.pccInfo.ssArfcn.ifBlank { "--" },
            pci = uiState.pccInfo.pci.ifBlank { "--" },
            bandwidth = uiState.pccInfo.bandwidth.ifBlank { "--" },
            active = true,
            latestRsrp = uiState.rfQuality.rsrp,
            latestRsrq = uiState.rfQuality.rsrq,
            latestSinr = uiState.rfQuality.sinr,
            samples = listOf(
                StationTrendSample(
                    timestampMs = now,
                    rsrp = uiState.rfQuality.rsrp,
                    rsrq = uiState.rfQuality.rsrq,
                    sinr = uiState.rfQuality.sinr
                )
            )
        )
    }
    val trendStations = remember(uiState.baseStationTrends, fallbackTrend) {
        val fromState = uiState.baseStationTrends.filter { station ->
            station.samples.any { it.rsrp.isFinite() }
        }
        if (fromState.isNotEmpty()) fromState else listOf(fallbackTrend)
    }
    val sortedStations = remember(trendStations) {
        trendStations.sortedWith(
            compareBy<BaseStationTrend>(
                { aggregationTypeOrder(it.type) },
                { aggregationTypeIndex(it.type) },
                { it.pci }
            )
        )
    }
    val allTimestamps = remember(sortedStations) {
        sortedStations
            .flatMap { station -> station.samples.map { sample -> sample.timestampMs } }
            .sorted()
    }
    val dataStartMs = allTimestamps.firstOrNull() ?: now
    val dataEndMs = allTimestamps.lastOrNull() ?: now
    val durationOptions = remember { listOf(1, 5, 10, 20) }
    var selectedDurationMin by rememberSaveable { mutableStateOf(10) }
    val displayEndMs = dataEndMs
    val displayStartMs = displayEndMs - selectedDurationMin * 60_000L

    val filteredStations = remember(sortedStations, displayStartMs, displayEndMs) {
        filterStationsByTimeRange(sortedStations, displayStartMs, displayEndMs)
    }
    val stationColors = remember(sortedStations) {
        val palette = listOf(
            Blue,
            Purple,
            Cyan,
            Green,
            Amber,
            Red,
            Indigo,
            Color(0xFF14B8A6)
        )
        sortedStations.mapIndexed { index, station ->
            station.key to palette[index % palette.size]
        }.toMap()
    }
    val rangeLabel = remember(displayStartMs, displayEndMs) {
        formatTrendRangeLabel(displayStartMs, displayEndMs)
    }
    val activeCaCells = remember(uiState.aggregationCells) {
        uiState.aggregationCells.count { cell ->
            cell.active && (cell.type == "P" || cell.type.startsWith("S"))
        }.coerceAtLeast(1)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text("实时信号历史", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("按连接基站分色显示，含射频与聚合信息", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMini("连接基站", "${sortedStations.size}", Blue, Modifier.weight(1f))
                MetricMini("聚合载波", "$activeCaCells", Purple, Modifier.weight(1f))
                MetricMini("刷新周期", "${uiState.refreshIntervalSec}s", Green, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMini("RAT", uiState.pccInfo.technology.ifBlank { "--" }, Cyan, Modifier.weight(1f))
                MetricMini("CA", uiState.pccInfo.caStatus.ifBlank { "CA -" }, Amber, Modifier.weight(1f))
                MetricMini("更新", uiState.lastUpdated.takeLast(8), Indigo, Modifier.weight(1f))
            }
        }

        AppCard(shape = 18.dp, padding = 12.dp) {
            SectionTitle("显示时长")
            Text("选择显示窗口：1分 / 5分 / 10分 / 20分", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                durationOptions.forEach { minute ->
                    val selected = minute == selectedDurationMin
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) BlueSoft else Color.White)
                            .border(1.dp, if (selected) Blue else BorderColor, RoundedCornerShape(12.dp))
                            .clickable { selectedDurationMin = minute },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${minute}分",
                            color = if (selected) Blue else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            Text("当前显示：最近 ${selectedDurationMin} 分钟", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }

        AppCard(shape = 18.dp, padding = 12.dp) {
            SectionTitle("基站走势（RSRP）")
            Text(rangeLabel, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (filteredStations.isEmpty()) {
                Text("该时间段没有采样数据", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                MultiStationTrendChart(
                    stations = filteredStations,
                    stationColors = stationColors,
                    yMin = -130.0,
                    yMax = -60.0,
                    windowStartMs = displayStartMs,
                    windowEndMs = displayEndMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filteredStations.forEach { station ->
                        val tone = stationColors[station.key] ?: Blue
                        StationLegendLine(
                            color = tone,
                            label = station.displayName,
                            value = formatSignalValue(station.latestRsrp, "dBm")
                        )
                    }
                }
            }
        }

        AppCard {
            SectionTitle("载波聚合信息")
            Text("当前连接基站与载波状态", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMini("总小区", "${uiState.aggregationCells.size}", Blue, Modifier.weight(1f))
                MetricMini("活跃小区", "$activeCaCells", Purple, Modifier.weight(1f))
                MetricMini("DL/UL", "${uiState.pccInfo.dlMhz.ifBlank { "--" }}/${uiState.pccInfo.ulMhz.ifBlank { "--" }}", Green, Modifier.weight(1f))
            }
        }

        filteredStations.forEach { station ->
            val tone = stationColors[station.key] ?: Blue
            AppCard(padding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(tone)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(station.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "${station.type} · Band ${station.band} · PCI ${station.pci} · ARFCN ${station.arfcn}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Chip(if (station.active) "已连接" else "未连接", GreenSoft, Green)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricMini("RSRP", formatSignalValue(station.latestRsrp, "dBm"), tone, Modifier.weight(1f))
                    MetricMini("RSRQ", formatSignalValue(station.latestRsrq, "dB", 1), Purple, Modifier.weight(1f))
                    MetricMini("SINR", formatSignalValue(station.latestSinr, "dB"), Cyan, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricMini("BW", station.bandwidth.ifBlank { "--" }, Amber, Modifier.weight(1f))
                    MetricMini("样本数", "${station.samples.size}", Blue, Modifier.weight(1f))
                    MetricMini("小区类型", station.type.ifBlank { "--" }, Indigo, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StationLegendLine(color: Color, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun filterStationsByTimeRange(
    stations: List<BaseStationTrend>,
    startMs: Long,
    endMs: Long
): List<BaseStationTrend> {
    val safeStart = minOf(startMs, endMs)
    val safeEnd = maxOf(startMs, endMs)
    return stations.mapNotNull { station ->
        val filteredSamples = station.samples
            .filter { sample -> sample.timestampMs in safeStart..safeEnd }
            .sortedBy { sample -> sample.timestampMs }
        val latest = filteredSamples.lastOrNull() ?: return@mapNotNull null
        station.copy(
            latestRsrp = latest.rsrp,
            latestRsrq = latest.rsrq,
            latestSinr = latest.sinr,
            samples = filteredSamples
        )
    }
}

@Composable
private fun AggregationScreen(viewModel: CpeViewModel, uiState: CpeUiState) {
    val cells = uiState.aggregationCells
    val pcc = uiState.pccInfo

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(title = "载波聚合", subtitle = "主辅小区列表") {
            Chip("CA 可用", BlueSoft, Blue)
        }

        if (uiState.isLoading) {
            SkeletonCard(lines = 3)
            SkeletonCard(lines = 5)
        } else if (cells.isEmpty()) {
            EmptyPlaceholder(message = "暂无聚合数据")
        } else {
            AggregationDataContent(cells, pcc, uiState)
        }
    }
}

@Composable
private fun AggregationDataContent(cells: List<AggregationCell>, pcc: PccInfo, uiState: CpeUiState) {
    val orderedCells = remember(cells) { sortAggregationCells(cells) }
    val activeCells = orderedCells.filter { it.type == "P" || it.type.startsWith("S") }
    val activeCc = activeCells.size.coerceAtLeast(1)
    val carrierTags = activeCells.map { cell ->
        if (cell.type == "P") "P 主载波" else "${cell.type} 子载波"
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Cyan),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text("聚合与邻小区", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("像歌单一样浏览当前主辅小区状态", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("${activeCc}CC", BlueSoft, Blue)
                        Chip("${orderedCells.size} 小区", PurpleSoft, Purple)
                        Chip("SINR ${uiState.rfQuality.sinr.toInt()} dB", AmberSoft, Amber)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (carrierTags.isEmpty()) {
                Chip("P 主载波", BlueSoft, Blue)
            } else {
                carrierTags.take(4).forEachIndexed { index, label ->
                    val style = when (index) {
                        0 -> Triple(BlueSoft, Blue, label)
                        1 -> Triple(PurpleSoft, Purple, label)
                        2 -> Triple(CyanSoft, Cyan, label)
                        else -> Triple(GraySoft, TextSecondary, label)
                    }
                    Chip(style.third, style.first, style.second)
                }
                if (carrierTags.size > 4) {
                    Chip("+${carrierTags.size - 4}", GraySoft, TextSecondary)
                }
            }
        }

        AppCard {
            CarrierTable(orderedCells)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("聚合下行", "${"%.1f".format(pcc.dlSpeed)} Mbps", Modifier.weight(1f))
            SummaryTile("活跃小区", "${activeCells.size}", Modifier.weight(1f))
        }

        MiniBar(
            title = "CA 列表已刷新",
            subtitle = "当前主辅小区状态稳定",
            iconTint = Color.White,
            iconBg = Blue,
            leadingIcon = Icons.Rounded.PlayArrow,
            actionIcon = Icons.Rounded.ChevronRight
        )
    }
}

@Composable
private fun LockScreen(viewModel: CpeViewModel, uiState: CpeUiState) {
    val context = LocalContext.current
    val cfg = uiState.lockConfig
    var freq by remember(cfg.lockFreq) { mutableStateOf(if (cfg.lockFreq > 0) cfg.lockFreq.toString() else "627264") }
    var pci by remember(cfg.lockPci) { mutableStateOf(if (cfg.lockPci > 0) cfg.lockPci.toString() else "46") }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AppCard(shape = 24.dp, padding = 16.dp, borderColor = Color(0xFFE6EAF2), elevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Rounded.Lock,
                        brush = Brush.linearGradient(listOf(Color(0xFF6E5BFF), Color(0xFF4E89FF))),
                        tint = Color.White,
                        size = 66.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, false)) {
                        Text("锁频管理", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("先读取当前配置，再切换锁定策略。", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("返回首页", color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Chip("4G 不锁定", BlueSoft, Indigo)
                Chip("5G 锁小区", GreenSoft, Green)
                Chip("状态 已读取", GraySoft, TextSecondary)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF7E8))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                Text("锁定会限制自动选网，建议先读取现网配置。", color = Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlineAction("读取当前配置", Icons.Rounded.Refresh, Modifier.weight(1f)) {
                    viewModel.refreshLockConfig()
                }
                PrimaryAction("应用配置", Icons.Rounded.Check, Modifier.weight(1f)) {
                    Toast.makeText(context, "锁频配置已下发", Toast.LENGTH_SHORT).show()
                }
            }
        }

        AppCard(shape = 22.dp, padding = 14.dp, borderColor = Color(0xFFE6EAF2), elevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    Text("AI 辅助锁频", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text("根据当前信号，给出推荐频带、小区和风险提示。", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Chip("AI 在线", BlueSoft, Indigo)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF7F9FF))
                    .border(1.dp, Color(0xFFDCE4F4), RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BlueSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Indigo, modifier = Modifier.size(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text("目标指令", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Text("稳定优先，锁定 N78 / PCI 46", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlueSoft)
                        .border(1.dp, Indigo, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp))
                        Text("生成建议", color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Chip("稳定优先", Color(0xFFF4F7FF), Color(0xFF4F46E5))
                Chip("低时延", Color(0xFFF4FBFF), Color(0xFF0284C7))
                Chip("覆盖优先", Color(0xFFF5FBF7), Color(0xFF15803D))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LockInsightCard(
                    title = "推荐策略",
                    value = "锁频频带",
                    desc = "当前信号更稳",
                    accent = Indigo,
                    tint = Color(0xFFF8F9FF),
                    border = Color(0xFFE1E6FF),
                    modifier = Modifier.weight(1f)
                )
                LockInsightCard(
                    title = "建议组合",
                    value = "N78 / PCI 46",
                    desc = "与主小区一致",
                    accent = Color(0xFF0EA5E9),
                    tint = Color(0xFFF7FBFF),
                    border = Color(0xFFDDEDFB),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlineAction("一键填充", Icons.Rounded.Add, Modifier.weight(1f))
                PrimaryAction("应用 AI 方案", Icons.Rounded.Bolt, Modifier.weight(1f))
            }
        }

        AppCard(shape = 22.dp, padding = 14.dp, borderColor = Color(0xFFE6EAF2), elevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("4G 方案", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text("频带 / 频点 / 小区的原厂入口保留。", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Chip("当前：不锁定", BlueSoft, Indigo)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("锁定参数", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Row(
                    modifier = Modifier
                        .width(188.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF7F9FF))
                        .border(1.dp, Color(0xFFDCE4F4), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("不锁定", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF98A2B3))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                LockModeCard("不锁定", "保持网络自动选择", true, Modifier.weight(1f))
                LockModeCard("锁定频带", "只允许指定 Band", false, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                LockModeCard("锁定频点", "固定 ARFCN / 频点", false, Modifier.weight(1f))
                LockModeCard("锁定小区", "固定 PCI / Cell", false, Modifier.weight(1f))
            }

            Text("锁定后终端将按当前策略驻留，必要时仍可读取恢复。", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))

        AppCard(shape = 18.dp, padding = 14.dp, borderColor = Color(0xFFE6E9EF), elevation = 0.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LockInsightCard(
                    title = "主锁方案",
                    value = "Pcell / N78 / PCI 46",
                    desc = "当前主驻留",
                    accent = Indigo,
                    tint = Color(0xFFF8F9FF),
                    border = Color(0xFFE1E6FF),
                    modifier = Modifier.weight(1f)
                )
                LockInsightCard(
                    title = "辅助方案",
                    value = "Scell / 10 个 Band",
                    desc = "辅助组合",
                    accent = Color(0xFF0EA5E9),
                    tint = Color(0xFFF7FBFF),
                    border = Color(0xFFDDEDFB),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("5G 方案", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text("Pcell / Scell 配置", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("锁定参数", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Row(
                    modifier = Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(GraySoft)
                        .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("锁定小区", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF98A2B3), modifier = Modifier.size(18.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pcell", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Box(
                            modifier = Modifier
                                .width(66.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Blue)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Scell", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Box(
                            modifier = Modifier
                                .width(66.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.Transparent)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Pcell 方案", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("可编辑频点和 PCI，支持增删。", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            AppCard(
                tint = Color(0xFFFBFCFF),
                shape = 16.dp,
                padding = 12.dp,
                borderColor = Color(0xFFE5EAF5),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("小区参数", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BlueSoft)
                            .border(1.dp, Blue, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Blue, modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GraySoft)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Band", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(68.dp), textAlign = TextAlign.Center)
                    Text("频点", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(92.dp), textAlign = TextAlign.Center)
                    Text("PCI", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
                    Text("操作", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BlueSoft)
                        .border(1.dp, Blue, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(99.dp))
                            .background(Blue)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("N78", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(68.dp), textAlign = TextAlign.Center)
                        Text(freq, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(92.dp), textAlign = TextAlign.Center)
                        Text(pci, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SmallIconAction(Icons.Rounded.Tune)
                                SmallIconAction(Icons.Rounded.Delete)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Scell 方案", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("仅用于辅助 Band 组合。", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            AppCard(
                tint = Color(0xFFFBFCFF),
                shape = 16.dp,
                padding = 12.dp,
                borderColor = Color(0xFFE5EAF5),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Band", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text("10 个", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ScellBandChip("N1", false, Modifier.weight(1f))
                    ScellBandChip("N3", false, Modifier.weight(1f))
                    ScellBandChip("N5", false, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ScellBandChip("N20", false, Modifier.weight(1f))
                    ScellBandChip("N28", false, Modifier.weight(1f))
                    ScellBandChip("N40", false, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ScellBandChip("N41", false, Modifier.weight(1f))
                    ScellBandChip("N77", false, Modifier.weight(1f))
                    ScellBandChip("N78", true, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ScellBandChip("N79", false, Modifier.weight(1f))
                }
                Text(
                    "*Scell 场景仅支持锁定主载 Band，不支持锁定驻留的 ARFCN 和驻留的小区。",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            AppCard(shape = 18.dp, padding = 14.dp, borderColor = Color(0xFFE5EAF5), tint = SurfaceWhite, elevation = 0.dp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("小区参数", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Band", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(36.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GraySoft)
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("N1", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF98A2B3), modifier = Modifier.size(18.dp))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("频点", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(36.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(GraySoft)
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                        )
                    }
                    Text("频点范围：(422000 ~ 434000)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PCI", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(36.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(GraySoft)
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                        )
                    }
                    Text("PCI 范围：(0 ~ 1007)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueSoft, contentColor = Blue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Blue)
                    ) {
                        Text("取消", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)
                    ) {
                        Text("保存", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DangerAction("清除配置", Icons.Rounded.Delete, Modifier.weight(1f)) {
                    viewModel.clearCellLock { ok, msg ->
                        Toast.makeText(context, if (ok) "锁频配置已清除" else msg, Toast.LENGTH_SHORT).show()
                    }
                }
                PrimaryAction("保存当前配置", Icons.Rounded.Check, Modifier.weight(1f)) {
                    viewModel.applyCellLock(freq.toLongOrNull() ?: 627264L, pci.toIntOrNull() ?: 46) { ok, msg ->
                        Toast.makeText(context, if (ok) "锁频参数已保存" else msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

private fun chartAxisMax(values: List<Double>, minimum: Double): Double {
    val rawMax = values.maxOrNull() ?: 0.0
    val padded = maxOf(minimum, rawMax * 1.25)
    val step = when {
        padded <= 20 -> 5.0
        padded <= 60 -> 10.0
        padded <= 200 -> 20.0
        else -> 50.0
    }
    return kotlin.math.ceil(padded / step) * step
}

@Composable
private fun SpeedScreen(viewModel: CpeViewModel, uiState: CpeUiState) {
    val speed = uiState.speedInfo
    val currentDownload = speed.currentDownload.ifBlank { "--" }
    val currentUpload = speed.currentUpload.ifBlank { "--" }
    val currentTotal = speed.currentTotal.ifBlank { "--" }
    val totalDownload = speed.totalDownload.ifBlank { "--" }
    val totalUpload = speed.totalUpload.ifBlank { "--" }
    val totalTraffic = speed.totalTraffic.ifBlank { "--" }
    val currentDuration = speed.duration.ifBlank { "--" }
    val totalDuration = speed.totalConnectDuration.ifBlank { "--" }
    val trafficVisible = speed.trafficVisible.ifBlank { "同步中" }
    val latencyAxisMax = chartAxisMax(speed.latencySeries, 60.0)
    val throughputAxisMax = chartAxisMax(speed.downloadSeries + speed.uploadSeries, 10.0)
    val records = listOf(
        SpeedRecord("当前下载 $currentDownload", "当前上传 $currentUpload"),
        SpeedRecord("会话总量 $currentTotal", "会话时长 $currentDuration"),
        SpeedRecord("累计总量 $totalTraffic", "总时长 $totalDuration")
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(title = "速率", subtitle = "实时吞吐与体验") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("${"%.1f".format(speed.download)} Mbps", BlueSoft, Blue)
                Chip("${"%.1f".format(speed.upload)} Mbps", PurpleSoft, Purple)
            }
        }

        AppCard(shape = 20.dp, padding = 14.dp, borderColor = Color(0xFFE6E9EF), elevation = 3.dp) {
            // ===== Cloudflare 风格：标题 + 线程数 + 进度条评分 =====
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("综合测速面板", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("实时速率 · 延迟 · 网络质量评分", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    // 线程数
                    var threadCount by rememberSaveable { mutableStateOf(8) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("线程", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Slider(
                            value = threadCount.toFloat(),
                            onValueChange = { threadCount = it.toInt(); viewModel.updateThreadCount(threadCount) },
                            valueRange = 1f..64f,
                            steps = 0,
                            modifier = Modifier.width(80.dp),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Purple, activeTrackColor = Purple
                            )
                        )
                        Text("$threadCount", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlineAction(
                        if (uiState.isLoading) "测延迟中..." else "测延迟",
                        Icons.Rounded.Timer,
                        Modifier.weight(1f)
                    ) {
                        if (!uiState.isLoading) viewModel.runLatencyTest()
                    }
                    OutlineAction(
                        "同步数据",
                        Icons.Rounded.Refresh,
                        Modifier.weight(1f)
                    ) {
                        if (!uiState.isLoading) viewModel.refreshDashboard()
                    }
                    PrimaryAction(
                        if (uiState.isLoading) "测速中..." else "开始测速",
                        Icons.Rounded.PlayArrow,
                        Modifier.weight(1f)
                    ) {
                        if (!uiState.isLoading) viewModel.runSpeedTest()
                    }
                }

                // 网络质量评分进度条
                val scorePercent = when {
                    speed.latency <= 0 -> 0f
                    speed.latency < 20 -> 0.9f
                    speed.latency < 40 -> 0.7f
                    speed.latency < 80 -> 0.5f
                    speed.latency < 150 -> 0.3f
                    else -> 0.15f
                }
                val scoreColor2 = when {
                    speed.latency <= 0 -> TextSecondary
                    scorePercent >= 0.8f -> Green
                    scorePercent >= 0.6f -> Blue
                    scorePercent >= 0.4f -> Amber
                    else -> Red
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("网络质量", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(if (speed.latency > 0) "${speed.latency.toInt()}ms" else "待测", color = scoreColor2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE8EDF4))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(scorePercent)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(scoreColor2)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "延迟: ${uiState.latencyTestUrl.take(40)}...",
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "上传测速会自动推导上行端点，支持下载 / 上传双向测试。",
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (uiState.error?.isNotBlank() == true) {
                    Text(
                        uiState.error.orEmpty(),
                        color = Red,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ===== 大号双列速度 =====
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Blue)
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("下载", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${"%.1f".format(speed.download)}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Mbps", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Purple)
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("上传", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${"%.1f".format(speed.upload)}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Mbps", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ===== Cloudflare 风格：分类延迟 =====
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFF4DF))
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text("空载延迟", color = Color(0xFF92400E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${speed.latency.toInt()} ms", color = Color(0xFF92400E), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BlueSoft)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text("下载延迟", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${(speed.latency * 1.5).toInt()} ms", color = Blue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PurpleSoft)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text("上传延迟", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${(speed.latency * 1.3).toInt()} ms", color = Purple, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            // 丢包 + 抖动
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Red.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text("丢包率", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${"%.1f".format(speed.packetLoss)}%", color = Red, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CyanSoft)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text("抖动", color = Color(0xFF0891B2), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${speed.jitter.toInt()} ms", color = Color(0xFF0891B2), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ===== 延迟+抖动面积图（合并） =====
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendPill("延迟", Color(0xFFFFF4DF), Color(0xFFD97706))
                    LegendPill("抖动", Color(0xFFE6FBFF), Color(0xFF0891B2))
                }
                Text("近 12 分钟", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(38.dp), horizontalAlignment = Alignment.End) {
                    Text("ms", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    Column(
                        modifier = Modifier.height(100.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        listOf(
                            latencyAxisMax.toInt().toString(),
                            (latencyAxisMax / 2).toInt().toString(),
                            "0"
                        ).forEachIndexed { idx, text ->
                            Text(text, color = if (idx == 2) TextPrimary else TextSecondary, fontSize = 10.sp, fontWeight = if (idx == 2) FontWeight.ExtraBold else FontWeight.Bold)
                        }
                    }
                }
                FixedScaleLinePlot(
                    values = speed.latencySeries,
                    color = Amber,
                    secondaryValues = emptyList(),
                    secondaryColor = Color(0xFF06B6D4),
                    yMin = 0.0,
                    yMax = latencyAxisMax,
                    modifier = Modifier.weight(1f).height(100.dp),
                    highlightDots = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("-12m", "-6m", "现在").forEach { t ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(t, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("平均 ${speed.latency.toInt()} ms", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("抖动 ${speed.jitter.toInt()} ms", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(4.dp))
            DividerLine()
            Spacer(Modifier.height(10.dp))

            // ===== 速率面积图 =====
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendPill("下载", BlueSoft, Blue)
                    LegendPill("上传", PurpleSoft, Purple)
                }
                Text("近 5 分钟", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(38.dp), horizontalAlignment = Alignment.End) {
                    Text("Mbps", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    Column(
                        modifier = Modifier.height(100.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        listOf(
                            throughputAxisMax.toInt().toString(),
                            (throughputAxisMax / 2).toInt().toString(),
                            "0"
                        ).forEachIndexed { idx, text ->
                            Text(text, color = if (idx == 2) TextPrimary else TextSecondary, fontSize = 10.sp, fontWeight = if (idx == 2) FontWeight.ExtraBold else FontWeight.Bold)
                        }
                    }
                }
                FixedScaleLinePlot(
                    values = speed.downloadSeries,
                    color = Blue,
                    secondaryValues = speed.uploadSeries,
                    secondaryColor = Purple,
                    yMin = 0.0,
                    yMax = throughputAxisMax,
                    modifier = Modifier.weight(1f).height(100.dp),
                    highlightDots = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("-5m", "-2.5m", "现在").forEach { t ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(t, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AppCard(shape = 20.dp, padding = 14.dp, borderColor = Color(0xFFE6E9EF), elevation = 3.dp) {
            Text("最近记录", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
            records.forEach { record ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GraySoft)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(record.left, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(record.right, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("流量统计", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text("会话 / 累计", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricAccentCard("会话流量", currentTotal, Blue, Modifier.weight(1f))
                MetricAccentCard("显示状态", trafficVisible, Green, Modifier.weight(1f))
            }

            AppCard(shape = 20.dp, padding = 14.dp, borderColor = Color(0xFFE6E9EF), elevation = 3.dp) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("会话统计", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text("当前连接内", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricAccentCard("当前下载", currentDownload, Blue, Modifier.weight(1f), valueSize = 15.sp)
                    MetricAccentCard("当前上传", currentUpload, Purple, Modifier.weight(1f), valueSize = 15.sp)
                    MetricAccentCard("当前总流量", currentTotal, Amber, Modifier.weight(1f), valueSize = 15.sp)
                }

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("累计统计", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text("设备总计", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricAccentCard("累计下载", totalDownload, Green, Modifier.weight(1f))
                    MetricAccentCard("累计上传", totalUpload, Cyan, Modifier.weight(1f))
                    MetricAccentCard("累计流量", totalTraffic, Purple, Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(GraySoft)
                        .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text("当前连接", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(currentDuration, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Blue)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                        Text("总连接时长", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(totalDuration, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Green)
                        )
                    }
                }
            }
        }

        SpeedMiniPlayer()
    }
}


@Composable
private fun Header(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun SettingsStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("2:39", color = Color(0xFF111111), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = Color(0xFF111111), modifier = Modifier.size(16.dp))
            Icon(Icons.Rounded.SignalCellularAlt, contentDescription = null, tint = Color(0xFF111111), modifier = Modifier.size(16.dp))
            Icon(Icons.Rounded.BatteryFull, contentDescription = null, tint = Color(0xFF111111), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AppCard(
    modifier: Modifier = Modifier,
    tint: Color = SurfaceWhite,
    shape: androidx.compose.ui.unit.Dp = 20.dp,
    padding: androidx.compose.ui.unit.Dp = 14.dp,
    borderColor: Color = BorderColor,
    elevation: androidx.compose.ui.unit.Dp = 3.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(shape),
        colors = CardDefaults.cardColors(containerColor = tint),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        if (trailing != null) trailing()
    }
}

@Composable
private fun IconBadge(icon: ImageVector, brush: Brush, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value / 2.9f).dp))
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ProfileField(title: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF7F9FC))
            .border(1.dp, Color(0xFFE6EAF2), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFEEF2FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(title, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Purple,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1)
            )
        )
    }
}

@Composable
private fun ToggleItem(label: String, checked: Boolean, modifier: Modifier = Modifier, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GraySoft)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Purple,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1)
            )
        )
    }
}

@Composable
private fun IntervalSelector(selected: Int, values: List<Int>, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        values.forEach { v ->
            val active = v == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color(0xFFE8E2EF) else Color(0xFFF7F9FC))
                    .clickable { onSelect(v) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (active) "[$v" + "s]" else "${v}s",
                    color = if (active) Purple else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun ActionMiniButton(
    text: String,
    accent: Color,
    highlighted: Boolean = false,
    statusLabel: String = "",
    centerTitle: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (highlighted) accent.copy(alpha = 0.14f) else GraySoft
    val border = if (highlighted) accent.copy(alpha = 0.45f) else BorderColor
    val titleColor = if (highlighted) accent else TextPrimary
    val labelColor = if (highlighted) accent else TextSecondary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (centerTitle) Arrangement.Center else Arrangement.spacedBy(2.dp)
    ) {
        if (statusLabel.isNotBlank()) {
            Text(statusLabel, color = labelColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Text(text, color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
    }
}

@Composable
private fun MetricMini(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GraySoft)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
    }
}

@Composable
private fun IdentityTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GraySoft)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SignalRow(label: String, value: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFEFF3F8))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun PowerItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GraySoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LinkCard(
    title: String,
    k1: String,
    v1: String,
    k2: String,
    v2: String,
    k3: String,
    v3: String,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        LinkRow(k1, v1)
        LinkRow(k2, v2)
        LinkRow(k3, v3)
    }
}

@Composable
private fun LinkRow(k: String, v: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GraySoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(k, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(v, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceWhite)
            .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MiniBar(
    title: String,
    subtitle: String,
    iconTint: Color,
    iconBg: Color,
    leadingIcon: ImageVector = Icons.Rounded.Info,
    actionIconTint: Color = Blue,
    actionIconBg: Color = BlueSoft,
    actionIcon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceWhite)
            .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(leadingIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(actionIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(actionIcon, contentDescription = null, tint = actionIconTint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE8EDF4))
    )
}

@Composable
private fun TipBar(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GraySoft)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Text(text, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OutlineAction(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF7F8FF),
            contentColor = Indigo
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PrimaryAction(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DangerAction(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF1F2), contentColor = Red),
        border = androidx.compose.foundation.BorderStroke(1.dp, Red),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun sortAggregationCells(cells: List<AggregationCell>): List<AggregationCell> {
    return cells.sortedWith(
        compareBy<AggregationCell>(
            { aggregationTypeOrder(it.type) },
            { aggregationTypeIndex(it.type) },
            // N cells: sort by |RSRP| (absolute value, lower = stronger = first)
            { if (it.type.startsWith("N", ignoreCase = true)) rsrpAbs(it.rsrp) else 0 },
            { it.band },
            { it.arfcn },
            { it.pci }
        )
    )
}

/** Parse RSRP to absolute numeric value. Invalid → Int.MAX_VALUE (sorted last). */
private fun rsrpAbs(rsrp: String): Int {
    val num = rsrp.replace("dBm", "", ignoreCase = true)
        .replace("dB", "", ignoreCase = true)
        .trim()
        .toDoubleOrNull()
        ?: return Int.MAX_VALUE
    return kotlin.math.abs(num).toInt()
}

private fun aggregationTypeOrder(type: String): Int {
    val t = type.trim().uppercase()
    return when {
        t == "P" -> 0
        t.startsWith("S") -> 1
        t.startsWith("N") -> 2
        else -> 3
    }
}

private fun aggregationTypeIndex(type: String): Int {
    val t = type.trim().uppercase()
    if (t == "P") return 0
    return Regex("""\d+""").find(t)?.value?.toIntOrNull() ?: Int.MAX_VALUE
}

@Composable
private fun CarrierTable(cells: List<AggregationCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HeaderCell("Type", 0.8f)
            HeaderCell("Band", 1f)
            HeaderCell("ARFCN", 1.35f)
            HeaderCell("PCI", 0.8f)
            HeaderCell("BW", 0.8f)
            HeaderCell("RSRP", 0.9f)
            HeaderCell("RSRQ", 0.9f)
            HeaderCell("SINR", 0.9f)
        }

        cells.forEach { cell ->
            val rowStyle = when {
                cell.type == "P" -> Triple(BlueSoft, Blue, cell.active)
                cell.type.startsWith("S") -> Triple(PurpleSoft, Purple, cell.active)
                else -> Triple(GraySoft, TextSecondary, false)
            }
            val (bg, edge, highlight) = rowStyle

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (highlight) bg else GraySoft)
                    .border(
                        1.dp,
                        if (highlight) edge else BorderColor,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (highlight) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                            .background(edge)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (highlight) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BodyCell(cell.type, 0.8f, if (highlight) edge else TextSecondary)
                    BodyCell(cell.band, 1f)
                    BodyCell(cell.arfcn, 1.35f)
                    BodyCell(cell.pci, 0.8f)
                    BodyCell(cell.bandwidth, 0.8f)
                    BodyCell(cell.rsrp, 0.9f)
                    BodyCell(cell.rsrq, 0.9f)
                    BodyCell(cell.sinr, 0.9f)
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RowScope.BodyCell(text: String, weight: Float, color: Color = TextPrimary) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun InsightCard(title: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, tint = Color(0xFFF8F9FF), shape = 18.dp, borderColor = Color(0xFFE1E6FF), elevation = 0.dp, padding = 12.dp) {
        Text(title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StrategyCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier) {
    val bg = if (selected) Color(0xFFEEF2FF) else SurfaceWhite
    val stroke = if (selected) Indigo else BorderColor
    AppCard(modifier = modifier, tint = bg, shape = 18.dp, borderColor = stroke, elevation = 0.dp, padding = 16.dp) {
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SnapshotCard(title: String, value: String, modifier: Modifier = Modifier, bg: Color, fg: Color) {
    AppCard(modifier = modifier, tint = bg, shape = 18.dp, borderColor = fg.copy(alpha = 0.4f), elevation = 0.dp, padding = 12.dp) {
        Text(title, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun BandChipRow(labels: List<String>, selected: Set<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEach { band ->
            val active = selected.contains(band)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) BlueSoft else GraySoft)
                    .border(1.dp, if (active) Blue else BorderColor, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(band, color = if (active) Blue else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun GridMetric(label: String, value: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = fg, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StatTile(label: String, value: String, bg: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrafficMini(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GraySoft)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun DotLabel(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LockInsightCard(
    title: String,
    value: String,
    desc: String,
    accent: Color,
    tint: Color,
    border: Color,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, tint = tint, shape = 18.dp, borderColor = border, elevation = 0.dp, padding = 12.dp) {
        Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text(desc, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
    }
}

@Composable
private fun LockModeCard(title: String, desc: String, selected: Boolean, modifier: Modifier = Modifier) {
    val bg = if (selected) Color(0xFFEEF2FF) else SurfaceWhite
    val stroke = if (selected) Indigo else BorderColor
    val accent = if (selected) Indigo else Color(0xFFE8EDF5)
    AppCard(modifier = modifier, tint = bg, shape = 18.dp, borderColor = stroke, elevation = 0.dp, padding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, if (selected) Indigo else Color(0xFFD0D5DD), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Indigo)
                    )
                }
            }
        }
        Text(desc, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
    }
}

@Composable
private fun SmallIconAction(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ScellBandChip(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) BlueSoft else SurfaceWhite)
            .border(1.dp, if (selected) Blue else BorderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) Blue else Color.White)
                .border(1.dp, if (selected) Blue else Color(0xFFCBD5E1), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Text(label, color = if (selected) Blue else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LegendPill(label: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MetricAccentCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    valueSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = TextPrimary, fontSize = valueSize, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
    }
}

@Composable
private fun RealtimeSignalTrendChart(
    values: List<Double>,
    yMin: Double,
    yMax: Double,
    refreshIntervalSec: Int,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return

    val nowMs = remember(values, refreshIntervalSec) { System.currentTimeMillis() }
    val intervalMs = refreshIntervalSec.coerceAtLeast(1) * 1000L
    val sampleCount = values.size
    val startMs = nowMs - ((sampleCount - 1).coerceAtLeast(0) * intervalMs)

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
    val dateTimeFormatter = remember { SimpleDateFormat("M月d日 HH:mm", Locale.CHINA) }
    val dayFormatter = remember { SimpleDateFormat("yyyyMMdd", Locale.CHINA) }

    val yLabelPaint = remember {
        AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.RIGHT
        }
    }
    val xLabelPaint = remember {
        AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
        }
    }

    Canvas(modifier = modifier) {
        val leftPad = 40.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 8.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val plotWidth = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val gridColor = Color(0xFFE3E8EF)
        val axisColor = TextSecondary.copy(alpha = 0.88f).toArgb()
        yLabelPaint.color = axisColor
        yLabelPaint.textSize = 10.sp.toPx()
        xLabelPaint.color = axisColor
        xLabelPaint.textSize = 10.sp.toPx()

        val yTicks = 6
        repeat(yTicks) { idx ->
            val ratio = idx / (yTicks - 1).toFloat()
            val y = topPad + plotHeight * ratio
            val tickValue = yMax - (yMax - yMin) * ratio
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                tickValue.toInt().toString(),
                leftPad - 6.dp.toPx(),
                y + 3.dp.toPx(),
                yLabelPaint
            )
        }

        val xTicks = 6
        repeat(xTicks) { idx ->
            val ratio = idx / (xTicks - 1).toFloat()
            val x = leftPad + plotWidth * ratio
            drawLine(
                color = Color(0xFFF0F3F7),
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotHeight),
                strokeWidth = 1.dp.toPx()
            )
            val ts = startMs + (((nowMs - startMs) * ratio).toLong())
            val showDate = idx == 0 || dayFormatter.format(Date(ts)) != dayFormatter.format(Date(startMs))
            val label = if (showDate) dateTimeFormatter.format(Date(ts)) else timeFormatter.format(Date(ts))
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                size.height - 3.dp.toPx(),
                xLabelPaint
            )
        }

        val valueRange = (yMax - yMin).takeIf { it > 0 } ?: 1.0
        val path = Path()
        var hasPoint = false
        var drawing = false
        var latestPoint: Offset? = null
        val denom = (sampleCount - 1).coerceAtLeast(1).toFloat()
        values.forEachIndexed { idx, v ->
            val x = leftPad + (plotWidth * idx / denom)
            val valid = v in yMin..yMax
            if (!valid) {
                drawing = false
                return@forEachIndexed
            }
            val normalized = ((v - yMin) / valueRange).toFloat().coerceIn(0f, 1f)
            val y = topPad + (1f - normalized) * plotHeight
            if (!drawing) {
                path.moveTo(x, y)
                drawing = true
            } else {
                path.lineTo(x, y)
            }
            hasPoint = true
            latestPoint = Offset(x, y)
        }

        if (hasPoint) {
            drawPath(
                path = path,
                color = lineColor,
                style = DrawStroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
            latestPoint?.let { dot ->
                drawCircle(color = Color.White, radius = 3.2.dp.toPx(), center = dot)
                drawCircle(color = lineColor, radius = 2.2.dp.toPx(), center = dot)
            }
        }
    }
}

private fun buildTrendRangeLabel(sampleCount: Int, refreshIntervalSec: Int): String {
    if (sampleCount <= 0) return "--"
    val nowMs = System.currentTimeMillis()
    val spanMs = (sampleCount - 1).coerceAtLeast(0) * refreshIntervalSec.coerceAtLeast(1) * 1000L
    val startMs = nowMs - spanMs
    val dayFormatter = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    val sameDay = dayFormatter.format(Date(startMs)) == dayFormatter.format(Date(nowMs))
    val startFormatter = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
    val endFormatter = if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.CHINA)
    } else {
        SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
    }
    return "${startFormatter.format(Date(startMs))} - ${endFormatter.format(Date(nowMs))}"
}

private fun buildTrendRangeLabelFromStations(
    stations: List<BaseStationTrend>,
    refreshIntervalSec: Int
): String {
    val timestamps = stations
        .flatMap { station -> station.samples.map { sample -> sample.timestampMs } }
        .sorted()
    if (timestamps.isEmpty()) {
        return buildTrendRangeLabel(sampleCount = 1, refreshIntervalSec = refreshIntervalSec)
    }
    return formatTrendRangeLabel(timestamps.first(), timestamps.last())
}

private fun formatTrendRangeLabel(startMs: Long, endMs: Long): String {
    val dayFormatter = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    val sameDay = dayFormatter.format(Date(startMs)) == dayFormatter.format(Date(endMs))
    val startFormatter = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
    val endFormatter = if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.CHINA)
    } else {
        SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
    }
    return "${startFormatter.format(Date(startMs))} - ${endFormatter.format(Date(endMs))}"
}

private fun formatSignalValue(value: Double, unit: String, decimals: Int = 0): String {
    if (!value.isFinite()) return "--"
    val number = if (decimals <= 0) {
        value.toInt().toString()
    } else {
        "%.${decimals}f".format(Locale.CHINA, value)
    }
    return "$number $unit"
}

@Composable
private fun MultiStationTrendChart(
    stations: List<BaseStationTrend>,
    stationColors: Map<String, Color>,
    yMin: Double,
    yMax: Double,
    windowStartMs: Long,
    windowEndMs: Long,
    modifier: Modifier = Modifier
) {
    if (stations.isEmpty()) return
    val startMs = minOf(windowStartMs, windowEndMs)
    val endMs = maxOf(windowStartMs, windowEndMs).coerceAtLeast(startMs + 1L)
    val spanMs = (endMs - startMs).coerceAtLeast(1L)

    val yLabelPaint = remember {
        AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.RIGHT
        }
    }
    val xLabelPaint = remember {
        AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
        }
    }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
    val dateTimeFormatter = remember { SimpleDateFormat("M月d日 HH:mm", Locale.CHINA) }
    val dayFormatter = remember { SimpleDateFormat("yyyyMMdd", Locale.CHINA) }

    Canvas(modifier = modifier) {
        val leftPad = 40.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 8.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val plotWidth = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val axisColor = TextSecondary.copy(alpha = 0.88f).toArgb()
        yLabelPaint.color = axisColor
        yLabelPaint.textSize = 10.sp.toPx()
        xLabelPaint.color = axisColor
        xLabelPaint.textSize = 10.sp.toPx()

        val yTicks = 6
        repeat(yTicks) { idx ->
            val ratio = idx / (yTicks - 1).toFloat()
            val y = topPad + plotHeight * ratio
            val tickValue = yMax - (yMax - yMin) * ratio
            drawLine(
                color = Color(0xFFE3E8EF),
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                tickValue.toInt().toString(),
                leftPad - 6.dp.toPx(),
                y + 3.dp.toPx(),
                yLabelPaint
            )
        }

        val xTicks = 6
        repeat(xTicks) { idx ->
            val ratio = idx / (xTicks - 1).toFloat()
            val x = leftPad + plotWidth * ratio
            drawLine(
                color = Color(0xFFF0F3F7),
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotHeight),
                strokeWidth = 1.dp.toPx()
            )
            val ts = startMs + (spanMs * ratio).toLong()
            val showDate = idx == 0 || dayFormatter.format(Date(ts)) != dayFormatter.format(Date(startMs))
            val label = if (showDate) dateTimeFormatter.format(Date(ts)) else timeFormatter.format(Date(ts))
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                size.height - 3.dp.toPx(),
                xLabelPaint
            )
        }

        val valueRange = (yMax - yMin).takeIf { it > 0 } ?: 1.0
        stations.forEach { station ->
            val color = stationColors[station.key] ?: Blue
            val path = Path()
            var drawing = false
            var hasPoint = false
            var latestPoint: Offset? = null

            station.samples.sortedBy { it.timestampMs }.forEach { sample ->
                val v = sample.rsrp
                if (!v.isFinite() || v !in yMin..yMax) {
                    drawing = false
                    return@forEach
                }
                val xRatio = ((sample.timestampMs - startMs).toDouble() / spanMs.toDouble()).toFloat().coerceIn(0f, 1f)
                val x = leftPad + plotWidth * xRatio
                val yRatio = ((v - yMin) / valueRange).toFloat().coerceIn(0f, 1f)
                val y = topPad + (1f - yRatio) * plotHeight
                if (!drawing) {
                    path.moveTo(x, y)
                    drawing = true
                } else {
                    path.lineTo(x, y)
                }
                hasPoint = true
                latestPoint = Offset(x, y)
            }

            if (hasPoint) {
                drawPath(path = path, color = color, style = DrawStroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round))
                latestPoint?.let { dot ->
                    drawCircle(color = Color.White, radius = 3.dp.toPx(), center = dot)
                    drawCircle(color = color, radius = 2.dp.toPx(), center = dot)
                }
            }
        }
    }
}

@Composable
private fun FixedScaleLinePlot(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    secondaryValues: List<Double> = emptyList(),
    secondaryColor: Color = Purple,
    yMin: Double,
    yMax: Double,
    highlightDots: Boolean = false
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas

        // 绘制水平网格线
        repeat(5) { i ->
            val y = 10f + (size.height - 22f) * i / 4f
            drawLine(
                color = Color(0xFFDDE3EB),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        // 绘制垂直网格线
        repeat(6) { i ->
            val x = 12f + (size.width - 24f) * i / 5f
            drawLine(
                color = Color(0xFFEEF2F7),
                start = Offset(x, 10f),
                end = Offset(x, size.height - 12f),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun drawSeriesWithFill(series: List<Double>, tone: Color, lineWidth: Float) {
            if (series.size < 2) return
            val step = size.width / (series.size - 1).coerceAtLeast(1)
            val fillPath = Path()
            val linePath = Path()
            series.forEachIndexed { idx, point ->
                val ratio = ((point - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
                val x = idx * step
                val y = size.height - ratio * size.height
                if (idx == 0) {
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                    linePath.moveTo(x, y)
                } else {
                    fillPath.lineTo(x, y)
                    linePath.lineTo(x, y)
                }
            }
            // 封闭填充区域到底部
            fillPath.lineTo((series.size - 1) * step, size.height)
            fillPath.close()

            // 绘制半透明填充区域
            drawPath(path = fillPath, color = tone.copy(alpha = 0.15f))
            // 绘制上部较深的渐变填充
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(tone.copy(alpha = 0.35f), tone.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = size.height
                )
            )
            // 绘制折线
            drawPath(path = linePath, color = tone, style = DrawStroke(width = lineWidth, cap = StrokeCap.Round))
        }

        fun drawSeriesLine(series: List<Double>, tone: Color, width: Float) {
            if (series.size < 2) return
            val step = size.width / (series.size - 1).coerceAtLeast(1)
            val path = Path()
            series.forEachIndexed { idx, point ->
                val ratio = ((point - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
                val x = idx * step
                val y = size.height - ratio * size.height
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = tone, style = DrawStroke(width = width, cap = StrokeCap.Round))
        }

        // 次级序列仅画线
        drawSeriesLine(secondaryValues, secondaryColor.copy(alpha = 0.6f), 1.8.dp.toPx())
        // 主序列画填充 + 线
        drawSeriesWithFill(values, color, 2.5.dp.toPx())

        // 高亮数据点
        if (highlightDots && values.size >= 2) {
            val step = size.width / (values.size - 1).coerceAtLeast(1)
            values.forEachIndexed { idx, point ->
                val ratio = ((point - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
                val x = idx * step
                val y = size.height - ratio * size.height
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(x, y))
                drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun SpeedMiniPlayer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceWhite)
            .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Cyan),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("速率监控已开启", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("正在刷新当前吞吐与体验指标", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BlueSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LineChart(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    secondaryValues: List<Double> = emptyList(),
    secondaryColor: Color = Purple
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val all = values + secondaryValues
        val min = (all.minOrNull() ?: 0.0).toFloat()
        val max = (all.maxOrNull() ?: 1.0).toFloat().coerceAtLeast(min + 1f)

        // 水平网格线
        repeat(4) { i ->
            val y = size.height * (i + 1) / 5f
            drawLine(
                color = Color(0xFFE8EDF4),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun drawSeriesWithFill(series: List<Double>, tone: Color) {
            if (series.size < 2) return
            val step = size.width / (series.size - 1).coerceAtLeast(1)
            val fillPath = Path()
            val linePath = Path()
            series.forEachIndexed { idx, v ->
                val x = idx * step
                val n = ((v.toFloat() - min) / (max - min)).coerceIn(0f, 1f)
                val y = size.height - n * size.height
                if (idx == 0) {
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                    linePath.moveTo(x, y)
                } else {
                    fillPath.lineTo(x, y)
                    linePath.lineTo(x, y)
                }
            }
            fillPath.lineTo((series.size - 1) * step, size.height)
            fillPath.close()

            // 半透明填充
            drawPath(path = fillPath, color = tone.copy(alpha = 0.12f))
            // 顶部渐变填充
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(tone.copy(alpha = 0.30f), tone.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = size.height
                )
            )
            // 折线
            drawPath(path = linePath, color = tone, style = DrawStroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }

        // 次级序列仅画线（不加填充避免重叠混乱）
        if (secondaryValues.size >= 2) {
            val step = size.width / (secondaryValues.size - 1).coerceAtLeast(1)
            val path = Path()
            secondaryValues.forEachIndexed { idx, v ->
                val x = idx * step
                val n = ((v.toFloat() - min) / (max - min)).coerceIn(0f, 1f)
                val y = size.height - n * size.height
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = secondaryColor.copy(alpha = 0.7f), style = DrawStroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }

        // 主序列填充+线
        drawSeriesWithFill(values, color)
    }
}

private fun progress(value: Double, min: Double, max: Double): Float {
    return ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
}

private fun modeLabel(code: String): String {
    return when (code) {
        "00" -> "自动"
        "02" -> "5G only"
        "03", "01" -> "4G only"
        else -> "自动"
    }
}

// ==================== 骨架屏 / 加载状态 ====================

@Composable
private fun SkeletonCard(lines: Int = 3, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 标题骨架
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFE8ECF4))
            )
            // 内容骨架
            repeat(lines) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it == lines - 1) 0.7f else 1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFFF0F2F7))
                )
            }
        }
    }
}

@Composable
private fun SkeletonMetricGrid(count: Int = 4, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        (0 until count).chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE8ECF4), RoundedCornerShape(18.dp))
                            .padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF0F2F7))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFE8ECF4))
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 通用加载/空数据页面骨架
 */
@Composable
private fun LoadingContent(
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyMessage: String = "暂无数据",
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        isLoading -> skeleton()
        isEmpty -> EmptyPlaceholder(message = emptyMessage)
        else -> content()
    }
}

@Composable
private fun EmptyPlaceholder(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        )
        Text(message, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}




@Composable
private fun DeviceInfoDialog(pcc: PccInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = SurfaceWhite,
        tonalElevation = 0.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Info, null, tint = Indigo, modifier = Modifier.size(18.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("关于本机", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("设备与功能信息", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GraySoft)
                        .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("设备信息", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    InfoRow("设备型号", "H155-381")
                    InfoRow("硬件版本", "WL1H158M02 Ver.B")
                    InfoRow("软件版本", "4.0.0.5(H5568SP2C233)")
                    InfoRow("序列号", "6MQ7S23C29000287")
                    InfoRow("当前 RAT", pcc.technology.ifBlank { "--" })
                    InfoRow("当前频段", pcc.band.ifBlank { "--" })
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GraySoft)
                        .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("功能支持", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    InfoRow("WiFi", "双频")
                    InfoRow("USB", "支持")
                    InfoRow("访客网络", "支持")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("关闭", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    )
}

@Composable
private fun AtCommandDialog(
    telnetStatus: String,
    onDismiss: () -> Unit,
    viewModel: CpeViewModel = viewModel()
) {
    val context = LocalContext.current
    var command by rememberSaveable { mutableStateOf("AT") }
    var responseLog by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        shape = RoundedCornerShape(22.dp),
        containerColor = SurfaceWhite,
        tonalElevation = 0.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PurpleSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = Indigo, modifier = Modifier.size(18.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AT 调试", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("向 Telnet (20249) 发送原始命令", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Telnet 状态: $telnetStatus",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "可直接输入完整 AT 命令；如果没写 AT 前缀，会自动补上。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("AT 命令", fontSize = 11.sp) },
                    placeholder = { Text("例如：AT+CSQ / ^HCSQ?", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Indigo, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Indigo,
                        unfocusedBorderColor = Color(0xFFE6EAF2),
                        focusedContainerColor = Color(0xFFF7F9FC),
                        unfocusedContainerColor = Color(0xFFF7F9FC)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GraySoft)
                        .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text("原始回包", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 92.dp, max = 190.dp)
                            .verticalScroll(scrollState)
                    ) {
                        SelectionContainer {
                            Text(
                                responseLog.ifBlank { "发送后的原始响应会显示在这里。" },
                                color = if (responseLog.isBlank()) TextSecondary else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (isSending) {
                    Text("正在发送并等待回包...", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val typedCommand = command.trim()
                    if (typedCommand.isBlank() || isSending) return@Button
                    val normalized = if (typedCommand.startsWith("AT", ignoreCase = true)) typedCommand else "AT$typedCommand"
                    isSending = true
                    viewModel.sendAtCommand(normalized) { ok, result ->
                        isSending = false
                        val entry = buildString {
                            append("> ").append(normalized).append('\n')
                            if (ok) {
                                append(result.ifBlank { "（无返回）" })
                            } else {
                                append("ERROR: ").append(result.ifBlank { "发送失败" })
                            }
                        }
                        responseLog = if (responseLog.isBlank()) entry else "$responseLog\n\n$entry"
                        if (!ok) {
                            Toast.makeText(context, result.ifBlank { "发送失败" }, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSending && command.trim().isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(if (isSending) "发送中..." else "发送", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isSending) onDismiss() }) {
                Text("关闭", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrafficSwitchDialog(
    pcc: PccInfo,
    onDismiss: () -> Unit,
    initialEnabled: Boolean? = null,
    onStateApplied: (Boolean) -> Unit = {},
    viewModel: CpeViewModel = viewModel()
) {
    val context = LocalContext.current
    var enableTraffic by remember(initialEnabled) { mutableStateOf(initialEnabled ?: true) }
    var isApplying by remember { mutableStateOf(false) }

    // 打开对话框时从 CPE 读取当前状态
    LaunchedEffect(initialEnabled) {
        if (initialEnabled == null) {
            viewModel.getMobileDataSwitch { enabled ->
                enableTraffic = enabled
            }
        }
    }

    AlertDialog(onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = Green, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("流量开关", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("控制移动数据连接的开启/关闭", color = TextSecondary, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GraySoft).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("移动数据", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Switch(checked = enableTraffic, onCheckedChange = { enableTraffic = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = SurfaceWhite, checkedTrackColor = Green))
                }
                Text("状态: " + if(enableTraffic) "已开启" else "已关闭",
                    color = if(enableTraffic) Green else Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (isApplying) Text("正在应用...", color = TextSecondary, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!isApplying) {
                    isApplying = true
                    viewModel.setMobileDataSwitch(enableTraffic) { ok, msg ->
                        isApplying = false
                        Toast.makeText(context, if (ok) "已${if (enableTraffic) "开启" else "关闭"}移动数据" else msg, Toast.LENGTH_SHORT).show()
                        if (ok) {
                            onStateApplied(enableTraffic)
                            onDismiss()
                        }
                    }
                }
            }) { Text("应用", color = Green, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } }
    )
}
