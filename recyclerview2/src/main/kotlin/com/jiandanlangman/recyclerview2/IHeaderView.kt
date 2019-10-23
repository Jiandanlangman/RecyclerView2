package com.jiandanlangman.recyclerview2

import android.view.View

interface IHeaderView {
    fun getView(): View
    fun onLoadStatusChanged(status: LoadStatus)
    fun getViewMaxHeight(): Int
    fun getViewMinHeight(): Int
    fun getCanRefreshHeight(): Int
    fun onCanRefreshStatusChanged(canRefresh: Boolean)
}