package com.novaaforgee.screenrecorder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.novaaforgee.screenrecorder.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordingsAdapter
    private var isRecording = false

    private val recordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isRecording = false
            updateUiState()
            loadRecordings()
            Toast.makeText(this@MainActivity, "Recording saved in App Vault", Toast.LENGTH_SHORT).show()
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
            updateUiState()
        } else {
            Toast.makeText(this, "Screen capture permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (audioGranted || !binding.switchAudio.isChecked) {
            checkOverlayAndStart()
        } else {
            Toast.makeText(this, "Audio permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestMediaProjection()
        } else {
            Toast.makeText(this, "Floating permission recommended for on-screen controls", Toast.LENGTH_SHORT).show()
            requestMediaProjection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        loadRecordings()

        val filter = IntentFilter("ACTION_RECORDING_COMPLETED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(recordReceiver, filter)
        }
    }

    private fun setupRecyclerView() {
        adapter = RecordingsAdapter(this) {
            updateSelectionBar()
        }
        binding.rvRecordings.layoutManager = LinearLayoutManager(this)
        binding.rvRecordings.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnToggleRecord.setOnClickListener {
            if (isRecording) {
                val serviceIntent = Intent(this, RecordService::class.java).apply {
                    action = RecordService.ACTION_STOP
                }
                startService(serviceIntent)
                isRecording = false
                updateUiState()
            } else {
                checkPermissionsAndProceed()
            }
        }

        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            adapter.selectAll(isChecked)
        }

        // Export Selected to Phone Public Gallery
        binding.btnExportGallery.setOnClickListener {
            exportSelectedToGallery()
        }

        // Share Selected Videos
        binding.btnShareSelected.setOnClickListener {
            shareSelectedVideos()
        }

        // Delete Selected
        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedVideos()
        }
    }

    private fun loadRecordings() {
        val internalFolder = File(filesDir, "recordings")
        val files = internalFolder.listFiles { f -> f.extension == "mp4" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        adapter.setData(files)
        binding.tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        updateSelectionBar()
    }

    private fun updateSelectionBar() {
        val count = adapter.selectedFiles.size
        binding.btnExportGallery.isEnabled = count > 0
        binding.btnShareSelected.isEnabled = count > 0
        binding.btnDeleteSelected.isEnabled = count > 0
    }

    private fun exportSelectedToGallery() {
        val selected = adapter.selectedFiles.toList()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select recordings first", Toast.LENGTH_SHORT).show()
            return
        }

        var exportedCount = 0
        for (file in selected) {
            if (saveVideoToMediaStore(file)) {
                exportedCount++
            }
        }
        Toast.makeText(this, "$exportedCount video(s) exported to phone Gallery!", Toast.LENGTH_LONG).show()
    }

    private fun saveVideoToMediaStore(file: File): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LiteScreenRecorder")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = contentResolver.insert(collection, values) ?: return false

            contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun shareSelectedVideos() {
        val selected = adapter.selectedFiles.toList()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select recordings first", Toast.LENGTH_SHORT).show()
            return
        }

        val uris = ArrayList<Uri>()
        for (f in selected) {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            uris.add(uri)
        }

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/mp4"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Selected Videos"))
    }

    private fun deleteSelectedVideos() {
        val selected = adapter.selectedFiles.toList()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select recordings first", Toast.LENGTH_SHORT).show()
            return
        }

        for (f in selected) {
            f.delete()
        }
        Toast.makeText(this, "Selected recordings deleted", Toast.LENGTH_SHORT).show()
        loadRecordings()
    }

    private fun checkPermissionsAndProceed() {
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
            checkOverlayAndStart()
        }
    }

    private fun checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun updateUiState() {
        if (isRecording) {
            binding.btnToggleRecord.text = "Recording..."
            binding.btnToggleRecord.setBackgroundColor(0xFF2E7D32.toInt())
            binding.switchAudio.isEnabled = false
        } else {
            binding.btnToggleRecord.text = "Start Record"
            binding.btnToggleRecord.setBackgroundColor(0xFFE53935.toInt())
            binding.switchAudio.isEnabled = true
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(recordReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
