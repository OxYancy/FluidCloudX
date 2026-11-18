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
                _rules.value = json.decodeFromString(text)
            } catch (e: Exception) {
                _rules.value = getDefaultRules()
            }
        } else {
            _rules.value = getDefaultRules()
            saveRules(_rules.value)
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
                    val fileReq = Request.Builder().url("$rawBase/$filename").build()
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

    private fun getDefaultRules(): List<Rule> {
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
