package com.nyehueh.fluidcloud.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nyehueh.fluidcloud.data.RuleRepository
import com.nyehueh.fluidcloud.overlay.FluidOverlayService
import com.nyehueh.fluidcloud.ui.screens.SettingsScreen
import com.nyehueh.fluidcloud.ui.theme.FluidCloudTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: RuleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = RuleRepository(this)

        checkAndRequestOverlayPermission()
        startOverlayService()

        setContent {
            FluidCloudTheme {
                SettingsScreen(
                    repository = repository,
                    onOpenOverlayPermission = {
                        requestOverlayPermission()
                    }
                )
            }
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限以正常展示流体云胶囊", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, FluidOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
