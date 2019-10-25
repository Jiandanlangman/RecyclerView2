package com.jiandanlangman.recyclerview2

import android.view.View
import android.view.ViewGroup

interface IActionView {

    fun getView(): View

    fun onBindToRecyclerView(recyclerView: RecyclerView2, layoutParams: ViewGroup.LayoutParams)

    fun onRecyclerViewLoadStatusChanged(status: LoadStatus)

}