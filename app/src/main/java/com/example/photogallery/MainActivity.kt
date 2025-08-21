package com.example.photogallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.photogallery.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Data class to hold media items
data class MediaItem(val path: String, val isVideo: Boolean, val dateAdded: Long, val duration: Long = 0)

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: ZoomableRecyclerView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val factory = ImageDetailViewModelFactory(AppDatabase.getDatabase(application).collectionDao())
        viewModel = ViewModelProvider(this, factory).get(ImageDetailViewModel::class.java)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        setupRecyclerView()
        checkPermissionAndLoadMedia()
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
                        intent.putExtra("image_path", mediaItem.path)
                        startActivity(intent)
                    }
                }
            },
            onItemLongClick = { mediaItem ->
                if (actionMode == null) {
                    actionMode = startActionMode(actionModeCallback)
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
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.contextual_action_bar_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_add_to_collection_cab -> {
                    showCollectionsDialogForSelectedItems()
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
}