package com.nyehueh.fluidcloud

import android.app.Application
import com.highcapable.yukihookapi.YukiHookAPI

class FluidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize YukiHookAPI context for Module App
        YukiHookAPI.configs {
            debugTag = "FluidCloudX"
            isDebug = BuildConfig.DEBUG
        }
    }
}
