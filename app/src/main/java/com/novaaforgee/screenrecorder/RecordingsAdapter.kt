package com.novaaforgee.screenrecorder

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.novaaforgee.screenrecorder.databinding.ItemRecordingBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsAdapter(
    private val context: Context,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {

    private val items = mutableListOf<File>()
    val selectedFiles = mutableSetOf<File>()

    fun setData(newItems: List<File>) {
        items.clear()
        items.addAll(newItems)
        selectedFiles.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun selectAll(select: Boolean) {
        selectedFiles.clear()
        if (select) {
            selectedFiles.addAll(items)
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        holder.bind(file)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemRecordingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            binding.tvFileName.text = file.name

            val sizeMb = file.length().toDouble() / (1024 * 1024)
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            binding.tvFileMeta.text = String.format(Locale.getDefault(), "%.1f MB • %s", sizeMb, dateStr)

            binding.cbSelect.setOnCheckedChangeListener(null)
            binding.cbSelect.isChecked = selectedFiles.contains(file)

            binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedFiles.add(file)
                } else {
                    selectedFiles.remove(file)
                }
                onSelectionChanged()
            }

            binding.root.setOnClickListener {
                binding.cbSelect.isChecked = !binding.cbSelect.isChecked
            }

            binding.btnSingleShare.setOnClickListener {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
            }
        }
    }
}
