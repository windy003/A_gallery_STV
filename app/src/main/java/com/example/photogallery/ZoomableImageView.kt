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
    private var isScaling = false
    
    // 性能优化：重用数组避免频繁创建
    private val matrixValues = FloatArray(9)

    private lateinit var scaleDetector: ScaleGestureDetector

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

        setOnTouchListener { _, event ->
            val curr = PointF(event.x, event.y)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    start.set(curr)
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                    mode = NONE  // 不立即进入ZOOM模式
                    isScaling = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                    isScaling = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 2) {
                        // 双指操作，处理缩放检测
                        scaleDetector.onTouchEvent(event)
                    } else if (mode == DRAG) {
                        // 单指拖拽
                        matrix.set(savedMatrix)
                        val dx = curr.x - start.x
                        val dy = curr.y - start.y
                        matrix.postTranslate(dx, dy)
                    }
                }
            }
            
            // 只有在单指操作时才处理拖拽的matrix更新
            if (event.pointerCount == 1 && mode == DRAG) {
                imageMatrix = matrix
                fixTranslation()
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
            // 不要立即进入缩放模式，等待实际的缩放动作
            savedMatrix.set(matrix)
            isScaling = false
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            
            // 只有当缩放因子明显偏离1.0时才开始真正的缩放
            if (!isScaling && kotlin.math.abs(scaleFactor - 1.0f) > 0.05f) {
                isScaling = true
                mode = ZOOM
            }
            
            // 只有在真正开始缩放时才处理缩放逻辑
            if (isScaling) {
                val origScale = saveScale
                saveScale *= scaleFactor

                if (saveScale > maxScale) {
                    saveScale = maxScale
                } else if (saveScale < minScale) {
                    saveScale = minScale
                }

                val finalScaleFactor = saveScale / origScale
                
                // 性能优化：减少条件判断的复杂度
                val focusX = if (originalWidth * saveScale <= viewWidth) viewWidth / 2f else detector.focusX
                val focusY = if (originalHeight * saveScale <= viewHeight) viewHeight / 2f else detector.focusY
                
                matrix.set(savedMatrix)
                matrix.postScale(finalScaleFactor, finalScaleFactor, focusX, focusY)
                imageMatrix = matrix
                fixTranslation()
            }
            
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            mode = NONE
        }
    }

    private fun fixTranslation() {
        // 重用数组避免频繁创建对象
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
            maxTrans = (viewSize - contentSize) / 2f
        } else {
            minTrans = (viewSize - contentSize) / 2f
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
        fixTranslation()
    }
}