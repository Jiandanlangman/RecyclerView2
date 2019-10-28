package com.jiandanlangman.recyclerview2

import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Scroller
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.jiandanlangman.recyclerview2.defaultimpl.DefaultEmptyView
import com.jiandanlangman.recyclerview2.defaultimpl.DefaultFooterView
import com.jiandanlangman.recyclerview2.defaultimpl.DefaultHeaderView
import java.lang.Exception
import kotlin.math.abs

class RecyclerView2 @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RecyclerView(context, attrs, defStyleAttr) {

    private companion object {
        private const val ITEM_VIEW_TYPE_EMPTY = Int.MIN_VALUE
        private const val ITEM_VIEW_TYPE_HEADER = ITEM_VIEW_TYPE_EMPTY + 1
        private const val ITEM_VIEW_TYPE_FOOTER = ITEM_VIEW_TYPE_HEADER + 1
        private const val PRELOAD_DEFAULT_OFFSET = 6
        private const val FAST_SCROLL_TO_TOP_DEFAULT_DURATION = 0
    }

    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop / 2
    private val internalAdapter = InternalAdapter()
    private val externalAdapterDataObserver: AdapterDataObserver
    private val emptyViewLayoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
    private val headerViewLayoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
    private val footerViewLayoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
    private val scroller = Scroller(context, AccelerateDecelerateInterpolator())

    private var onLoadStatusChangedListener: (LoadStatus) -> Unit = {}
    private var loadStatus = LoadStatus.STATUS_NORMAL
    private var isEnablePullToRefresh = true
    private var isEnableLoadMore = true
    private var touchEventPrevY = 0f
    private var isMoved = false
    private var preloadOffset = PRELOAD_DEFAULT_OFFSET
    private var emptyView: IActionView = DefaultEmptyView(context)
    private var headerView: IHeaderView = DefaultHeaderView(context)
    private var footerView: IActionView = DefaultFooterView(context)
    private var fastScrollToTopCompleteListener: () -> Unit = {}
    private var fastScrolledY = 0
    private var isFastScrolling = false

    private var externalAdapter: Adapter<ViewHolder>? = null
    private var staggeredGridLayoutManagerLastVisiblePositions: IntArray? = null


