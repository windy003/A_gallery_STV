package com.example.photogallery

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ImageDetailActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)
        
        val imagePath = intent.getStringExtra("image_path")
        val zoomableImageView = findViewById<ZoomableImageView>(R.id.zoomableImageView)
        
        imagePath?.let {
            // 直接使用BitmapFactory加载图片，避免Glide的开销
            val bitmap = BitmapFactory.decodeFile(it)
            bitmap?.let { bmp ->
                zoomableImageView.setImageBitmap(bmp)
            }
        }
    }
}