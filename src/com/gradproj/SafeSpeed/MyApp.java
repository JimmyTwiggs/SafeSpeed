package com.gradproj.SafeSpeed;

import android.app.Application;
import android.content.Context;

public class MyApp extends Application{

    private static Context mContext;

    public void onCreate(){
       mContext = this.getApplicationContext();
    }

    public static Context getAppContext(){
       return mContext;
    }
}