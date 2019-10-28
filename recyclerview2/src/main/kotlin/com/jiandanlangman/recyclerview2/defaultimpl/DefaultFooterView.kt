package com.jiandanlangman.recyclerview2.defaultimpl

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.jiandanlangman.recyclerview2.*

class DefaultFooterView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), IActionView {


    private val progressBar: ProgressBar
    private val hintView: TextView

    private lateinit var recyclerView: RecyclerView2

    private var loadStatus: LoadStatus? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.recyclerview2_default_footer_view, this, true)
        progressBar = findViewById(R.id.progress)
        hintView = findViewById(R.id.hint)
        findViewById<View>(R.id.contentLayout).setOnClickListener {
            if (LoadStatus.STATUS_LOAD_FAILED == loadStatus) {
                recyclerView.setLoadStatus(LoadStatus.STATUS_LOADING_MORE)
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
            if (recyclerView.adapter?.itemCount ?: 0 != 0) {
                when (loadStatus) {
                    LoadStatus.STATUS_LOADING_MORE -> {
                        hintView.text = "正在加载数据，请稍后..."
                        progressBar.visibility = View.VISIBLE
                        hintView.visibility = View.VISIBLE
                    }
                    LoadStatus.STATUS_NO_MORE_DATA ->  {
                        hintView.text = "没有更多了哦~"
                        progressBar.visibility = View.GONE
                        hintView.visibility = View.VISIBLE
                    }
                    LoadStatus.STATUS_LOAD_FAILED -> {
                        hintView.text = "加载失败，点击此处重新加载!"
                        progressBar.visibility = View.GONE
                        hintView.visibility = View.VISIBLE
                    }
                    else -> {
                        hintView.text = ""
                        progressBar.visibility = View.GONE
                        hintView.visibility = View.GONE
                    }
                }
            } else {
                hintView.text = ""
                progressBar.visibility = View.GONE
                hintView.visibility = View.GONE
            }
        }
    }


}