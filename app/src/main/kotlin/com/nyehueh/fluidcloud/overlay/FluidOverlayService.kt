package com.nyehueh.fluidcloud.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.nyehueh.fluidcloud.core.engine.RuleEngine
import com.nyehueh.fluidcloud.core.extractor.LinkExtractor
import com.nyehueh.fluidcloud.core.launcher.AppLauncher
import com.nyehueh.fluidcloud.core.model.MatchedResult
import com.nyehueh.fluidcloud.data.RuleRepository

class FluidOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "fluid_cloud_overlay_channel"
        const val NOTIFICATION_ID = 1001

        fun showTestPopup(context: Context, testText: String? = null) {
            val intent = Intent(context, FluidOverlayService::class.java).apply {
                putExtra("action", "test_popup")
                if (testText != null) {
                    putExtra("sampleText", testText)
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var repository: RuleRepository
    private lateinit var linkExtractor: LinkExtractor
    private lateinit var launcher: AppLauncher

    private var currentOverlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val textReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.nyehueh.fluidcloud.ACTION_TEXT_DETECTED") {
                val rawText = intent.getStringExtra("rawText") ?: return
                processInputText(rawText)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = RuleRepository(this)
        linkExtractor = LinkExtractor(repository.config.value)
        launcher = AppLauncher(this, repository.config.value)

        ensureNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val filter = IntentFilter("com.nyehueh.fluidcloud.ACTION_TEXT_DETECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(textReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(textReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val action = intent?.getStringExtra("action")
        when (action) {
            "test_popup" -> {
                val sampleText = intent.getStringExtra("sampleText") ?: "https://pan.baidu.com/s/1xxxxxx 提取码: 8888"
                triggerPopupWithFallback(sampleText)
            }
            "remove" -> {
                removeOverlay()
            }
            else -> {
                val rawText = intent?.getStringExtra("rawText")
                if (!rawText.isNullOrBlank()) {
                    processInputText(rawText)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(textReceiver)
        } catch (_: Exception) {}
        removeOverlayDirectly()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun processInputText(text: String) {
        val extractedLinks = linkExtractor.extract(text)
        val linkToProcess = extractedLinks.firstOrNull() ?: return

        val ruleEngine = RuleEngine(this, repository.rules.value)
        val matches = ruleEngine.match(linkToProcess)
        if (matches.isEmpty()) return

        val bestMatch = matches.first()
        showFluidCloudCapsule(bestMatch)
    }

    private fun triggerPopupWithFallback(text: String) {
        val extractedLinks = linkExtractor.extract(text)
        val linkToProcess = extractedLinks.firstOrNull()
        if (linkToProcess != null) {
            val ruleEngine = RuleEngine(this, repository.rules.value)
            val matches = ruleEngine.match(linkToProcess)
            if (matches.isNotEmpty()) {
                showFluidCloudCapsule(matches.first())
                return
            }
        }

        // 兜底测试示例弹窗（当测试输入未匹配到任何特定规则时）
        val demoMatch = MatchedResult(
            ruleName = "百度网盘 (测试弹窗)",
            type = "url",
            targetUriOrIntent = "bdnetdisk://n/action.VIEW_SHORTCUT?origin=com.android.browser&url=https://pan.baidu.com/s/1xxxxxx",
            targetPackage = "com.baidu.netdisk",
            targetActivity = null,
            extractionCode = "8888",
            title = "百度网盘 (流体云胶囊)",
            message = "提取码: 8888 | 点击主体全屏打开，右侧小窗打开",
            iconPkg = "com.baidu.netdisk"
        )
        showFluidCloudCapsule(demoMatch)
    }

    private fun showFluidCloudCapsule(result: MatchedResult) {
        mainHandler.post {
            if (!Settings.canDrawOverlays(this)) return@post

            removeOverlayDirectly()

            val config = repository.config.value
            val isTop = config.fluidCloudPosition == "顶部"

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(36, 24, 36, 24)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EE1E1E1E"))
                    cornerRadius = 80f
                    setStroke(2, Color.parseColor("#33FFFFFF"))
                }
                elevation = 20f
            }

            // 1. App Icon
            val iconView = ImageView(this).apply {
                val iconSize = 100
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = 24
                }
                val iconDrawable = result.iconPkg?.let { pkg ->
                    try {
                        packageManager.getApplicationIcon(pkg)
                    } catch (e: Exception) {
                        null
                    }
                } ?: getDrawable(android.R.drawable.sym_def_app_icon)
                setImageDrawable(iconDrawable)
            }
            layout.addView(iconView)

            // 2. Title & Message text
            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                val titleView = TextView(context).apply {
                    text = result.title
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val msgView = TextView(context).apply {
                    text = result.message
                    textSize = 12f
                    setTextColor(Color.parseColor("#B0B0B0"))
                    maxLines = 1
                }
                addView(titleView)
                addView(msgView)
            }
            layout.addView(textLayout)

            // 3. Small Window Button
            val windowBtn = TextView(this).apply {
                text = "小窗"
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(32, 16, 32, 16)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#33FFFFFF"))
                    cornerRadius = 40f
                }
                setOnClickListener {
                    removeOverlay()
                    launcher.launch(result, AppLauncher.LaunchMode.WINDOW)
                }
            }
            layout.addView(windowBtn)

            // 4. Tap body to open Fullscreen
            layout.setOnClickListener {
                removeOverlay()
                launcher.launch(result, AppLauncher.LaunchMode.FULLSCREEN)
            }

            // 5. Swipe gesture handling (向上轻扫关闭，避免遮挡)
            var downY = 0f
            layout.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val upY = event.rawY
                        val diff = upY - downY
                        if (isTop && diff < -60f) {
                            removeOverlay()
                            true
                        } else if (!isTop && diff > 60f) {
                            removeOverlay()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }

            val screenWidth = resources.displayMetrics.widthPixels
            val params = WindowManager.LayoutParams(
                (screenWidth * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = if (isTop) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = config.fluidCloudPositionOffset + 40
            }

            // 入场动效：从顶部滑入并放大展开
            layout.alpha = 0f
            layout.translationY = if (isTop) -80f else 80f
            layout.scaleX = 0.9f
            layout.scaleY = 0.9f

            windowManager.addView(layout, params)
            currentOverlayView = layout

            layout.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // 自动定时淡出
            mainHandler.postDelayed({
                if (currentOverlayView == layout) {
                    removeOverlay()
                }
            }, config.fluidCloudTimeout)
        }
    }

    private fun removeOverlay() {
        val view = currentOverlayView ?: return
        currentOverlayView = null

        val isTop = repository.config.value.fluidCloudPosition == "顶部"
        view.animate()
            .alpha(0f)
            .translationY(if (isTop) -80f else 80f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(220)
            .withEndAction {
                removeOverlayDirectly(view)
            }
            .start()
    }

    private fun removeOverlayDirectly(view: View? = currentOverlayView) {
        val targetView = view ?: currentOverlayView ?: return
        try {
            windowManager.removeView(targetView)
        } catch (_: Exception) {}
        if (targetView == currentOverlayView) {
            currentOverlayView = null
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "流体云悬浮监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "维持流体云胶囊弹窗与系统外链分发监听"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("流体云服务运行中")
            .setContentText("实时拦截并分发系统外部链接")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
