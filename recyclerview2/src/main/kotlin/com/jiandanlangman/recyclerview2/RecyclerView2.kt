package com.jiandanlangman.recyclerview2

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.jiandanlangman.recyclerview2.defaultimpl.DefaultEmptyView
import com.jiandanlangman.recyclerview2.defaultimpl.DefaultFooterView
import com.jiandanlangman.recyclerview2.defaultimpl.DefaultHeaderView

class RecyclerView2 @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RecyclerView(context, attrs, defStyleAttr) {

    private companion object {
        private const val ITEM_VIEW_TYPE_EMPTY = Int.MIN_VALUE
        private const val ITEM_VIEW_TYPE_HEADER = ITEM_VIEW_TYPE_EMPTY + 1
        private const val ITEM_VIEW_TYPE_FOOTER = ITEM_VIEW_TYPE_HEADER + 1
        private const val PRELOAD_DEFAULT_OFFSET = 6
        private const val INTERNAL_PAYLOAD = "internalPayload"
        private const val FAST_SCROLL_TO_TOP_DEFAULT_DURATION = 400
    }

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
    private var preloadOffset = PRELOAD_DEFAULT_OFFSET
    private var emptyView: IEmptyView = DefaultEmptyView(context)
    private var headerView: IHeaderView = DefaultHeaderView(context)
    private var canRefreshStatus = -1
    private var isWaitingHeaderViewReady = false
    private var footerView: IFooterView = DefaultFooterView(context)
    private var fastScrollToTopCompleteListener: () -> Unit = {}
    private var fastScrolledY = 0
    private var isFastScrolling = false

