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
    private val minColumns = 1
    private val maxColumns = 10
    private var currentColumns = 3
    private var isScaling = false
    
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
            val detectorScaleFactor = detector.scaleFactor
            
            // 只有当缩放因子明显偏离1.0时才开始真正的缩放
            if (!isScaling && kotlin.math.abs(detectorScaleFactor - 1.0f) > 0.1f) {
                isScaling = true
            }
            
            // 只有在真正开始缩放时才处理缩放逻辑
            if (isScaling) {
                scaleFactor *= detectorScaleFactor
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
                    // 移除post调用，直接在当前线程执行
                    updateGridLayout()
                }
            }
            
            return true
        }
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = false
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            // 缩放结束后更新缩放因子，以保持当前的列数
            scaleFactor = when (currentColumns) {
                maxColumns -> 0.5f // 6列
                5 -> 0.7f          // 5列
                4 -> 0.9f          // 4列
                3 -> 1.1f          // 3列
                minColumns -> 1.5f // 2列
                else -> 1.1f       // 默认3列
            }
        }
    }
    
    private fun updateGridLayout() {
        val gridLayoutManager = layoutManager as? GridLayoutManager
        if (gridLayoutManager?.spanCount != currentColumns) {
            gridLayoutManager?.spanCount = currentColumns
            // 只在列数真正改变时才重新布局，避免不必要的重绘
            requestLayout()
        }
    }
}