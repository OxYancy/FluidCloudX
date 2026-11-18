package com.nyehueh.fluidcloud.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.highcapable.yukihookapi.YukiHookAPI
import com.nyehueh.fluidcloud.core.engine.RuleEngine
import com.nyehueh.fluidcloud.core.extractor.LinkExtractor
import com.nyehueh.fluidcloud.core.launcher.AppLauncher
import com.nyehueh.fluidcloud.core.model.MatchedResult
import com.nyehueh.fluidcloud.data.RuleRepository
import com.nyehueh.fluidcloud.overlay.FluidOverlayService
import kotlinx.coroutines.launch

data class RecommendedScopeApp(
    val name: String,
    val packageName: String,
    val description: String,
    val isCore: Boolean = false
)

private val RECOMMENDED_APPS = listOf(
    RecommendedScopeApp(
        name = "系统框架 (system_server)",
        packageName = "android",
        description = "【唯一必须・防封安全】仅 Hook 系统底层框架，不注入任何第三方应用（微信/QQ等）进程，零封号与风控危险！全局统管全系统外链跳转与剪贴板分发。",
        isCore = true
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: RuleRepository,
    onOpenOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val rules by repository.rules.collectAsState()
    val config by repository.config.collectAsState()

    var isSyncing by remember { mutableStateOf(false) }
    var testInputText by remember { mutableStateOf("https://pan.baidu.com/s/1xxxxxx 提取码: 8888") }
    var testMatchResults by remember { mutableStateOf<List<MatchedResult>>(emptyList()) }
    var showInAppCapsuleDialog by remember { mutableStateOf(false) }

    val linkExtractor = remember(config) { LinkExtractor(config) }
    val launcher = remember(config) { AppLauncher(context, config) }

    val isModuleActive = remember { YukiHookAPI.Status.isModuleActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("流体云 Xposed 模块", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Module Activation Status Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isModuleActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isModuleActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isModuleActive) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = if (isModuleActive) "Xposed 模块已激活" else "Xposed 模块未激活",
                                fontWeight = FontWeight.Bold,
                                color = if (isModuleActive) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 18.sp
                            )
                            Text(
                                text = if (isModuleActive) "LibXposed API 102 拦截链路已正常就绪" else "请在 LSPosed 管理器中勾选下方推荐作用域并重启系统",
                                color = if (isModuleActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 2. Recommended Hook Scope (推荐勾选应用)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("推荐 Hook 作用域 (LSPosed)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val text = RECOMMENDED_APPS.joinToString("\n") { "${it.name}: ${it.packageName}" }
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("推荐作用域包名", text))
                                    Toast.makeText(context, "已复制推荐包名列表到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("复制包名", fontSize = 12.sp)
                            }
                        }

                        Text(
                            text = "纯系统框架 Hook 设计：在 LSPosed 中仅需勾选【系统框架 (android)】即可全局生效。零封号与风控隐患，不注入任何第三方应用（微信/QQ等）！",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        RECOMMENDED_APPS.forEach { app ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (app.isCore) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(app.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            if (app.isCore) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "核心必须",
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                        Text(app.description, fontSize = 12.sp, color = Color.Gray)
                                    }
                                    IconButton(onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("PackageName", app.packageName))
                                        Toast.makeText(context, "已复制: ${app.packageName}", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "复制包名", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Cloud Rules Sync & Reset Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("规则库管理", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "内置全套 27 条社区精选规则（覆盖百度网盘、123网盘、115、B站、淘宝、京东、小红书等），支持云端增量同步与一键重置。",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    isSyncing = true
                                    coroutineScope.launch {
                                        val result = repository.syncCloudRules()
                                        isSyncing = false
                                        result.onSuccess { count ->
                                            Toast.makeText(context, "同步成功！更新 $count 条规则", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "同步失败: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("云端同步")
                            }

                            OutlinedButton(
                                onClick = {
                                    val count = repository.restoreDefaultRules()
                                    Toast.makeText(context, "已恢复内置 $count 条默认规则", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("恢复预置规则")
                            }
                        }
                    }
                }
            }

            // 4. Playground / Test Simulator & Popup Demo
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("规则调试与弹窗效果测试", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "可输入含有外链或提取码的文本，实时测试流体云胶囊悬浮弹窗与小窗/全屏唤起效果。",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = testInputText,
                            onValueChange = { testInputText = it },
                            label = { Text("测试文本（含链接与提取码）") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // 快速填入测试示例
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { testInputText = "http://ghcr.io/kmworks/kmrs:latest" },
                                label = { Text("HTTP外链", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { testInputText = "https://pan.baidu.com/s/1xxxxxx 提取码: 8888" },
                                label = { Text("网盘提取码", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { testInputText = "https://b23.tv/BV1xx411c7mD" },
                                label = { Text("哔哩哔哩", fontSize = 11.sp) }
                            )
                        }

                        // Action Buttons: Row 1
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val links = linkExtractor.extract(testInputText)
                                    if (links.isEmpty()) {
                                        Toast.makeText(context, "未识别出任何链接", Toast.LENGTH_SHORT).show()
                                        testMatchResults = emptyList()
                                    } else {
                                        val engine = RuleEngine(context, rules)
                                        testMatchResults = engine.match(links.first())
                                        if (testMatchResults.isEmpty()) {
                                            Toast.makeText(context, "已识别链接，但未匹配到本地已安装应用的规则", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("匹配测试")
                            }

                            OutlinedButton(
                                onClick = onOpenOverlayPermission,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("悬浮窗权限")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons: Row 2 (Popup Testing)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (!Settings.canDrawOverlays(context)) {
                                        Toast.makeText(context, "请先授予悬浮窗权限以展示系统级流体云胶囊", Toast.LENGTH_LONG).show()
                                        onOpenOverlayPermission()
                                    } else {
                                        FluidOverlayService.showTestPopup(context, testInputText)
                                        Toast.makeText(context, "已触发屏幕上方悬浮胶囊弹窗！点击可体验小窗或轻扫关闭", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("测试悬浮胶囊弹窗")
                            }

                            OutlinedButton(
                                onClick = {
                                    val links = linkExtractor.extract(testInputText)
                                    if (links.isNotEmpty()) {
                                        val engine = RuleEngine(context, rules)
                                        testMatchResults = engine.match(links.first())
                                    }
                                    showInAppCapsuleDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("预览弹窗效果")
                            }
                        }

                        if (testMatchResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("匹配结果 (${testMatchResults.size}):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            testMatchResults.forEach { match ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("${match.ruleName} -> ${match.targetPackage}", fontWeight = FontWeight.Bold)
                                        Text("URI: ${match.targetUriOrIntent}", fontSize = 12.sp)
                                        Text("提取码: ${match.extractionCode ?: "无"}", fontSize = 12.sp)
                                        Row(
                                            modifier = Modifier.padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilledTonalButton(onClick = {
                                                launcher.launch(match, AppLauncher.LaunchMode.FULLSCREEN)
                                            }) {
                                                Text("全屏打开")
                                            }
                                            FilledTonalButton(onClick = {
                                                launcher.launch(match, AppLauncher.LaunchMode.WINDOW)
                                            }) {
                                                Text("小窗打开")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Rule List Header
            item {
                Text(
                    text = "已启用规则列表 (${rules.size} 条)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 6. Rules items
            items(rules) { rule ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("触发正则: ${rule.trigger.joinToString(", ")}", fontSize = 12.sp, color = Color.Gray)
                            Text("目标协议: ${rule.type}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // In-App Interactive Capsule Preview Dialog
    if (showInAppCapsuleDialog) {
        val currentSampleMatch = testMatchResults.firstOrNull() ?: MatchedResult(
            ruleName = "百度网盘 (预览)",
            type = "url",
            targetUriOrIntent = "bdnetdisk://n/action.VIEW_SHORTCUT?origin=com.android.browser&url=https://pan.baidu.com/s/1xxxxxx",
            targetPackage = "com.baidu.netdisk",
            targetActivity = null,
            extractionCode = "8888",
            title = "百度网盘",
            message = "提取码: 8888 | 点击主体全屏打开，右侧小窗打开",
            iconPkg = "com.baidu.netdisk"
        )

        Dialog(
            onDismissRequest = { showInAppCapsuleDialog = false }
        ) {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE1E1E1E)),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showInAppCapsuleDialog = false
                        launcher.launch(currentSampleMatch, AppLauncher.LaunchMode.FULLSCREEN)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSampleMatch.title,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = currentSampleMatch.message,
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = {
                            showInAppCapsuleDialog = false
                            launcher.launch(currentSampleMatch, AppLauncher.LaunchMode.WINDOW)
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0x33FFFFFF),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("小窗", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
