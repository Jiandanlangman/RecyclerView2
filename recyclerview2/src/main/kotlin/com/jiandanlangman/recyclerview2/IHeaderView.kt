package com.jiandanlangman.recyclerview2

interface IHeaderView : IActionView {

    fun onPullingDown(dy:Float) : Boolean

    fun onEndPullDown()

}