package com.example.photogallery

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.data.Collection
import com.example.photogallery.databinding.ActivityImageDetailBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    private fun updateTitle() {
        supportActionBar?.title = "${currentIndex + 1}/${imagePaths.size}"
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
