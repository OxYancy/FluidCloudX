package com.nyehueh.fluidcloud.overlay

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nyehueh.fluidcloud.core.engine.RuleEngine
import com.nyehueh.fluidcloud.core.extractor.LinkExtractor
import com.nyehueh.fluidcloud.core.launcher.AppLauncher
import com.nyehueh.fluidcloud.core.model.MatchedResult
import com.nyehueh.fluidcloud.data.RuleRepository

class FluidOverlayService : Service() {

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

        val filter = IntentFilter("com.nyehueh.fluidcloud.ACTION_TEXT_DETECTED")
        registerReceiver(textReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(textReceiver)
        removeOverlay()
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

    private fun showFluidCloudCapsule(result: MatchedResult) {
        mainHandler.post {
            removeOverlay()

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
            }

            // 1. App Icon
            val iconView = ImageView(this).apply {
                val iconSize = 110
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

            // 5. Swipe gesture handling
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
                        if (isTop && diff < -80f) {
                            // Swipe up to dismiss
                            removeOverlay()
                            true
                        } else if (!isTop && diff > 80f) {
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

            windowManager.addView(layout, params)
            currentOverlayView = layout

            // Auto dismiss after timeout
            mainHandler.postDelayed({
                if (currentOverlayView == layout) {
                    removeOverlay()
                }
            }, config.fluidCloudTimeout)
        }
    }

    private fun removeOverlay() {
        currentOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed
            }
            currentOverlayView = null
        }
    }
}
