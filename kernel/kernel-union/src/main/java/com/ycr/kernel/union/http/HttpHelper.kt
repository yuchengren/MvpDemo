package com.ycr.kernel.union.http

import android.content.Context
import com.ycr.kernel.http.IApi
import com.ycr.kernel.http.IHttpScheduler
import com.ycr.kernel.http.IResult
import com.ycr.kernel.task.TASK_INFO
import com.ycr.kernel.task.TaskInfo
import com.ycr.kernel.union.exception.NetworkNotConnectedException
import com.ycr.kernel.union.helper.ContextHelper
import com.ycr.kernel.union.helper.UnionContainer
import com.ycr.kernel.util.ThreadLocalHelper
import com.ycr.kernel.util.isNetworkConnected

/**
 * Created by yuchengren on 2018/12/13.
 */
object HttpHelper {

    private var  httpScheduler: IHttpScheduler = UnionContainer.httpScheduler
    private var  context: Context = ContextHelper.context

    fun <T> execute(api: IApi, params: Any?): IResult<T>{
        if(!context.isNetworkConnected()){
            throw NetworkNotConnectedException()
        }
        val newCall = httpScheduler.newCall(api.newRequestBuilder().setParams(params).build())
        val taskInfo = ThreadLocalHelper.getThreadLocalInfo<TaskInfo>(TASK_INFO)
        val response = httpScheduler.execute(newCall, taskInfo?.groupName, taskInfo?.taskName)
        return httpScheduler.parse(api, response)
    }

    fun cancelGroup(groupName: String?){
        httpScheduler.cancelGroup(groupName)
    }


}