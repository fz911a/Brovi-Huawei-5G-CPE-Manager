package com.cpemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpemanager.data.model.SystemLogEntry
import com.cpemanager.viewmodel.CpeViewModel

// 本地颜色常量
private val LogTextPrimary = Color(0xFF111827)
private val LogTextSecondary = Color(0xFF667085)
private val LogSurfaceWhite = Color.White
private val LogIndigo = Color(0xFF5B67F0)
private val LogBlue = Color(0xFF3D5AFE)
private val LogPurple = Color(0xFF7C4DFF)
private val LogRed = Color(0xFFFB7185)
private val LogAmber = Color(0xFFF59E0B)
private val LogCyan = Color(0xFF00B8D9)
private val LogGreen = Color(0xFF31C27C)

@Composable
fun SystemLogScreen(viewModel: CpeViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredEntries = uiState.systemLogEntries.filter { entry ->
        (uiState.systemLogTypeFilter == "全部" || entry.type == uiState.systemLogTypeFilter) &&
        (uiState.systemLogLevelFilter == "全部" || entry.level == uiState.systemLogLevelFilter)
    }

    LaunchedEffect(Unit) {
        if (uiState.systemLogEntries.isEmpty()) {
            viewModel.loadSystemLog()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FA))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部导航栏：返回按钮 + 标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = LogTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                "系统日志",
                color = LogTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            // 刷新按钮
            IconButton(onClick = { viewModel.loadSystemLog() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "刷新",
                    tint = LogIndigo,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 说明文字
        Text(
            "日志记录用户的操作及设备的异常。格式：\"时间\"\"类型\"\"级别\"\"内容\"。",
            color = LogTextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        // 筛选行：类型 + 级别 + 计数
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogFilterChips(
                options = listOf("全部", "用户操作", "系统日志", "安全日志"),
                selected = uiState.systemLogTypeFilter,
                onSelect = { viewModel.setSystemLogTypeFilter(it) }
            )
            Spacer(Modifier.width(4.dp))
            LogFilterChips(
                options = listOf("全部", "警告", "提示", "信息"),
                selected = uiState.systemLogLevelFilter,
                onSelect = { viewModel.setSystemLogLevelFilter(it) }
            )
            Spacer(Modifier.weight(1f))
            Text("${filteredEntries.size} 条", color = LogTextSecondary, fontSize = 12.sp)
        }

        // 表头
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text("时间", color = LogTextSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
            Text("类型", color = LogTextSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
            Text("级别", color = LogTextSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
            Text("内容", color = LogTextSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        // 日志列表
        if (uiState.systemLogLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("加载中...", color = LogTextSecondary, fontSize = 13.sp)
            }
        } else if (filteredEntries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = null,
                        tint = LogTextSecondary, modifier = Modifier.size(32.dp))
                    Text("暂无日志记录", color = LogTextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredEntries) { entry -> LogEntryRow(entry) }
            }
        }
    }
}

@Composable
private fun LogFilterChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val isSel = option == selected
            Box(Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (isSel) LogIndigo else Color(0xFFF1F5F9))
                .clickable { onSelect(option) }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(option, color = if (isSel) Color.White else LogTextSecondary,
                    fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: SystemLogEntry) {
    val typeColor = when (entry.type) {
        "用户操作" -> LogBlue; "系统日志" -> LogPurple; "安全日志" -> LogRed; else -> LogTextSecondary
    }
    val levelColor = when (entry.level) {
        "警告" -> LogAmber; "信息" -> LogCyan; else -> LogGreen
    }
    Row(Modifier.fillMaxWidth().background(LogSurfaceWhite, RoundedCornerShape(6.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(entry.time, color = LogTextSecondary, fontSize = 10.sp, modifier = Modifier.width(130.dp))
        Box(Modifier.width(56.dp).clip(RoundedCornerShape(4.dp))
            .background(typeColor.copy(alpha = 0.1f)).padding(horizontal = 3.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center) {
            Text(entry.type, color = typeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(56.dp).clip(RoundedCornerShape(4.dp))
            .background(levelColor.copy(alpha = 0.1f)).padding(horizontal = 3.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center) {
            Text(entry.level, color = levelColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text(entry.content, color = LogTextPrimary, fontSize = 11.sp,
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
