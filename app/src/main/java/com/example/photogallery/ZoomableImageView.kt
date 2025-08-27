package com.example.photogallery

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private val start = PointF()
    private val mid = PointF()

    private var mode = NONE
    private var minScale = 1f
    private var maxScale = 5f
    private var saveScale = 1f
    private var originalWidth = 0f
    private var originalHeight = 0f
    private var viewWidth = 0
    private var viewHeight = 0
    private var oldMeasuredWidth = 0
    private var oldMeasuredHeight = 0

    private lateinit var scaleDetector: ScaleGestureDetector
    
    // 性能优化：复用数组避免频繁内存分配
    private val matrixValues = FloatArray(9)
    private var lastUpdateTime = 0L
    private val updateThreshold = 16L // 约60fps

    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
    }

    init {
        super.setClickable(true)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        matrix.setTranslate(1f, 1f)
        imageMatrix = matrix
        scaleType = ScaleType.MATRIX
        
        // 启用硬件加速以提升性能
        setLayerType(LAYER_TYPE_HARDWARE, null)

        setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            val curr = PointF(event.x, event.y)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    start.set(event.x, event.y)
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                    mode = ZOOM
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG) {
                        val currentTime = System.currentTimeMillis()
                        
                        // 限制拖拽更新频率
                        if (currentTime - lastUpdateTime >= updateThreshold) {
                            matrix.set(savedMatrix)
                            val dx = curr.x - start.x
                            val dy = curr.y - start.y
                            matrix.postTranslate(dx, dy)
                            fixTranslationOptimized()
                            imageMatrix = matrix
                            lastUpdateTime = currentTime
                        }
                    }
                }
            }
            true
        }
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentTime = System.currentTimeMillis()
            
            // 限制更新频率，避免过度重绘
            if (currentTime - lastUpdateTime < updateThreshold) {
                return true
            }
            lastUpdateTime = currentTime
            
            var scaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= scaleFactor

            if (saveScale > maxScale) {
                saveScale = maxScale
                scaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                scaleFactor = minScale / origScale
            }

            if (originalWidth * saveScale <= viewWidth || originalHeight * saveScale <= viewHeight) {
                matrix.postScale(scaleFactor, scaleFactor, viewWidth / 2f, viewHeight / 2f)
            } else {
                matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            }

            fixTranslationOptimized()
            imageMatrix = matrix
            return true
        }
    }

    private fun fixTranslation() {
        val values = FloatArray(9)
        matrix.getValues(values)
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        val fixTransX = getFixTranslation(transX, viewWidth.toFloat(), originalWidth * saveScale)
        val fixTransY = getFixTranslation(transY, viewHeight.toFloat(), originalHeight * saveScale)

        if (fixTransX != 0f || fixTransY != 0f) {
            matrix.postTranslate(fixTransX, fixTransY)
        }
    }
    
    // 优化版本：复用数组，减少内存分配
    private fun fixTranslationOptimized() {
        matrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]

        val fixTransX = getFixTranslation(transX, viewWidth.toFloat(), originalWidth * saveScale)
        val fixTransY = getFixTranslation(transY, viewHeight.toFloat(), originalHeight * saveScale)

        if (fixTransX != 0f || fixTransY != 0f) {
            matrix.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTranslation(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        if (trans < minTrans) {
            return -trans + minTrans
        }
        if (trans > maxTrans) {
            return -trans + maxTrans
        }
        return 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (oldMeasuredHeight == viewWidth && oldMeasuredHeight == viewHeight || viewWidth == 0 || viewHeight == 0) {
            return
        }

        oldMeasuredHeight = viewHeight
        oldMeasuredWidth = viewWidth

        if (saveScale == 1f) {
            val drawable = drawable
            if (drawable == null || drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) {
                return
            }

            val bmWidth = drawable.intrinsicWidth
            val bmHeight = drawable.intrinsicHeight

            val scaleX = viewWidth.toFloat() / bmWidth.toFloat()
            val scaleY = viewHeight.toFloat() / bmHeight.toFloat()
            val scale = min(scaleX, scaleY)

            matrix.setScale(scale, scale)

            var redundantYSpace = viewHeight.toFloat() - scale * bmHeight.toFloat()
            var redundantXSpace = viewWidth.toFloat() - scale * bmWidth.toFloat()
            redundantYSpace /= 2f
            redundantXSpace /= 2f

            matrix.postTranslate(redundantXSpace, redundantYSpace)

            originalWidth = viewWidth - 2 * redundantXSpace
            originalHeight = viewHeight - 2 * redundantYSpace
            imageMatrix = matrix
        }
        fixTranslationOptimized()
    }
}