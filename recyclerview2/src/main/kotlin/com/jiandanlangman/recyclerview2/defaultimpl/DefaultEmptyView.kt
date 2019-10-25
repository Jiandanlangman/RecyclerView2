package com.jiandanlangman.recyclerview2.defaultimpl

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.jiandanlangman.recyclerview2.*

class DefaultEmptyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), IActionView {


    private val onLayoutChangeListener =
            OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                onLayoutChanged(bottom - top)
            }

    private val imageView: ImageView
    private val hintView: TextView


    private lateinit var recyclerView: RecyclerView2

    private var loadStatus: LoadStatus? = null
    private var imageDrawable: AnimationDrawable? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.recyclerview2_default_empty_view, this, true)
        imageView = findViewById(R.id.image)
        hintView = findViewById(R.id.hint)
        findViewById<View>(R.id.contentLayout).setOnClickListener {
            if (LoadStatus.STATUS_NO_MORE_DATA == loadStatus || LoadStatus.STATUS_LOAD_FAILED == loadStatus) {
                recyclerView.setLoadStatus(LoadStatus.STATUS_REFRESHING)
                recyclerView.notifyLoadStatusChanged()
            }
        }
    }

    override fun getView() = this

    override fun onBindToRecyclerView(recyclerView: RecyclerView2, layoutParams: ViewGroup.LayoutParams) {
        this.recyclerView = recyclerView
        this.layoutParams = layoutParams
    }

    override fun onRecyclerViewLoadStatusChanged(status: LoadStatus) {
        if (status != loadStatus) {
            loadStatus = status
            imageDrawable?.stop()
            when (status) {
                LoadStatus.STATUS_REFRESHING -> {
                    imageDrawable = ContextCompat.getDrawable(context, R.drawable.large_loading_anim) as AnimationDrawable
                    imageView.setImageDrawable(imageDrawable)
                    imageDrawable!!.start()
                    hintView.text = "玩命加载中..."
                }
                LoadStatus.STATUS_LOAD_FAILED -> {
                    imageDrawable = ContextCompat.getDrawable(context, R.drawable.large_loading_fail_anim) as AnimationDrawable
                    imageView.setImageDrawable(imageDrawable)
                    imageDrawable!!.start()
                    hintView.text = "加载数据失败，点击刷新"
                }
                LoadStatus.STATUS_NO_MORE_DATA -> {
                    imageDrawable = ContextCompat.getDrawable(context, R.drawable.empty_anim) as AnimationDrawable
                    imageView.setImageDrawable(imageDrawable)
                    imageDrawable!!.start()
                    hintView.text = "暂时没有数据，点击刷新"
                }
                else -> {
                    imageView.setImageBitmap(null)
                    hintView.text = ""
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        recyclerView.addOnLayoutChangeListener(onLayoutChangeListener)
        imageDrawable?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recyclerView.removeOnLayoutChangeListener(onLayoutChangeListener)
        imageDrawable?.stop()
    }

    private fun onLayoutChanged(height: Int) {
        val params = layoutParams
        if (params.height != height) {
            params.height = height
            parent?.requestLayout()
        }
    }
}