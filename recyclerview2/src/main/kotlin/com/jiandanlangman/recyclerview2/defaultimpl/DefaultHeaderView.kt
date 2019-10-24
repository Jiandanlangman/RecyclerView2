package com.jiandanlangman.recyclerview2.defaultimpl

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.jiandanlangman.recyclerview2.IHeaderView
import com.jiandanlangman.recyclerview2.LoadStatus
import com.jiandanlangman.recyclerview2.R

class DefaultHeaderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), IHeaderView {

    private val canRefreshHeight = (resources.displayMetrics.density * 50 + .5f).toInt()
    private val viewMaxHeight = (resources.displayMetrics.density * 144 + .5f).toInt()
    private val viewMinHeight = 1
    private val imageView: ImageView
    private val imageViewWidth: Int
    private val imageViewHeight: Int
    private val hintView: TextView
    private val onLayoutChangeListener = OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
        onLayoutChanged(bottom - top)
    }
    private var loadStatus: LoadStatus? = null
    private var animator: ValueAnimator? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.recyclerview2_default_header_view, this, true)
        imageView = findViewById(R.id.image)
        imageViewWidth = imageView.layoutParams.width
        imageViewHeight = imageView.layoutParams.height
        hintView = findViewById(R.id.hint)
    }

    override fun getView() = this

    override fun getCanRefreshHeight() = canRefreshHeight

    override fun getViewMaxHeight() = viewMaxHeight

    override fun getViewMinHeight() = viewMinHeight

    override fun onCanRefreshStatusChanged(canRefresh: Boolean) {
        loadStatus = null
        if (canRefresh) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(1f, 1.24f, 1f, 1.12f, 1f)
            animator!!.duration = 600
            animator!!.addUpdateListener {
                val value = it.animatedValue as Float
                imageView.scaleX = value
                imageView.scaleY = value
            }
            animator!!.start()
            hintView.text = "松开刷新"
        } else {
            animator?.cancel()
            imageView.scaleX = 1f
            imageView.scaleY = 1f
            hintView.text = "下拉刷新"
        }
    }

    override fun onLoadStatusChanged(status: LoadStatus) {
        if (status != loadStatus) {
            this.loadStatus = status
            imageView.animate().cancel()
            imageView.rotation = 0f
            when (status) {
                LoadStatus.STATUS_REFRESHING -> {
                    (imageView.drawable as AnimationDrawable).start()
                    hintView.text = "正在刷新，请稍后..."
                }
                LoadStatus.STATUS_LOAD_FAILED -> {
                    (imageView.drawable as AnimationDrawable).stop()
                    hintView.text = "刷新失败！"
                }
                LoadStatus.STATUS_NO_MORE_DATA, LoadStatus.STATUS_NORMAL -> {
                    (imageView.drawable as AnimationDrawable).stop()
                    hintView.text = "刷新成功！"
                }
                else -> {
                    (imageView.drawable as AnimationDrawable).stop()
                    hintView.text = "下拉刷新"
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as ViewGroup).addOnLayoutChangeListener(onLayoutChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        (imageView.drawable as AnimationDrawable).stop()
        (parent as ViewGroup).removeOnLayoutChangeListener(onLayoutChangeListener)
    }

    private fun onLayoutChanged(newHeight: Int) {
        if (newHeight <= getCanRefreshHeight()) {
            val scale = newHeight.toFloat() / getCanRefreshHeight()
            imageView.alpha = scale
            val params = imageView.layoutParams
            params.width = (imageViewWidth * scale + .5f).toInt()
            params.height = (imageViewHeight * scale + .5f).toInt()
            imageView.layoutParams = params
        }
    }

}