    init {
        super.setAdapter(internalAdapter)
        externalAdapterDataObserver = object : AdapterDataObserver() {
            override fun onChanged() = internalAdapter.notifyDataSetChanged()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = internalAdapter.notifyItemRangeRemoved(positionStart + 1, itemCount)
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = internalAdapter.notifyItemMoved(fromPosition + 1, toPosition + 1) //TODO 这里似乎有点问题
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = internalAdapter.notifyItemRangeInserted(positionStart + 1, itemCount)
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = internalAdapter.notifyItemRangeChanged(positionStart + 1, itemCount)
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = internalAdapter.notifyItemRangeChanged(positionStart + 1, itemCount, payload)
        }
        headerViewLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        emptyView.onBindToRecyclerView(this, emptyViewLayoutParams)
        headerView.onBindToRecyclerView(this, headerViewLayoutParams)
        footerView.onBindToRecyclerView(this, footerViewLayoutParams)
        try {
            val field = this::class.java.superclass!!.getDeclaredField("mMaxFlingVelocity")
            field.isAccessible = true
            field.set(this, Int.MAX_VALUE)
        } catch (ignore: Exception) {

        }
    }


    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isFastScrolling) {
            isFastScrolling = false
            scroller.forceFinished(true)
            stopScroll()
        }
        val y = ev.y
        if (isEnablePullToRefresh && loadStatus != LoadStatus.STATUS_REFRESHING && loadStatus != LoadStatus.STATUS_LOADING_MORE)
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> isMoved = false
                MotionEvent.ACTION_MOVE -> if (isEmptyExternalAdapter())
                    return true
                else if (isTop()) {
                    val dy = y - touchEventPrevY
                    if (!isMoved)
                        isMoved = abs(dy) >= scaledTouchSlop
                    if (isMoved && headerView.onPullingDown(dy)) {
                        touchEventPrevY = y
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (isMoved && isTop()) {
                    headerView.onEndPullDown()
                    return true
                }
            }
        touchEventPrevY = y
        return super.dispatchTouchEvent(ev)
    }


    override fun setAdapter(adapter: Adapter<*>?) {
        externalAdapter?.unregisterAdapterDataObserver(externalAdapterDataObserver)
        externalAdapter = adapter as Adapter<ViewHolder>?
        externalAdapter?.registerAdapterDataObserver(externalAdapterDataObserver)
        internalAdapter.notifyDataSetChanged()
    }

    override fun getAdapter(): Adapter<*>? {
        return externalAdapter
    }

    override fun setLayoutManager(layout: LayoutManager?) = when (layout) {
        null -> super.setLayoutManager(layout)
        is GridLayoutManager -> {
            if (VERTICAL != layout.orientation)
                throw RuntimeException("Only LinearLayoutManager, GridLayoutManager, StaggeredGridLayoutManager can be set, and the direction must be RecyclerView.VERTICAL")
            val spanCount = layout.spanCount
            val externalSpanSizeLookup = layout.spanSizeLookup
            layout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (internalAdapter.getItemViewType(position) == ITEM_VIEW_TYPE_EMPTY || internalAdapter.getItemViewType(position) == ITEM_VIEW_TYPE_HEADER || internalAdapter.getItemViewType(position) == ITEM_VIEW_TYPE_FOOTER) spanCount else externalSpanSizeLookup?.getSpanSize(position)
                        ?: 1
            }
            super.setLayoutManager(layout)
        }
        is StaggeredGridLayoutManager -> {
            if (VERTICAL != layout.orientation)
                throw RuntimeException("Only LinearLayoutManager, GridLayoutManager, StaggeredGridLayoutManager can be set, and the direction must be RecyclerView.VERTICAL")
            staggeredGridLayoutManagerLastVisiblePositions = IntArray(layout.spanCount)
            super.setLayoutManager(layout)
        }
        is LinearLayoutManager -> {
            if (VERTICAL != layout.orientation)
                throw RuntimeException("Only LinearLayoutManager, GridLayoutManager, StaggeredGridLayoutManager can be set, and the direction must be RecyclerView.VERTICAL")
            super.setLayoutManager(layout)
        }
        else -> throw RuntimeException("Only LinearLayoutManager, GridLayoutManager, StaggeredGridLayoutManager can be set, and the direction must be RecyclerView.VERTICAL")
    }


    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        if (isEnableLoadMore && dy > 0 && loadStatus != LoadStatus.STATUS_REFRESHING && loadStatus != LoadStatus.STATUS_LOADING_MORE && loadStatus != LoadStatus.STATUS_NO_MORE_DATA && !isEmptyExternalAdapter()) {
            val lm = layoutManager
            val lastCompletelyVisibleItemPosition =
                    if (lm is LinearLayoutManager) lm.findLastCompletelyVisibleItemPosition() else {
                        (lm as StaggeredGridLayoutManager).findLastCompletelyVisibleItemPositions(staggeredGridLayoutManagerLastVisiblePositions)
                        staggeredGridLayoutManagerLastVisiblePositions?.max() ?: -1
                    }
            if (lastCompletelyVisibleItemPosition >= internalAdapter.itemCount - 1 - preloadOffset) {
                loadStatus = LoadStatus.STATUS_LOADING_MORE
                if (isBottom())
                    footerView.onRecyclerViewLoadStatusChanged(loadStatus)
                notifyLoadStatusChanged()
            }
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollBy(0, scroller.currY - fastScrolledY)
            fastScrolledY = scroller.currY
        } else if (isFastScrolling) {
            isFastScrolling = false
            scrollToPosition(0)
            fastScrollToTopCompleteListener.invoke()
        }
    }

    fun setOnLoadStatusChangedListener(listener: (status: LoadStatus) -> Unit) {
        onLoadStatusChangedListener = listener
    }


    fun setLoadStatus(status: LoadStatus) {
        if (loadStatus != status) {
            loadStatus = status
            emptyView.onRecyclerViewLoadStatusChanged(status)
            headerView.onRecyclerViewLoadStatusChanged(status)
            footerView.onRecyclerViewLoadStatusChanged(status)
            post { autoLoadMore() }
        }
    }

    fun setPreloadOffset(offset: Int) {
        preloadOffset = offset
    }

    fun setEnablePullToRefresh(enabled: Boolean) {
        isEnablePullToRefresh = enabled
    }

    fun setEnableLoadMore(enabled: Boolean) {
        isEnableLoadMore = enabled
    }

    fun setEmptyView(emptyView: IActionView) {
//        val oldEmptyView = this.emptyView.getView()
//        if (oldEmptyView.parent != null)
//            (oldEmptyView.parent as ViewGroup).removeView(oldEmptyView)
//        this.emptyView = emptyView
//        internalAdapter.notifyItemChanged(0, INTERNAL_PAYLOAD)
    }

    fun setHeaderView(headerView: IHeaderView) {
//        val oldHeaderView = this.headerView.getView()
//        if (oldHeaderView.parent != null)
//            (oldHeaderView.parent as ViewGroup).removeView(oldHeaderView)
//        this.headerView = headerView
//        internalAdapter.notifyItemChanged(0, INTERNAL_PAYLOAD)
    }

    fun setFooterView(footerView: IActionView) {
//        val oldFooterView = this.footerView.getView()
//        if (oldFooterView.parent != null)
//            (oldFooterView.parent as ViewGroup).removeView(oldFooterView)
//        this.footerView = footerView
//        internalAdapter.notifyItemChanged(internalAdapter.itemCount - 1, INTERNAL_PAYLOAD)
    }

    fun setFastScrollToTopCompleteListener(listener: () -> Unit) {
        fastScrollToTopCompleteListener = listener
    }

    fun fastScrollToTop(duration: Int) {
        stopScroll()
        isFastScrolling = true
        fastScrolledY = 0
        scroller.startScroll(0, 0, 0, -computeVerticalScrollOffset(), duration)
        invalidate()
    }

    fun fastScrollToTop() = fastScrollToTop(FAST_SCROLL_TO_TOP_DEFAULT_DURATION)


    internal fun notifyLoadStatusChanged() = onLoadStatusChangedListener.invoke(loadStatus)

    private fun isTop() = headerView.getView().parent != null

    private fun isBottom() = footerView.getView().parent != null

    private fun isEmptyExternalAdapter() = internalAdapter.itemCount == 2

    private fun autoLoadMore() {
        if (isEnableLoadMore && loadStatus == LoadStatus.STATUS_NORMAL && isBottom() && !isEmptyExternalAdapter()) {
            loadStatus = LoadStatus.STATUS_LOADING_MORE
            notifyLoadStatusChanged()
            footerView.onRecyclerViewLoadStatusChanged(loadStatus)
        }
    }


    private inner class InternalAdapter : RecyclerView.Adapter<ViewHolder>() {

        override fun getItemId(position: Int) = when (getItemViewType(position)) {
            ITEM_VIEW_TYPE_EMPTY, ITEM_VIEW_TYPE_HEADER, ITEM_VIEW_TYPE_FOOTER -> NO_ID
            else -> externalAdapter!!.getItemId(position - 1)
        }

        override fun onFailedToRecycleView(holder: ViewHolder) = if (holder is InternalViewHolder) super.onFailedToRecycleView(holder) else externalAdapter!!.onFailedToRecycleView(holder)

        override fun onViewRecycled(holder: ViewHolder) = if (holder is InternalViewHolder) super.onViewRecycled(holder) else externalAdapter!!.onViewRecycled(holder)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
            ITEM_VIEW_TYPE_EMPTY -> EmptyViewHolder()
            ITEM_VIEW_TYPE_HEADER -> HeaderViewHolder()
            ITEM_VIEW_TYPE_FOOTER -> FooterViewHolder()
            else -> externalAdapter!!.onCreateViewHolder(parent, viewType)
        }

        override fun getItemCount() = if (externalAdapter == null) 0 else externalAdapter!!.itemCount + 2

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            when (holder) {
                is InternalViewHolder -> holder.update()
                else -> super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = externalAdapter!!.onBindViewHolder(holder, position - 1)

        override fun onViewAttachedToWindow(holder: ViewHolder) {
            super.onViewAttachedToWindow(holder)
            if (holder is InternalViewHolder) {
                val view: View = when (holder) {
                    is EmptyViewHolder -> emptyView.getView()
                    is HeaderViewHolder -> headerView.getView()
                    else -> footerView.getView()
                }
                if (view.parent != null)
                    (view.parent as ViewGroup).removeView(view)
                (holder.itemView as ConstraintLayout).addView(view)
                if (layoutManager is StaggeredGridLayoutManager)
                    (holder.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
            } else
                externalAdapter!!.onViewAttachedToWindow(holder)
        }

        override fun onViewDetachedFromWindow(holder: ViewHolder) {
            super.onViewDetachedFromWindow(holder)
            when (holder) {
                is InternalViewHolder -> (holder.itemView as ConstraintLayout).removeAllViews()
                else -> externalAdapter!!.onViewDetachedFromWindow(holder)
            }
        }

        override fun getItemViewType(position: Int) = when (position) {
            0 -> if (isEmptyExternalAdapter()) ITEM_VIEW_TYPE_EMPTY else ITEM_VIEW_TYPE_HEADER
            itemCount - 1 -> ITEM_VIEW_TYPE_FOOTER
            else -> {
                val itemViewType = externalAdapter!!.getItemViewType(position - 1)
                if (itemViewType == ITEM_VIEW_TYPE_HEADER || itemViewType == ITEM_VIEW_TYPE_FOOTER)
                    throw RuntimeException("The value of ItemViewType cannot be $ITEM_VIEW_TYPE_EMPTY, $ITEM_VIEW_TYPE_HEADER, $ITEM_VIEW_TYPE_FOOTER")
                itemViewType
            }
        }

    }

    private abstract inner class InternalViewHolder : ViewHolder(ConstraintLayout(context)) {

        init {
            itemView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        abstract fun update()

    }

    private inner class EmptyViewHolder : InternalViewHolder() {

        override fun update() = emptyView.onRecyclerViewLoadStatusChanged(loadStatus)

    }

    private inner class HeaderViewHolder : InternalViewHolder() {

        override fun update() = headerView.onRecyclerViewLoadStatusChanged(loadStatus)

    }

    private inner class FooterViewHolder : InternalViewHolder() {

        override fun update() = footerView.onRecyclerViewLoadStatusChanged(loadStatus)

    }

}

