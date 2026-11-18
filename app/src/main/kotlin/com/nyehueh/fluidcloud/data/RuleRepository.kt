package com.nyehueh.fluidcloud.data

import android.content.Context
import com.nyehueh.fluidcloud.core.model.FluidConfig
import com.nyehueh.fluidcloud.core.model.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File

class RuleRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = OkHttpClient()

    private val rulesFile = File(context.filesDir, "rules.json")
    private val configFile = File(context.filesDir, "config.json")

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    private val _config = MutableStateFlow(FluidConfig())
    val config: StateFlow<FluidConfig> = _config.asStateFlow()

    init {
        loadLocalData()
    }

    private fun loadLocalData() {
        if (rulesFile.exists()) {
            try {
                val text = rulesFile.readText()
                val loaded = json.decodeFromString<List<Rule>>(text)
                // 若本地规则仅有早期测试的3条或为空，自动升级初始化为内置的完整规则
                if (loaded.size <= 3) {
                    val defaultList = getDefaultRules()
                    _rules.value = defaultList
                    saveRules(defaultList)
                } else {
                    // 增量补全任何新版本加入的内置规则（如 PiliNara）
                    val defaultList = getDefaultRules()
                    val existingNames = loaded.map { it.name }.toSet()
                    val missingDefaults = defaultList.filter { it.name !in existingNames }
                    if (missingDefaults.isNotEmpty()) {
                        val merged = loaded + missingDefaults
                        _rules.value = merged
                        saveRules(merged)
                    } else {
                        _rules.value = loaded
                    }
                }
            } catch (e: Exception) {
                val defaultList = getDefaultRules()
                _rules.value = defaultList
                saveRules(defaultList)
            }
        } else {
            val defaultList = getDefaultRules()
            _rules.value = defaultList
            saveRules(defaultList)
        }

        if (configFile.exists()) {
            try {
                val text = configFile.readText()
                _config.value = json.decodeFromString(text)
            } catch (e: Exception) {
                _config.value = FluidConfig()
            }
        }
    }

    fun saveRules(newRules: List<Rule>) {
        _rules.value = newRules
        try {
            rulesFile.writeText(json.encodeToString(newRules))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreDefaultRules(): Int {
        val defaultList = getDefaultRules()
        saveRules(defaultList)
        return defaultList.size
    }

    fun getDefaultRules(): List<Rule> {
        return try {
            val inputStream = context.assets.open("default_rules.json")
            val text = inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<List<Rule>>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            getHardcodedFallbackRules()
        }
    }

    fun getDefaultNoLinkRules(): List<Rule> {
        return try {
            val inputStream = context.assets.open("default_nolinkrules.json")
            val text = inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<List<Rule>>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveConfig(newConfig: FluidConfig) {
        _config.value = newConfig
        try {
            configFile.writeText(json.encodeToString(newConfig))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncCloudRules(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val owner = "nyehueh"
            val repo = "shortx-Fluid_Cloud"
            val branch = "main"
            val folder = "rules"

            val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$folder?ref=$branch"
            val rawBase = "https://raw.githubusercontent.com/$owner/$repo/$branch/$folder"

            val listRequest = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "FluidCloudX-Android")
                .build()

            val listResponse = client.newCall(listRequest).execute()
            if (!listResponse.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API 请求失败: HTTP ${listResponse.code}"))
            }

            val body = listResponse.body?.string() ?: return@withContext Result.failure(Exception("空响应"))
            val jsonArray = JSONArray(body)
            val jsonFileNames = mutableListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = obj.optString("type")
                val name = obj.optString("name")
                if (type == "file" && name.endsWith(".json", ignoreCase = true)) {
                    jsonFileNames.add(name)
                }
            }

            val downloadedRules = mutableListOf<Rule>()
            for (filename in jsonFileNames) {
                try {
                    val encodedName = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
                    val fileReq = Request.Builder().url("$rawBase/$encodedName").build()
                    val fileResp = client.newCall(fileReq).execute()
                    val fileText = fileResp.body?.string()
                    if (!fileText.isNullOrBlank()) {
                        val parsedRule = json.decodeFromString<Rule>(fileText)
                        downloadedRules.add(parsedRule)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (downloadedRules.isNotEmpty()) {
                // Merge with existing local custom rules (don't overwrite custom ones)
                val existingCustom = _rules.value.filter { existing ->
                    downloadedRules.none { it.name == existing.name }
                }
                val merged = existingCustom + downloadedRules
                saveRules(merged)
                Result.success(downloadedRules.size)
            } else {
                Result.failure(Exception("未获取到云端规则"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getHardcodedFallbackRules(): List<Rule> {
        return listOf(
            Rule(
                name = "百度网盘",
                trigger = listOf("pan\\.baidu\\.com"),
                type = "url",
                check = "bdnetdisk://",
                rule = listOf(
                    com.nyehueh.fluidcloud.core.model.RuleItem(
                        ruleText = "bdnetdisk://n/action.VIEW_SHORTCUT?origin=com.android.browser&url=【link】"
                    )
                ),
                title = "百度网盘",
                message = "在百度网盘中打开链接",
                icon = "com.baidu.netdisk"
            ),
            Rule(
                name = "哔哩哔哩",
                trigger = listOf("bilibili\\.com", "b23\\.tv"),
                type = "url",
                check = "bilibili://",
                rule = listOf(
                    com.nyehueh.fluidcloud.core.model.RuleItem(
                        ruleText = "bilibili://video/【link】"
                    )
                ),
                title = "哔哩哔哩",
                message = "在哔哩哔哩中播放视频",
                icon = "tv.danmaku.bili"
            ),
            Rule(
                name = "Telegram",
                trigger = listOf("t\\.me"),
                type = "url",
                check = "tg://",
                rule = listOf(
                    com.nyehueh.fluidcloud.core.model.RuleItem(
                        ruleText = "tg://resolve?domain=【link】"
                    )
                ),
                title = "Telegram",
                message = "在 Telegram 中打开",
                icon = "org.telegram.messenger"
            )
        )
    }
}
