package com.tadiotv.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * จับคู่ tvg-id ของเพลย์ลิสต์ กับ channel id ของผังรายการ
 * ใช้ตารางแมปใน assets/epg_map.json ก่อน ถ้าไม่พบจะเดาจากชื่อ
 */
public class EpgMap {

    private final Map<String, String> explicit = new HashMap<String, String>();

    public static EpgMap load(Context ctx) {
        EpgMap map = new EpgMap();
        try {
            InputStream is = ctx.getAssets().open("epg_map.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONObject obj = root.optJSONObject("map");
            if (obj != null) {
                java.util.Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    map.explicit.put(k, obj.optString(k));
                }
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    public String resolve(String tvgId, String name, Set<String> available) {
        if (tvgId != null && explicit.containsKey(tvgId)) {
            return explicit.get(tvgId);
        }
        String key = norm(tvgId == null || tvgId.length() == 0 ? name : tvgId);
        for (String id : available) {
            if (norm(id).equals(key)) {
                return id;
            }
        }
        String alias = aliasOf(key);
        if (alias != null && available.contains(alias)) {
            return alias;
        }
        String key2 = norm(name);
        for (String id : available) {
            if (norm(id).equals(key2)) {
                return id;
            }
        }
        String alias2 = aliasOf(key2);
        if (alias2 != null && available.contains(alias2)) {
            return alias2;
        }
        return null;
    }

    private static String aliasOf(String key) {
        if ("tpbs".equals(key)) {
            return "iptv36.thaipbs";
        }
        if ("5hd".equals(key) || "tv5".equals(key)) {
            return "iptv36.tv5";
        }
        if ("3hd".equals(key) || "ch3".equals(key)) {
            return "iptv36.ch3";
        }
        if ("7hd".equals(key) || "ch7".equals(key)) {
            return "iptv36.ch7";
        }
        if ("ช่อง8".equals(key) || "ch8".equals(key)) {
            return "iptv36.ch8";
        }
        if ("tnn".equals(key) || "tnn16".equals(key)) {
            return "iptv36.tnn16";
        }
        if (key.startsWith("monomax")) {
            return "iptv36.mono29";
        }
        return null;
    }

    static String norm(String s) {
        if (s == null) {
            return "";
        }
        String v = s.toLowerCase(Locale.US).trim();
        if (v.startsWith("iptv36.")) {
            v = v.substring(7);
        }
        int dot = v.lastIndexOf('.');
        if (dot > 0 && v.length() - dot <= 4) {
            v = v.substring(0, dot);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isLetterOrDigit(c) || (c >= 0x0E00 && c <= 0x0E7F)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
