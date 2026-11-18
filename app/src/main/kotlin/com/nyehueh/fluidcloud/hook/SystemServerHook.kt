package com.nyehueh.fluidcloud.hook

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog

object SystemServerHook : YukiBaseHooker() {

    override fun onHook() {
        // 1. Hook Clipboard Service: Monitor system-wide clipboard changes
        "com.android.server.clipboard.ClipboardService".toClass().apply {
            method {
                name = "setPrimaryClip"
            }.hook {
                after {
                    val clip = args[0] as? ClipData ?: return@after
                    if (clip.itemCount == 0) return@after
                    val text = clip.getItemAt(0)?.text?.toString() ?: return@after
                    val callingPkg = args.getOrNull(1) as? String ?: ""

                    // Don't intercept if copied by FluidCloud itself
                    if (callingPkg == "com.nyehueh.fluidcloud") return@after

                    YLog.debug("Clipboard change detected from $callingPkg: $text")

                    val context = instance.javaClass.getMethod("getContext").invoke(instance) as? Context ?: return@after
                    sendBroadcastToModule(context, text, "clipboard")
                }
            }
        }

        // 2. Hook ActivityTaskManagerService: Intercept web navigation intents globally
        "com.android.server.wm.ActivityTaskManagerService".toClass().apply {
            method {
                name = "startActivity"
            }.hook {
                before {
                    val intent = args.firstOrNull { it is Intent } as? Intent ?: return@before
                    val callingPackage = args.getOrNull(1) as? String ?: ""

                    if (callingPackage == "com.nyehueh.fluidcloud") return@before

                    val data = intent.dataString
                    if (intent.action == Intent.ACTION_VIEW && data != null && (data.startsWith("http://") || data.startsWith("https://"))) {
                        YLog.debug("Global VIEW intent detected: $data from $callingPackage")
                        // Can notify Fluid Cloud overlay service
                    }
                }
            }
        }
    }

    private fun sendBroadcastToModule(context: Context, text: String, source: String) {
        val intent = Intent("com.nyehueh.fluidcloud.ACTION_TEXT_DETECTED").apply {
            setPackage("com.nyehueh.fluidcloud")
            putExtra("rawText", text)
            putExtra("source", source)
        }
        try {
            // Broadcast across users (UserHandle.ALL)
            val sendMethod = context.javaClass.getMethod("sendBroadcastAsUser", Intent::class.java, UserHandle::class.java)
            val allUser = UserHandle::class.java.getField("ALL").get(null)
            sendMethod.invoke(context, intent, allUser)
        } catch (e: Exception) {
            context.sendBroadcast(intent)
        }
    }
}
