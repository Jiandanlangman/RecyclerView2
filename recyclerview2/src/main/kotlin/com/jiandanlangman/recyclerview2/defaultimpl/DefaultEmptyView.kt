package com.jiandanlangman.recyclerview2.defaultimpl

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.jiandanlangman.recyclerview2.IEmptyView
import com.jiandanlangman.recyclerview2.LoadStatus
import com.jiandanlangman.recyclerview2.R
import com.jiandanlangman.recyclerview2.RecyclerView2

class DefaultEmptyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), IEmptyView {

    private val imageView: ImageView
    private val hintView: TextView
    private var loadStatus: LoadStatus? = null
    private var imageDrawable: AnimationDrawable? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.recyclerview2_default_empty_view, this, true)
        imageView = findViewById(R.id.image)
        hintView = findViewById(R.id.hint)
        findViewById<View>(R.id.contentLayout).setOnClickListener {
            if (LoadStatus.STATUS_NO_MORE_DATA == loadStatus || LoadStatus.STATUS_LOAD_FAILED == loadStatus) {
                val target = parent?.parent as? RecyclerView2
                if (target != null) {
                    target.setLoadStatus(LoadStatus.STATUS_REFRESHING)
                    target.post { target.notifyLoadStatusChanged() }
                }
            }
        }
    }

    override fun getView() = this

    override fun onLoadStatusChanged(status: LoadStatus) {
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
                    hintView.text = "加载失败，点击重试."
                }
                LoadStatus.STATUS_NO_MORE_DATA -> {
                    imageView.setImageResource(R.drawable.no_data)
                    hintView.text = ""
                }
                else -> {
                    imageDrawable = null
                    imageView.setImageBitmap(null)
                    hintView.text = ""
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        imageDrawable?.stop()
        imageDrawable = null
    }
}