package com.tadiotv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * โหลดโลโก้ช่องแบบเบื้องหลัง + แคชในหน่วยความจำ
 * เขียนเองเพื่อไม่ต้องเพิ่มไลบรารีภายนอก (ประหยัดจำนวนเมธอดสำหรับ Android 4.2)
 */
public class LogoLoader {

    private static LruCache<String, Object> cache;
    private static ExecutorService pool;
    private static Handler main;

    private static synchronized void init() {
        if (cache == null) {
            cache = new LruCache<String, Object>(150);
            pool = Executors.newFixedThreadPool(3);
            main = new Handler(Looper.getMainLooper());
        }
    }

    public static void load(Context ctx, final ImageView image, final TextView fallback, final String url) {
        init();
        if (url == null || url.length() == 0) {
            showFallback(image, fallback);
            return;
        }
        Object hit = cache.get(url);
        if (hit != null) {
            image.setImageBitmap((android.graphics.Bitmap) hit);
            image.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
            return;
        }
        showFallback(image, fallback);
        image.setTag(url);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final android.graphics.Bitmap bmp = fetch(url);
                if (bmp == null) {
                    return;
                }
                cache.put(url, bmp);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(image.getTag())) {
                            image.setImageBitmap(bmp);
                            image.setVisibility(View.VISIBLE);
                            fallback.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });
    }

    private static void showFallback(ImageView image, TextView fallback) {
        image.setImageDrawable(null);
        image.setVisibility(View.GONE);
        fallback.setVisibility(View.VISIBLE);
    }

    private static android.graphics.Bitmap fetch(String url) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            in = conn.getInputStream();
            return android.graphics.BitmapFactory.decodeStream(in);
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
