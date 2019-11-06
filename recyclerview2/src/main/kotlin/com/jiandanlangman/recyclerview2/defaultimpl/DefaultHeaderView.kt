package com.jiandanlangman.recyclerview2.defaultimpl

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.os.Handler
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.jiandanlangman.recyclerview2.IHeaderView
import com.jiandanlangman.recyclerview2.LoadStatus
import com.jiandanlangman.recyclerview2.R
import com.jiandanlangman.recyclerview2.RecyclerView2

class DefaultHeaderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), IHeaderView {


    private val canRefreshHeight = (resources.displayMetrics.density * 48 + .5f).toInt()
    private val viewMaxHeight = (resources.displayMetrics.density * 144 + .5f).toInt()
    private val showRefreshResultHeight = (resources.displayMetrics.density * 20 + .5f).toInt()
    private val viewMinHeight = 1
    private val h = Handler {
        animateHeight(viewMinHeight)
        true
    }

    private val onLayoutChangeListener = OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
        val newHeight = bottom - top
        if (newHeight != prevHeight) {
            onLayoutChanged(newHeight)
            prevHeight = newHeight
        }
    }

    private val imageView: ImageView
    private val hintView: TextView
    private val heartAnimationDrawable: AnimationDrawable

    private lateinit var recyclerView: RecyclerView2

    private var canRefreshStatus = -1
    private var adapterItemCount = 0
    private var prevHeight = -1

    private var loadStatus: LoadStatus? = null
    private var heightAnimator: ValueAnimator? = null
    private var canRefreshAnimator: ValueAnimator? = null
    private var onHeaderViewResetListener :(() -> Unit) ?= null


    init {
        LayoutInflater.from(context).inflate(R.layout.recyclerview2_default_header_view, this, true)
        imageView = findViewById(R.id.image)
        imageView.pivotX = imageView.layoutParams.width / 2f
        imageView.pivotY = imageView.layoutParams.height.toFloat()
        heartAnimationDrawable = imageView.drawable as AnimationDrawable
        hintView = findViewById(R.id.hint)
    }

    override fun getView() = this

    override fun onBindToRecyclerView(recyclerView: RecyclerView2, layoutParams: ViewGroup.LayoutParams) {
        this.recyclerView = recyclerView
        this.layoutParams = layoutParams
        layoutParams.height = viewMinHeight
    }

    override fun onRecyclerViewLoadStatusChanged(status: LoadStatus) {
        if (status != loadStatus) {
            val prevLoadStatus = loadStatus
            loadStatus = status
            if (recyclerView.adapter?.itemCount ?: 0 != 0) {
                when (loadStatus) {
                    LoadStatus.STATUS_REFRESHING -> {
                        if (1 != imageView.tag) {
                            heartAnimationDrawable.start()
                            hintView.text = "正在刷新，请稍后..."
                            imageView.tag = 1
                        }
                        h.removeMessages(200)
                        animateHeight(canRefreshHeight)
                    }
                    LoadStatus.STATUS_NORMAL, LoadStatus.STATUS_NO_MORE_DATA -> if (prevLoadStatus == LoadStatus.STATUS_REFRESHING) { //只处理刷新带来的这两个事件, LoadMore带来的不处理
                        if (3 != imageView.tag) {
                            heartAnimationDrawable.stop()
                            hintView.text = "刷新成功!"
                            imageView.tag = 3
                        }
                        h.removeMessages(200)
                        h.sendEmptyMessageDelayed(200, 400)
                        animateHeight(showRefreshResultHeight)
                    }
                    LoadStatus.STATUS_LOAD_FAILED -> if (prevLoadStatus == LoadStatus.STATUS_REFRESHING) { //不可能是空页面失败，但是有可能是加载更多失败的
                        adapterItemCount = recyclerView.adapter!!.itemCount
                        if (2 != imageView.tag) {
                            heartAnimationDrawable.stop()
                            hintView.text = "刷新失败"
                            imageView.tag = 2
                        }
                        h.removeMessages(200)
                        h.sendEmptyMessageDelayed(200, 400)
                        animateHeight(showRefreshResultHeight)
                    }
                    else -> {
                    }
                }
            }
        }
    }

    override fun setOnHeaderViewResetListener(listener: (() -> Unit)?) {
        onHeaderViewResetListener = listener
    }

    override fun onPullingDown(dy: Float): Boolean {
        val params = layoutParams
        var ret = true
        if ((dy > 0 && params.height < viewMaxHeight) || params.height > viewMinHeight) {
            val paramsHeight = params.height
            var h = when {
                dy <= 0 -> (paramsHeight + dy).toInt()
                paramsHeight <= canRefreshHeight -> (paramsHeight + dy * .32 + .5f).toInt()
                else -> (paramsHeight + dy * .12 + .5f).toInt()
            }
            if (h < viewMinHeight) {
                h = viewMinHeight
                ret = false
            } else if (h > viewMaxHeight) {
                ret = false
                h = viewMaxHeight
            }
            params.height = h
            parent?.requestLayout()
            val status = if (h >= canRefreshHeight) 1 else 0
            if (status != canRefreshStatus) {
                canRefreshStatus = status
                onCanRefreshStatusChanged(canRefreshStatus == 1)
            }
        } else
            ret = false
        return ret
    }

    override fun onEndPullDown() {
        if (canRefreshStatus == 1)
            recyclerView.setLoadStatus(LoadStatus.STATUS_REFRESHING, true)
        else
            animateHeight(viewMinHeight)
        canRefreshStatus = -1
    }


    private fun animateHeight(height: Int) {
        heightAnimator?.cancel()
        heightAnimator = null
        if (height != layoutParams.height) {
            heightAnimator = ValueAnimator.ofInt(layoutParams.height, height)
            heightAnimator!!.duration = 200
            heightAnimator!!.interpolator = LinearInterpolator()
            heightAnimator!!.addUpdateListener {
                val animatedValue = it.animatedValue as Int
                layoutParams.height = animatedValue
                if(parent != null && parent.parent != null) {
                    parent?.requestLayout()
                    recyclerView.scrollToPosition(0)
                }
                if(height == viewMinHeight && animatedValue == height)
                    onHeaderViewResetListener?.invoke()
            }
            heightAnimator!!.start()
        }
    }

    private fun onCanRefreshStatusChanged(canRefresh: Boolean) {
        if (canRefresh) {
            canRefreshAnimator?.cancel()
            canRefreshAnimator = ValueAnimator.ofFloat(1f, 1.16f, 0.92f, 1.08f, 1f)
            canRefreshAnimator!!.duration = 600
            canRefreshAnimator!!.addUpdateListener {
                val value = it.animatedValue as Float
                imageView.scaleX = value
                imageView.scaleY = value
            }
            canRefreshAnimator!!.start()
            hintView.text = "松开刷新"
        } else {
            canRefreshAnimator?.cancel()
            imageView.scaleX = 1f
            imageView.scaleY = 1f
            hintView.text = "下拉刷新"
        }
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addOnLayoutChangeListener(onLayoutChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        heartAnimationDrawable.stop()
        removeOnLayoutChangeListener(onLayoutChangeListener)
    }

    private fun onLayoutChanged(newHeight: Int) {
        if (newHeight <= canRefreshHeight) {
            val scale = newHeight.toFloat() / canRefreshHeight
            imageView.scaleX = scale
            imageView.scaleY = scale
            imageView.alpha = scale
        }
    }

}