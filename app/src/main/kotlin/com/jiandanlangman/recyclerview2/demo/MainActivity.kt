package com.jiandanlangman.recyclerview2.demo

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
        for(i in 0 until 100)
            tempDatas.add("这是一条纯文本的ITEM")
        val adapter = Adapter()

        val recyclerView = findViewById<RecyclerView2>(R.id.recyclerView)
        recyclerView.layoutManager = StaggeredGridLayoutManager( 2, RecyclerView.VERTICAL)
        recyclerView.setOnLoadStatusChangedListener {
                recyclerView.postDelayed({
                    if(it == LoadStatus.STATUS_REFRESHING) {
                        datas.clear()
                        datas.addAll(tempDatas)
                    } else {
                        for (i in 0 until 100)
                            datas.add("这是一条纯文本的ITEM")
                    }
                    adapter.notifyDataSetChanged()
                    recyclerView.setLoadStatus(LoadStatus.STATUS_NORMAL)
                }, 400)
        }
       datas.addAll(tempDatas)
        recyclerView.adapter = adapter
    }

    private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView = itemView as TextView
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
