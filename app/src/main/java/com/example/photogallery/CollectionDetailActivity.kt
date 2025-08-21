package com.example.photogallery

import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.data.Collection
import com.example.photogallery.data.CollectionItem
import com.example.photogallery.databinding.ActivityCollectionDetailBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CollectionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionDetailBinding
    private lateinit var photoAdapter: PhotoAdapter
    private var actionMode: ActionMode? = null
    private var collectionId: Long = -1
    private var collectionName: String? = null

    private val viewModel: CollectionDetailViewModel by viewModels {
        CollectionDetailViewModelFactory(application, AppDatabase.getDatabase(this).collectionDao(), collectionId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        collectionId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1)
        collectionName = intent.getStringExtra(EXTRA_COLLECTION_NAME)

        if (collectionId == -1L) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = collectionName

        photoAdapter = PhotoAdapter(
            onItemClick = { mediaItem ->
                if (actionMode != null) {
                    toggleSelection(mediaItem)
                } else {
                    val intent = if (mediaItem.isVideo) {
                        Intent(this, VideoPlayerActivity::class.java).apply {
                            putExtra("video_path", mediaItem.path)
                        }
                    } else {
                        Intent(this, ImageDetailActivity::class.java).apply {
                            // 获取所有图片路径（非视频）
                            val allImagePaths = photoAdapter.getMediaItems()
                                .filter { !it.isVideo }
                                .map { it.path }
                                .toTypedArray()
                            
                            val currentIndex = allImagePaths.indexOf(mediaItem.path)
                            
                            putExtra("image_paths", allImagePaths)
                            putExtra("current_index", currentIndex)
                        }
                    }
                    startActivity(intent)
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

        binding.recyclerView.adapter = photoAdapter
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)

        lifecycleScope.launch {
            viewModel.mediaItems.collectLatest {
                photoAdapter.submitList(it)
            }
        }
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
            // 当选择数量变化时，强制更新菜单
            actionMode?.invalidate()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // 默认加载单张图片的菜单，因为通常ActionMode是由长按单个项目触发的
            mode?.menuInflater?.inflate(R.menu.collection_single_item_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // 当选中项目数量变化时，重新创建菜单
            val selectedCount = photoAdapter.getSelectedItems().size
            val currentMenuResource = if (selectedCount == 1) {
                R.menu.collection_single_item_menu
            } else {
                R.menu.collection_detail_contextual_menu
            }
            
            // 清除当前菜单并重新加载
            menu?.clear()
            mode?.menuInflater?.inflate(currentMenuResource, menu)
            
            // 更新标题
            mode?.title = "$selectedCount selected"
            
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_remove_from_collection -> {
                    removeSelectedItemsFromCollection()
                    mode?.finish()
                    true
                }
                R.id.action_delete_item -> {
                    val selectedItems = photoAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        val mediaItem = selectedItems.first()
                        showDeleteConfirmDialog(mediaItem.path)
                    }
                    mode?.finish()
                    true
                }
                R.id.action_edit_filename -> {
                    val selectedItems = photoAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        val mediaItem = selectedItems.first()
                        showEditFilenameDialog(mediaItem.path)
                    }
                    mode?.finish()
                    true
                }
                R.id.action_view_info -> {
                    val selectedItems = photoAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        val mediaItem = selectedItems.first()
                        showFileInfoDialog(mediaItem.path)
                    }
                    mode?.finish()
                    true
                }
                R.id.action_add_to_collection -> {
                    val selectedItems = photoAdapter.getSelectedItems()
                    if (selectedItems.isNotEmpty()) {
                        showCollectionSelectionDialog(selectedItems.map { it.path })
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

    private fun removeSelectedItemsFromCollection() {
        val selectedPaths = photoAdapter.getSelectedItems().map { it.path }
        if (selectedPaths.isNotEmpty()) {
            viewModel.removeItemsFromCollection(selectedPaths)
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

    private fun showFileInfoDialog(filePath: String) {
        val file = File(filePath)
        
        val fileName = file.name
        val fileSize = formatFileSize(file.length())
        val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(file.lastModified()))
        val fileDimensions = getImageDimensions(filePath)
        
        val message = buildString {
            append("文件名: $fileName\n")
            append("文件大小: $fileSize\n") 
            append("修改时间: $lastModified\n")
            append("文件路径: $filePath\n")
            if (fileDimensions.isNotEmpty()) {
                append("图片尺寸: $fileDimensions")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("文件信息")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showDeleteConfirmDialog(filePath: String) {
        val file = File(filePath)
        val fileName = file.name

        AlertDialog.Builder(this)
            .setTitle("删除文件")
            .setMessage("确定要删除文件 \"$fileName\" 吗？\n\n此操作不可撤销！")
            .setPositiveButton("删除") { _, _ ->
                deleteMediaFile(filePath)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteMediaFile(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists() && file.delete()) {
                // 从收藏中移除
                viewModel.removeItemsFromCollection(listOf(filePath))
                
                Toast.makeText(this, "文件已删除", Toast.LENGTH_SHORT).show()
                
                // 记录变动日志
                ChangeLogHelper.getInstance(this).logItemRemovedFromCollection(
                    collectionName ?: "Collection", filePath
                )
            } else {
                Toast.makeText(this, "删除失败，文件不存在或权限不足", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCollectionSelectionDialog(filePaths: List<String>) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@CollectionDetailActivity)
                val allCollections = db.collectionDao().getAllCollectionsSync()
                
                // 过滤掉当前收藏列表
                val otherCollections = allCollections.filter { it.id != collectionId }
                
                if (otherCollections.isEmpty()) {
                    Toast.makeText(this@CollectionDetailActivity, "没有其他收藏列表", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val collectionNames = otherCollections.map { it.name }.toTypedArray()
                
                AlertDialog.Builder(this@CollectionDetailActivity)
                    .setTitle("选择目标收藏列表")
                    .setItems(collectionNames) { _, which ->
                        val selectedCollection = otherCollections[which]
                        addFilesToCollection(filePaths, selectedCollection)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                    
            } catch (e: Exception) {
                Toast.makeText(this@CollectionDetailActivity, "获取收藏列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addFilesToCollection(filePaths: List<String>, targetCollection: Collection) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@CollectionDetailActivity)
                var addedCount = 0
                
                filePaths.forEach { filePath ->
                    // 检查文件是否已存在于目标收藏中
                    val existingItems = db.collectionDao().getItemsForCollectionSync(targetCollection.id)
                    val alreadyExists = existingItems.any { it.mediaPath == filePath }
                    
                    if (!alreadyExists) {
                        val collectionItem = CollectionItem(
                            collectionId = targetCollection.id,
                            mediaPath = filePath
                        )
                        db.collectionDao().insertItemIntoCollection(collectionItem)
                        addedCount++
                        
                        // 记录变动日志
                        ChangeLogHelper.getInstance(this@CollectionDetailActivity).logItemAddedToCollection(
                            targetCollection.name, filePath
                        )
                    }
                }
                
                val message = if (addedCount == filePaths.size) {
                    "已添加 $addedCount 个文件到 ${targetCollection.name}"
                } else {
                    "已添加 $addedCount 个文件到 ${targetCollection.name}（${filePaths.size - addedCount} 个文件已存在）"
                }
                
                Toast.makeText(this@CollectionDetailActivity, message, Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Toast.makeText(this@CollectionDetailActivity, "添加到收藏失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
                // 更新数据库中的路径
                val newPath = newFile.absolutePath
                viewModel.updateImagePath(oldPath, newPath)
                
                Toast.makeText(this, "文件重命名成功", Toast.LENGTH_SHORT).show()
                
                // 记录变动日志
                ChangeLogHelper.getInstance(this).logItemRemovedFromCollection(
                    collectionName ?: "Collection", oldPath
                )
                ChangeLogHelper.getInstance(this).logItemAddedToCollection(
                    collectionName ?: "Collection", newPath
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_COLLECTION_ID = "collection_id"
        const val EXTRA_COLLECTION_NAME = "collection_name"
    }
}
