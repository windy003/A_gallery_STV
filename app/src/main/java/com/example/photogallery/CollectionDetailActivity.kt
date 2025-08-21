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
            // 当选择数量变化时，更新菜单
            actionMode?.invalidate()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // 根据选中项目数量选择菜单
            val selectedCount = photoAdapter.getSelectedItems().size
            val menuResource = if (selectedCount == 1) {
                R.menu.collection_single_item_menu
            } else {
                R.menu.collection_detail_contextual_menu
            }
            mode?.menuInflater?.inflate(menuResource, menu)
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
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_remove_from_collection -> {
                    removeSelectedItemsFromCollection()
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
