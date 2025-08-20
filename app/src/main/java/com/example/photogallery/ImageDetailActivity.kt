package com.example.photogallery

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.data.Collection
import com.example.photogallery.databinding.ActivityImageDetailBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ImageDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageDetailBinding
    private var imagePath: String? = null
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

        imagePath = intent.getStringExtra("image_path")

        imagePath?.let {
            Glide.with(this)
                .load(it)
                .into(binding.photoView)
        }

        binding.photoView.setOnLongClickListener {
            showCollectionsDialog()
            true
        }
    }

    private fun showCollectionsDialog() {
        imagePath?.let { path ->
            lifecycleScope.launch {
                val allCollections = viewModel.getAllCollections().first()
                val currentCollections = viewModel.getCollectionsForImage(path).first()
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
                        viewModel.updateImageInCollections(listOf(path), selectedCollectionIds)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
