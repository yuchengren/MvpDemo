package com.ycr.lib.ui.view.gridimage

import android.support.annotation.IdRes
import android.support.v7.widget.RecyclerView
import android.util.SparseArray
import android.view.View
import java.util.*

/**
 * Created by yuchengren on 2019/1/17.
 */
class BaseRecyclerHolder(var convertView: View): RecyclerView.ViewHolder(convertView) {
    val views: SparseArray<View> = SparseArray()
    val childClickViewIds = LinkedList<Int>()
    val childLongClickViewIds = LinkedList<Int>()

    lateinit var adapter: BaseRecyclerAdapter<*,*>

    fun <T: View?> getView(@IdRes viewResId: Int): T?{
        var view: View? = views.get(viewResId)
        if(view == null){
            view = convertView.findViewById(viewResId)
            views.put(viewResId,view)
        }
        return view as? T
    }

    fun addOnClickListener(@IdRes viewResId: Int){
        childClickViewIds.add(viewResId)
        getView<View>(viewResId)?.run {
            if(!isClickable){
                isClickable = true
            }
            setOnClickListener {
                adapter.onItemChildClickListener?.onItemChildClick(adapter,this,getClickPosition())
            }
        }
    }

    fun addOnLongClickListener(@IdRes viewResId: Int){
        childLongClickViewIds.add(viewResId)
        getView<View>(viewResId)?.run {
            if(!isLongClickable){
                isLongClickable = true
            }
            setOnLongClickListener{
                adapter.onItemLongClickListener?.onItemChildLongClick(adapter,this,getClickPosition())?:false
            }
        }
    }

    private fun getClickPosition(): Int {
        return if (layoutPosition >= adapter.getHeaderLayoutCount()) {
            layoutPosition - adapter.getHeaderLayoutCount()
        } else 0
    }
}