    private var externalAdapter: Adapter<ViewHolder>? = null
    private var headerViewHolder: HeaderViewHolder? = null
    private var setHeaderViewHolderHeightAnimator: ValueAnimator? = null
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
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        internalAdapter.notifyItemChanged(0, INTERNAL_PAYLOAD)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isFastScrolling) {
            isFastScrolling = false
            scroller.forceFinished(true)
            stopScroll()
        }
        if (!isEnablePullToRefresh || loadStatus == LoadStatus.STATUS_REFRESHING || loadStatus == LoadStatus.STATUS_LOADING_MORE || isWaitingHeaderViewReady || setHeaderViewHolderHeightAnimator != null)
            return super.dispatchTouchEvent(ev)
        val y = ev.y
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> canRefreshStatus = -1
            MotionEvent.ACTION_MOVE -> if (isEmptyExternalAdapter())
                return true
            else if (isTop()) {
                val offset = y - touchEventPrevY
                if (offset > 0 || headerViewHolder!!.itemView.layoutParams.height > headerView.getViewMinHeight()) {
                    val paramsHeight = headerViewHolder!!.itemView.layoutParams.height
                    var h = when {
                        offset <= 0 -> (paramsHeight + offset).toInt()
                        paramsHeight <= headerView.getCanRefreshHeight() -> (paramsHeight + offset * .32 + .5f).toInt()
                        else -> (paramsHeight + offset * .12 + .5f).toInt()
                    }
                    if (h < 0)
                        h = 0
                    else if (h > headerView.getViewMaxHeight())
                        h = headerView.getViewMaxHeight()
                    val params = headerViewHolder!!.itemView.layoutParams
                    params.height = h
                    headerViewHolder!!.itemView.layoutParams = params
                    val status = if (h >= headerView.getCanRefreshHeight()) 1 else 0
                    if (status != canRefreshStatus) {
                        canRefreshStatus = status
                        headerView.onCanRefreshStatusChanged(canRefreshStatus == 1)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (isTop() && headerViewHolder!!.itemView.layoutParams.height > headerView.getViewMinHeight()) {
                if (canRefreshStatus == 1) {
                    headerView.onLoadStatusChanged(LoadStatus.STATUS_REFRESHING)
                    loadStatus = LoadStatus.STATUS_REFRESHING
                    notifyLoadStatusChanged()
                }
                animateHeaderViewHolderHeight(if (loadStatus == LoadStatus.STATUS_REFRESHING) headerView.getCanRefreshHeight() else headerView.getViewMinHeight())
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
        if (isEnableLoadMore && dy > 0 && loadStatus != LoadStatus.STATUS_REFRESHING && loadStatus != LoadStatus.STATUS_LOADING_MORE && !isEmptyExternalAdapter()) {
            val lm = layoutManager
            val lastCompletelyVisibleItemPosition = if (lm is LinearLayoutManager) lm.findLastCompletelyVisibleItemPosition() else {
                (lm as StaggeredGridLayoutManager).findLastCompletelyVisibleItemPositions(staggeredGridLayoutManagerLastVisiblePositions)
                staggeredGridLayoutManagerLastVisiblePositions?.max() ?: -1
            }
            if (lastCompletelyVisibleItemPosition >= internalAdapter.itemCount - 1 - preloadOffset) {
                loadStatus = LoadStatus.STATUS_LOADING_MORE
                if (isBottom())
                    post { internalAdapter.notifyItemChanged(internalAdapter.itemCount - 1, INTERNAL_PAYLOAD) }
                notifyLoadStatusChanged()
            }
        }
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        if (state == SCROLL_STATE_IDLE && isFastScrolling) {
            isFastScrolling = false
            if (!isTop())
                scrollToPosition(0)
            fastScrollToTopCompleteListener.invoke()
        }
    }


    fun setOnLoadStatusChangedListener(listener: (status: LoadStatus) -> Unit) {
        onLoadStatusChangedListener = listener
    }


    fun setLoadStatus(status: LoadStatus) = post {
        if (loadStatus != status) {
            val prevStatus = loadStatus
            loadStatus = status
            internalAdapter.notifyItemChanged(0, INTERNAL_PAYLOAD)
            internalAdapter.notifyItemChanged(internalAdapter.itemCount - 1, INTERNAL_PAYLOAD)
            if (isEnablePullToRefresh && prevStatus == LoadStatus.STATUS_REFRESHING && isTop()) {
                isWaitingHeaderViewReady = true
                postDelayed({
                    animateHeaderViewHolderHeight(headerView.getViewMinHeight())
                    isWaitingHeaderViewReady = false
                    autoLoadMore()
                }, 800)
            } else {
                isWaitingHeaderViewReady = false
                autoLoadMore()
            }
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

    fun setEmptyView(emptyView: IEmptyView) {
        val oldEmptyView = this.emptyView.getView()
        if (oldEmptyView.parent != null)
            (oldEmptyView.parent as ViewGroup).removeView(oldEmptyView)
        this.emptyView = emptyView
        internalAdapter.notifyItemChanged(0, INTERNAL_PAYLOAD)
    }

    fun setHeaderView(headerView: IHeaderView) {
        val oldHeaderView = this.headerView.getView()
        if (oldHeaderView.parent != null)
            (oldHeaderView.parent as ViewGroup).removeView(oldHeaderView)
        this.headerView = headerView
        internalAdapter.notifyItemChanged(0, INTERNAL_PAYLOAD)
    }

    fun setFooterView(footerView: IFooterView) {
        val oldFooterView = this.footerView.getView()
        if (oldFooterView.parent != null)
            (oldFooterView.parent as ViewGroup).removeView(oldFooterView)
        this.footerView = footerView
        internalAdapter.notifyItemChanged(internalAdapter.itemCount - 1, INTERNAL_PAYLOAD)
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

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollBy(0, scroller.currY - fastScrolledY)
            fastScrolledY = scroller.currY
        }
    }


    internal fun notifyLoadStatusChanged() = onLoadStatusChangedListener.invoke(loadStatus)

    private fun isTop() = headerView.getView().parent != null

    private fun isBottom() = footerView.getView().parent != null

    private fun isEmptyExternalAdapter() = internalAdapter.itemCount == 2

    private fun animateHeaderViewHolderHeight(height: Int) {
        if (headerViewHolder != null) {
            setHeaderViewHolderHeightAnimator?.cancel()
            setHeaderViewHolderHeightAnimator = ValueAnimator.ofInt(headerViewHolder!!.itemView.height, height)
            setHeaderViewHolderHeightAnimator!!.duration = 200
            setHeaderViewHolderHeightAnimator!!.interpolator = DecelerateInterpolator()
            setHeaderViewHolderHeightAnimator!!.addUpdateListener {
                if (headerViewHolder != null) {
                    val animatedValue = it.animatedValue as Int
                    val params = headerViewHolder!!.itemView.layoutParams
                    params.height = animatedValue
                    headerViewHolder!!.itemView.layoutParams = params
                    if (animatedValue == height)
                        setHeaderViewHolderHeightAnimator = null
                } else {
                    setHeaderViewHolderHeightAnimator!!.cancel()
                    setHeaderViewHolderHeightAnimator = null
                }
            }
            setHeaderViewHolderHeightAnimator!!.start()
        }
    }

    private fun autoLoadMore() {
        if (isEnableLoadMore && loadStatus == LoadStatus.STATUS_NORMAL && isBottom() && !isEmptyExternalAdapter()) {
            loadStatus = LoadStatus.STATUS_LOADING_MORE
            notifyLoadStatusChanged()
            internalAdapter.notifyItemChanged(internalAdapter.itemCount - 1, INTERNAL_PAYLOAD)
        }
    }


    private inner class InternalAdapter : RecyclerView.Adapter<ViewHolder>() {

        override fun getItemId(position: Int) = when (getItemViewType(position)) {
            ITEM_VIEW_TYPE_EMPTY -> ITEM_VIEW_TYPE_EMPTY.toLong()
            ITEM_VIEW_TYPE_HEADER -> ITEM_VIEW_TYPE_HEADER.toLong()
            ITEM_VIEW_TYPE_FOOTER -> ITEM_VIEW_TYPE_FOOTER.toLong()
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
                else -> externalAdapter!!.onBindViewHolder(holder, position - 1, payloads)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = externalAdapter!!.onBindViewHolder(holder, position)

        override fun onViewAttachedToWindow(holder: ViewHolder) {
            super.onViewAttachedToWindow(holder)
            if (holder is InternalViewHolder) {
                val view: View
                val viewParams: ConstraintLayout.LayoutParams
                when (holder) {
                    is EmptyViewHolder -> {
                        view = emptyView.getView()
                        viewParams = emptyViewLayoutParams
                    }
                    is HeaderViewHolder -> {
                        headerViewHolder = holder
                        view = headerView.getView()
                        viewParams = headerViewLayoutParams
                        viewParams.height = headerView.getCanRefreshHeight()
                    }
                    else -> {
                        view = footerView.getView()
                        viewParams = footerViewLayoutParams
                        viewParams.height = footerView.getViewMaxHeight()
                    }
                }
                if (view.parent != null)
                    (view.parent as ViewGroup).removeView(view)
                (holder.itemView as ConstraintLayout).addView(view, viewParams)
                if (layoutManager is StaggeredGridLayoutManager)
                    (holder.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
            } else
                externalAdapter!!.onViewAttachedToWindow(holder)
        }

        override fun onViewDetachedFromWindow(holder: ViewHolder) {
            super.onViewDetachedFromWindow(holder)
            when (holder) {
                is InternalViewHolder -> {
                    (holder.itemView as ConstraintLayout).removeAllViews()
                    if (holder == headerViewHolder)
                        headerViewHolder = null
                }
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

        override fun update() {
            emptyView.onLoadStatusChanged(loadStatus)
            val height = this@RecyclerView2.height - footerView.getViewMinHeight()
            val params = itemView.layoutParams
            if (params.height != height) {
                params.height = height
                itemView.layoutParams = params
            }
        }

    }

    private inner class HeaderViewHolder : InternalViewHolder() {

        override fun update() {
            headerView.onLoadStatusChanged(loadStatus)
            if (setHeaderViewHolderHeightAnimator == null) {
                val params = itemView.layoutParams
                val height = if (isEnablePullToRefresh && (loadStatus == LoadStatus.STATUS_REFRESHING || (isWaitingHeaderViewReady && (loadStatus == LoadStatus.STATUS_NORMAL || loadStatus == LoadStatus.STATUS_NO_MORE_DATA || loadStatus == LoadStatus.STATUS_LOAD_FAILED))))
                    headerView.getCanRefreshHeight()
                else
                    headerView.getViewMinHeight()
                if (params.height != height) {
                    params.height = height
                    itemView.layoutParams = params
                }
            }
        }

    }

    private inner class FooterViewHolder : InternalViewHolder() {

        override fun update() {
            val params = itemView.layoutParams
            var height = footerView.getViewMinHeight()
            if (!isEmptyExternalAdapter()) {
                footerView.onLoadStatusChanged(loadStatus)
                height = if (isEnableLoadMore && (loadStatus == LoadStatus.STATUS_LOADING_MORE || loadStatus == LoadStatus.STATUS_NO_MORE_DATA || loadStatus == LoadStatus.STATUS_LOAD_FAILED))
                    footerView.getViewMaxHeight()
                else
                    footerView.getViewMinHeight()
            }
            if (params.height != height) {
                params.height = height
                itemView.layoutParams = params
            }
        }

    }

}

