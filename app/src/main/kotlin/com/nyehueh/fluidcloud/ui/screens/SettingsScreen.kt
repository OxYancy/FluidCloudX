package com.nyehueh.fluidcloud.ui.screens

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
                                text = if (isModuleActive) "系统服务与 QQ/微信拦截已正常就绪" else "请在 LSPosed 管理器中勾选作用域并重启系统",
                                color = if (isModuleActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 2. Cloud Rules Sync Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("云端规则同步", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text("已加载 ${rules.size} 条规则，支持合并社区规则", fontSize = 12.sp, color = Color.Gray)
                            }
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
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("立即同步")
                            }
                        }
                    }
                }
            }

            // 3. Playground / Test Simulator
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("规则调试与实时测试", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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

            // 4. Rule List Header
            item {
                Text(
                    text = "已启用规则列表 (${rules.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 5. Rules items
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
