package com.ubtechinc.alphatranslation;

import android.app.Application;

import com.tencent.bugly.crashreport.CrashReport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by ubt on 2017/6/20.
 */

public class TransferApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        initBugly();
    }
    private void initBugly() {
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(getApplicationContext());
        strategy.setCrashHandleCallback(new CrashReport.CrashHandleCallback() {
            public Map<String, String> onCrashHandleStart(int crashType, String errorType,
                                                          String errorMessage, String errorStack) {
                //上报的自定义字段
                LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();

                return map;
            }

            @Override
            public byte[] onCrashHandleStart2GetExtraDatas(int crashType, String errorType,
                                                           String errorMessage, String errorStack) {
                try {

                    return "Extra data.".getBytes("UTF-8");
                } catch (Exception e) {
                    return null;
                }
            }

        });

        CrashReport.initCrashReport(getApplicationContext(),"e83b01ab11",true,strategy);

//		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//			@Override
//			public void run() {
//				CrashReport.testJavaCrash();
//			}
//		},5000);
    }
}
