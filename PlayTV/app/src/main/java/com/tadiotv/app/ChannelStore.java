package com.tadiotv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * โหลด/แคชเพลย์ลิสต์ช่อง และผังรายการ EPG
 * - โหลดจากเน็ตทุกครั้งที่สั่งรีเฟรช แล้วเก็บไฟล์สำรองไว้ในหน่วยความจำเครื่อง
 * - ถ้าเน็ตใช้ไม่ได้ จะใช้ไฟล์ที่แคชไว้ (หรือไฟล์ตัวอย่างที่แนบมากับแอปในครั้งแรก)
 */
public class ChannelStore {

    public static final String PLAYLIST_URL =
            "https://raw.githubusercontent.com/teera3447/tadioplus/refs/heads/main/tadio1.m3u";
    public static final String EPG_URL = "https://iptv36.vercel.app/epg.xml";

    private static final String PREF = "tadio_store";
    private static final String KEY_PLAYLIST_TIME = "playlist_time";
    private static final String KEY_EPG_TIME = "epg_time";

    public static File playlistFile(Context c) {
        return new File(c.getFilesDir(), "tadio1.m3u");
    }

    public static File epgFile(Context c) {
        return new File(c.getFilesDir(), "epg.xml");
    }

    public static long lastPlaylistTime(Context c) {
        return prefs(c).getLong(KEY_PLAYLIST_TIME, 0);
    }

    public static long lastEpgTime(Context c) {
        return prefs(c).getLong(KEY_EPG_TIME, 0);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** ดาวน์โหลดไฟล์ (รองรับ gzip) ลงไฟล์ปลายทาง คืนค่า true เมื่อสำเร็จ */
    public static boolean downloadTo(String url, File dest) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        File tmp = new File(dest.getAbsolutePath() + ".tmp");
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TadioTV/2.0");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return false;
            }
            in = conn.getInputStream();
            String enc = conn.getContentEncoding();
            if (enc != null && enc.toLowerCase().contains("gzip")) {
                in = new GZIPInputStream(in);
            }
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            out.close();
            out = null;
            in.close();
            in = null;
            if (tmp.length() < 64) {
                tmp.delete();
                return false;
            }
            if (dest.exists() && !dest.delete()) {
                tmp.delete();
                return false;
            }
            return tmp.renameTo(dest);
        } catch (Throwable t) {
            if (tmp.exists()) {
                tmp.delete();
            }
            return false;
        } finally {
            close(out);
            close(in);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void close(java.io.Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void copyAsset(Context c, String assetName, File dest) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = c.getAssets().open(assetName);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            close(out);
            close(in);
        }
    }

    public static List<Channel> loadChannels(Context c, boolean force) {
        File f = playlistFile(c);
        if (force || !f.exists()) {
            if (downloadTo(PLAYLIST_URL, f)) {
                prefs(c).edit().putLong(KEY_PLAYLIST_TIME, System.currentTimeMillis()).apply();
            }
        }
        if (!f.exists()) {
            copyAsset(c, "tadio1.m3u", f);
        }
        try {
            InputStream in = new FileInputStream(f);
            List<Channel> list = M3uParser.parse(in);
            in.close();
            return list;
        } catch (Throwable t) {
            return new ArrayList<Channel>();
        }
    }

    public static Map<String, List<EpgProgram>> loadEpg(Context c, boolean force) {
        File f = epgFile(c);
        if (force || !f.exists()) {
            if (downloadTo(EPG_URL, f)) {
                prefs(c).edit().putLong(KEY_EPG_TIME, System.currentTimeMillis()).apply();
            }
        }
        if (!f.exists()) {
            copyAsset(c, "epg_fallback.xml", f);
        }
        try {
            InputStream in = new FileInputStream(f);
            long now = System.currentTimeMillis();
            Map<String, List<EpgProgram>> map = EpgParser.parse(in, now - 6L * 3600 * 1000, now + 48L * 3600 * 1000);
            in.close();
            return map;
        } catch (Throwable t) {
            return new HashMap<String, List<EpgProgram>>();
        }
    }

    /** โหลดทั้งช่องและผังรายการ ใช้จากงานเบื้องหลัง (WorkManager) */
    public static boolean refreshAll(Context c) {
        boolean ok = downloadTo(PLAYLIST_URL, playlistFile(c));
        if (ok) {
            prefs(c).edit().putLong(KEY_PLAYLIST_TIME, System.currentTimeMillis()).apply();
        }
        boolean ok2 = downloadTo(EPG_URL, epgFile(c));
        if (ok2) {
            prefs(c).edit().putLong(KEY_EPG_TIME, System.currentTimeMillis()).apply();
        }
        return ok || ok2;
    }
}
