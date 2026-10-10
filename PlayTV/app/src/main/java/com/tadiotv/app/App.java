package com.tadiotv.app;

import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDex;

/**
 * Tadio TV - Application หลัก
 * ติดตั้ง MultiDex เพราะรองรับ Android 4.2 (API 17) ซึ่งโหลด dex ได้ไฟล์เดียว
 */
public class App extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
