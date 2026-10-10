package com.tadiotv.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.multidex.MultiDex;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Tadio TV - Application หลัก
 * - ติดตั้ง MultiDex (Android 4.2 โหลด dex ได้ไฟล์เดียว)
 * - เปิด TLS 1.2 บน Android 4.1-4.4 ให้ทุกการเชื่อมต่อ https
 * - เก็บบันทึกข้อผิดพลาดที่ทำให้แอปปิดเอง (crash.log) เพื่อให้ตรวจสอบสาเหตุได้บนเครื่องจริง
 */
public class App extends Application {

    public static final String CRASH_FILE = "crash.log";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        installTls();
        installCrashLogger();
    }

    private void installTls() {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 21) {
            try {
                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                HttpsURLConnection.setDefaultSSLSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));
            } catch (Throwable ignored) {
            }
        }
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    pw.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                            + "  Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")  "
                            + Build.MANUFACTURER + " " + Build.MODEL);
                    pw.println("thread: " + t.getName());
                    Runtime rt = Runtime.getRuntime();
                    pw.println("heap: used " + ((rt.totalMemory() - rt.freeMemory()) >> 20)
                            + "MB / max " + (rt.maxMemory() >> 20) + "MB");
                    e.printStackTrace(pw);
                    pw.flush();
                    FileOutputStream out = new FileOutputStream(new File(getFilesDir(), CRASH_FILE));
                    out.write(sw.toString().getBytes("UTF-8"));
                    out.close();
                } catch (Throwable ignored) {
                }
                if (previous != null) {
                    previous.uncaughtException(t, e);
                }
            }
        });
    }
}
