package com.example.photogallery

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.photogallery.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class to hold media items
data class MediaItem(val path: String, val isVideo: Boolean, val dateAdded: Long, val duration: Long = 0)

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: SimpleZoomableRecyclerView
    private lateinit var photoAdapter: PhotoAdapter
    private var actionMode: ActionMode? = null

    private lateinit var viewModel: ImageDetailViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.entries.any { it.value }
        if (isGranted) {
            loadMedia()
        }
    }

    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "已获得存储管理权限", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要存储管理权限才能删除文件", Toast.LENGTH_LONG).show()
            }
        }
    }

    private var pendingDeletePaths: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val factory = ImageDetailViewModelFactory(AppDatabase.getDatabase(application).collectionDao())
        viewModel = ViewModelProvider(this, factory).get(ImageDetailViewModel::class.java)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        setupRecyclerView()
        checkPermissionAndLoadMedia()
        
        // 清理无效的文件记录
        cleanupInvalidFiles()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_collections -> {
                startActivity(Intent(this, CollectionsActivity::class.java))
                true
            }
            R.id.action_sync -> {
                startActivity(Intent(this, SyncSettingsActivity::class.java))
                true
            }
            R.id.action_show_file_list -> {
                showFileListDialog()
                true
            }
            R.id.action_cleanup_invalid -> {
                showCleanupConfirmDialog()
                true
            }
            R.id.action_reset_database -> {
                showResetDatabaseConfirmDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        photoAdapter = PhotoAdapter(
            onItemClick = { mediaItem ->
                if (actionMode != null) {
                    toggleSelection(mediaItem)
                } else {
                    if (mediaItem.isVideo) {
                        val intent = Intent(this, VideoPlayerActivity::class.java)
                        intent.putExtra("video_path", mediaItem.path)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, ImageDetailActivity::class.java)
                        
                        // 获取所有图片路径（非视频）
                        val allImagePaths = photoAdapter.getMediaItems()
                            .filter { !it.isVideo }
                            .map { it.path }
                            .toTypedArray()
                        
                        val currentIndex = allImagePaths.indexOf(mediaItem.path)
                        
                        intent.putExtra("image_paths", allImagePaths)
                        intent.putExtra("current_index", currentIndex)
                        startActivity(intent)
                    }
                }
            },
            onItemLongClick = { mediaItem ->
                if (actionMode == null) {
                    actionMode = startActionMode(actionModeCallback)
                    photoAdapter.selectionMode = true
                }
                toggleSelection(mediaItem)
            }
        )
        recyclerView.setPhotoAdapter(photoAdapter)
    }

    private fun toggleSelection(mediaItem: MediaItem) {
        if (photoAdapter.getSelectedItems().contains(mediaItem)) {
            photoAdapter.deselectItem(mediaItem)
        } else {
            photoAdapter.selectItem(mediaItem)
        }
        val selectedCount = photoAdapter.getSelectedItems().size
        if (selectedCount == 0) {
            actionMode?.finish()
        } else {
            actionMode?.title = "$selectedCount selected"
            // 当选择数量变化时，更新菜单
            actionMode?.invalidate()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // 第一次创建ActionMode时，默认使用单选菜单，因为通常是长按单个项目触发的
            mode?.menuInflater?.inflate(R.menu.main_single_item_menu, menu)
            android.util.Log.d("MainActivity", "onCreateActionMode: single item menu loaded, menu size: ${menu?.size()}")
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // 当选中项目数量变化时，重新创建菜单
            val selectedCount = photoAdapter.getSelectedItems().size
            val currentMenuResource = if (selectedCount == 1) {
                R.menu.main_single_item_menu
            } else {
                R.menu.contextual_action_bar_menu
            }
            
            android.util.Log.d("MainActivity", "onPrepareActionMode: selectedCount=$selectedCount, using menu=${if (selectedCount == 1) "single" else "multi"}")
            
            // 清除当前菜单并重新加载
            menu?.clear()
            mode?.menuInflater?.inflate(currentMenuResource, menu)
            
            // 更新标题
            mode?.title = "$selectedCount selected"
            
            android.util.Log.d("MainActivity", "onPrepareActionMode: menu size after inflate: ${menu?.size()}")
            for (i in 0 until (menu?.size() ?: 0)) {
                val item = menu?.getItem(i)
                android.util.Log.d("MainActivity", "Menu item $i: id=${item?.itemId}, title=${item?.title}")
            }
            
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_add_to_collection_cab -> {
                    showCollectionsDialogForSelectedItems()
                    mode?.finish()
                    true
                }
                R.id.action_edit_filename -> {
                    val selectedItems = photoAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        val mediaItem = selectedItems.first()
                        if (!mediaItem.isVideo) { // 只对图片文件启用编辑
                            showEditFilenameDialog(mediaItem.path)
                        } else {
                            Toast.makeText(this@MainActivity, "视频文件名编辑暂不支持", Toast.LENGTH_SHORT).show()
                        }
                    }
                    mode?.finish()
                    true
                }
                R.id.action_view_info -> {
                    val selectedItems = photoAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        val mediaItem = selectedItems.first()
                        showFileInfoDialog(mediaItem)
                    }
                    mode?.finish()
                    true
                }
                R.id.action_delete_item -> {
                    val selectedItems = photoAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        val mediaItem = selectedItems.first()
                        showDeleteConfirmDialog(listOf(mediaItem.path))
                    }
                    mode?.finish()
                    true
                }
                R.id.action_delete_items -> {
                    val selectedItems = photoAdapter.getSelectedItems()
                    if (selectedItems.isNotEmpty()) {
                        val filePaths = selectedItems.map { it.path }
                        showDeleteConfirmDialog(filePaths)
                    }
                    mode?.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            photoAdapter.selectionMode = false
            actionMode = null
        }
    }

    private fun showCollectionsDialogForSelectedItems() {
        val selectedPaths = photoAdapter.getSelectedItems().map { it.path }.toList()
        if (selectedPaths.isEmpty()) return

        lifecycleScope.launch {
            val allCollections = viewModel.getAllCollections().first()

            val collectionNames = allCollections.map { it.name }.toTypedArray()
            val checkedItems = BooleanArray(allCollections.size) { false }
            val selectedCollectionIds = mutableListOf<Long>()

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Add selected to Collection")
                .setMultiChoiceItems(collectionNames, checkedItems) { _, which, isChecked ->
                    val collectionId = allCollections[which].id
                    if (isChecked) {
                        selectedCollectionIds.add(collectionId)
                    } else {
                        selectedCollectionIds.remove(collectionId)
                    }
                }
                .setPositiveButton("OK") { _, _ ->
                    viewModel.updateImageInCollections(selectedPaths, selectedCollectionIds)
                    
                    // Log the change
                    if (selectedCollectionIds.isNotEmpty()) {
                        lifecycleScope.launch {
                            val collections = viewModel.getAllCollections().first()
                            val selectedCollectionNames = collections.filter { selectedCollectionIds.contains(it.id) }.map { it.name }
                            ChangeLogHelper.getInstance(this@MainActivity).logMultipleItemsAddedToCollections(selectedPaths, selectedCollectionNames)
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun checkPermissionAndLoadMedia() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            loadMedia()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun loadMedia() {
        val mediaItems = mutableListOf<MediaItem>()

        // Load Images
        val imageProjection = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED)
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                val date = cursor.getLong(dateColumn)
                mediaItems.add(MediaItem(path, false, date))
            }
        }

        // Load Videos
        val videoProjection = arrayOf(MediaStore.Video.Media.DATA, MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DURATION)
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                val date = cursor.getLong(dateColumn)
                val duration = cursor.getLong(durationColumn)
                mediaItems.add(MediaItem(path, true, date, duration))
            }
        }

        mediaItems.sortByDescending { it.dateAdded }

        if (mediaItems.isEmpty()) {
            findViewById<View>(R.id.tvNoImages).visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            findViewById<View>(R.id.tvNoImages).visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            photoAdapter.submitList(mediaItems)
        }
    }

    private fun showEditFilenameDialog(currentPath: String) {
        val file = File(currentPath)
        val currentFileName = file.nameWithoutExtension
        val extension = file.extension

        val editText = EditText(this).apply {
            setText(currentFileName)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("编辑文件名")
            .setMessage("当前文件名: $currentFileName.$extension")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newFileName = editText.text.toString().trim()
                if (newFileName.isNotEmpty() && newFileName != currentFileName) {
                    renameFile(currentPath, newFileName, extension)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFileInfoDialog(mediaItem: MediaItem) {
        val file = File(mediaItem.path)
        
        val fileName = file.name
        val fileSize = formatFileSize(file.length())
        val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(file.lastModified()))
        val fileDimensions = if (mediaItem.isVideo) {
            "视频时长: ${formatDuration(mediaItem.duration)}"
        } else {
            getImageDimensions(mediaItem.path)
        }
        
        val message = buildString {
            append("文件名: $fileName\n")
            append("文件类型: ${if (mediaItem.isVideo) "视频" else "图片"}\n")
            append("文件大小: $fileSize\n") 
            append("修改时间: $lastModified\n")
            append("文件路径: ${mediaItem.path}\n")
            if (fileDimensions.isNotEmpty()) {
                if (mediaItem.isVideo) {
                    append(fileDimensions)
                } else {
                    append("图片尺寸: $fileDimensions")
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("文件信息")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun renameFile(oldPath: String, newFileName: String, extension: String) {
        try {
            val oldFile = File(oldPath)
            val newFile = File(oldFile.parent, "$newFileName.$extension")
            
            if (newFile.exists()) {
                Toast.makeText(this, "文件名已存在，请选择其他名称", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (oldFile.renameTo(newFile)) {
                Toast.makeText(this, "文件重命名成功", Toast.LENGTH_SHORT).show()
                
                // 重新加载媒体文件列表以反映更改
                loadMedia()
                
                // 记录变动日志
                ChangeLogHelper.getInstance(this).logItemRemovedFromCollection(
                    "Main Gallery", oldPath
                )
                ChangeLogHelper.getInstance(this).logItemAddedToCollection(
                    "Main Gallery", newFile.absolutePath
                )
            } else {
                Toast.makeText(this, "重命名失败，请检查权限", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "重命名失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatFileSize(size: Long): String {
        val kb = 1024
        val mb = kb * 1024
        val gb = mb * 1024
        
        return when {
            size >= gb -> String.format("%.2f GB", size.toDouble() / gb)
            size >= mb -> String.format("%.2f MB", size.toDouble() / mb)
            size >= kb -> String.format("%.2f KB", size.toDouble() / kb)
            else -> "$size B"
        }
    }

    private fun getImageDimensions(filePath: String): String {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(filePath, options)
            "${options.outWidth} × ${options.outHeight}"
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatDuration(duration: Long): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
            else -> String.format("%02d:%02d", minutes, seconds % 60)
        }
    }

    private fun showDeleteConfirmDialog(filePaths: List<String>) {
        val message = if (filePaths.size == 1) {
            val fileName = File(filePaths.first()).name
            "确定要删除文件 \"$fileName\" 吗？\n\n此操作不可撤销！"
        } else {
            "确定要删除选中的 ${filePaths.size} 个文件吗？\n\n此操作不可撤销！"
        }

        AlertDialog.Builder(this)
            .setTitle("删除文件")
            .setMessage(message)
            .setPositiveButton("删除") { _, _ ->
                pendingDeletePaths = filePaths
                checkDeletePermissionAndDelete()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun checkDeletePermissionAndDelete() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+: 需要 MANAGE_EXTERNAL_STORAGE 权限
                if (Environment.isExternalStorageManager()) {
                    deleteFiles(pendingDeletePaths)
                } else {
                    requestManageExternalStoragePermission()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10: 使用 MediaStore API
                deleteFilesWithMediaStore(pendingDeletePaths)
            }
            else -> {
                // Android 9 及以下: 检查 WRITE_EXTERNAL_STORAGE 权限
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED) {
                    deleteFiles(pendingDeletePaths)
                } else {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            }
        }
    }

    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                requestManageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                requestManageStorageLauncher.launch(intent)
            }
        }
    }

    private fun deleteFiles(filePaths: List<String>) {
        var deletedCount = 0
        var failedCount = 0

        filePaths.forEach { filePath ->
            try {
                val file = File(filePath)
                if (file.exists() && file.delete()) {
                    deletedCount++
                    
                    // 通知媒体扫描器文件已删除
                    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    intent.data = Uri.fromFile(file.parentFile)
                    sendBroadcast(intent)
                    
                    // 记录变动日志
                    ChangeLogHelper.getInstance(this).logItemRemovedFromCollection(
                        "Main Gallery", filePath
                    )
                } else {
                    failedCount++
                }
            } catch (e: Exception) {
                failedCount++
                android.util.Log.e("MainActivity", "Failed to delete file: $filePath", e)
            }
        }

        val message = when {
            failedCount == 0 -> "已删除 $deletedCount 个文件"
            deletedCount == 0 -> "删除失败，请检查权限"
            else -> "已删除 $deletedCount 个文件，$failedCount 个文件删除失败"
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // 重新加载媒体文件列表以反映更改
        if (deletedCount > 0) {
            loadMedia()
        }
    }

    private fun deleteFilesWithMediaStore(filePaths: List<String>) {
        var deletedCount = 0
        var failedCount = 0

        filePaths.forEach { filePath ->
            try {
                var fileDeleted = false
                val uri = getMediaUriFromPath(filePath)
                
                if (uri != null) {
                    // 尝试通过MediaStore删除
                    val deletedRows = contentResolver.delete(uri, null, null)
                    if (deletedRows > 0) {
                        deletedCount++
                        fileDeleted = true
                        
                        // 记录变动日志
                        ChangeLogHelper.getInstance(this).logItemRemovedFromCollection(
                            "Main Gallery", filePath
                        )
                    }
                }
                
                if (!fileDeleted) {
                    // 如果MediaStore删除失败或找不到URI，直接删除文件
                    val file = File(filePath)
                    android.util.Log.d("MainActivity", "尝试删除文件: $filePath")
                    android.util.Log.d("MainActivity", "文件存在: ${file.exists()}")
                    android.util.Log.d("MainActivity", "文件可读: ${file.canRead()}")
                    android.util.Log.d("MainActivity", "文件可写: ${file.canWrite()}")
                    android.util.Log.d("MainActivity", "父目录可写: ${file.parentFile?.canWrite()}")
                    
                    if (file.exists()) {
                        // 尝试设置文件权限
                        try {
                            file.setWritable(true)
                            file.setReadable(true)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "设置文件权限失败: $filePath", e)
                        }
                        
                        if (file.delete()) {
                            deletedCount++
                            fileDeleted = true
                            
                            // 通知媒体扫描器文件已删除
                            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            intent.data = Uri.fromFile(file.parentFile)
                            sendBroadcast(intent)
                            
                            // 记录变动日志
                            ChangeLogHelper.getInstance(this).logItemRemovedFromCollection(
                                "Main Gallery", filePath
                            )
                        } else {
                            android.util.Log.e("MainActivity", "文件删除失败: $filePath")
                        }
                    } else {
                        android.util.Log.e("MainActivity", "文件不存在: $filePath")
                    }
                }
                
                if (!fileDeleted) {
                    failedCount++
                }
            } catch (e: Exception) {
                failedCount++
                android.util.Log.e("MainActivity", "Failed to delete file: $filePath", e)
            }
        }

        val message = when {
            failedCount == 0 -> "已删除 $deletedCount 个文件"
            deletedCount == 0 -> "删除失败，请检查权限"
            else -> "已删除 $deletedCount 个文件，$failedCount 个文件删除失败"
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // 重新加载媒体文件列表以反映更改
        if (deletedCount > 0) {
            loadMedia()
        }
    }

    private fun getMediaUriFromPath(filePath: String): Uri? {
        val file = File(filePath)
        val isImage = filePath.lowercase().run {
            endsWith(".jpg") || endsWith(".jpeg") || endsWith(".png") || 
            endsWith(".gif") || endsWith(".bmp") || endsWith(".webp")
        }
        
        val contentUri = if (isImage) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(filePath)
        
        contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(contentUri, id)
            }
        }
        
        return null
    }

    private fun showFileListDialog() {
        val mediaItems = photoAdapter.getMediaItems()
        if (mediaItems.isEmpty()) {
            Toast.makeText(this, "没有文件可显示", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建文件列表
        val fileList = mediaItems.map { mediaItem ->
            val fileName = File(mediaItem.path).name
            FileListItem(fileName, mediaItem.path, mediaItem.isVideo)
        }.sortedBy { it.fileName.lowercase() } // 按文件名排序

        // 创建对话框视图
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_file_list, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewFileList)
        val titleTextView = dialogView.findViewById<TextView>(R.id.tvTitle)
        
        titleTextView.text = "文件名列表 (${fileList.size} 个文件)"
        
        // 创建文件列表适配器，添加点击和长按事件监听
        val fileListAdapter = FileListAdapter(fileList, object : FileListItemClickListener {
            override fun onItemClick(fileItem: FileListItem) {
                openFileFromList(fileItem)
            }
            
            override fun onItemLongClick(fileItem: FileListItem) {
                showFileItemMenu(fileItem)
            }
        })
        
        // 设置RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = fileListAdapter

        // 显示对话框
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showFileItemMenu(fileItem: FileListItem) {
        val options = arrayOf("重命名", "删除", "添加到收藏")
        
        AlertDialog.Builder(this)
            .setTitle(fileItem.fileName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditFilenameDialog(fileItem.filePath) // 重命名
                    1 -> showDeleteConfirmDialog(listOf(fileItem.filePath)) // 删除
                    2 -> showCollectionsDialogForFile(fileItem.filePath) // 添加到收藏
                }
            }
            .show()
    }

    private fun showCollectionsDialogForFile(filePath: String) {
        lifecycleScope.launch {
            val allCollections = viewModel.getAllCollections().first()
            
            if (allCollections.isEmpty()) {
                Toast.makeText(this@MainActivity, "没有收藏列表，请先创建收藏列表", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val collectionNames = allCollections.map { it.name }.toTypedArray()
            val checkedItems = BooleanArray(allCollections.size) { false }
            val selectedCollectionIds = mutableListOf<Long>()

            AlertDialog.Builder(this@MainActivity)
                .setTitle("添加到收藏列表")
                .setMultiChoiceItems(collectionNames, checkedItems) { _, which, isChecked ->
                    val collectionId = allCollections[which].id
                    if (isChecked) {
                        selectedCollectionIds.add(collectionId)
                    } else {
                        selectedCollectionIds.remove(collectionId)
                    }
                }
                .setPositiveButton("确定") { _, _ ->
                    if (selectedCollectionIds.isNotEmpty()) {
                        viewModel.updateImageInCollections(listOf(filePath), selectedCollectionIds)
                        
                        // Log the change
                        lifecycleScope.launch {
                            val collections = viewModel.getAllCollections().first()
                            val selectedCollectionNames = collections.filter { selectedCollectionIds.contains(it.id) }.map { it.name }
                            ChangeLogHelper.getInstance(this@MainActivity).logMultipleItemsAddedToCollections(listOf(filePath), selectedCollectionNames)
                        }
                        
                        Toast.makeText(this@MainActivity, "已添加到 ${selectedCollectionIds.size} 个收藏列表", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun openFileFromList(fileItem: FileListItem) {
        if (fileItem.filePath.isEmpty()) {
            Toast.makeText(this, "无法打开文件：文件路径为空", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(fileItem.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在：${fileItem.fileName}", Toast.LENGTH_SHORT).show()
            return
        }

        if (fileItem.isVideo) {
            // 打开视频播放器
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("video_path", fileItem.filePath)
            startActivity(intent)
        } else {
            // 打开图片详情页面
            val allImagePaths = photoAdapter.getMediaItems()
                .filter { !it.isVideo }
                .map { it.path }
                .toTypedArray()
            
            val currentIndex = allImagePaths.indexOf(fileItem.filePath)
            if (currentIndex >= 0) {
                val intent = Intent(this, ImageDetailActivity::class.java)
                intent.putExtra("image_paths", allImagePaths)
                intent.putExtra("current_index", currentIndex)
                startActivity(intent)
            } else {
                // 如果在当前列表中找不到，单独显示这张图片
                val intent = Intent(this, ImageDetailActivity::class.java)
                intent.putExtra("image_paths", arrayOf(fileItem.filePath))
                intent.putExtra("current_index", 0)
                startActivity(intent)
            }
        }
    }

    private fun cleanupInvalidFiles() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val allItems = db.collectionDao().getAllItemsSync()
                var cleanedCount = 0
                
                for (item in allItems) {
                    val file = File(item.mediaPath)
                    if (!file.exists()) {
                        // 文件不存在，从数据库中删除记录
                        db.collectionDao().removeItemByPath(item.mediaPath)
                        cleanedCount++
                        android.util.Log.d("MainActivity", "清理无效文件记录: ${item.mediaPath}")
                    }
                }
                
                if (cleanedCount > 0) {
                    android.util.Log.d("MainActivity", "已清理 $cleanedCount 个无效文件记录")
                    // 重新加载媒体文件
                    loadMedia()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "清理无效文件时出错", e)
            }
        }
    }

    private fun showCleanupConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("清理无效文件")
            .setMessage("这将清理数据库中指向不存在文件的记录，确定继续吗？")
            .setPositiveButton("清理") { _, _ ->
                performManualCleanup()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performManualCleanup() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val allItems = db.collectionDao().getAllItemsSync()
                var cleanedCount = 0
                
                // 创建一个要删除的路径列表
                val pathsToDelete = mutableListOf<String>()
                
                for (item in allItems) {
                    val file = File(item.mediaPath)
                    if (!file.exists()) {
                        pathsToDelete.add(item.mediaPath)
                        cleanedCount++
                    }
                }
                
                // 批量删除
                for (path in pathsToDelete) {
                    db.collectionDao().removeItemByPath(path)
                }
                
                Toast.makeText(this@MainActivity, "已清理 $cleanedCount 个无效文件记录", Toast.LENGTH_SHORT).show()
                
                if (cleanedCount > 0) {
                    // 重新加载媒体文件
                    loadMedia()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "清理时出错: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showResetDatabaseConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("重置所有数据")
            .setMessage("⚠️ 警告：这将清空所有收藏列表和相关数据，此操作不可撤销！\n\n确定要继续吗？")
            .setPositiveButton("重置") { _, _ ->
                showSecondConfirmDialog()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSecondConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("最后确认")
            .setMessage("请再次确认：\n\n• 所有收藏列表将被删除\n• 所有收藏关联将被清除\n• 同步历史将被清空\n\n这个操作无法撤销！")
            .setPositiveButton("确认重置") { _, _ ->
                resetDatabase()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun resetDatabase() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                
                // 清空所有数据表
                db.collectionDao().deleteAllCollectionItems()
                db.collectionDao().deleteAllCollections()
                
                Toast.makeText(this@MainActivity, "数据库已重置，应用将重启", Toast.LENGTH_LONG).show()
                
                // 延迟重启应用
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }, 2000)
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "重置失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}