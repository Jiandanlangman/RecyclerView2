package com.jiandanlangman.recyclerview2

import android.view.View

interface IFooterView  {
    fun getView(): View
    fun onLoadStatusChanged(status: LoadStatus)
    fun getViewMaxHeight(): Int
    fun getViewMinHeight(): Int
}