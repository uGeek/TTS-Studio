package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioExportHelper
import com.example.audio.TtsManager
import com.example.audio.TtsPlaybackState
import com.example.data.local.AppDatabase
import com.example.data.local.ExportedAudioEntity
import com.example.data.repository.AppSettingsRepository
import com.example.sync.WebDavSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

class FloatingBubbleService : Service() {
    private val tag = "FloatingBubbleService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var dismissTargetView: View? = null
    private var isDismissTargetAdded = false

    private var params: WindowManager.LayoutParams? = null
    private var dismissParams: WindowManager.LayoutParams? = null

    private lateinit var ttsManager: TtsManager
    private lateinit var appSettings: AppSettingsRepository
    private var ttsObserverJob: Job? = null

    private var isExpanded = false
    private var isDragging = false
    private var isInDismissZone = false

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var cachedClipboardText: String = ""

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm != null && cm.hasPrimaryClip()) {
                val item = cm.primaryClip?.getItemAt(0)
                val text = item?.coerceToText(this)?.toString()
                if (!text.isNullOrBlank()) {
                    cachedClipboardText = text
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Clip listener exception: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager.getInstance(this)
        appSettings = AppSettingsRepository.getInstance(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.addPrimaryClipChangedListener(clipListener)
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                if (!clipText.isNullOrBlank()) {
                    cachedClipboardText = clipText
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Error adding clip listener: ${e.message}")
        }

        createNotificationChannel()

        // Android 14+ / 15 / 16 (API 34-36) safe foreground service initialization
        val notification = createNotification("Toca para leer portapapeles desde el principio")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "startForeground error", e)
            startForeground(NOTIFICATION_ID, notification)
        }

        setupFloatingBubble()
        createDismissTargetView()
        observeTtsState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                closeAppAndStopService()
            }
            ACTION_RESTART_CLIPBOARD, ACTION_READ_CLIPBOARD -> {
                readClipboardContent()
            }
            ACTION_PREV -> {
                ttsManager.previousParagraph()
            }
            ACTION_PLAY_PAUSE -> {
                val state = ttsManager.state.value
                if (state.playbackState == TtsPlaybackState.PLAYING) {
                    ttsManager.pause()
                } else if (state.playbackState == TtsPlaybackState.PAUSED) {
                    ttsManager.resume()
                } else {
                    readClipboardContent()
                }
            }
            ACTION_NEXT -> {
                ttsManager.nextParagraph()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lector Flotante TTS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de burbuja flotante y reproducción de voz TTS"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification {
        // Tapping notification restarts clipboard reading from the beginning
        val restartIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_RESTART_CLIPBOARD
        }
        val pendingRestartIntent = PendingIntent.getService(
            this, 0, restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous Action
        val prevIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getService(
            this, 1, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play / Pause Action
        val playPauseIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next Action
        val nextIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Close Action
        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 4, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPlaying = ttsManager.state.value.playbackState == TtsPlaybackState.PLAYING
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pausar" else "Reproducir"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TTS Studio")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingRestartIntent)
            .addAction(android.R.drawable.ic_media_previous, "<", prevPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, ">", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "X", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getOverlayLayoutType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val metrics = wm?.currentWindowMetrics
            val bounds = metrics?.bounds
            Pair(
                bounds?.width() ?: resources.displayMetrics.widthPixels,
                bounds?.height() ?: resources.displayMetrics.heightPixels
            )
        } else {
            val dm = resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        bubbleView = createBubbleLayout()

        try {
            windowManager?.addView(bubbleView, params)
            appSettings.setFloatingBubbleActive(true)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add floating bubble view", e)
            stopSelf()
        }
    }

    private fun createDismissTargetView() {
        val density = resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        val targetContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dpToPx(72), dpToPx(72))
        }

        val circleView = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xDDDC2626.toInt()) // Red dismiss target
                setStroke(dpToPx(2), Color.WHITE)
            }
            background = bg
            elevation = dpToPx(10).toFloat()
            val lp = FrameLayout.LayoutParams(dpToPx(56), dpToPx(56)).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }

        val closeIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            val pad = dpToPx(12)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        circleView.addView(closeIcon)
        targetContainer.addView(circleView)

        dismissParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(48)
        }

        dismissTargetView = targetContainer
    }

    private fun showDismissTarget() {
        if (!isDismissTargetAdded && dismissTargetView != null) {
            try {
                windowManager?.addView(dismissTargetView, dismissParams)
                isDismissTargetAdded = true
            } catch (e: Exception) {
                Log.e(tag, "Error adding dismiss target", e)
            }
        }
    }

    private fun hideDismissTarget() {
        if (isDismissTargetAdded && dismissTargetView != null) {
            try {
                windowManager?.removeView(dismissTargetView)
            } catch (e: Exception) {
                Log.e(tag, "Error removing dismiss target", e)
            }
            isDismissTargetAdded = false
        }
    }

    private fun updateDismissTargetHighlight(isHovered: Boolean) {
        val container = dismissTargetView as? FrameLayout ?: return
        val circleView = container.getChildAt(0) as? FrameLayout ?: return
        val density = resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (isHovered) {
                setColor(0xFFFF0033.toInt()) // Vibrant bright red
                setStroke(dpToPx(3), Color.WHITE)
            } else {
                setColor(0xDDDC2626.toInt()) // Translucent crimson
                setStroke(dpToPx(2), Color.WHITE)
            }
        }
        circleView.background = bg

        val targetSize = if (isHovered) dpToPx(66) else dpToPx(56)
        val lp = circleView.layoutParams as? FrameLayout.LayoutParams
        if (lp != null && (lp.width != targetSize || lp.height != targetSize)) {
            lp.width = targetSize
            lp.height = targetSize
            circleView.layoutParams = lp
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubbleLayout(): View {
        val density = resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // 1. Circular Main Bubble Button
        val bubbleFrame = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF0284C7.toInt()) // Sky Blue
                setStroke(dpToPx(2), Color.WHITE)
            }
            background = bg
            elevation = dpToPx(8).toFloat()
            layoutParams = LinearLayout.LayoutParams(dpToPx(58), dpToPx(58))
        }

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.WHITE)
            val pad = dpToPx(14)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bubbleFrame.addView(icon)

        // 2. Expanded Mini Controls Panel: ONLY Contains [<] [Pause/Play] [>] [X]
        val expandedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val panelBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor(0xF00F172A.toInt()) // Sleek Deep Slate
                setStroke(dpToPx(1), 0x5594A3B8.toInt())
            }
            background = panelBg
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(48)
            ).apply {
                marginStart = dpToPx(8)
            }
            layoutParams = lp
            visibility = View.GONE
        }

        // Action 1: Previous Paragraph [<]
        val prevBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(0xFF38BDF8.toInt())
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
                ttsManager.previousParagraph()
            }
        }

        // Action 2: Play / Pause Button
        val playPauseBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(0xFF38BDF8.toInt())
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
                val state = ttsManager.state.value
                if (state.playbackState == TtsPlaybackState.PLAYING) {
                    ttsManager.pause()
                } else if (state.playbackState == TtsPlaybackState.PAUSED) {
                    ttsManager.resume()
                } else {
                    if (state.paragraphs.isNotEmpty()) {
                        ttsManager.resume()
                    } else if (state.fullText.isNotBlank()) {
                        ttsManager.playText(state.fullText, startIndex = 0)
                    } else {
                        readClipboardContent()
                    }
                }
            }
        }

        // Action 3: Next Paragraph [>]
        val nextBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(0xFF38BDF8.toInt())
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
                ttsManager.nextParagraph()
            }
        }

        // Action 4: Record / Export to Audio (Last Selected Format)
        val recordBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_record_audio)
            setColorFilter(0xFFFB7185.toInt()) // Rose Red / Record color
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
                recordCurrentTextToAudio()
            }
        }

        // Action 5: Paste from Clipboard into App / TTS and play immediately
        val pasteBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_paste_clipboard)
            setColorFilter(0xFF38BDF8.toInt())
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
                pasteClipboardToTts()
            }
        }

        // Action 6: Open App (to the left of Close 'X')
        val openAppBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_open_app)
            setColorFilter(0xFF10B981.toInt()) // Emerald Green
            val pad = dpToPx(6)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
                openApp()
            }
        }

        // Action 7: Close / Hide icons [X]
        val closePanelBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFF87171.toInt()) // Soft Coral Red
            val pad = dpToPx(7)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
            setOnClickListener {
                isExpanded = false
                expandedPanel.visibility = View.GONE
            }
        }

        expandedPanel.addView(prevBtn)
        expandedPanel.addView(playPauseBtn)
        expandedPanel.addView(nextBtn)
        expandedPanel.addView(recordBtn)
        expandedPanel.addView(pasteBtn)
        expandedPanel.addView(openAppBtn)
        expandedPanel.addView(closePanelBtn)

        container.addView(bubbleFrame)
        container.addView(expandedPanel)

        // 3. Touch & Drag-to-Close gesture handling (2-second hold to reveal controls panel)
        bubbleFrame.setOnTouchListener { _, event ->
            val curParams = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = curParams.x
                    initialY = curParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isInDismissZone = false
                    longPressTriggered = false

                    // Schedule long-press for 2 seconds (2000ms) to show/hide controls panel
                    longPressRunnable = Runnable {
                        if (!isDragging) {
                            longPressTriggered = true
                            isExpanded = !isExpanded
                            expandedPanel.visibility = if (isExpanded) View.VISIBLE else View.GONE
                            try {
                                bubbleFrame.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            } catch (ignored: Exception) {}
                            val msg = if (isExpanded) "Panel de controles mostrado" else "Panel de controles ocultado"
                            Toast.makeText(this@FloatingBubbleService, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    mainHandler.postDelayed(longPressRunnable!!, 2000L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(deltaX) > 12 || Math.abs(deltaY) > 12) {
                        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (!isDragging) {
                            isDragging = true
                            showDismissTarget()
                        }
                        curParams.x = initialX + deltaX
                        curParams.y = initialY + deltaY

                        // Check proximity to bottom dismiss target circle
                        val (screenWidth, screenHeight) = getScreenDimensions()
                        val bubbleCenterX = curParams.x + dpToPx(29)
                        val bubbleCenterY = curParams.y + dpToPx(29)

                        val dismissCenterX = screenWidth / 2
                        val dismissCenterY = screenHeight - dpToPx(48 + 28)

                        val distance = hypot(
                            (bubbleCenterX - dismissCenterX).toDouble(),
                            (bubbleCenterY - dismissCenterY).toDouble()
                        )

                        isInDismissZone = distance < dpToPx(80)
                        updateDismissTargetHighlight(isInDismissZone)

                        try {
                            windowManager?.updateViewLayout(bubbleView, curParams)
                        } catch (e: Exception) {
                            Log.w(tag, "Error updating bubble layout", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    hideDismissTarget()

                    if (isInDismissZone) {
                        // Dropped on dismiss target -> Close/hide bubble
                        Toast.makeText(this@FloatingBubbleService, "Burbuja flotante cerrada", Toast.LENGTH_SHORT).show()
                        stopSelf()
                    } else if (longPressTriggered) {
                        // 2-second hold already toggled the controls panel
                    } else if (!isDragging) {
                        // Regular tap on circular bubble: READ & PLAY CLIPBOARD IMMEDIATELY
                        readClipboardContent()
                    } else {
                        // Drag ended elsewhere -> Snap smoothly to left or right screen edge
                        val (screenWidth, _) = getScreenDimensions()
                        val middle = screenWidth / 2
                        curParams.x = if (curParams.x < middle) dpToPx(16) else (screenWidth - dpToPx(74))
                        try {
                            windowManager?.updateViewLayout(bubbleView, curParams)
                        } catch (e: Exception) {
                            Log.w(tag, "Error snapping bubble", e)
                        }
                    }
                    isDragging = false
                    isInDismissZone = false
                    longPressTriggered = false
                    true
                }
                else -> false
            }
        }

        return container
    }

    private fun openApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error al abrir la app desde la burbuja", e)
            Toast.makeText(this, "No se pudo abrir la aplicación", Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeAppAndStopService() {
        ttsManager.stop()
        appSettings.setFloatingBubbleActive(false)
        Toast.makeText(this, "TTS Studio cerrado", Toast.LENGTH_SHORT).show()

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(homeIntent)
        } catch (ignored: Exception) {}

        stopSelf()
    }

    private fun pasteClipboardToTts(): Boolean {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).coerceToText(this)?.toString()
                    if (!text.isNullOrBlank()) {
                        ttsManager.playText(text, startIndex = 0)
                        val preview = if (text.length > 30) "${text.take(30)}..." else text
                        Toast.makeText(this, "Reproduciendo: \"$preview\"", Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Direct clipboard read not available in background: ${e.message}")
        }

        // On Android 10+ background processes cannot read clipboard without focus.
        // Launch TransparentClipboardActivity to temporarily gain focus and read clipboard seamlessly.
        try {
            val intent = Intent(this, TransparentClipboardActivity::class.java).apply {
                action = TransparentClipboardActivity.ACTION_PASTE_AND_PLAY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch TransparentClipboardActivity", e)
            Toast.makeText(this, "No se encontró texto en el portapapeles", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun recordCurrentTextToAudio() {
        var textToExport = ttsManager.state.value.fullText
        if (textToExport.isBlank()) {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null && clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                    textToExport = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
                }
            } catch (ignored: Exception) {}
        }

        if (textToExport.isBlank()) {
            // Try via TransparentClipboardActivity
            try {
                val intent = Intent(this, TransparentClipboardActivity::class.java).apply {
                    action = TransparentClipboardActivity.ACTION_PASTE_AND_RECORD
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                }
                startActivity(intent)
                return
            } catch (e: Exception) {
                Toast.makeText(this, "No hay texto para grabar. Copia o pega un texto primero.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val lastFormat = appSettings.lastExportFormat.value.ifBlank { "MP3" }
        Toast.makeText(this, "Grabando audio en formato $lastFormat...", Toast.LENGTH_SHORT).show()

        serviceScope.launch(Dispatchers.IO) {
            try {
                val customPath = appSettings.localAudioDirectory.value.trim()
                val targetDir = if (customPath.isNotEmpty()) {
                    val dir = File(customPath)
                    if (dir.exists() || dir.mkdirs()) dir else File(filesDir, "audio_exports")
                } else {
                    File(filesDir, "audio_exports")
                }
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val firstLine = textToExport.trim().lines().firstOrNull { it.isNotBlank() } ?: "Grabacion_TTS"
                val cleanTitle = firstLine
                    .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
                    .trim()
                    .replace(Regex("\\s+"), " ")
                    .take(50)
                    .ifBlank { "Grabacion_TTS" }

                val exportResult = ttsManager.exportToFile(
                    text = textToExport,
                    title = cleanTitle,
                    format = lastFormat,
                    targetDirectory = targetDir
                )

                exportResult.onSuccess { file ->
                    val durationMs = AudioExportHelper.getAudioDurationMs(file)
                    val database = AppDatabase.getInstance(this@FloatingBubbleService)
                    val audioDao = database.exportedAudioDao()
                    val newAudio = ExportedAudioEntity(
                        title = cleanTitle,
                        originalText = textToExport,
                        filePath = file.absolutePath,
                        fileName = file.name,
                        format = lastFormat.uppercase(),
                        durationMs = durationMs,
                        fileSizeBytes = file.length(),
                        createdAt = System.currentTimeMillis()
                    )
                    val id = audioDao.insertAudio(newAudio)
                    val savedAudio = newAudio.copy(id = id)

                    // Auto sync if configured
                    val webDavConf = appSettings.webDavConfig.value
                    if (webDavConf.isEnabled && webDavConf.autoSyncOnExport) {
                        val webDavManager = WebDavSyncManager.getInstance(this@FloatingBubbleService)
                        webDavManager.uploadAudioFile(savedAudio, webDavConf)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FloatingBubbleService,
                            "Audio grabado con éxito ($lastFormat): ${file.name}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { err ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FloatingBubbleService,
                            "Error al grabar audio: ${err.localizedMessage ?: "Error desconocido"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error al procesar grabación de audio flotante", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@FloatingBubbleService,
                        "Error al grabar: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun readClipboardContent(): Boolean {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0)?.coerceToText(this)?.toString()
                    if (!text.isNullOrBlank()) {
                        Toast.makeText(this, "Leyendo portapapeles...", Toast.LENGTH_SHORT).show()
                        ttsManager.playText(text, startIndex = 0)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Clipboard direct access error: ${e.message}")
        }

        return pasteClipboardToTts()
    }

    private fun observeTtsState() {
        ttsObserverJob = serviceScope.launch {
            ttsManager.state.collect { state ->
                val bubbleContainer = bubbleView as? LinearLayout ?: return@collect
                val bubbleFrame = bubbleContainer.getChildAt(0) as? FrameLayout ?: return@collect
                val expandedPanel = bubbleContainer.getChildAt(1) as? LinearLayout ?: return@collect
                val playPauseBtn = expandedPanel.getChildAt(1) as? ImageView

                val density = resources.displayMetrics.density
                val dpToPx = { dp: Int -> (dp * density).toInt() }

                // Dynamic background glow/color
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    when (state.playbackState) {
                        TtsPlaybackState.PLAYING -> {
                            setColor(0xFF10B981.toInt()) // Emerald Green while reading
                            setStroke(dpToPx(3), Color.WHITE)
                        }
                        TtsPlaybackState.PAUSED -> {
                            setColor(0xFFF59E0B.toInt()) // Amber when paused
                            setStroke(dpToPx(2), Color.WHITE)
                        }
                        else -> {
                            setColor(0xFF0284C7.toInt()) // Cyan/Sky Blue default
                            setStroke(dpToPx(2), Color.WHITE)
                        }
                    }
                }
                bubbleFrame.background = bg

                // Update play/pause icon in mini panel
                if (state.playbackState == TtsPlaybackState.PLAYING) {
                    playPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    playPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
                }

                // Update Ongoing Notification
                val notifText = when (state.playbackState) {
                    TtsPlaybackState.PLAYING -> {
                        val currentChunk = if (state.currentParagraphIndex in state.paragraphs.indices) {
                            state.paragraphs[state.currentParagraphIndex]
                        } else ""
                        "Reproduciendo (${state.currentParagraphIndex + 1}/${state.paragraphs.size}): ${currentChunk.take(30)}..."
                    }
                    TtsPlaybackState.PAUSED -> "En pausa. Toca para reiniciar desde el inicio."
                    else -> "Toca para leer el portapapeles desde el principio."
                }
                updateNotification(notifText)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.removePrimaryClipChangedListener(clipListener)
        } catch (ignored: Exception) {}
        ttsObserverJob?.cancel()
        serviceScope.cancel()
        hideDismissTarget()
        try {
            if (bubbleView != null) {
                windowManager?.removeView(bubbleView)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error removing bubble view on destroy", e)
        }
        appSettings.setFloatingBubbleActive(false)
    }

    companion object {
        const val CHANNEL_ID = "tts_floating_bubble_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_SERVICE"
        const val ACTION_READ_CLIPBOARD = "com.example.service.ACTION_READ_CLIPBOARD"
        const val ACTION_RESTART_CLIPBOARD = "com.example.service.ACTION_RESTART_CLIPBOARD"
        const val ACTION_PREV = "com.example.service.ACTION_PREV"
        const val ACTION_PLAY_PAUSE = "com.example.service.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }
}
