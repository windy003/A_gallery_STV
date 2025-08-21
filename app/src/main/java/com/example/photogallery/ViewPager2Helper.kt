package com.example.photogallery

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.lang.reflect.Field

object ViewPager2Helper {
    
    /**
     * 配置ViewPager2的滑动敏感度，让1-2cm的滑动距离就能切换页面
     */
    fun configureSensitivity(viewPager: ViewPager2) {
        try {
            // 通过反射获取内部的RecyclerView
            val recyclerViewField: Field = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            recyclerViewField.isAccessible = true
            val recyclerView = recyclerViewField.get(viewPager) as RecyclerView
            
            // 设置自定义的滑动阈值 - 约1.5cm
            val density = viewPager.context.resources.displayMetrics.density
            val customTouchSlop = (40 * density).toInt() // 40dp ≈ 1.5cm
            
            val touchSlopField: Field = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            touchSlopField.setInt(recyclerView, customTouchSlop)
            
            // 设置更敏感的页面切换行为
            viewPager.offscreenPageLimit = 1
            
            // 延迟设置最小滑动速度
            viewPager.post {
                configureFlingVelocity(recyclerView)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun configureFlingVelocity(recyclerView: RecyclerView) {
        try {
            // 大幅减少滑动所需的最小速度，让轻微滑动也能切换
            val density = recyclerView.context.resources.displayMetrics.density
            val minimumVelocity = (25 * density).toInt() // 25dp/s
            
            val minVelocityField: Field = RecyclerView::class.java.getDeclaredField("mMinFlingVelocity")
            minVelocityField.isAccessible = true
            minVelocityField.setInt(recyclerView, minimumVelocity)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}