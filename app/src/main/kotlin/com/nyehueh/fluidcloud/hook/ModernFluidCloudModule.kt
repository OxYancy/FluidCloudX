package com.nyehueh.fluidcloud.hook

import android.app.Activity
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
 * 采用 OkHttp 式 Interceptor Chain (拦截器链) 模型进行全局方法拦截
 */
class ModernFluidCloudModule : XposedModule() {

    companion object {
        const val TAG = "FluidCloud-API102"
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)

        val classLoader = param.defaultClassLoader

        when (param.packageName) {
            // =========================================================================
            // 1. 系统框架 (system_server)
            // =========================================================================
            "android" -> {
                Log.i(TAG, "API 102: system_server (android) loaded -> initializing framework hooks")

                // A. 拦截系统 Activity 启动控制器 (ActivityStartController.obtainStarter)
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

                // B. 拦截系统剪贴板服务 (ClipboardService.setPrimaryClip)
                try {
                    val clipboardClass = classLoader.loadClass("com.android.server.clipboard.ClipboardService")
                    val setPrimaryClipMethod = clipboardClass.declaredMethods.firstOrNull {
                        it.name == "setPrimaryClip" && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == ClipData::class.java
                    }
                    if (setPrimaryClipMethod != null) {
                        hook(setPrimaryClipMethod).intercept { chain ->
                            val clip = chain.getArg(0) as? ClipData
                            val callingPkg = chain.getArg(1) as? String ?: ""

                            if (clip != null && clip.itemCount > 0 && callingPkg != "com.nyehueh.fluidcloud") {
                                val text = clip.getItemAt(0)?.text?.toString()
                                val thisObj = chain.thisObject
                                if (!text.isNullOrBlank() && thisObj != null) {
                                    val context = try {
                                        thisObj.javaClass.getMethod("getContext").invoke(thisObj) as? Context
                                    } catch (_: Exception) {
                                        null
                                    }
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

            // =========================================================================
            // 2. QQ 内置网页 (com.tencent.mobileqq)
            // =========================================================================
            "com.tencent.mobileqq" -> {
                try {
                    val qqBrowser = classLoader.loadClass("com.tencent.mobileqq.activity.QQBrowserActivity")
                    val onCreate = qqBrowser.getDeclaredMethod("onCreate", Bundle::class.java)
                    hook(onCreate).intercept { chain ->
                        val activity = chain.thisObject as? Activity
                        val intent = activity?.intent
                        if (intent != null) {
                            var url = intent.getStringExtra("url")
                            if (url.isNullOrBlank()) {
                                val uriStr = intent.toUri(0)
                                val match = Regex("S\\.url=(.*?);").find(uriStr)
                                if (match != null) {
                                    url = Uri.decode(match.groupValues[1])
                                }
                            }
                            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                                Log.d(TAG, "🔥 [API 102 QQ 内置网页拦截] -> $url")
                                broadcastDetectedText(activity, url, "QQ")
                            }
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "API 102: Hooked QQBrowserActivity.onCreate")
                } catch (e: Exception) {
                    Log.e(TAG, "API 102: Failed to hook QQBrowserActivity", e)
                }
            }

            // =========================================================================
            // 3. 微信内置网页 (com.tencent.mm)
            // =========================================================================
            "com.tencent.mm" -> {
                try {
                    val mmWebView = classLoader.loadClass("com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI")
                    val onCreate = mmWebView.getDeclaredMethod("onCreate", Bundle::class.java)
                    hook(onCreate).intercept { chain ->
                        val activity = chain.thisObject as? Activity
                        val intent = activity?.intent
                        if (intent != null) {
                            var url = intent.getStringExtra("rawUrl")
                            if (url.isNullOrBlank()) {
                                val uriStr = intent.toUri(0)
                                val match = Regex("S\\.rawUrl=(.*?);").find(uriStr)
                                if (match != null) {
                                    url = Uri.decode(match.groupValues[1])
                                }
                            }
                            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                                Log.d(TAG, "🔥 [API 102 微信内置网页拦截] -> $url")
                                broadcastDetectedText(activity, url, "WeChat")
                            }
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "API 102: Hooked MMWebViewUI.onCreate")
                } catch (e: Exception) {
                    Log.e(TAG, "API 102: Failed to hook MMWebViewUI", e)
                }
            }
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
