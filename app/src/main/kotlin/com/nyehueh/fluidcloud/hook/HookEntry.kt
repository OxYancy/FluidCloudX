package com.nyehueh.fluidcloud.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
        debugTag = "FluidCloudX-Hook"
    }

    override fun onHook() = com.highcapable.yukihookapi.hook.factory.encase {
        loadSystem {
            // Hook system_server (Clipboard and Activity Management)
            loadApp("android") {
                SystemServerHook.onHook(this)
            }
        }

        loadApp("com.tencent.mobileqq") {
            QQHook.onHook(this)
        }

        loadApp("com.tencent.mm") {
            WeChatHook.onHook(this)
        }
    }
}
