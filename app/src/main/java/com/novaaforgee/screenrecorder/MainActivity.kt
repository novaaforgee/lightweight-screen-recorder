package com.novaaforgee.screenrecorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.novaaforgee.screenrecorder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    private var startTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            val millis = SystemClock.uptimeMillis() - startTime
            val seconds = (millis / 1000).toInt()
            val minutes = seconds / 60
            val secs = seconds % 60
            binding.tvTimer.text = String.format("%02d:%02d", minutes, secs)
            handler.postDelayed(this, 1000)
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val serviceIntent = Intent(this, RecordService::class.java).apply {
                action = RecordService.ACTION_START
                putExtra(RecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(RecordService.EXTRA_DATA, result.data)
                putExtra(RecordService.EXTRA_AUDIO, binding.switchAudio.isChecked)
                putExtra(RecordService.EXTRA_WIDTH, metrics.widthPixels)
                putExtra(RecordService.EXTRA_HEIGHT, metrics.heightPixels)
                putExtra(RecordService.EXTRA_DENSITY, metrics.densityDpi)
            }
            ContextCompat.startForegroundService(this, serviceIntent)

            isRecording = true
            updateUi(true)
        } else {
            Toast.makeText(this, "Permission denied for screen capture", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (audioGranted || !binding.switchAudio.isChecked) {
            requestMediaProjection()
        } else {
            Toast.makeText(this, "Audio permission needed if switch is enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggleRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        if (binding.switchAudio.isChecked &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopRecording() {
        val serviceIntent = Intent(this, RecordService::class.java).apply {
            action = RecordService.ACTION_STOP
        }
        startService(serviceIntent)
        isRecording = false
        updateUi(false)
        Toast.makeText(this, "Recording saved to Movies/LiteScreenRecorder", Toast.LENGTH_LONG).show()
    }

    private fun updateUi(recording: Boolean) {
        if (recording) {
            binding.tvStatus.text = getString(R.string.status_recording)
            binding.tvTimer.visibility = View.VISIBLE
            startTime = SystemClock.uptimeMillis()
            handler.post(timerRunnable)
            binding.btnToggleRecord.text = getString(R.string.stop_recording)
            binding.btnToggleRecord.setBackgroundColor(0xFF2E7D32.toInt())
            binding.switchAudio.isEnabled = false
        } else {
            binding.tvStatus.text = getString(R.string.status_idle)
            binding.tvTimer.visibility = View.GONE
            handler.removeCallbacks(timerRunnable)
            binding.btnToggleRecord.text = getString(R.string.start_recording)
            binding.btnToggleRecord.setBackgroundColor(0xFFE53935.toInt())
            binding.switchAudio.isEnabled = true
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        super.onDestroy()
    }
}
