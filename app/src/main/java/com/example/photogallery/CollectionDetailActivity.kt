package com.example.photogallery

import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
    private lateinit var photoAdapter: PhotoAdapter
    private var actionMode: ActionMode? = null
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
                            putExtra("image_path", mediaItem.path)
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
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.collection_detail_contextual_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_remove_from_collection -> {
                    removeSelectedItemsFromCollection()
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_COLLECTION_ID = "collection_id"
        const val EXTRA_COLLECTION_NAME = "collection_name"
    }
}
