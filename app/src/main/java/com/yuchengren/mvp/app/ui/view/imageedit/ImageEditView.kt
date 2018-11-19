package com.yuchengren.mvp.app.ui.view.imageedit

import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.*
import android.support.annotation.Nullable
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.yuchengren.mvp.R
import com.yuchengren.mvp.util.CompressUtils
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 图片编辑View
 * Created by yuchengren on 2018/11/1.
 */
class ImageEditView : View {
    companion object {
        const val SCALE_MAX = 3f //缩放的最大倍数
        const val SCALE_MIN = 1f //缩放的最小倍数
        const val PATH_COUNT_MAX = 10 //涂鸦最大可画的轨迹数

        const val PATH_EFFECT_INTERVAL = 200L //轨迹描点生效时间
        const val PATH_EFFECT_POINT_COUNT = 3 //轨迹描点生效的个数
    }
    private val pathIdAuto: AtomicInteger = AtomicInteger(0)
    private val msgOrderAuto: AtomicInteger = AtomicInteger(0)
    private var pathEventListener: PathEventListener? = null
    private var bitmap: Bitmap? = null

    private var graffitiPaintColor: Int = 0 //涂鸦画笔的颜色
    private var graffitiPaintWidth: Int = 0 //涂鸦画笔的宽度
    private lateinit var graffitiPaint: Paint
    private lateinit var pathEndIconTextPaint: Paint
    private lateinit var pathEndIconBgPaint: Paint
    private var pathEndIconSize = 0f //路径末端的圆圈大小
    private var pathEndIconInnerSize = 0f  //路径末端的圆圈内部的图标大小

    private var pathList: LinkedList<GraffitiPath> = LinkedList() //已编译完成的涂鸦路径
    private var editGraffitiPath: GraffitiPath = GraffitiPath() //正在编辑的涂鸦路径
    private var currentTouchInEndIconPath: GraffitiPath? = null //当前触摸点在其末端Icon区域范围内的路径 处理为点击事件

    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var windowRectF = RectF() //控件可视化区域
    private var imageRectF = RectF() //完整图片区域
    private var hasInitHoming = false //是否已经初始化原始图片状态
    private var imageScale: Float = 1f //图片的当前已缩放的比例
    private var initScale: Float = 1f //图片居中适配屏幕的初始化缩放比例
    private var homingAnimator: ImgHomingAnimator? = null // 图片归位动画

    private var pointerDownTime = 0L //触摸down事件的时间

    private fun initPaint() {
        graffitiPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE //样式 描边
            strokeWidth = graffitiPaintWidth.toFloat() //描边宽度
            color = graffitiPaintColor //颜色
            strokeCap = Paint.Cap.BUTT //起始端的线帽的类型 圆角
            strokeJoin = Paint.Join.ROUND //多线条连接拐角
            //PathEffect是用来控制绘制轮廓(线条)的方式
            //可以使用圆角来代替尖锐的角从而对基本图形的形状尖锐的边角进行平滑。
            pathEffect = CornerPathEffect(graffitiPaintWidth.toFloat())
        }

