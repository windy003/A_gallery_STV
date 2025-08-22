package com.example.photogallery

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.databinding.ActivitySyncSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        lifecycleScope.launch {
            try {
                val message = StringBuilder()
                
                // Only local collections - 显示收藏名称和其中的文件
                if (result.onlyLocal.isNotEmpty()) {
                    message.append("📱 只在本地的收藏 (${result.onlyLocal.size}个):\n")
                    for (collectionName in result.onlyLocal) {
                        val fileCount = getLocalCollectionFileCount(collectionName)
                        message.append("  📂 $collectionName (${fileCount}个文件)\n")
                        
                        // 显示该收藏中的文件
                        val files = getLocalCollectionFiles(collectionName)
                        files.take(10).forEach { fileName ->
                            message.append("      📄 $fileName\n")
                        }
                        if (files.size > 10) {
                            message.append("      ... 还有${files.size - 10}个文件\n")
                        }
                    }
                    message.append("\n")
                }
                
                // Only remote collections - 显示收藏名称和其中的文件
                if (result.onlyRemote.isNotEmpty()) {
                    message.append("☁️ 只在VPS上的收藏 (${result.onlyRemote.size}个):\n")
                    for (collectionName in result.onlyRemote) {
                        val files = getRemoteCollectionFiles(collectionName, result)
                        message.append("  📂 $collectionName (${files.size}个文件)\n")
                        
                        // 显示该收藏中的文件
                        files.take(10).forEach { fileName ->
                            message.append("      📥 $fileName\n")
                        }
                        if (files.size > 10) {
                            message.append("      ... 还有${files.size - 10}个文件\n")
                        }
                    }
                    message.append("\n")
                }
                
                // Different collections - 在协程中检查文件存在性
                if (result.different.isNotEmpty()) {
                    message.append("⚠️ Different Content (${result.different.size}):\n")
                    for (diff in result.different) {
                        message.append("  📂 ${diff.name}:\n")
                        if (diff.onlyInLocal.isNotEmpty()) {
                            val existsCount = diff.onlyInLocal.count { checkLocalFileExistsAsync(it) }
                            val missingCount = diff.onlyInLocal.size - existsCount
                            message.append("    📱 只在本地有的文件 (${diff.onlyInLocal.size}个): ")
                            if (missingCount > 0) {
                                message.append("✅${existsCount}个存在 ❌${missingCount}个已删除\n")
                            } else {
                                message.append("✅全部${existsCount}个都存在\n")
                            }
                            
                            // 显示所有文件名，不限制数量
                            diff.onlyInLocal.forEach { fileName ->
                                val exists = checkLocalFileExistsAsync(fileName)
                                val status = if (exists) "✅" else "❌"
                                message.append("        $status $fileName\n")
                            }
                        }
                        if (diff.onlyInRemote.isNotEmpty()) {
                            message.append("    ☁️ 只在VPS上有的文件 (${diff.onlyInRemote.size}个):\n")
                            // 显示所有VPS独有的文件名
                            diff.onlyInRemote.forEach { fileName ->
                                message.append("        📥 $fileName\n")
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

                withContext(Dispatchers.Main) {
                    androidx.appcompat.app.AlertDialog.Builder(this@SyncSettingsActivity)
                        .setTitle("收藏同步比较结果")
                        .setMessage(message.toString())
                        .setPositiveButton("确定", null)
                        .setNeutralButton("查看具体文件") { _, _ ->
                            showDetailedDifferencesDialog(result)
                        }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    androidx.appcompat.app.AlertDialog.Builder(this@SyncSettingsActivity)
                        .setTitle("比较结果")
                        .setMessage("生成比较结果时出错: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
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
        
        // 在协程中检查文件存在性，然后显示对话框
        lifecycleScope.launch {
            try {
                // 创建文件列表项，并检查本地文件是否存在
                val fileList = items.map { fileName ->
                    val localExists = checkLocalFileExistsAsync(fileName)
                    val displayName = if (localExists) {
                        "✅ $fileName"
                    } else {
                        "❌ $fileName"
                    }
                    
                    FileListItem(
                        fileName = displayName,
                        filePath = "", // 同步比对时我们只有文件名
                        isVideo = fileName.lowercase().let { 
                            it.endsWith(".mp4") || it.endsWith(".avi") || it.endsWith(".mov") || 
                            it.endsWith(".mkv") || it.endsWith(".wmv") || it.endsWith(".flv") 
                        }
                    )
                }
                
                // 在主线程显示对话框
                withContext(Dispatchers.Main) {
                    showFileListDialogInternal(title, fileList)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    androidx.appcompat.app.AlertDialog.Builder(this@SyncSettingsActivity)
                        .setTitle("错误")
                        .setMessage("加载文件列表时出错: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    private fun showFileListDialogInternal(title: String, fileList: List<FileListItem>) {
        // 使用现有的文件列表对话框布局
        val dialogView = layoutInflater.inflate(R.layout.dialog_file_list, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewFileList)
        val titleTextView = dialogView.findViewById<android.widget.TextView>(R.id.tvTitle)
        
        titleTextView.text = title
        
        // 设置RecyclerView，添加点击事件尝试查找并打开文件
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = FileListAdapter(fileList, object : FileListItemClickListener {
            override fun onItemClick(fileItem: FileListItem) {
                // 移除emoji标记，获取原始文件名
                val originalFileName = fileItem.fileName.replace("✅ ", "").replace("❌ ", "")
                findAndOpenLocalFile(originalFileName)
            }
            
            override fun onItemLongClick(fileItem: FileListItem) {
                // 显示文件信息
                showSyncFileInfo(fileItem.fileName)
            }
        })
        
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

    private fun findAndOpenLocalFile(fileName: String) {
        lifecycleScope.launch {
            try {
                // 在所有收藏中查找同名文件
                val database = AppDatabase.getDatabase(this@SyncSettingsActivity)
                val allCollections = database.collectionDao().getAllCollectionsSync()
                var foundFilePath: String? = null
                
                for (collection in allCollections) {
                    val items = database.collectionDao().getItemsForCollectionSync(collection.id)
                    val matchingItem = items.find { item ->
                        File(item.mediaPath).name == fileName
                    }
                    if (matchingItem != null) {
                        foundFilePath = matchingItem.mediaPath
                        break
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (foundFilePath != null) {
                        openFoundFile(foundFilePath, fileName)
                    } else {
                        Toast.makeText(this@SyncSettingsActivity, 
                            "在本地收藏中未找到文件：$fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SyncSettingsActivity, 
                        "查找文件时出错：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun openFoundFile(filePath: String, fileName: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在：$fileName", Toast.LENGTH_SHORT).show()
            return
        }
        
        val isVideo = fileName.lowercase().let { 
            it.endsWith(".mp4") || it.endsWith(".avi") || it.endsWith(".mov") || 
            it.endsWith(".mkv") || it.endsWith(".wmv") || it.endsWith(".flv") 
        }
        
        if (isVideo) {
            // 打开视频播放器
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("video_path", filePath)
            startActivity(intent)
        } else {
            // 打开图片详情页面
            val intent = Intent(this, ImageDetailActivity::class.java)
            intent.putExtra("image_paths", arrayOf(filePath))
            intent.putExtra("current_index", 0)
            startActivity(intent)
        }
    }
    
    private suspend fun checkLocalFileExistsAsync(fileName: String): Boolean {
        return try {
            val database = AppDatabase.getDatabase(this@SyncSettingsActivity)
            val allCollections = database.collectionDao().getAllCollectionsSync()
            
            for (collection in allCollections) {
                val items = database.collectionDao().getItemsForCollectionSync(collection.id)
                val matchingItem = items.find { item ->
                    File(item.mediaPath).name == fileName
                }
                if (matchingItem != null) {
                    // 检查文件是否真的存在
                    return File(matchingItem.mediaPath).exists()
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkLocalFileExists(fileName: String): Boolean {
        return try {
            // 使用runBlocking在主线程安全地调用suspend函数
            kotlinx.coroutines.runBlocking {
                checkLocalFileExistsAsync(fileName)
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getLocalCollectionFileCount(collectionName: String): Int {
        return try {
            val database = AppDatabase.getDatabase(this@SyncSettingsActivity)
            val collections = database.collectionDao().getAllCollectionsSync()
            val collection = collections.find { it.name == collectionName }
            if (collection != null) {
                val items = database.collectionDao().getItemsForCollectionSync(collection.id)
                items.size
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private suspend fun getLocalCollectionFiles(collectionName: String): List<String> {
        return try {
            val database = AppDatabase.getDatabase(this@SyncSettingsActivity)
            val collections = database.collectionDao().getAllCollectionsSync()
            val collection = collections.find { it.name == collectionName }
            if (collection != null) {
                val items = database.collectionDao().getItemsForCollectionSync(collection.id)
                items.map { File(it.mediaPath).name }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getRemoteCollectionFiles(collectionName: String, result: ComparisonResult): List<String> {
        return result.remoteCollectionFiles[collectionName] ?: emptyList()
    }

    private fun showSyncFileInfo(fileName: String) {
        // 移除显示名称中的emoji标记，获取原始文件名
        val originalFileName = fileName.replace("✅ ", "").replace("❌ ", "")
        
        val isVideo = originalFileName.lowercase().let { 
            it.endsWith(".mp4") || it.endsWith(".avi") || it.endsWith(".mov") || 
            it.endsWith(".mkv") || it.endsWith(".wmv") || it.endsWith(".flv") 
        }
        
        val localExists = checkLocalFileExists(originalFileName)
        val fileType = if (isVideo) "视频文件" else "图片文件"
        val existsStatus = if (localExists) "✅ 存在于本地" else "❌ 本地不存在"
        
        val message = """
            文件名: $originalFileName
            文件类型: $fileType
            本地状态: $existsStatus
            
            提示：
            • 点击文件名可尝试在本地收藏中查找并打开
            • ✅ 表示文件在本地存在且可访问
            • ❌ 表示文件在本地不存在或已被删除
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("文件信息")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
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