package com.example.photogallery

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ZoomableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val TAG = "ZoomableRecyclerView"

    private var scaleDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private val minScale = 0.5f
    private val maxScale = 3.0f
    
    // 网格列数配置
    private val minColumns = 2
    private val maxColumns = 6
    private var currentColumns = 3
    private var isScaling = false
    
    // 性能优化：防抖动和延迟更新
    private var pendingLayoutUpdate = false
    private val layoutUpdateHandler = Handler(Looper.getMainLooper())
    private val layoutUpdateDelay = 100L // 适中的延迟，平衡性能和响应性
    
    // 缩放敏感度优化
    private val scaleThreshold = 0.15f // 增加阈值，减少频繁更新
    private var lastUpdateScale = 1.0f
    private var initialSpanCount = 3 // 记录初始列数
    private var lastUpdateTime = 0L // 上次更新时间
    private val minUpdateInterval = 150L // 最小更新间隔150ms
    
    // 手势优化
    private var activePointerId = -1
    private var isUserScaling = false // 区分用户主动缩放和程序缩放
    
    // 视觉反馈优化
    private var scaleAnimationRunning = false
    private var gestureInProgress = false
    
    private var photoAdapter: PhotoAdapter? = null

    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        // 设置更敏感的缩放检测
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            scaleDetector.isQuickScaleEnabled = false // 禁用快速缩放，避免冲突
        }
    }

    fun setPhotoAdapter(adapter: PhotoAdapter) {
        this.photoAdapter = adapter
        this.adapter = adapter
        // 记录初始列数
        val gridLayoutManager = GridLayoutManager(context, initialSpanCount)
        layoutManager = gridLayoutManager
        currentColumns = initialSpanCount
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                gestureInProgress = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-touch started, immediately mark as gesture in progress
                gestureInProgress = true
                isScaling = true
                Log.d(TAG, "ACTION_POINTER_DOWN: gestureInProgress = true")
                return true // 立即消费事件
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 && gestureInProgress) {
                    return true // 缩放手势进行中，消费事件
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                if (event.pointerCount <= 1) {
                    gestureInProgress = false
                    isScaling = false
                    Log.d(TAG, "ACTION_UP/CANCEL: gestureInProgress = false")
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) { // 即将变成单指或无指
                    gestureInProgress = false
                    isScaling = false
                    Log.d(TAG, "ACTION_POINTER_UP: gestureInProgress = false")
                }
            }
        }

        // 如果是缩放手势或多指触摸，消费事件
        if (gestureInProgress || event.pointerCount > 1) {
            return true
        }

        return super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // 立即拦截多指触摸事件
        if (e.pointerCount > 1) {
            gestureInProgress = true
            Log.d(TAG, "onInterceptTouchEvent: Intercepting multi-touch (pointerCount=${e.pointerCount})")
            return true
        }
        
        // 如果手势正在进行中，继续拦截
        if (gestureInProgress) {
            Log.d(TAG, "onInterceptTouchEvent: Intercepting (gestureInProgress=true)")
            return true
        }
        
        return super.onInterceptTouchEvent(e)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val detectorScaleFactor = detector.scaleFactor
            
            // 累积缩放因子
            scaleFactor *= detectorScaleFactor
            scaleFactor = max(minScale, min(scaleFactor, maxScale))
            
            Log.d(TAG, "onScale: scaleFactor=$scaleFactor, detectorScaleFactor=$detectorScaleFactor")

            // 使用时间间隔控制更新频率
            val currentTime = System.currentTimeMillis()
            val targetColumns = calculateColumnsFromScale()
            
            if (targetColumns != currentColumns && 
                abs(scaleFactor - lastUpdateScale) > scaleThreshold &&
                (currentTime - lastUpdateTime) > minUpdateInterval) {
                
                Log.d(TAG, "onScale: Triggering layout update. targetColumns=$targetColumns, currentColumns=$currentColumns")
                updateLayoutWithDelay()
                lastUpdateScale = scaleFactor
                lastUpdateTime = currentTime
            }
            
            return true
        }
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            gestureInProgress = true
            isScaling = true
            isUserScaling = true
            
            // 取消任何待处理的布局更新
            layoutUpdateHandler.removeCallbacksAndMessages(null)
            pendingLayoutUpdate = false
            scaleAnimationRunning = false
            lastUpdateScale = scaleFactor
            lastUpdateTime = System.currentTimeMillis()
            
            Log.d(TAG, "onScaleBegin: gestureInProgress = true, isScaling = true")

            // 提供触觉反馈（如果设备支持）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
            
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isUserScaling = false
            
            Log.d(TAG, "onScaleEnd: isUserScaling = false")

            // 缩放结束后立即更新到最终状态
            layoutUpdateHandler.removeCallbacksAndMessages(null)
            pendingLayoutUpdate = false
            updateLayoutImmediate()
            
            // 提供结束触觉反馈
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
            }
            
            // 延迟重置手势状态
            layoutUpdateHandler.postDelayed({
                gestureInProgress = false
                isScaling = false
                Log.d(TAG, "onScaleEnd: All scaling states reset to false")
            }, 200) // 增加延迟时间，确保手势完全结束
        }
    }
    
    private fun updateLayoutWithDelay() {
        if (pendingLayoutUpdate) return
        
        pendingLayoutUpdate = true
        layoutUpdateHandler.postDelayed({
            try {
                Log.d(TAG, "updateLayoutWithDelay: Executing immediate update")
                updateLayoutImmediate()
            } catch (e: Exception) {
                Log.e(TAG, "updateLayoutWithDelay: Error during layout update", e)
                // 防止在视图销毁时更新布局导致崩溃
            } finally {
                pendingLayoutUpdate = false
            }
        }, layoutUpdateDelay)
    }
    
    private fun updateLayoutImmediate() {
        val newColumns = calculateColumnsFromScale()
        if (newColumns != currentColumns) {
            Log.d(TAG, "updateLayoutImmediate: newColumns=$newColumns, currentColumns=$currentColumns")
            currentColumns = newColumns
            updateGridLayoutSafe()
        } else {
            Log.d(TAG, "updateLayoutImmediate: Columns unchanged ($newColumns)")
        }
    }
    
    private fun calculateColumnsFromScale(): Int {
        return when {
            scaleFactor <= 0.6f -> maxColumns    // 最小：6列
            scaleFactor <= 0.8f -> 5             // 较小：5列
            scaleFactor <= 1.0f -> 4             // 小：4列
            scaleFactor <= 1.2f -> 3             // 默认：3列
            else -> minColumns                   // 大：2列
        }
    }
    
    private fun updateGridLayoutSafe() {
        if (scaleAnimationRunning) {
            Log.d(TAG, "updateGridLayoutSafe: Animation already running, skipping.")
            return
        }
        
        try {
            val gridLayoutManager = layoutManager as? GridLayoutManager
            if (gridLayoutManager != null && gridLayoutManager.spanCount != currentColumns) {
                scaleAnimationRunning = true
                Log.d(TAG, "updateGridLayoutSafe: Setting spanCount to $currentColumns")
                
                // 直接在当前线程更新，避免post延迟
                if (isAttachedToWindow) {
                    try {
                        gridLayoutManager.spanCount = currentColumns
                        Log.d(TAG, "updateGridLayoutSafe: spanCount updated to $currentColumns")
                        
                        // 使用invalidate而不是requestLayout，更轻量级
                        invalidate()
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "updateGridLayoutSafe: Error during layout update", e)
                    }
                }
                
                // 立即重置动画标志
                scaleAnimationRunning = false
                
            } else {
                Log.d(TAG, "updateGridLayoutSafe: gridLayoutManager is null or spanCount already matches currentColumns.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateGridLayoutSafe: Error in updateGridLayoutSafe", e)
            scaleAnimationRunning = false
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 清理Handler，防止内存泄漏
        layoutUpdateHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "onDetachedFromWindow: Handler callbacks removed.")
    }
}