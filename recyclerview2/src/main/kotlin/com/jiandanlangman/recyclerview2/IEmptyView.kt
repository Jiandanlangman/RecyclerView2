package com.jiandanlangman.recyclerview2

import android.view.View

interface IEmptyView {
    fun getView(): View
    fun onLoadStatusChanged(status: LoadStatus)
}