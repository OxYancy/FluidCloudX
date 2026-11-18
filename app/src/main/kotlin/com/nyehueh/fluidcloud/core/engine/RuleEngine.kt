package com.nyehueh.fluidcloud.core.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.nyehueh.fluidcloud.core.model.Condition
import com.nyehueh.fluidcloud.core.model.CustomVariable
import com.nyehueh.fluidcloud.core.model.ExtractedLink
import com.nyehueh.fluidcloud.core.model.MatchedResult
import com.nyehueh.fluidcloud.core.model.Rule
import com.nyehueh.fluidcloud.core.model.RuleItem

class RuleEngine(
    private val context: Context,
    private val rules: List<Rule>
) {

    private val packageManager: PackageManager = context.packageManager

    fun match(extractedLink: ExtractedLink): List<MatchedResult> {
        val results = mutableListOf<MatchedResult>()

        for (rule in rules) {
            // Check triggers
            val isTriggerMatched = rule.trigger.any { pattern ->
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(extractedLink.url)
                } catch (e: Exception) {
                    false
                }
            }
            if (!isTriggerMatched) continue

            // Check availability (check package or scheme)
            val isAvailable = checkAvailability(rule)
            if (!isAvailable) continue

            // Variables Map
            val variableMap = mutableMapOf<String, String>(
                "input" to extractedLink.rawInput,
                "link" to extractedLink.url,
                "code" to (extractedLink.code ?: "")
            )

            // Resolve Custom Variables
            rule.customVariables?.forEach { customVar ->
                resolveCustomVariable(customVar, variableMap)
            }

            // Choose best RuleItem based on conditions
            val ruleItem = chooseBestRuleItem(rule.rule, extractedLink.code != null, variableMap)
            val resolvedRuleText = synthesize(ruleItem.ruleText, variableMap)

            val title = synthesize(rule.title ?: "打开 ${rule.name}", variableMap)
            val message = synthesize(rule.message ?: extractedLink.url, variableMap)

            // Check top condition
            val isTop = rule.topcondition?.let { top ->
                evaluateConditions(top.condition, top.manyConditionTactic, extractedLink.code != null, variableMap)
            } ?: false

            val targetPackage = rule.pkg ?: resolveTargetPackage(rule.type, resolvedRuleText)

            val result = MatchedResult(
                ruleName = rule.name,
                type = rule.type,
                targetUriOrIntent = resolvedRuleText,
                targetPackage = targetPackage,
                targetActivity = rule.activityname,
                extractionCode = extractedLink.code,
                title = title,
                message = message,
                iconPkg = rule.icon ?: targetPackage,
                userId = 0
            )

            if (isTop) {
                results.add(0, result)
            } else {
                results.add(result)
            }
        }

        // 默认浏览器兜底与联动：对于任意 http/https 链接（包括通用外链如 http://ghcr.io/kmworks/kmrs:latest）
        if (extractedLink.url.startsWith("http://", ignoreCase = true) ||
            extractedLink.url.startsWith("https://", ignoreCase = true)
        ) {
            val browserPkg = resolveDefaultBrowserPackage()
            val browserName = browserPkg?.let { getAppName(it) } ?: "浏览器"
            val browserResult = MatchedResult(
                ruleName = "浏览器打开",
                type = "url",
                targetUriOrIntent = extractedLink.url,
                targetPackage = browserPkg,
                targetActivity = null,
                extractionCode = extractedLink.code,
                title = "在${browserName}中打开",
                message = extractedLink.url,
                iconPkg = browserPkg,
                userId = 0
            )

            if (results.isEmpty()) {
                // 没有命中任何特定规则的纯外链，浏览器作为首要唤起目标
                results.add(browserResult)
            } else {
                // 已命中专属 App（如 B站），将浏览器作为备选加入列表末尾
                if (results.none { it.targetPackage == browserPkg }) {
                    results.add(browserResult)
                }
            }
        }

        return results
    }

    private fun resolveDefaultBrowserPackage(): String? {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
        val resolveInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                browserIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        val pkg = resolveInfo?.activityInfo?.packageName
        if (pkg != null && pkg != "android" && pkg != context.packageName && isPackageInstalled(pkg)) {
            return pkg
        }

        val activities = packageManager.queryIntentActivities(browserIntent, 0)
        for (info in activities) {
            val p = info.activityInfo.packageName
            if (p != context.packageName && p != "android" && isPackageInstalled(p)) {
                return p
            }
        }
        return null
    }

    private fun getAppName(packageName: String): String? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun checkAvailability(rule: Rule): Boolean {
        val checkTarget = rule.check ?: rule.pkg ?: return true
        return if (checkTarget.contains("://")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(checkTarget))
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        } else {
            try {
                packageManager.getPackageInfo(checkTarget, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun resolveTargetPackage(type: String, uriString: String): String? {
        return try {
            val intent = if (type == "intent") {
                Intent.parseUri(uriString, 0)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun chooseBestRuleItem(
        ruleItems: List<RuleItem>,
        withCode: Boolean,
        variableMap: Map<String, String>
    ): RuleItem {
        if (ruleItems.isEmpty()) return RuleItem(ruleText = "【link】")

        var bestItem = ruleItems.first()
        var maxConditionMatched = -1

        for (item in ruleItems) {
            val conditions = item.condition
            if (conditions.isNullOrEmpty()) {
                if (maxConditionMatched < 0) {
                    bestItem = item
                    maxConditionMatched = 0
                }
                continue
            }

            val passed = evaluateConditions(conditions, item.manyConditionTactic, withCode, variableMap)
            if (passed && conditions.size > maxConditionMatched) {
                maxConditionMatched = conditions.size
                bestItem = item
            }
        }

        return bestItem
    }

    private fun evaluateConditions(
        conditions: List<Condition>,
        tactic: String,
        withCode: Boolean,
        variableMap: Map<String, String>
    ): Boolean {
        if (conditions.isEmpty()) return true

        val results = conditions.map { cond ->
            when (cond.conditionType) {
                "withcode" -> withCode
                "withoutcode" -> !withCode
                "contains" -> {
                    val text = variableMap[cond.conditionText] ?: ""
                    val pattern = cond.conditionPattern ?: ""
                    try {
                        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
                    } catch (e: Exception) {
                        false
                    }
                }
                "notcontains" -> {
                    val text = variableMap[cond.conditionText] ?: ""
                    val pattern = cond.conditionPattern ?: ""
                    try {
                        !Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
                    } catch (e: Exception) {
                        true
                    }
                }
                else -> true
            }
        }

        return when (tactic.lowercase()) {
            "all" -> results.all { it }
            "any" -> results.any { it }
            "none" -> results.none { it }
            else -> results.all { it }
        }
    }

    private fun resolveCustomVariable(customVar: CustomVariable, variableMap: MutableMap<String, String>) {
        val sourceText = variableMap[customVar.text] ?: ""
        when (customVar.action) {
            "match" -> {
                val pattern = customVar.pattern ?: return
                try {
                    val match = Regex(pattern).find(sourceText)
                    val value = match?.groupValues?.getOrNull(customVar.index) ?: ""
                    variableMap[customVar.name] = value
                } catch (e: Exception) {
                    variableMap[customVar.name] = ""
                }
            }
            "replace" -> {
                val pattern = customVar.pattern ?: return
                val replacement = customVar.replacement ?: ""
                try {
                    variableMap[customVar.name] = sourceText.replace(Regex(pattern), replacement)
                } catch (e: Exception) {
                    variableMap[customVar.name] = sourceText
                }
            }
            "splicing" -> {
                val valueText = customVar.valuetext ?: return
                variableMap[customVar.name] = synthesize(valueText, variableMap)
            }
        }
    }

    private fun synthesize(template: String, variableMap: Map<String, String>): String {
        var result = template
        variableMap.forEach { (key, value) ->
            result = result.replace("【$key】", value)
        }
        return result
    }
}
