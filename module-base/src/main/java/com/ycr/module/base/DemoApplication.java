package com.ycr.module.base;

import android.os.Environment;

import com.ycr.kernel.log.LogHelper;
import com.ycr.kernel.log.config.FileLogPrinterConfig;
import com.ycr.kernel.log.config.LogConfig;
import com.ycr.kernel.log.constants.LogLevel;
import com.ycr.kernel.log.printer.ConsoleLogPrinter;
import com.ycr.kernel.log.printer.FileLogPrinter;
import com.ycr.lib.changeskin.SkinManager;
import com.ycr.module.framework.base.SuperApplication;
import com.ycr.module.base.constant.Constants;
import com.ycr.module.base.greendao.gen.DaoMaster;
import com.ycr.module.base.greendao.gen.DaoSession;
import com.ycr.module.base.util.CrashHandler;
import com.ycr.module.base.util.DataBaseHelper;
import com.ycr.module.base.util.OkHttpUtil;
import com.ycr.module.base.util.SharePrefsUtil;

import org.greenrobot.greendao.database.Database;

import okhttp3.OkHttpClient;

/**
 * Created by yuchengren on 2016/9/2.
 */
public class DemoApplication extends SuperApplication {

    private static DemoApplication mDemoApplication;
    /**
     * 默认初始化的OkHttpClient
     */
    private OkHttpClient mDefaultOkHttpClient;
    private DaoSession mDaoSession;

    public synchronized static DemoApplication getInstance(){
        if(mDemoApplication == null ){
            mDemoApplication = new DemoApplication();
        }
        return mDemoApplication;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mDemoApplication = this;
        SharePrefsUtil.getInstance().init(getApplicationContext());
        CrashHandler.getInstance().init(getApplicationContext());
        initGreenDao();
        initOkHttp();
        initLog();
        initSkin();
    }

    private void initSkin() {
		SkinManager.INSTANCE.init(this);
    }

    private String getAppRootPath(){
        String appRootPath;
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
            appRootPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + getPackageName();
        }else{
            appRootPath = getFilesDir().getPath();
        }
        appRootPath += "/logs";
        return appRootPath;
    }

    private void initLog() {
		LogHelper.initAppModule("app").
                config(LogConfig.create(this).
                        setTagPre("mvp").
                        setEnabled(true).
                        setLevel(LogLevel.VERBOSE)).
                setLogPrinters(
                        new ConsoleLogPrinter(),
                        new FileLogPrinter(FileLogPrinterConfig.create(this).setFileRootPath(getAppRootPath()).setLevel(LogLevel.ERROR)));
        //使用范例
//        LogHelper.e("tag","msg");
//        ILogEngine task = LogHelper.module("task");
//        LogHelper.module("task").e("tag","msg");
    }

    private void initGreenDao() {
        // 通过 DaoMaster 的内部类 DevOpenHelper，你可以得到一个便利的 SQLiteOpenHelper 对象。
        // 可能你已经注意到了，你并不需要去编写「CREATE TABLE」这样的 SQL 语句，因为 greenDAO 已经帮你做了。
        // 注意：默认的 DaoMaster.DevOpenHelper 会在数据库升级时，删除所有的表，意味着这将导致数据的丢失。
        // 所以，在正式的项目中，你还应该做一层封装，来实现数据库的安全升级。
        DataBaseHelper helper = new DataBaseHelper(getApplicationContext(), Constants.DB_NAME);
        Database db = helper.getWritableDb();
        DaoMaster daoMaster = new DaoMaster(db);
        mDaoSession = daoMaster.newSession();
    }
    public DaoSession getDaoSession() {
        return mDaoSession;
    }

    /**
     * 初始化OkHttpClient
     */
    private void initOkHttp() {
        OkHttpUtil.getInstance().initClient();
        setDefaultOkHttpClient(OkHttpUtil.getInstance().getOkHttpClient());
    }

    public OkHttpClient getDefaultOkHttpClient() {
        if(mDefaultOkHttpClient == null){
            initOkHttp();
        }
        return mDefaultOkHttpClient;
    }

    public void setDefaultOkHttpClient(OkHttpClient mDefaultOkHttpClient) {
        this.mDefaultOkHttpClient = mDefaultOkHttpClient;
    }


}
