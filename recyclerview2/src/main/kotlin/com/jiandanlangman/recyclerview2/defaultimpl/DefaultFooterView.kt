package com.jiandanlangman.recyclerview2.defaultimpl

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.jiandanlangman.recyclerview2.IFooterView
import com.jiandanlangman.recyclerview2.LoadStatus
import com.jiandanlangman.recyclerview2.R
import com.jiandanlangman.recyclerview2.RecyclerView2

class DefaultFooterView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), IFooterView {

    private val viewMaxHeight = (resources.displayMetrics.density * 36 + .5f).toInt()
    private val viewMinHeight = 1
    private val progressBar: ProgressBar
    private val hintView: TextView

    private var loadStatus: LoadStatus? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.recyclerview2_default_footer_view, this, true)
        progressBar = findViewById(R.id.progress)
        hintView = findViewById(R.id.hint)
        isFocusable = true
        isClickable = true
        setOnClickListener {
            if (LoadStatus.STATUS_LOAD_FAILED == loadStatus) {
                val target = parent?.parent as? RecyclerView2
                if (target != null) {
                    target.setLoadStatus(LoadStatus.STATUS_LOADING_MORE)
                    target.setLoadStatus(LoadStatus.STATUS_REFRESHING)
                    target.post { target.notifyLoadStatusChanged() }
                }
            }
        }
    }

    override fun getView() = this

    override fun getViewMaxHeight() = viewMaxHeight

    override fun getViewMinHeight() = viewMinHeight

    override fun onLoadStatusChanged(status: LoadStatus) {
        if (status != this.loadStatus) {
            this.loadStatus = status
            when (status) {
                LoadStatus.STATUS_NORMAL, LoadStatus.STATUS_REFRESHING -> {
                    progressBar.visibility = View.GONE
                    hintView.visibility = View.GONE
                }
                LoadStatus.STATUS_LOADING_MORE -> {
                    hintView.text = "正在加载数据，请稍后..."
                    progressBar.visibility = View.VISIBLE
                    hintView.visibility = View.VISIBLE
                }
                LoadStatus.STATUS_LOAD_FAILED -> {
                    hintView.text = "加载失败，点击此处重新加载!"
                    progressBar.visibility = View.GONE
                    hintView.visibility = View.VISIBLE
                }
                LoadStatus.STATUS_NO_MORE_DATA -> {
                    hintView.text = "没有更多了哦~"
                    progressBar.visibility = View.GONE
                    hintView.visibility = View.VISIBLE
                }
            }
        }
    }

}