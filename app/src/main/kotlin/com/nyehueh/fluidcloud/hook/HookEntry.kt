package com.nyehueh.fluidcloud.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
        debugTag = "FluidCloudX-Hook"
    }

    override fun onHook() = encase {
        loadSystem {
            loadHooker(SystemServerHook)
        }

        loadApp("com.tencent.mobileqq") {
            loadHooker(QQHook)
        }

        loadApp("com.tencent.mm") {
            loadHooker(WeChatHook)
        }
    }
}
