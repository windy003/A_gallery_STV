package com.example.photogallery

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class ZoomableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var scaleDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private val minScale = 0.5f
    private val maxScale = 3.0f
    
    // 网格列数配置
    private val minColumns = 2
    private val maxColumns = 6
    private var currentColumns = 3
    
    private var photoAdapter: PhotoAdapter? = null

    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
    }

    fun setPhotoAdapter(adapter: PhotoAdapter) {
        this.photoAdapter = adapter
        this.adapter = adapter
        updateGridLayout()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        // 如果是缩放手势，拦截事件
        if (event.pointerCount > 1) {
            return true
        }
        
        return super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // 如果是多指触摸，拦截事件进行缩放处理
        if (e.pointerCount > 1) {
            return true
        }
        return super.onInterceptTouchEvent(e)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val previousScaleFactor = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(minScale, min(scaleFactor, maxScale))
            
            // 根据缩放因子调整网格列数，增加更细致的级别
            val newColumns = when {
                scaleFactor <= 0.6f -> maxColumns    // 非常小：6列
                scaleFactor <= 0.8f -> 5             // 较小：5列
                scaleFactor <= 1.0f -> 4             // 小：4列
                scaleFactor <= 1.2f -> 3             // 默认：3列
                scaleFactor <= 1.8f -> 2             // 大：2列
                else -> minColumns                   // 非常大：2列
            }
            
            // 只有当列数确实需要改变时才更新布局，避免频繁刷新
            if (newColumns != currentColumns) {
                currentColumns = newColumns
                post { updateGridLayout() } // 使用post确保在主线程中执行
            }
            
            return true
        }
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            // 缩放结束后重置缩放因子，保持当前的列数
            scaleFactor = 1.0f
        }
    }
    
    private fun updateGridLayout() {
        val gridLayoutManager = layoutManager as? GridLayoutManager
        gridLayoutManager?.spanCount = currentColumns
        
        // 通知适配器数据可能发生变化（主要是为了重新计算item大小）
        photoAdapter?.notifyDataSetChanged()
    }
}