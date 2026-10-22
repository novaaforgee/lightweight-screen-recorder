package com.novaaforgee.screenrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var isPaused = false

    // Floating Overlay
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null

    // Timer
    private var startTime = 0L
    private var elapsedPausedTime = 0L
    private var lastPauseTimestamp = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE_RESUME = "ACTION_PAUSE_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_AUDIO = "EXTRA_AUDIO"
        const val EXTRA_WIDTH = "EXTRA_WIDTH"
        const val EXTRA_HEIGHT = "EXTRA_HEIGHT"
        const val EXTRA_DENSITY = "EXTRA_DENSITY"
        private const val CHANNEL_ID = "record_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                val enableAudio = intent.getBooleanExtra(EXTRA_AUDIO, true)
                val width = intent.getIntExtra(EXTRA_WIDTH, 1080)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 1920)
                val density = intent.getIntExtra(EXTRA_DENSITY, 400)

                if (resultCode != 0 && data != null && !isRecording) {
                    startForegroundNotification()
                    startRecording(resultCode, data, width, height, density, enableAudio)
                    showFloatingOverlay()
                }
            }
            ACTION_PAUSE_RESUME -> {
                togglePauseResume()
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recorder Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val pauseIntent = Intent(this, RecordService::class.java).apply { action = ACTION_PAUSE_RESUME }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Screen (In-App Vault)")
            .setContentText("Use floating controls or notification actions.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_media_pause, if (isPaused) "Resume" else "Pause", pausePendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        density: Int,
        enableAudio: Boolean
    ) {
        // App internal private directory: recordings will NOT go to gallery directly
        val internalFolder = File(filesDir, "recordings")
        if (!internalFolder.exists()) {
            internalFolder.mkdirs()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFile = File(internalFolder, "Record_$timeStamp.mp4")

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            if (enableAudio) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(videoFile.absolutePath)
            setVideoSize(width, height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (enableAudio) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
            }
            setVideoEncodingBitRate(8 * 1000 * 1000)
            setVideoFrameRate(30)
            prepare()
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null,
            null
        )

        mediaRecorder?.start()
        isRecording = true
        isPaused = false
        startTime = SystemClock.uptimeMillis()
        elapsedPausedTime = 0L
    }

    private fun showFloatingOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_control, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        floatingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val tvTimer = floatingView?.findViewById<TextView>(R.id.tvFloatTimer)
        val btnPauseResume = floatingView?.findViewById<ImageView>(R.id.btnFloatPauseResume)
        val btnStop = floatingView?.findViewById<ImageView>(R.id.btnFloatStop)
        val dragHandle = floatingView?.findViewById<ImageView>(R.id.btnDragHandle)

        btnPauseResume?.setOnClickListener {
            togglePauseResume()
            btnPauseResume.setImageResource(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            )
        }

        btnStop?.setOnClickListener {
            val stopIntent = Intent(this, RecordService::class.java).apply { action = ACTION_STOP }
            startService(stopIntent)
        }

        // Drag listener
        dragHandle?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = floatingParams?.x ?: 0
                        initialY = floatingParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        floatingParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                        floatingParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                        if (floatingView != null && floatingParams != null) {
                            windowManager?.updateViewLayout(floatingView, floatingParams)
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Timer runner for floating bubble
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording && !isPaused) {
                    val totalMillis = SystemClock.uptimeMillis() - startTime - elapsedPausedTime
                    val sec = (totalMillis / 1000).toInt()
                    val m = sec / 60
                    val s = sec % 60
                    tvTimer?.text = String.format("%02d:%02d", m, s)
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)

        try {
            windowManager?.addView(floatingView, floatingParams)
        } catch (_: Exception) {}
    }

    private fun togglePauseResume() {
        if (!isRecording) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (isPaused) {
                    mediaRecorder?.resume()
                    isPaused = false
                    elapsedPausedTime += (SystemClock.uptimeMillis() - lastPauseTimestamp)
                } else {
                    mediaRecorder?.pause()
                    isPaused = true
                    lastPauseTimestamp = SystemClock.uptimeMillis()
                }
                startForegroundNotification()
            } catch (_: Exception) {}
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
        } catch (_: Exception) {
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.stop()
            mediaProjection = null
            isRecording = false
            isPaused = false

            // Remove floating overlay
            if (floatingView != null) {
                try {
                    windowManager?.removeView(floatingView)
                } catch (_: Exception) {}
                floatingView = null
            }
            timerRunnable?.let { handler.removeCallbacks(it) }

            // Notify UI
            sendBroadcast(Intent("ACTION_RECORDING_COMPLETED"))
        }
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
