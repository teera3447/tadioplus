package com.tadiotv.app;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ข้อมูล 1 ช่องที่ได้จากการแกะเพลย์ลิสต์ M3U
 */
public class Channel {

    public static final int KIND_HLS = 0;          // .m3u8
    public static final int KIND_PROGRESSIVE = 1;  // ไฟล์วิดีโอตรง
    public static final int KIND_DASH = 2;         // .mpd (เครื่องรุ่นเก่าเล่นไม่ได้)
    public static final int KIND_INVALID = 3;      // ลิงก์ว่าง / ไม่ถูกรูปแบบ

    public final String id;        // tvg-id
    public final String name;
    public final String group;
    public final String url;
    public final String logo;
    public final String chno;
    public final Map<String, String> headers;   // จาก #EXTVLCOPT
    public final int kind;
    public final boolean drm;

    public String epgId;           // channel id ที่ใช้กับ EPG (จับคู่ให้ภายหลัง)

    public Channel(String id, String name, String group, String url, String logo,
                   String chno, Map<String, String> headers, int kind, boolean drm) {
        this.id = id;
        this.name = name;
        this.group = group;
        this.url = url;
        this.logo = logo;
        this.chno = chno;
        this.headers = headers == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, String>(headers));
        this.kind = kind;
        this.drm = drm;
    }

    public String key() {
        return id + "|" + url;
    }

    public boolean playable() {
        return kind == KIND_HLS || kind == KIND_PROGRESSIVE;
    }

    /** เหตุผลที่เล่นไม่ได้ ใช้แสดงให้ผู้ใช้ทราบ */
    public String problem() {
        if (kind == KIND_INVALID) {
            return "ลิงก์สตรีมว่างหรือไม่ถูกรูปแบบ";
        }
        if (kind == KIND_DASH) {
            return "เป็นสตรีม MPEG-DASH (.mpd) ซึ่งเครื่อง Android รุ่นเก่าเล่นไม่ได้";
        }
        if (drm) {
            return "สตรีมมีการเข้ารหัส (DRM) ต้องมีคีย์จึงเล่นได้";
        }
        return "";
    }
}
