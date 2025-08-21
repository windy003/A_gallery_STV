package com.example.photogallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.databinding.ActivityCollectionDetailBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CollectionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionDetailBinding
    private var collectionId: Long = -1

    private val viewModel: CollectionDetailViewModel by viewModels {
        CollectionDetailViewModelFactory(application, AppDatabase.getDatabase(this).collectionDao(), collectionId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        collectionId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1)
        val collectionName = intent.getStringExtra(EXTRA_COLLECTION_NAME)

        if (collectionId == -1L) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = collectionName

        val photoAdapter = PhotoAdapter { mediaItem ->
            val intent = if (mediaItem.isVideo) {
                Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra("video_path", mediaItem.path)
                }
            } else {
                Intent(this, ImageDetailActivity::class.java).apply {
                    putExtra("image_path", mediaItem.path)
                }
            }
            startActivity(intent)
        }

        binding.recyclerView.adapter = photoAdapter
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)

        lifecycleScope.launch {
            viewModel.mediaItems.collectLatest {
                photoAdapter.submitList(it)
            }
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
