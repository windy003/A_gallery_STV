package com.example.photogallery

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class ImageDetailActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)
        
        val imagePath = intent.getStringExtra("image_path")
        val zoomableImageView = findViewById<ZoomableImageView>(R.id.zoomableImageView)
        
        imagePath?.let {
            Glide.with(this)
                .load(it)
                .into(zoomableImageView)
        }
    }
}