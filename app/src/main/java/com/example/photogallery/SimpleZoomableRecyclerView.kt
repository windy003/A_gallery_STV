package com.example.photogallery

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class SimpleZoomableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var scaleDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private val minScale = 0.5f
    private val maxScale = 2.5f
    
    // 网格列数配置
    private val minColumns = 2
    private val maxColumns = 6
    private var currentColumns = 3
    private var isScaling = false
    
    private var photoAdapter: PhotoAdapter? = null

    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
    }

    fun setPhotoAdapter(adapter: PhotoAdapter) {
        this.photoAdapter = adapter
        this.adapter = adapter
        val gridLayoutManager = GridLayoutManager(context, currentColumns)
        layoutManager = gridLayoutManager
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        // 如果是多指触摸，拦截事件
        if (event.pointerCount > 1 || isScaling) {
            return true
        }
        
        return super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // 如果是多指触摸，拦截事件
        if (e.pointerCount > 1 || isScaling) {
            return true
        }
        return super.onInterceptTouchEvent(e)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(minScale, min(scaleFactor, maxScale))
            
            // 根据缩放因子直接计算列数
            val newColumns = when {
                scaleFactor <= 0.7f -> maxColumns    // 6列
                scaleFactor <= 0.9f -> 5             // 5列  
                scaleFactor <= 1.1f -> 4             // 4列
                scaleFactor <= 1.3f -> 3             // 3列
                else -> minColumns                   // 2列
            }
            
            // 如果列数改变，立即更新
            if (newColumns != currentColumns) {
                currentColumns = newColumns
                updateGridLayout()
            }
            
            return true
        }
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    }
    
    private fun updateGridLayout() {
        (layoutManager as? GridLayoutManager)?.let { manager ->
            if (manager.spanCount != currentColumns) {
                manager.spanCount = currentColumns
                // 强制重新布局
                post { 
                    if (isAttachedToWindow) {
                        adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }
}