package com.nyehueh.fluidcloud.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import com.highcapable.yukihookapi.YukiHookAPI
import com.nyehueh.fluidcloud.core.engine.RuleEngine
import com.nyehueh.fluidcloud.core.extractor.LinkExtractor
import com.nyehueh.fluidcloud.core.launcher.AppLauncher
import com.nyehueh.fluidcloud.core.model.MatchedResult
import com.nyehueh.fluidcloud.data.RuleRepository
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
        description = "【核心必须】全局监听外部链接 Intent 分发与剪贴板变动，实现全系统流体云调度",
        isCore = true
    ),
    RecommendedScopeApp(
        name = "QQ",
        packageName = "com.tencent.mobileqq",
        description = "拦截聊天窗口点击网址，避免进入 QQ 内置中转页，直接呼出流体云"
    ),
    RecommendedScopeApp(
        name = "微信",
        packageName = "com.tencent.mm",
        description = "拦截微信内置 WebView (MMWebViewUI) 外链，一键呼出对应 App"
    ),
    RecommendedScopeApp(
        name = "新浪微博",
        packageName = "com.sina.weibo",
        description = "拦截正文及评论中的网盘、视频、电商等外部链接"
    ),
    RecommendedScopeApp(
        name = "哔哩哔哩",
        packageName = "tv.danmaku.bili",
        description = "拦截动态及评论区中的外部网址，直达关联客户端"
    ),
    RecommendedScopeApp(
        name = "酷安",
        packageName = "com.coolapk.market",
        description = "拦截头条、动态及评论中的外链与酷品口令"
    ),
    RecommendedScopeApp(
        name = "知乎",
        packageName = "com.zhihu.android",
        description = "拦截回答与文章内的外链跳转，直接转为流体云快捷打开"
    ),
    RecommendedScopeApp(
        name = "小红书",
        packageName = "com.xingin.xhs",
        description = "拦截图文笔记与评论中的外部分享链接"
    ),
    RecommendedScopeApp(
        name = "抖音",
        packageName = "com.ss.android.ugc.aweme",
        description = "拦截短视频评论及私信内的外链分享"
    ),
    RecommendedScopeApp(
        name = "淘宝",
        packageName = "com.taobao.taobao",
        description = "拦截淘口令与商品外部跳转"
    ),
    RecommendedScopeApp(
        name = "京东",
        packageName = "com.jingdong.app.mall",
        description = "拦截京口令与商品外部跳转"
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
                            text = "在 LSPosed 模块作用域中勾选以下应用可获得最佳流体云拦截体验：",
                            fontSize = 12.sp,
                            color = Color.Gray,
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

            // 4. Playground / Test Simulator
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("规则调试与实时测试", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = testInputText,
                            onValueChange = { testInputText = it },
                            label = { Text("粘贴测试文本（含链接与提取码）") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val links = linkExtractor.extract(testInputText)
                                    if (links.isEmpty()) {
                                        Toast.makeText(context, "未识别出任何链接", Toast.LENGTH_SHORT).show()
                                        testMatchResults = emptyList()
                                    } else {
                                        val engine = RuleEngine(context, rules)
                                        testMatchResults = engine.match(links.first())
                                    }
                                }
                            ) {
                                Text("匹配测试")
                            }
                            OutlinedButton(onClick = onOpenOverlayPermission) {
                                Text("检查悬浮窗权限")
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
}
