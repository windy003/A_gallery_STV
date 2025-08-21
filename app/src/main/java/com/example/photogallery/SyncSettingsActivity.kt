package com.example.photogallery

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.photogallery.databinding.ActivitySyncSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncSettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var syncService: SyncService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sync Settings"

        sharedPreferences = getSharedPreferences("sync_settings", MODE_PRIVATE)
        syncService = SyncService(this)

        loadSettings()
        setupClickListeners()
        loadLocalChangeInfo()
    }

    private fun loadSettings() {
        binding.etVpsAddress.setText(sharedPreferences.getString("vps_address", ""))
        binding.etPort.setText(sharedPreferences.getString("port", "22"))
        binding.etUsername.setText(sharedPreferences.getString("username", ""))
        binding.etPassword.setText(sharedPreferences.getString("password", ""))
        binding.etRemotePath.setText(sharedPreferences.getString("remote_path", "/home/username/gallery_sync"))
    }

    private fun setupClickListeners() {
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnUploadCollections.setOnClickListener {
            uploadCollections()
        }

        binding.btnDownloadCollections.setOnClickListener {
            downloadCollections()
        }

        binding.btnRefreshVpsInfo.setOnClickListener {
            refreshVpsInfo()
        }

        binding.btnCompareCollections.setOnClickListener {
            compareCollections()
        }
    }

    private fun saveSettings() {
        val editor = sharedPreferences.edit()
        editor.putString("vps_address", binding.etVpsAddress.text.toString())
        editor.putString("port", binding.etPort.text.toString())
        editor.putString("username", binding.etUsername.text.toString())
        editor.putString("password", binding.etPassword.text.toString())
        editor.putString("remote_path", binding.etRemotePath.text.toString())
        editor.apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        if (!validateSettings()) return

        setLoadingState(true, "Testing connection...")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = getSyncConfig()
                val success = syncService.testConnection(config)
                
                withContext(Dispatchers.Main) {
                    setLoadingState(false, if (success) "Connection successful!" else "Connection failed!")
                    Toast.makeText(this@SyncSettingsActivity, 
                        if (success) "Connection successful!" else "Connection failed!", 
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false, "Connection error: ${e.message}")
                    Toast.makeText(this@SyncSettingsActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun uploadCollections() {
        if (!validateSettings()) return

        setLoadingState(true, "Uploading collections...")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = getSyncConfig()
                val success = syncService.uploadCollections(config)
                
                withContext(Dispatchers.Main) {
                    setLoadingState(false, if (success) "Upload successful!" else "Upload failed!")
                    Toast.makeText(this@SyncSettingsActivity, 
                        if (success) "Collections uploaded successfully!" else "Upload failed!", 
                        Toast.LENGTH_SHORT).show()
                    if (success) {
                        loadLocalChangeInfo() // Refresh local info after upload
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false, "Upload error: ${e.message}")
                    Toast.makeText(this@SyncSettingsActivity, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadCollections() {
        if (!validateSettings()) return

        setLoadingState(true, "Downloading collections...")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = getSyncConfig()
                val success = syncService.downloadCollections(config)
                
                withContext(Dispatchers.Main) {
                    setLoadingState(false, if (success) "Download successful!" else "Download failed!")
                    Toast.makeText(this@SyncSettingsActivity, 
                        if (success) "Collections downloaded successfully!" else "Download failed!", 
                        Toast.LENGTH_SHORT).show()
                    if (success) {
                        loadLocalChangeInfo() // Refresh local info after download
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false, "Download error: ${e.message}")
                    Toast.makeText(this@SyncSettingsActivity, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun validateSettings(): Boolean {
        if (binding.etVpsAddress.text.toString().isEmpty()) {
            Toast.makeText(this, "Please enter VPS address", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etUsername.text.toString().isEmpty()) {
            Toast.makeText(this, "Please enter username", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etPassword.text.toString().isEmpty()) {
            Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun getSyncConfig(): SyncConfig {
        return SyncConfig(
            vpsAddress = binding.etVpsAddress.text.toString(),
            port = binding.etPort.text.toString().toIntOrNull() ?: 22,
            username = binding.etUsername.text.toString(),
            password = binding.etPassword.text.toString(),
            remotePath = binding.etRemotePath.text.toString()
        )
    }

    private fun setLoadingState(loading: Boolean, message: String) {
        binding.btnTestConnection.isEnabled = !loading
        binding.btnUploadCollections.isEnabled = !loading
        binding.btnDownloadCollections.isEnabled = !loading
        binding.btnCompareCollections.isEnabled = !loading
        binding.tvSyncStatus.text = message
        
        if (loading) {
            binding.tvSyncStatus.visibility = View.VISIBLE
        }
    }

    private fun loadLocalChangeInfo() {
        lifecycleScope.launch {
            try {
                val latestChange = syncService.getLocalLatestChange()
                withContext(Dispatchers.Main) {
                    if (latestChange != null) {
                        binding.tvLocalLastChange.text = latestChange.description
                        binding.tvLocalLastTime.text = formatTimestamp(latestChange.timestamp)
                    } else {
                        binding.tvLocalLastChange.text = "No changes recorded yet"
                        binding.tvLocalLastTime.text = ""
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvLocalLastChange.text = "Error loading local info"
                    binding.tvLocalLastTime.text = e.message ?: "Unknown error"
                }
            }
        }
    }

    private fun refreshVpsInfo() {
        if (!validateSettings()) return

        binding.tvVpsLastChange.text = "Checking VPS information..."
        binding.tvVpsLastTime.text = ""
        binding.btnRefreshVpsInfo.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = getSyncConfig()
                val vpsInfo = syncService.getVpsLatestChange(config)
                
                withContext(Dispatchers.Main) {
                    binding.btnRefreshVpsInfo.isEnabled = true
                    if (vpsInfo != null) {
                        binding.tvVpsLastChange.text = vpsInfo.description
                        if (vpsInfo.lastModified > 0) {
                            binding.tvVpsLastTime.text = "Last modified: ${formatTimestamp(vpsInfo.lastModified)}"
                        } else {
                            binding.tvVpsLastTime.text = "No collections found on VPS"
                        }
                    } else {
                        binding.tvVpsLastChange.text = "Failed to connect to VPS"
                        binding.tvVpsLastTime.text = "Check your connection settings"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnRefreshVpsInfo.isEnabled = true
                    binding.tvVpsLastChange.text = "Error connecting to VPS"
                    binding.tvVpsLastTime.text = e.message ?: "Unknown error"
                }
            }
        }
    }

    private fun compareCollections() {
        if (!validateSettings()) return

        setLoadingState(true, "Comparing collections...")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = getSyncConfig()
                val comparisonResult = syncService.compareCollections(config)
                
                withContext(Dispatchers.Main) {
                    setLoadingState(false, "Comparison completed")
                    if (comparisonResult != null) {
                        showComparisonDialog(comparisonResult)
                    } else {
                        Toast.makeText(this@SyncSettingsActivity, "Failed to compare collections", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false, "Comparison error: ${e.message}")
                    Toast.makeText(this@SyncSettingsActivity, "Comparison error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showComparisonDialog(result: ComparisonResult) {
        val message = StringBuilder()
        
        // Only local collections
        if (result.onlyLocal.isNotEmpty()) {
            message.append("📱 Only on Local (${result.onlyLocal.size}):\n")
            result.onlyLocal.forEach { name ->
                message.append("  • $name\n")
            }
            message.append("\n")
        }
        
        // Only remote collections
        if (result.onlyRemote.isNotEmpty()) {
            message.append("☁️ Only on VPS (${result.onlyRemote.size}):\n")
            result.onlyRemote.forEach { name ->
                message.append("  • $name\n")
            }
            message.append("\n")
        }
        
        // Different collections
        if (result.different.isNotEmpty()) {
            message.append("⚠️ Different Content (${result.different.size}):\n")
            result.different.forEach { diff ->
                message.append("  📂 ${diff.name}:\n")
                if (diff.onlyInLocal.isNotEmpty()) {
                    message.append("    📱 Only on Local (${diff.onlyInLocal.size}):\n")
                    diff.onlyInLocal.take(10).forEach { fileName ->
                        message.append("      • $fileName\n")
                    }
                    if (diff.onlyInLocal.size > 10) {
                        message.append("      ... and ${diff.onlyInLocal.size - 10} more\n")
                    }
                }
                if (diff.onlyInRemote.isNotEmpty()) {
                    message.append("    ☁️ Only on VPS (${diff.onlyInRemote.size}):\n")
                    diff.onlyInRemote.take(10).forEach { fileName ->
                        message.append("      • $fileName\n")
                    }
                    if (diff.onlyInRemote.size > 10) {
                        message.append("      ... and ${diff.onlyInRemote.size - 10} more\n")
                    }
                }
                message.append("\n")
            }
        }
        
        // Identical collections
        if (result.identical.isNotEmpty()) {
            message.append("✅ Identical (${result.identical.size}):\n")
            result.identical.forEach { name ->
                message.append("  • $name\n")
            }
        }

        if (message.isEmpty()) {
            message.append("No collections found to compare.")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Collection Comparison Results")
            .setMessage(message.toString())
            .setPositiveButton("OK", null)
            .setNeutralButton("详细差异") { _, _ ->
                showDetailedDifferencesDialog(result)
            }
            .show()
    }

    private fun showDetailedDifferencesDialog(result: ComparisonResult) {
        // 创建分类数据
        val categories = mutableListOf<String>()
        val categoryData = mutableMapOf<String, List<String>>()
        
        // 只在本地的收藏
        if (result.onlyLocal.isNotEmpty()) {
            categories.add("📱 只在本地的收藏 (${result.onlyLocal.size})")
            categoryData["📱 只在本地的收藏 (${result.onlyLocal.size})"] = result.onlyLocal
        }
        
        // 只在VPS的收藏
        if (result.onlyRemote.isNotEmpty()) {
            categories.add("☁️ 只在VPS的收藏 (${result.onlyRemote.size})")
            categoryData["☁️ 只在VPS的收藏 (${result.onlyRemote.size})"] = result.onlyRemote
        }
        
        // 有差异的收藏中的文件
        result.different.forEach { diff ->
            if (diff.onlyInLocal.isNotEmpty()) {
                val key = "📱 ${diff.name} - 只在本地 (${diff.onlyInLocal.size})"
                categories.add(key)
                categoryData[key] = diff.onlyInLocal
            }
            if (diff.onlyInRemote.isNotEmpty()) {
                val key = "☁️ ${diff.name} - 只在VPS (${diff.onlyInRemote.size})"
                categories.add(key)
                categoryData[key] = diff.onlyInRemote
            }
        }
        
        // 相同的收藏
        if (result.identical.isNotEmpty()) {
            categories.add("✅ 完全相同的收藏 (${result.identical.size})")
            categoryData["✅ 完全相同的收藏 (${result.identical.size})"] = result.identical
        }
        
        if (categories.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("详细差异")
                .setMessage("没有找到任何差异。")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        
        // 显示分类选择对话框
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择要查看的类别")
            .setItems(categories.toTypedArray()) { _, which ->
                val selectedCategory = categories[which]
                val items = categoryData[selectedCategory] ?: emptyList()
                showFileListDialog(selectedCategory, items)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showFileListDialog(title: String, items: List<String>) {
        if (items.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("没有项目可显示。")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        
        // 使用现有的文件列表对话框布局
        val dialogView = layoutInflater.inflate(R.layout.dialog_file_list, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewFileList)
        val titleTextView = dialogView.findViewById<android.widget.TextView>(R.id.tvTitle)
        
        titleTextView.text = title
        
        // 创建文件列表项（假设都是图片，因为我们只有文件名）
        val fileList = items.map { fileName ->
            FileListItem(
                fileName = fileName,
                filePath = "", // 同步比对时我们只有文件名
                isVideo = fileName.lowercase().let { 
                    it.endsWith(".mp4") || it.endsWith(".avi") || it.endsWith(".mov") || 
                    it.endsWith(".mkv") || it.endsWith(".wmv") || it.endsWith(".flv") 
                }
            )
        }
        
        // 设置RecyclerView（不添加点击事件，仅显示）
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = FileListAdapter(fileList)
        
        // 显示对话框
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

data class SyncConfig(
    val vpsAddress: String,
    val port: Int,
    val username: String,
    val password: String,
    val remotePath: String
)