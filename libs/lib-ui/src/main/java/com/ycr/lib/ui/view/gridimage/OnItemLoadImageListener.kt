package com.ycr.lib.ui.view.gridimage

import android.widget.ImageView

/**
 * Created by yuchengren on 2019/1/17.
 */
interface OnItemLoadImageListener<T> {
    fun  loadImage(item: T, view: ImageView,position: Int)
}
