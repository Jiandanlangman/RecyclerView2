package com.jiandanlangman.recyclerview2.defaultimpl

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.jiandanlangman.recyclerview2.IHeaderView
import com.jiandanlangman.recyclerview2.LoadStatus
import com.jiandanlangman.recyclerview2.R

class DefaultHeaderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), IHeaderView {

    private val canRefreshHeight = (resources.displayMetrics.density * 44 + .5f).toInt()
    private val viewMaxHeight = (resources.displayMetrics.density * 128 + .5f).toInt()
    private val viewMinHeight = 1
    private val iconView: ImageView
    private val hintView: TextView
    private val loadingProgressBar: View
    private val loadingHintView: View
    private var loadStatus: LoadStatus? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.recyclerview2_default_header_view, this, true)
        iconView = findViewById(R.id.icon)
        hintView = findViewById(R.id.hint)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        loadingHintView = findViewById(R.id.loadingHint)
    }

    override fun getView() = this

    override fun getCanRefreshHeight() = canRefreshHeight

    override fun getViewMaxHeight() = viewMaxHeight

    override fun getViewMinHeight() = viewMinHeight

    override fun onCanRefreshStatusChanged(canRefresh: Boolean) {
        loadStatus = null
        if (canRefresh) {
            hintView.text = "松开刷新"
            iconView.animate().cancel()
            iconView.animate().rotation(-180f).setInterpolator(DecelerateInterpolator())
                    .setDuration(200).start()
            iconView.visibility = View.VISIBLE
            hintView.visibility = View.VISIBLE
        } else {
            hintView.text = "下拉刷新"
            iconView.animate().cancel()
            iconView.animate().rotation(0f).setInterpolator(DecelerateInterpolator())
                    .setDuration(200).start()
        }
        iconView.setImageResource(R.drawable.recyclerview2_arrow)
        iconView.visibility = View.VISIBLE
        hintView.visibility = View.VISIBLE
        loadingProgressBar.visibility = View.GONE
        loadingHintView.visibility = View.GONE
    }

    override fun onLoadStatusChanged(status: LoadStatus) {
        if (status != loadStatus) {
            this.loadStatus = status
            iconView.animate().cancel()
            iconView.rotation = 0f
            when (status) {
                LoadStatus.STATUS_REFRESHING -> {
                    iconView.visibility = View.GONE
                    hintView.visibility = View.GONE
                    loadingProgressBar.visibility = View.VISIBLE
                    loadingHintView.visibility = View.VISIBLE
                }
                LoadStatus.STATUS_LOAD_FAILED -> {
                    hintView.text = "刷新失败！"
                    iconView.setImageResource(R.drawable.error)
                    iconView.visibility = View.VISIBLE
                    hintView.visibility = View.VISIBLE
                    loadingProgressBar.visibility = View.GONE
                    loadingHintView.visibility = View.GONE
                }
                LoadStatus.STATUS_NO_MORE_DATA, LoadStatus.STATUS_NORMAL -> {
                    hintView.text = "刷新成功！"
                    iconView.setImageResource(R.drawable.smile)
                    iconView.visibility = View.VISIBLE
                    hintView.visibility = View.VISIBLE
                    loadingProgressBar.visibility = View.GONE
                    loadingHintView.visibility = View.GONE
                }
                else -> {
                    iconView.setImageResource(R.drawable.recyclerview2_arrow)
                    hintView.text = "下拉刷新"
                    iconView.visibility = View.VISIBLE
                    hintView.visibility = View.VISIBLE
                    loadingProgressBar.visibility = View.GONE
                    loadingHintView.visibility = View.GONE
                }
            }
        }
    }

}