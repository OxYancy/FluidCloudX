package com.nyehueh.fluidcloud.hook

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog

object WeChatHook : YukiBaseHooker() {

    override fun onHook() {
        // Hook WeChat In-App WebView Activity
        "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI".toClass().apply {
            method {
                name = "onCreate"
                param(android.os.Bundle::class.java)
            }.hook {
                after {
                    val activity = instance as? Activity ?: return@after
                    val intent = activity.intent ?: return@after

                    var targetUrl = intent.getStringExtra("rawUrl")
                    if (targetUrl.isNullOrBlank()) {
                        val uriStr = intent.toUri(0)
                        val match = Regex("S\\.rawUrl=(.*?);").find(uriStr)
                        if (match != null) {
                            targetUrl = Uri.decode(match.groupValues[1])
                        }
                    }

                    if (!targetUrl.isNullOrBlank() && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                        YLog.debug("Intercepted WeChat URL: $targetUrl")
                        val broadcastIntent = Intent("com.nyehueh.fluidcloud.ACTION_TEXT_DETECTED").apply {
                            setPackage("com.nyehueh.fluidcloud")
                            putExtra("rawText", targetUrl)
                            putExtra("source", "WeChat")
                        }
                        activity.sendBroadcast(broadcastIntent)
                    }
                }
            }
        }
    }
}
