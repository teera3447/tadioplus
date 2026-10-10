package com.tadiotv.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * โหลดโลโก้ช่องแบบเบื้องหลัง + แคชในหน่วยความจำ
 * ปรับให้ประหยัดแรมสำหรับเครื่องเก่า (Android 4.x ที่ heap เล็ก):
 *  - ย่อภาพตอนถอดรหัส (inSampleSize) ไม่เก็บภาพขนาดเต็ม
 *  - จำกัดแคชตาม "ไบต์" (1/16 ของ heap) ไม่ใช่ตามจำนวนภาพ
 *  - จับ OutOfMemoryError ไม่ให้แอปปิด และล้างแคชเมื่อแรมตึง
 */
public class LogoLoader {

    private static final int MAX_PX = 128;              // ด้านยาวสุดของโลโก้ที่เก็บ
    private static final int MAX_DOWNLOAD = 400 * 1024; // ไฟล์โลโก้ใหญ่กว่านี้ข้าม

    private static LruCache<String, Bitmap> cache;
    private static ExecutorService pool;
    private static Handler main;
    private static final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());

    private static synchronized void init() {
        if (cache == null) {
            int bytes = (int) Math.max(1024 * 1024, Runtime.getRuntime().maxMemory() / 16);
            cache = new LruCache<String, Bitmap>(bytes) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getRowBytes() * value.getHeight();
                }
            };
            pool = Executors.newFixedThreadPool(2);
            main = new Handler(Looper.getMainLooper());
        }
    }

    /** เรียกเมื่อระบบแจ้งว่าแรมตึง */
    public static void clear() {
        if (cache != null) {
            cache.evictAll();
        }
    }

    public static void load(android.content.Context ctx, final ImageView image, final TextView fallback, final String url) {
        init();
        if (url == null || url.length() == 0 || failed.contains(url)) {
            showFallback(image, fallback);
            return;
        }
        Bitmap hit = cache.get(url);
        if (hit != null && !hit.isRecycled()) {
            image.setTag(url);
            image.setImageBitmap(hit);
            image.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
            return;
        }
        showFallback(image, fallback);
        image.setTag(url);
        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bmp = fetch(url);
                    if (bmp == null) {
                        failed.add(url);
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
        } catch (Throwable ignored) {
        }
    }

    private static void showFallback(ImageView image, TextView fallback) {
        image.setTag(null);
        image.setImageDrawable(null);
        image.setVisibility(View.GONE);
        fallback.setVisibility(View.VISIBLE);
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() / 100 != 2) {
                return null;
            }
            in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > MAX_DOWNLOAD) {
                    return null;
                }
            }
            byte[] data = bos.toByteArray();

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            int longest = Math.max(o.outWidth, o.outHeight);
            while (longest / (sample * 2) >= MAX_PX) {
                sample *= 2;
            }
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, d);
        } catch (Throwable t) {          // รวม OutOfMemoryError
            clear();
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
