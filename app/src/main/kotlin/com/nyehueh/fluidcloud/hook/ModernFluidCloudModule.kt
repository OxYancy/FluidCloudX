package com.nyehueh.fluidcloud.hook

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Method

/**
 * 纯正 LSPosed / LibXposed API 102 原生规范模块
 */
class ModernFluidCloudModule(
    base: XposedInterface,
    param: ModuleLoadedParam
) : XposedModule(base, param) {

    companion object {
        const val TAG = "FluidCloud-API102"
    }

    override fun onSystemServerLoaded(param: SystemServerLoadedParam) {
        super.onSystemServerLoaded(param)
        Log.i(TAG, "system_server loaded -> initializing API 102 hooks")

        val classLoader = param.classLoader

        // 1. Hook ActivityStartController.obtainStarter(Intent, String)
        try {
            val ascClass = classLoader.loadClass("com.android.server.wm.ActivityStartController")
            val obtainStarterMethod = ascClass.getDeclaredMethod(
                "obtainStarter",
                Intent::class.java,
                String::class.java
            )
            hook(obtainStarterMethod, SystemIntentHooker::class.java)
            Log.i(TAG, "API 102: Hooked ActivityStartController.obtainStarter successfully")
        } catch (e: Exception) {
            // Fallback for custom ROMs: hook ATMS.startActivity
            try {
                val atmsClass = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService")
                val startMethods = atmsClass.declaredMethods.filter {
                    it.name == "startActivity" && it.parameterTypes.contains(Intent::class.java)
                }
                for (m in startMethods) {
                    hook(m, SystemIntentHooker::class.java)
                }
                Log.i(TAG, "API 102 Fallback: Hooked ATMS.startActivity methods")
            } catch (ex: Exception) {
                Log.e(TAG, "API 102: Failed to hook system activity starter", ex)
            }
        }

        // 2. Hook ClipboardService.setPrimaryClip
        try {
            val clipboardClass = classLoader.loadClass("com.android.server.clipboard.ClipboardService")
            val setPrimaryClipMethod = clipboardClass.declaredMethods.firstOrNull {
                it.name == "setPrimaryClip" && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == ClipData::class.java
            }
            if (setPrimaryClipMethod != null) {
                hook(setPrimaryClipMethod, SystemClipboardHooker::class.java)
                Log.i(TAG, "API 102: Hooked ClipboardService.setPrimaryClip successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "API 102: Failed to hook ClipboardService", e)
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)

        when (param.packageName) {
            "com.tencent.mobileqq" -> {
                try {
                    val qqBrowser = param.classLoader.loadClass("com.tencent.mobileqq.activity.QQBrowserActivity")
                    val onCreate = qqBrowser.getDeclaredMethod("onCreate", Bundle::class.java)
                    hook(onCreate, QQBrowserHooker::class.java)
                    Log.i(TAG, "API 102: Hooked QQBrowserActivity.onCreate")
                } catch (e: Exception) {
                    Log.e(TAG, "API 102: Failed to hook QQBrowserActivity", e)
                }
            }
            "com.tencent.mm" -> {
                try {
                    val mmWebView = param.classLoader.loadClass("com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI")
                    val onCreate = mmWebView.getDeclaredMethod("onCreate", Bundle::class.java)
                    hook(onCreate, WeChatWebViewHooker::class.java)
                    Log.i(TAG, "API 102: Hooked MMWebViewUI.onCreate")
                } catch (e: Exception) {
                    Log.e(TAG, "API 102: Failed to hook MMWebViewUI", e)
                }
            }
        }
    }

    // =========================================================================
    // API 102 Hooker Classes
    // =========================================================================

    @XposedHooker
    class SystemIntentHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @BeforeInvocation
            fun before(callback: BeforeHookCallback) {
                val intent = callback.args.firstOrNull { it is Intent } as? Intent ?: return
                val url = extractUrlFromIntent(intent) ?: return

                Log.d(TAG, "🔥 [API 102 系统框架外链捕获] -> $url (pkg=${intent.`package`})")
            }
        }
    }

    @XposedHooker
    class SystemClipboardHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @BeforeInvocation
            fun before(callback: BeforeHookCallback) {
                val clip = callback.args.getOrNull(0) as? ClipData ?: return
                if (clip.itemCount == 0) return
                val text = clip.getItemAt(0)?.text?.toString() ?: return
                val callingPkg = callback.args.getOrNull(1) as? String ?: ""

                if (callingPkg == "com.nyehueh.fluidcloud") return

                val thisObj = callback.thisObject ?: return
                val context = try {
                    thisObj.javaClass.getMethod("getContext").invoke(thisObj) as? Context
                } catch (e: Exception) {
                    null
                } ?: return

                Log.d(TAG, "🔥 [API 102 系统剪贴板变动] from $callingPkg: $text")
                broadcastDetectedText(context, text, "clipboard")
            }
        }
    }

    @XposedHooker
    class QQBrowserHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @BeforeInvocation
            fun before(callback: BeforeHookCallback) {
                val activity = callback.thisObject as? Activity ?: return
                val intent = activity.intent ?: return
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
        }
    }

    @XposedHooker
    class WeChatWebViewHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            @BeforeInvocation
            fun before(callback: BeforeHookCallback) {
                val activity = callback.thisObject as? Activity ?: return
                val intent = activity.intent ?: return
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
