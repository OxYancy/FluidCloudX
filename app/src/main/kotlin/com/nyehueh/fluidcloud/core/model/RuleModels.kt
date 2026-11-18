package com.nyehueh.fluidcloud.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Rule(
    val name: String,
    @SerialName("tigger")
    val trigger: List<String> = emptyList(),
    val type: String, // "url", "pkg", "intent", "rein"
    val check: String? = null,
    val pkg: String? = null,
    val activityname: String? = null,
    val icon: String? = null,
    val title: String? = null,
    val message: String? = null,
    val topcondition: TopCondition? = null,
    @SerialName("Custom_variable")
    val customVariables: List<CustomVariable>? = null,
    val rule: List<RuleItem> = emptyList()
)

@Serializable
data class RuleItem(
    val condition: List<Condition>? = null,
    @SerialName("many_condition_tactic")
    val manyConditionTactic: String = "All", // "All", "Any", "None"
    @SerialName("rule_text")
    val ruleText: String
)

@Serializable
data class Condition(
    @SerialName("condition_type")
    val conditionType: String, // "withcode", "withoutcode", "contains", "notcontains"
    @SerialName("condition_text")
    val conditionText: String? = null,
    @SerialName("condition_pattern")
    val conditionPattern: String? = null
)

@Serializable
data class TopCondition(
    val condition: List<Condition> = emptyList(),
    @SerialName("many_condition_tactic")
    val manyConditionTactic: String = "Any"
)

@Serializable
data class CustomVariable(
    val name: String,
    val action: String = "match", // "match", "replace", "splicing"
    val pattern: String? = null,
    val text: String? = null,
    val index: Int = 1,
    val replacement: String? = null,
    val valuetext: String? = null
)

data class ExtractedLink(
    val url: String,
    val code: String? = null,
    val rawInput: String = ""
)

data class MatchedResult(
    val ruleName: String,
    val type: String, // "url", "pkg", "intent"
    val targetUriOrIntent: String,
    val targetPackage: String?,
    val targetActivity: String? = null,
    val extractionCode: String? = null,
    val title: String,
    val message: String,
    val iconPkg: String? = null,
    val userId: Int = 0
)

@Serializable
data class FluidConfig(
    val topLevelDomains: List<String> = listOf(
        "com", "cn", "top", "love", "xyz", "net", "org", "vip", "cloud", "online",
        "icu", "fun", "work", "tv", "wiki", "email", "plus", "co", "ltd", "shop",
        "tech", "wang", "site", "xin", "store", "art", "cc", "website", "press",
        "space", "beer", "luxe", "video", "group", "ren", "fit", "yoga", "pro",
        "ink", "info", "mobi", "kim", "red", "run", "chat", "cool", "zone", "host",
        "biz", "me", "so", "io", "dev", "app", "ai", "gg", "moe", "link", "is", "im"
    ),
    val emailKeywords: List<String> = listOf(
        "qq.com", "126.com", "163.com", "139.com", "gmail.com", "hotmail.com",
        "sohu.com", "sina.com", "sina.cn", "foxmail.com", "outlook.com", "aliyun.com"
    ),
    val extractionKeywords: List<String> = listOf(
        "提取码：", "密码：", "提取码:", "密码:", "访问码:", "访问码：", "提取码 : "
    ),
    val httpsOnlyKeywords: List<String> = listOf("123pan.com"),
    val linkBlacklistKeywords: List<String> = listOf("无"),
    val windowConfiguration: List<Float> = listOf(1200f, 1100f, 0.6f, 200f, 150f, 400f, 80f),
    val launchWindowingMode: Int = 5, // 5 = AOSP Freeform, 100 = ColorOS Zoom
    val fluidCloudPosition: String = "顶部", // "顶部", "底部"
    val fluidCloudPositionOffset: Int = 10,
    val fluidCloudTimeout: Long = 3000L,
    val browser: String = "自动",
    val browserBlacklist: List<String> = listOf(
        "com.jingdong.app.mall", "com.taobao.taobao", "air.svran.browser.nb",
        "com.trianguloy.urlchecker", "com.tmall.wireless", "com.thestore.main"
    ),
    val allowChar: String = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM#_/-:?%@=.&,，；;1234567890",
    val showMultipleUsers: Boolean = true,
    val showToast: Boolean = true,
    val extraDefaultAction: String = "询问"
)
