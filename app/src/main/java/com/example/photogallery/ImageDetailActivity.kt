package com.example.photogallery

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.data.Collection
import com.example.photogallery.databinding.ActivityImageDetailBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageDetailBinding
    private var imagePaths: List<String> = emptyList()
    private var currentIndex: Int = 0
    private lateinit var imageAdapter: ImagePagerAdapter
    private val viewModel: ImageDetailViewModel by viewModels {
        ImageDetailViewModelFactory(AppDatabase.getDatabase(this).collectionDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        // 获取传递的数据
        val imagePath = intent.getStringExtra("image_path")
        val imagePathsArray = intent.getStringArrayExtra("image_paths")
        currentIndex = intent.getIntExtra("current_index", 0)

        // 如果有图片列表，使用列表；否则只显示单张图片
        imagePaths = if (imagePathsArray != null && imagePathsArray.isNotEmpty()) {
            imagePathsArray.toList()
        } else if (imagePath != null) {
            listOf(imagePath)
        } else {
            emptyList()
        }

        if (imagePaths.isNotEmpty()) {
            setupViewPager()
        }
    }

    private fun setupViewPager() {
        imageAdapter = ImagePagerAdapter(imagePaths) { imagePath ->
            showCollectionsDialog(imagePath)
        }
        
        binding.viewPager.adapter = imageAdapter
        
        // 配置ViewPager2的滑动敏感度
        ViewPager2Helper.configureSensitivity(binding.viewPager)
        
        // 设置到当前图片位置
        if (currentIndex < imagePaths.size) {
            binding.viewPager.setCurrentItem(currentIndex, false)
        }
        
        // 监听页面切换，更新标题
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentIndex = position
                updateTitle()
            }
        })
        
        updateTitle()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit_filename -> {
                if (imagePaths.isNotEmpty() && currentIndex < imagePaths.size) {
                    showEditFilenameDialog(imagePaths[currentIndex])
                }
                true
            }
            R.id.action_view_info -> {
                if (imagePaths.isNotEmpty() && currentIndex < imagePaths.size) {
                    showFileInfoDialog(imagePaths[currentIndex])
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateTitle() {
        if (imagePaths.size > 1) {
            supportActionBar?.title = "${currentIndex + 1}/${imagePaths.size}"
        } else {
            // 显示文件名
            val fileName = File(imagePaths[currentIndex]).name
            supportActionBar?.title = fileName
        }
    }

    private fun showCollectionsDialog(imagePath: String) {
        lifecycleScope.launch {
            val allCollections = viewModel.getAllCollections().first()
            val currentCollections = viewModel.getCollectionsForImage(imagePath).first()
            val currentCollectionIds = currentCollections.map { it.collectionId }

            val collectionNames = allCollections.map { it.name }.toTypedArray()
            val checkedItems = allCollections.map { currentCollectionIds.contains(it.id) }.toBooleanArray()
            val selectedCollectionIds = currentCollectionIds.toMutableList()

            AlertDialog.Builder(this@ImageDetailActivity)
                .setTitle("Add to Collection")
                .setMultiChoiceItems(collectionNames, checkedItems) { _, which, isChecked ->
                    val collectionId = allCollections[which].id
                    if (isChecked) {
                        selectedCollectionIds.add(collectionId)
                    } else {
                        selectedCollectionIds.remove(collectionId)
                    }
                }
                .setPositiveButton("OK") { _, _ ->
                    viewModel.updateImageInCollections(listOf(imagePath), selectedCollectionIds)
                    
                    // Log the change
                    if (selectedCollectionIds.isNotEmpty()) {
                        lifecycleScope.launch {
                            val collections = viewModel.getAllCollections().first()
                            val selectedCollectionNames = collections.filter { selectedCollectionIds.contains(it.id) }.map { it.name }
                            ChangeLogHelper.getInstance(this@ImageDetailActivity).logItemAddedToCollection(
                                selectedCollectionNames.firstOrNull() ?: "Unknown",
                                imagePath
                            )
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
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
                
                // 更新当前路径列表
                val mutablePaths = imagePaths.toMutableList()
                val index = mutablePaths.indexOf(oldPath)
                if (index != -1) {
                    mutablePaths[index] = newPath
                    imagePaths = mutablePaths
                }
                
                // 重新设置适配器数据
                if (::imageAdapter.isInitialized) {
                    imageAdapter = ImagePagerAdapter(imagePaths) { imagePath ->
                        showCollectionsDialog(imagePath)
                    }
                    binding.viewPager.adapter = imageAdapter
                    binding.viewPager.setCurrentItem(currentIndex, false)
                }
                
                updateTitle()
                Toast.makeText(this, "文件重命名成功", Toast.LENGTH_SHORT).show()
                
                // 记录变动日志
                ChangeLogHelper.getInstance(this).logItemRemovedFromCollection(
                    "File Renamed", oldPath
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
}
