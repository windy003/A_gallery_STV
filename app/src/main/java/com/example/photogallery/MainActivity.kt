package com.example.photogallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager

// Data class to hold media items
data class MediaItem(val path: String, val isVideo: Boolean, val dateAdded: Long)

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: ZoomableRecyclerView
    private lateinit var photoAdapter: PhotoAdapter
    private val mediaItems = mutableListOf<MediaItem>()

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

        setupRecyclerView()
        checkPermissionAndLoadMedia()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        photoAdapter = PhotoAdapter(mediaItems) { mediaItem ->
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
        recyclerView.setPhotoAdapter(photoAdapter)
    }

    private fun checkPermissionAndLoadMedia() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            // For older versions, READ_EXTERNAL_STORAGE covers both
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
        mediaItems.clear()

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
        val videoProjection = arrayOf(MediaStore.Video.Media.DATA, MediaStore.Video.Media.DATE_ADDED)
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                val date = cursor.getLong(dateColumn)
                mediaItems.add(MediaItem(path, true, date))
            }
        }

        mediaItems.sortByDescending { it.dateAdded }

        if (mediaItems.isEmpty()) {
            findViewById<View>(R.id.tvNoImages).visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            findViewById<View>(R.id.tvNoImages).visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            photoAdapter.notifyDataSetChanged()
        }
    }
}
