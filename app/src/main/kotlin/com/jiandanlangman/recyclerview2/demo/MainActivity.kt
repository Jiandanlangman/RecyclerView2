package com.jiandanlangman.recyclerview2.demo

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.jiandanlangman.recyclerview2.LoadStatus
import com.jiandanlangman.recyclerview2.RecyclerView2

class MainActivity : AppCompatActivity() {

    private val datas = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
    }

    private fun initView() {
        val tempDatas = ArrayList<String>()
        for(i in 0 until 13)
            tempDatas.add("这是一条纯文本的ITEM")
        val adapter = Adapter()

        val recyclerView = findViewById<RecyclerView2>(R.id.recyclerView)
        val layotManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layotManager
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            val padding = (resources.displayMetrics.density * 12 + .5f).toInt()
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.set(padding,  padding, padding, 0)
            }
        })
        recyclerView.setOnLoadStatusChangedListener {
                recyclerView.postDelayed({
                    if(it == LoadStatus.STATUS_REFRESHING) {
                        datas.clear()
                        datas.addAll(tempDatas)
                        adapter.notifyDataSetChanged()
                        recyclerView.setLoadStatus(LoadStatus.STATUS_NORMAL)
                    } else {
//                        for (i in 0 until 100)
//                            datas.add("这是一条纯文本的ITEM")
                        adapter.notifyDataSetChanged()
                        recyclerView.setLoadStatus(LoadStatus.STATUS_NO_MORE_DATA)
                    }

                }, 2000)
        }
//        datas.addAll(tempDatas)
        recyclerView.adapter = adapter
        recyclerView.setLoadStatus(LoadStatus.STATUS_REFRESHING)
        recyclerView.postDelayed({
            datas.addAll(tempDatas)
            adapter.notifyDataSetChanged()
            recyclerView.setLoadStatus(LoadStatus.STATUS_NORMAL)
        }, 1000)
//        recyclerView.setLoadStatus(LoadStatus.STATUS_NO_MORE_DATA)
//        val padding = (resources.displayMetrics.density * 48f + .5f).toInt()
//        recyclerView.setHeaderViewPadding(0, padding, 0, padding)
//        recyclerView.setLoadStatus(LoadStatus.STATUS_REFRESHING)
//        recyclerView.postDelayed({
////            datas.addAll(tempDatas)
////            adapter.notifyDataSetChanged()
//            recyclerView.setLoadStatus(LoadStatus.STATUS_NO_MORE_DATA)
//        }, 4000)
    }

    private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView = itemView as TextView

        init {
            textView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("哈ah").setMessage("打算东东大东adsa").create().show()
//                Toast.makeText(this@MainActivity, "onItemClick", Toast.LENGTH_SHORT).show()
                true
            }
            textView.setOnClickListener {
                Toast.makeText(this@MainActivity, "onItemClick", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private inner class Adapter:RecyclerView.Adapter<ViewHolder>() {

        private val inflater = LayoutInflater.from(this@MainActivity)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(inflater.inflate(R.layout.list_item, parent, false))

        override fun getItemCount() = datas.size

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = "${datas[position]}--------$position"
        }
    }

}
