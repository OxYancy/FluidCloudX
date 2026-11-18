package com.nyehueh.fluidcloud.core.launcher

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import com.nyehueh.fluidcloud.core.model.FluidConfig
import com.nyehueh.fluidcloud.core.model.MatchedResult

class AppLauncher(
    private val context: Context,
    private val config: FluidConfig = FluidConfig()
) {

    enum class LaunchMode {
        FULLSCREEN,
        WINDOW
    }

    fun launch(result: MatchedResult, mode: LaunchMode) {
        // 1. Copy Extraction Code to Clipboard if exists
        result.extractionCode?.let { code ->
            if (code.isNotBlank()) {
                copyToClipboard(code, "提取码已自动复制: $code")
            }
        }

        // 2. Build Intent
        val intent = buildIntent(result)

        // 3. Launch with Mode
        when (mode) {
            LaunchMode.FULLSCREEN -> {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            LaunchMode.WINDOW -> {
                launchInWindow(intent)
            }
        }
    }

    private fun buildIntent(result: MatchedResult): Intent {
        val intent = if (result.type == "intent") {
            try {
                Intent.parseUri(result.targetUriOrIntent, 0)
            } catch (e: Exception) {
                Intent(Intent.ACTION_VIEW, Uri.parse(result.targetUriOrIntent))
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(result.targetUriOrIntent))
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

        result.targetPackage?.let { pkg ->
            intent.setPackage(pkg)
            result.targetActivity?.let { act ->
                intent.setClassName(pkg, act)
            }
        }

        return intent
    }

    private fun launchInWindow(intent: Intent) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        display.getRealSize(point)

        @Suppress("DEPRECATION")
        val rotation = display.rotation
        val isPortrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180

        val cfg = config.windowConfiguration
        val width = if (isPortrait) cfg.getOrElse(0) { 1200f }.toInt() else cfg.getOrElse(1) { 1100f }.toInt()
        val aspectRatio = cfg.getOrElse(2) { 0.6f }
        val height = (width / aspectRatio).toInt()
        val left = if (isPortrait) cfg.getOrElse(3) { 200f }.toInt() else cfg.getOrElse(4) { 150f }.toInt()
        val top = if (isPortrait) cfg.getOrElse(5) { 400f }.toInt() else cfg.getOrElse(6) { 80f }.toInt()

        val bounds = Rect(left, top, left + width, top + height)

        val options = ActivityOptions.makeBasic()

        // Detect ColorOS / Oplus or standard AOSP Freeform mode
        val isOplus = isPackageInstalled("com.oplus.gesture") || isPackageInstalled("oplus")
        val windowingMode = if (isOplus) 100 else config.launchWindowingMode

        try {
            // Reflect setLaunchWindowingMode (Hidden API)
            val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            method.invoke(options, windowingMode)
        } catch (e: Exception) {
            // Fallback for custom ROMs
        }

        options.launchBounds = bounds

        try {
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            // Fallback to fullscreen if freeform fails
            context.startActivity(intent)
        }
    }

    fun copyToClipboard(text: String, toastMsg: String? = null) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("FluidCloud", text)
        clipboard.setPrimaryClip(clip)

        if (config.showToast && toastMsg != null) {
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
