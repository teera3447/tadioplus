package com.tadiotv.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ตัวแกะเพลย์ลิสต์ M3U/M3U8
 * รองรับ #EXTINF (tvg-id, tvg-name, tvg-logo, group-title, tvg-chno, ch-number)
 * #EXTVLCOPT:http-user-agent / http-referrer  -> ส่งเป็นเฮดเดอร์ให้ตัวเล่น
 * #KODIPROP (license/drm)                     -> ทำเครื่องหมายว่าเป็น DRM
 */
public class M3uParser {

    private static final Pattern ATTR = Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");

    private static class Pending {
        Map<String, String> attrs = new HashMap<String, String>();
        List<String> opts = new ArrayList<String>();
        String title = "";
        boolean drm = false;
    }

    public static List<Channel> parse(InputStream in) throws IOException {
        List<Channel> out = new ArrayList<Channel>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        Pending p = null;
        String line;
        int index = 0;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.startsWith("#EXTM3U")) {
                continue;
            }
            if (line.startsWith("#EXTINF")) {
                p = new Pending();
                Matcher m = ATTR.matcher(line);
                while (m.find()) {
                    p.attrs.put(m.group(1).toLowerCase(Locale.US), m.group(2));
                }
                int comma = line.indexOf(',');
                p.title = comma >= 0 ? line.substring(comma + 1).trim() : "";
                continue;
            }
            if (line.startsWith("#EXTVLCOPT") || line.startsWith("#EXTHTTP")
                    || line.startsWith("#KODIPROP") || line.startsWith("#EXTGRP")) {
                if (p != null) {
                    p.opts.add(line);
                    String low = line.toLowerCase(Locale.US);
                    if (low.contains("license") || low.contains("drm")) {
                        p.drm = true;
                    }
                }
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            if (p == null) {
                continue;
            }
            index++;
            out.add(build(p, line, index));
            p = null;
        }
        reader.close();
        return out;
    }

    private static Channel build(Pending p, String url, int index) {
        String name = p.title;
        if (name.length() == 0) {
            name = value(p, "tvg-name");
        }
        if (name.length() == 0) {
            name = "Channel " + index;
        }
        String id = value(p, "tvg-id");
        if (id.length() == 0) {
            id = "ch" + index;
        }
        String group = value(p, "group-title");
        if (group.length() == 0) {
            group = "อื่น ๆ";
        }
        String chno = value(p, "tvg-chno");
        if (chno.length() == 0) {
            chno = value(p, "ch-number");
        }

        Map<String, String> headers = new HashMap<String, String>();
        for (String opt : p.opts) {
            String v = opt;
            int c = v.indexOf(':');
            if (c > 0) {
                v = v.substring(c + 1);
            }
            int eq = v.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = v.substring(0, eq).trim().toLowerCase(Locale.US);
            String val = v.substring(eq + 1).trim();
            if (k.endsWith("http-user-agent")) {
                headers.put("User-Agent", val);
            } else if (k.endsWith("http-referrer") || k.endsWith("http-referer")) {
                headers.put("Referer", val);
            } else if (k.endsWith("http-origin")) {
                headers.put("Origin", val);
            } else if (k.endsWith("http-cookie")) {
                headers.put("Cookie", val);
            }
        }

        String lower = url.toLowerCase(Locale.US);
        int kind;
        if (!lower.startsWith("http") || lower.length() < 12 || lower.equals("https://") || lower.equals("http://")) {
            kind = Channel.KIND_INVALID;
        } else if (lower.contains(".mpd")) {
            kind = Channel.KIND_DASH;
        } else if (lower.contains(".m3u8") || lower.contains("m3u8")) {
            kind = Channel.KIND_HLS;
        } else {
            kind = Channel.KIND_PROGRESSIVE;
        }

        return new Channel(id, name, group, url, value(p, "tvg-logo"), chno, headers, kind, p.drm);
    }

    private static String value(Pending p, String key) {
        String v = p.attrs.get(key);
        return v == null ? "" : v.trim();
    }
}
