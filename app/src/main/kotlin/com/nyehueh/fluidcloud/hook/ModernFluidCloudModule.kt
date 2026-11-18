package com.nyehueh.fluidcloud.hook

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 纯正 LSPosed / LibXposed API 102 原生规范模块
 * 专为系统级纯净调度设计：仅 Hook 系统框架 (android / system_server)，不注入任何第三方应用进程（防封号、零风控风险）
 */
class ModernFluidCloudModule : XposedModule() {

    companion object {
        const val TAG = "FluidCloud-API102"
        private var cachedSystemContext: Context? = null

        private fun getSystemContext(thisObj: Any?): Context? {
            if (cachedSystemContext != null) return cachedSystemContext
            cachedSystemContext = try {
                val atClass = Class.forName("android.app.ActivityThread")
                val at = atClass.getMethod("currentActivityThread").invoke(null)
                at?.javaClass?.getMethod("getSystemContext")?.invoke(at) as? Context
            } catch (_: Exception) {
                null
            }
            if (cachedSystemContext == null && thisObj != null) {
                cachedSystemContext = try {
                    thisObj.javaClass.getMethod("getContext").invoke(thisObj) as? Context
                } catch (_: Exception) {
                    null
                }
            }
            return cachedSystemContext
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)

        // 仅在系统框架进程 (android / system_server) 激活拦截逻辑
        // 绝不注入 QQ、微信、支付宝等任何第三方应用进程，避免触发应用反作弊与封号风控
        if (param.packageName != "android") {
            return
        }

        Log.i(TAG, "API 102: system_server (android) loaded -> initializing pure framework hooks")
        val classLoader = param.defaultClassLoader

        // =========================================================================
        // 1. 拦截全局 Activity 启动 (ActivityStartController / ATMS)
        // =========================================================================
        try {
            val ascClass = classLoader.loadClass("com.android.server.wm.ActivityStartController")
            val obtainStarterMethod = ascClass.getDeclaredMethod(
                "obtainStarter",
                Intent::class.java,
                String::class.java
            )
            hook(obtainStarterMethod).intercept { chain ->
                val intent = chain.getArg(0) as? Intent
                val reason = chain.getArg(1) as? String ?: ""
                if (intent != null) {
                    val url = extractUrlFromIntent(intent)
                    if (url != null) {
                        Log.d(TAG, "🔥 [API 102 系统外链捕获] -> $url | reason=$reason")
                        val ctx = getSystemContext(chain.thisObject)
                        if (ctx != null) {
                            broadcastDetectedText(ctx, url, "system_intent")
                        }
                    }
                }
                chain.proceed()
            }
            Log.i(TAG, "API 102: Hooked ActivityStartController.obtainStarter successfully")
        } catch (e: Exception) {
            // 兜底机制：若定制 ROM 修改了 obtainStarter，反射扫描 ATMS.startActivity
            try {
                val atmsClass = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService")
                val startMethods = atmsClass.declaredMethods.filter {
                    it.name == "startActivity" && it.parameterTypes.contains(Intent::class.java)
                }
                for (m in startMethods) {
                    hook(m).intercept { chain ->
                        val intent = chain.args.firstOrNull { it is Intent } as? Intent
                        if (intent != null) {
                            val url = extractUrlFromIntent(intent)
                            if (url != null) {
                                Log.d(TAG, "🔥 [API 102 ATMS 外链捕获] -> $url")
                                val ctx = getSystemContext(chain.thisObject)
                                if (ctx != null) {
                                    broadcastDetectedText(ctx, url, "system_intent")
                                }
                            }
                        }
                        chain.proceed()
                    }
                }
                Log.i(TAG, "API 102 Fallback: Hooked ATMS.startActivity methods")
            } catch (ex: Exception) {
                Log.e(TAG, "API 102: Failed to hook system activity starter", ex)
            }
        }

        // =========================================================================
        // 2. 拦截系统剪贴板服务 (ClipboardService.setPrimaryClip)
        // =========================================================================
        try {
            val clipboardClass = classLoader.loadClass("com.android.server.clipboard.ClipboardService")
            val setPrimaryClipMethod = clipboardClass.declaredMethods.firstOrNull {
                it.name == "setPrimaryClip" && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == ClipData::class.java
            }
            if (setPrimaryClipMethod != null) {
                hook(setPrimaryClipMethod).intercept { chain ->
                    val clip = chain.getArg(0) as? ClipData
                    val callingPkg = chain.getArg(1) as? String ?: ""

                    // 自身复制的链接不重复触发
                    if (clip != null && clip.itemCount > 0 && callingPkg != "com.nyehueh.fluidcloud") {
                        val text = clip.getItemAt(0)?.text?.toString()
                        val thisObj = chain.thisObject
                        if (!text.isNullOrBlank() && thisObj != null) {
                            val context = getSystemContext(thisObj)
                            if (context != null) {
                                Log.d(TAG, "🔥 [API 102 剪贴板变动] from $callingPkg: $text")
                                broadcastDetectedText(context, text, "clipboard")
                            }
                        }
                    }
                    chain.proceed()
                }
                Log.i(TAG, "API 102: Hooked ClipboardService.setPrimaryClip successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "API 102: Failed to hook ClipboardService", e)
        }
    }
}

// =========================================================================
// 辅助与解析方法
// =========================================================================

fun broadcastDetectedText(context: Context, text: String, source: String) {
    val intent = Intent("com.nyehueh.fluidcloud.ACTION_TEXT_DETECTED").apply {
        setPackage("com.nyehueh.fluidcloud")
        putExtra("rawText", text)
        putExtra("source", source)
    }
    try {
        val sendMethod = context.javaClass.getMethod("sendBroadcastAsUser", Intent::class.java, UserHandle::class.java)
        val allUser = UserHandle::class.java.getField("ALL").get(null)
        sendMethod.invoke(context, intent, allUser)
    } catch (e: Exception) {
        context.sendBroadcast(intent)
    }
}

fun extractUrlFromIntent(intent: Intent): String? {
    val dataString = intent.dataString
    if (isWebUrl(dataString)) return dataString

    val extras: Bundle? = intent.extras
    if (extras != null) {
        val urlExtra = extras.getString("url")
        if (isWebUrl(urlExtra)) return urlExtra

        val rawUrlExtra = extras.getString("rawUrl")
        if (isWebUrl(rawUrlExtra)) return rawUrlExtra

        for (key in listOf("target_url", "link", "web_url", "browser_url")) {
            val candidate = extras.getString(key)
            if (isWebUrl(candidate)) return candidate
        }
    }

    val clipData: ClipData? = intent.clipData
    if (clipData != null && clipData.itemCount > 0) {
        val clipText = clipData.getItemAt(0)?.text?.toString()
        if (isWebUrl(clipText)) return clipText
    }

    try {
        val uriStr = intent.toUri(Intent.URI_INTENT_SCHEME)
        val regex = Regex("S\\.(url|rawUrl)=(.*?);")
        val match = regex.find(uriStr)
        if (match != null) {
            val decoded = Uri.decode(match.groupValues[2])
            if (isWebUrl(decoded)) return decoded
        }
    } catch (_: Exception) {}

    return null
}

private fun isWebUrl(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    return text.startsWith("http://", ignoreCase = true) ||
           text.startsWith("https://", ignoreCase = true)
}