        pathEndIconTextPaint = Paint().apply {
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }
        pathEndIconBgPaint = Paint().apply { color = graffitiPaintColor }
    }

    constructor(context: Context) : this(context,null)

    constructor(context: Context, @Nullable attrs: AttributeSet?) : this(context, attrs,0)

    constructor(context: Context, @Nullable attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ImageEditView, defStyleAttr, 0)
        typedArray?.run {
            graffitiPaintColor = getColor(R.styleable.ImageEditView_graffitiPaintColor,context.getColor(R.color.teacher_paint))
            graffitiPaintWidth = getDimensionPixelSize(R.styleable.ImageEditView_graffitiPaintWidth,context.resources.getDimensionPixelSize(R.dimen.teacher_paint_width))
        }
        initPaint()
        initGesture(context)
    }

    private fun initGesture(context: Context) {
        gestureDetector = GestureDetector(context,object : GestureDetector.SimpleOnGestureListener(){
            override fun onDown(e: MotionEvent?): Boolean { return true }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
                return scroll(distanceX,distanceY)
            }
        })

        scaleGestureDetector = ScaleGestureDetector(context,object : ScaleGestureDetector.OnScaleGestureListener{
            override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean { return true }
            override fun onScaleEnd(detector: ScaleGestureDetector?) {}
            /**
             * focusX focusY 返回组成缩放手势(两个手指)中点x横纵坐标
             * scaleFactor 获取本次缩放事件的缩放因子
             * return 返回值表示是否下次缩放需要重置，如果返回ture，那么detector就会重置缩放事件，如果返回false，detector会在之前的缩放上继续进行计算
             */
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                if(detector == null){
                    return false
                }
                scale(detector.scaleFactor,scrollX + detector.focusX,scrollY + detector.focusY)
                pathEventListener?.onGestureScale(detector.scaleFactor)
                return true
            }
        })
    }

    private fun getScale(): Float{
        val bitmap: Bitmap = bitmap ?: return 1f
        return imageRectF.width() / bitmap.width
    }

    private fun scale(scaleFactor: Float, px: Float, py: Float) {
        if(scaleFactor == 1f || scaleFactor * imageScale > SCALE_MAX){
            return
        }
        Matrix().apply { setScale(scaleFactor, scaleFactor,px,py) }.mapRect(imageRectF)
        imageScale *= scaleFactor
        invalidate()

    }

    private fun scroll(distanceX: Float, distanceY: Float): Boolean {
        if(distanceX != 0f || distanceY != 0f){
            scrollBy(distanceX.toInt(),distanceY.toInt())
            return true
        }
        return false
    }

    fun setImageBitmap(bitmap: Bitmap?){
        if(bitmap == null){
            return
        }
        this.bitmap = bitmap
        hasInitHoming = false
        invalidate()
    }

    fun setPaths(path: Path){
        pathList.add(GraffitiPath(path))
        invalidate()
    }

    fun save(): Bitmap?{
        val bitmap = bitmap?:return null
        val createBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(createBitmap)
        canvas.drawBitmap(bitmap,0f,0f,null)
        pathList.forEachIndexed { index, graffitiPath ->
            canvas.drawPath(graffitiPath.path,graffitiPaint)
            drawPathEndIcon(canvas, graffitiPath, true)
        }
        return createBitmap
    }

    override fun onDraw(canvas: Canvas?) {
        if(canvas == null){
            return
        }
        drawBitmap(canvas)
        drawPaths(canvas)
    }

    private fun drawBitmap(canvas: Canvas) {
        val bitmap: Bitmap? = bitmap
        if(bitmap == null || bitmap.isRecycled){
            return
        }
        canvas.drawBitmap(bitmap,null,imageRectF,null)
    }

    private fun drawPaths(canvas: Canvas) {
        canvas.save()
        val scale = getScale()
        canvas.translate(imageRectF.left,imageRectF.top)
        canvas.scale(scale,scale)

        pathList.forEachIndexed { index, graffitiPath ->
            canvas.drawPath(graffitiPath.path,graffitiPaint)
            drawPathEndIcon(canvas, graffitiPath, false)
        }
        if(!editGraffitiPath.isEmpty()){
            canvas.drawPath(editGraffitiPath.path,graffitiPaint)
        }
        canvas.restore()
    }

    private fun drawPathEndIcon(canvas: Canvas, graffitiPath: GraffitiPath, isSave: Boolean) {
        if(graffitiPath.remarkStatus == GraffitiPath.RemarkStatus.MSG_NONE){
            return
        }

        if(graffitiPath.remarkStatus == GraffitiPath.RemarkStatus.MSG_DONE){
            canvas.drawCircle(graffitiPath.lastX,graffitiPath.lastY,pathEndIconSize / 2,pathEndIconBgPaint)
            val fm = pathEndIconTextPaint.fontMetricsInt
            canvas.drawText(graffitiPath.msgOrder.toString(),graffitiPath.lastX ,graffitiPath.lastY -(fm.bottom + fm.top) / 2,pathEndIconTextPaint)
        }else{
            if(isSave) return
            canvas.drawCircle(graffitiPath.lastX,graffitiPath.lastY,pathEndIconSize / 2,pathEndIconBgPaint)
            var bitmap: Bitmap? = null
            if(graffitiPath.remarkStatus == GraffitiPath.RemarkStatus.MSG_ADD){
                bitmap = BitmapFactory.decodeResource(context.resources,R.drawable.ic_path_add_remark)
            }else if(graffitiPath.remarkStatus == GraffitiPath.RemarkStatus.MSG_EDITING){
                bitmap = BitmapFactory.decodeResource(context.resources,R.drawable.ic_path_editing)
            }
            val halfSize = pathEndIconInnerSize / 2
            val rectF = RectF(graffitiPath.lastX - halfSize,graffitiPath.lastY - halfSize,
                    graffitiPath.lastX + halfSize,graffitiPath.lastY + halfSize)
            canvas.drawBitmap(bitmap,null,rectF,null)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if(event == null){
            return super.onTouchEvent(event)
        }
        if(isHoming()){
            return false
        }
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN -> pointerDownTime = System.currentTimeMillis()
            MotionEvent.ACTION_POINTER_DOWN -> {
//                Log.e("ACTION_POINTER_DOWN","${event?.x},${event?.y},${event?.actionMasked},${event?.getPointerId(0)}")
                if(System.currentTimeMillis() - pointerDownTime < PATH_EFFECT_INTERVAL){
                    //为解决双指操作，落在屏幕上的时间不一致导致其中先落的手指会画轨迹的问题
                    editGraffitiPath.reset()
                }
                pointerDownTime = 0
            }
        }
        var handled: Boolean
        if(event.pointerCount > 1){
            onPathDone(event)
            handled = scaleGestureDetector.onTouchEvent(event).or(gestureDetector.onTouchEvent(event))
        }else{
            handled = onTouchEventPath(event)
        }

        when(event.actionMasked){
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> homing()
        }

        return handled
    }

    private fun onTouchEventPath(event: MotionEvent?): Boolean{
        if(event == null){
            return super.onTouchEvent(event)
        }
//        Log.e("onTouchEventPath","${event?.x},${event?.y},${event?.actionMasked},${event?.getPointerId(0)}")
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN -> {
                if(!isPointInImageRecF(event.x,event.y)){
                    editGraffitiPath.pointerId = event.getPointerId(0)
                    return true
                }
                val lastTouchInEndIconPath = this.currentTouchInEndIconPath
                val touchInEndIconPath = getTouchInEndIconPath(event.x, event.y)
                if(touchInEndIconPath == null){
                    if(lastTouchInEndIconPath != null){
                        when(lastTouchInEndIconPath.remarkStatus){
                            GraffitiPath.RemarkStatus.MSG_EDITING -> lastTouchInEndIconPath.remarkStatus = GraffitiPath.RemarkStatus.MSG_ADD
                        }
                    }
                    currentTouchInEndIconPath = null
                    return onPathBegin(event)
                }else{
                    if(lastTouchInEndIconPath != null){
                        if(!(touchInEndIconPath === lastTouchInEndIconPath)){
                            when(lastTouchInEndIconPath.remarkStatus){
                                GraffitiPath.RemarkStatus.MSG_EDITING -> lastTouchInEndIconPath.remarkStatus = GraffitiPath.RemarkStatus.MSG_ADD
                            }
                        }else{
                            return false
                        }
                    }
                    this.currentTouchInEndIconPath = touchInEndIconPath
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                return if(currentTouchInEndIconPath == null){
                    onPathMove(event)
                }else{
                    true
                }
            }
            MotionEvent.ACTION_UP,  MotionEvent.ACTION_CANCEL ->{
                val currentTouchInEndIconPath = currentTouchInEndIconPath
                return if(currentTouchInEndIconPath == null){
                    editGraffitiPath.isSamePointer(event.getPointerId(0)) && onPathDone(event)
                }else{
                    onClickPathEndIcon(currentTouchInEndIconPath)
                    true
                }
            }
        }
        return false
    }

    private fun onClickPathEndIcon(touchInEndIconPath: GraffitiPath) {
        when(touchInEndIconPath.remarkStatus){
            GraffitiPath.RemarkStatus.MSG_EDITING -> return
            GraffitiPath.RemarkStatus.MSG_ADD -> {
                touchInEndIconPath.remarkStatus = GraffitiPath.RemarkStatus.MSG_EDITING
                invalidate()
                pathEventListener?.onClickPathAddMsg(touchInEndIconPath.id)
            }
            GraffitiPath.RemarkStatus.MSG_DONE -> pathEventListener?.onClickMsgPath(touchInEndIconPath.id)
        }
    }

    private fun getTouchInEndIconPath(x: Float, y: Float): GraffitiPath?{
        val pointArray = GraffitiPath.getMatrixMapPointArray(x, y, getOriginPathMatrix())
        pathList.forEach {
            if(it.remarkStatus == GraffitiPath.RemarkStatus.MSG_NONE){
                return@forEach
            }
            val halfSize = context.resources.getDimensionPixelSize(R.dimen.graffiti_path_end_icon_size).toFloat() /2
            val endIconRectF = RectF(it.lastX - halfSize,it.lastY - halfSize,
                    it.lastX + halfSize,it.lastY + halfSize)
            if(endIconRectF.contains(pointArray[0],pointArray[1])){
                return it
            }
        }
        return null
    }


    private fun onPathBegin(event: MotionEvent): Boolean {
        if(pathList.size >= PATH_COUNT_MAX){
            pathEventListener?.onDrawPathOverMax(pathList.size)
            return true
        }
        editGraffitiPath.moveTo(event.x,event.y,getOriginPathMatrix())
        editGraffitiPath.pointerId = event.getPointerId(0)
        return true
    }

    private fun onPathMove(event: MotionEvent): Boolean {
//        快速滑动时，move回调里有逻辑操作，不是全部及时处理会丢失，实际处理的点数小于event回调的点数
        if(editGraffitiPath.isSamePointer(event.getPointerId(0))){
            if(isPointInImageRecF(event.x,event.y)){
                if(editGraffitiPath.isEmpty()){
                    if(pathList.size >= PATH_COUNT_MAX){
                        pathEventListener?.onDrawPathOverMax(pathList.size)
                        return true
                    }
                    editGraffitiPath.moveTo(event.x,event.y,getOriginPathMatrix())
                }else{
                    editGraffitiPath.lineTo(event.x,event.y,getOriginPathMatrix())
                }
                invalidate()
            }
            return true
        }
        return false
    }

    private fun isPointInImageRecF(x: Float,y: Float): Boolean{
        val pointArray = floatArrayOf(x,y)
        Matrix().apply {
            setTranslate(scrollX.toFloat(),scrollY.toFloat())
        }.mapPoints(pointArray)
        return imageRectF.contains(pointArray[0],pointArray[1])
    }

    private fun onPathDone(event: MotionEvent): Boolean {
        val bitmap = this.bitmap?: return false
        if(editGraffitiPath.isEmpty()){
            return false
        }
        val radius = pathEndIconSize / 2
        if(editGraffitiPath.lastX + radius > bitmap.width){
            editGraffitiPath.lastX = bitmap.width - radius
        }else if(editGraffitiPath.lastX - radius < 0f){
            editGraffitiPath.lastX = radius
        }
        if(editGraffitiPath.lastY + radius > bitmap.height){
            editGraffitiPath.lastY = bitmap.height - radius
        }else if(editGraffitiPath.lastY - radius < 0f){
            editGraffitiPath.lastY = radius
        }

        editGraffitiPath.id = pathIdAuto.getAndIncrement()
        editGraffitiPath.remarkStatus = GraffitiPath.RemarkStatus.MSG_ADD
        pathList.add(editGraffitiPath)
        pathEventListener?.onPathDrawDone(editGraffitiPath.id)

        Log.e("onPathDone",editGraffitiPath.pathData.toString())
        Log.e("onPathDone length",editGraffitiPath.pathData.toString().toByteArray().size.toString())

        val compressString = CompressUtils.compressString(editGraffitiPath.pathData.toString())
        Log.e("compressString",compressString)
        Log.e("compressString length",compressString.length.toString())
        editGraffitiPath = GraffitiPath()

        invalidate()
        return true
    }

    private fun getOriginPathMatrix(): Matrix{
        return  Matrix().apply {
            setTranslate(scrollX.toFloat(),scrollY.toFloat())
            postTranslate(-imageRectF.left,-imageRectF.top)
            val scale: Float = 1 / imageScale
            postScale(scale,scale)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if(changed){
            onWindowSizeChanged((right - left).toFloat(),(bottom - top).toFloat())
        }else{
            if(!hasInitHoming){
                initHoming()
            }
        }
    }

    /**
     * 当View窗口大小发生变化时
     */
    private fun onWindowSizeChanged(width: Float, height: Float) {
        if(width == 0f || height == 0f){
            return
        }
        windowRectF.set(0f,0f,width,height)
        if(!hasInitHoming){
            initHoming()
        }else{
            Matrix().apply {
                setTranslate(windowRectF.centerX() - imageRectF.centerX(),windowRectF.centerY() - imageRectF.centerY())
            }.mapRect(imageRectF)
        }
    }

    private fun initHoming() {
        val bitmap = bitmap ?: return
        if(windowRectF.isEmpty){
            return
        }
        imageRectF.set(0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat())

        val scale = Math.min(windowRectF.width() / imageRectF.width(),windowRectF.height() / imageRectF.height())
        val matrix = Matrix()
        //图片适配View窗口大小
        matrix.setScale(scale,scale,imageRectF.centerX(),imageRectF.centerY())
        //图片平移至View窗口居中
        matrix.postTranslate(windowRectF.centerX() - imageRectF.centerX(),windowRectF.centerY() - imageRectF.centerY())
        matrix.mapRect(imageRectF) //通过矩阵变换矩形
        imageScale = scale
        initScale = scale
        pathEndIconTextPaint.textSize = context.resources.getDimensionPixelSize(R.dimen.text_size_small).toFloat() / initScale
        pathEndIconInnerSize = context.resources.getDimensionPixelSize(R.dimen.text_size_small).toFloat() / initScale
        pathEndIconSize = context.resources.getDimensionPixelSize(R.dimen.graffiti_path_end_icon_size).toFloat() / initScale
        hasInitHoming = true
    }
    /**
     * 撤销
     */
    fun undo(){
        if(pathList.size >= 1){
            pathList.removeLast()
            invalidate()
        }
    }

    /**
     * 清除
     */
    fun clear(){
        pathList.clear()
        invalidate()
    }

    /**
     * 根据Id删除对应的轨迹
     */
    fun remove(pathId: Int){
        val path = getPath(pathId) ?: return
        pathList.remove(path)
        pathList.forEach {
            if(it.msgOrder > path.msgOrder){
                it.msgOrder = it.msgOrder - 1
            }
        }
        msgOrderAuto.decrementAndGet()
        invalidate()
    }

    fun onMsgDone(pathId: Int){
        getPath(pathId)?.run {
            msgOrder = msgOrderAuto.incrementAndGet()
            remarkStatus = GraffitiPath.RemarkStatus.MSG_DONE
            invalidate()
        }
    }

    fun onMsgEditCancel(pathId: Int){
        getPath(pathId)?.run {
            remarkStatus = GraffitiPath.RemarkStatus.MSG_ADD
            invalidate()
        }
    }

    private fun getPath(pathId: Int): GraffitiPath?{
        val iterator = pathList.iterator()
        while (iterator.hasNext()){
            val next = iterator.next()
            if(next.id == pathId){
                return next
            }
        }
        return null
    }

    fun setEventListener(pathEventListener: PathEventListener?){
        this.pathEventListener = pathEventListener
    }

    interface PathEventListener{
        /**
         * 当轨迹超过最大条数限制
         */
        fun onDrawPathOverMax(currentPathCount: Int)

        /**
         * 当一条轨迹完成
         */
        fun onPathDrawDone(pathId: Int)

        /**
         * 当点击加号的轨迹
         */
        fun onClickPathAddMsg(pathId: Int)

        /**
         * 当点击已添加信息、有数字序号的轨迹
         */
        fun onClickMsgPath(pathId: Int)

        /**
         * 当手势缩放时
         */
        fun onGestureScale(scaleFactor: Float)
    }

    private fun isHoming(): Boolean{
        return homingAnimator?.isRunning ?:false
    }

    private fun stopHoming(){
        homingAnimator?.cancel()
    }

    /**
     * 图片归位
     */
    private fun homing() {
        stopHoming()
        startHoming()
    }

    private fun startHoming() {
        if(homingAnimator == null){
            homingAnimator = ImgHomingAnimator().apply {
                addUpdateListener {
                    val imageHomingValues = it.animatedValue as ImgHomingValues
                    scroll(imageHomingValues.x - scrollX,imageHomingValues.y - scrollY)
                    scale(imageHomingValues.scale / imageScale,imageRectF.centerX(),imageRectF.centerY())
                }
                addListener(object : AnimatorListenerAdapter() {})
            }
        }
        homingAnimator?.setHomingValues(getStartHomingValues(),getEndHomingValues())
        homingAnimator?.start()
    }

    private fun getStartHomingValues(): ImgHomingValues {
        return ImgHomingValues(scrollX.toFloat(),scrollY.toFloat(),imageScale)
    }

    private fun getEndHomingValues(): ImgHomingValues {
        val homingValues = getStartHomingValues()
        val imgRectF = RectF()
        Matrix().mapRect(imgRectF,imageRectF)

        val winRectF = RectF(windowRectF)
        winRectF.offset(scrollX.toFloat(),scrollY.toFloat())

        homingValues.scrollConcat(ImgHelper.getFitHomingValues(winRectF,imgRectF))
        return homingValues
    }


}