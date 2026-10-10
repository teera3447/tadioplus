package com.tadiotv.app;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * อ่านผังรายการ EPG (รูปแบบ XMLTV) แบบสตรีมมิ่งด้วย XmlPullParser
 * จึงอ่านไฟล์ใหญ่ได้โดยไม่กินหน่วยความจำมาก และคัดเฉพาะรายการในช่วงเวลาที่ต้องการ
 */
public class EpgParser {

    private static final SimpleDateFormat FMT_SPACE = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
    private static final SimpleDateFormat FMT_PLAIN = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US);

    public static Map<String, List<EpgProgram>> parse(InputStream in, long windowStart, long windowEnd) {
        Map<String, List<EpgProgram>> out = new HashMap<String, List<EpgProgram>>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(in, null);

            int ev = p.getEventType();
            String channel = null;
            long start = 0;
            long stop = 0;
            boolean keep = false;
            String title = null;
            String desc = null;

            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("programme".equals(tag)) {
                        channel = p.getAttributeValue(null, "channel");
                        start = time(p.getAttributeValue(null, "start"));
                        stop = time(p.getAttributeValue(null, "stop"));
                        title = null;
                        desc = null;
                        keep = channel != null && start > 0 && stop > start
                                && stop > windowStart && start < windowEnd;
                    } else if (keep && "title".equals(tag)) {
                        title = readText(p);
                    } else if (keep && "desc".equals(tag)) {
                        desc = readText(p);
                    }
                } else if (ev == XmlPullParser.END_TAG && "programme".equals(p.getName())) {
                    if (keep) {
                        List<EpgProgram> list = out.get(channel);
                        if (list == null) {
                            list = new ArrayList<EpgProgram>();
                            out.put(channel, list);
                        }
                        String t = (title == null || title.trim().length() == 0) ? "(ไม่ระบุชื่อรายการ)" : title.trim();
                        list.add(new EpgProgram(start, stop, t, desc == null ? "" : desc.trim()));
                    }
                    keep = false;
                }
                ev = p.next();
            }
        } catch (Throwable ignored) {
            // ไฟล์อาจถูกตัดกลางทาง - ใช้เท่าที่อ่านได้
        }

        for (List<EpgProgram> list : out.values()) {
            Collections.sort(list, new Comparator<EpgProgram>() {
                @Override
                public int compare(EpgProgram a, EpgProgram b) {
                    if (a.start < b.start) {
                        return -1;
                    }
                    return a.start > b.start ? 1 : 0;
                }
            });
        }
        return out;
    }

    /** อ่านข้อความจนจบแท็ก (ปลอดภัยกว่าการใช้ nextText เมื่อข้อความยาว) */
    private static String readText(XmlPullParser p) throws Exception {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int e = p.next();
            if (e == XmlPullParser.TEXT) {
                sb.append(p.getText());
            } else if (e == XmlPullParser.START_TAG) {
                depth++;
            } else if (e == XmlPullParser.END_TAG) {
                depth--;
            } else if (e == XmlPullParser.END_DOCUMENT) {
                break;
            }
        }
        return sb.toString();
    }

    public static long time(String value) {
        if (value == null) {
            return 0;
        }
        String v = value.trim();
        try {
            synchronized (FMT_SPACE) {
                return FMT_SPACE.parse(v).getTime();
            }
        } catch (Throwable ignored) {
        }
        try {
            synchronized (FMT_PLAIN) {
                return FMT_PLAIN.parse(v).getTime();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
