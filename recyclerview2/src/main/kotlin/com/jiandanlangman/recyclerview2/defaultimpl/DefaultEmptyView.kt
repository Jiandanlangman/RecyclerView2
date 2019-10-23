package com.jiandanlangman.recyclerview2.defaultimpl

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.jiandanlangman.recyclerview2.IEmptyView
import com.jiandanlangman.recyclerview2.LoadStatus
import com.jiandanlangman.recyclerview2.R

class DefaultEmptyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr), IEmptyView {

    init {
        LayoutInflater.from(context).inflate(R.layout.recyclerview2_default_empty_view, this, true)
    }

    override fun getView() = this

    override fun onLoadStatusChanged(status: LoadStatus) {

    }
}