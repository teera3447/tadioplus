package com.tadiotv.app;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Android เก่า (ก่อน 7.1) ไม่รู้จักใบรับรองรากชุดใหม่ เช่น ISRG Root X1 (Let's Encrypt)
 * จึงต่อ https ของหลายเว็บ/สตรีมไม่ได้ ที่นี่แนบใบรับรองรากของ Mozilla ไปกับแอป (res/raw/cacerts.pem)
 * แล้วใช้เมื่อชุดของระบบตรวจไม่ผ่าน ความปลอดภัยเทียบเท่ากับ Android รุ่นใหม่ (ยังตรวจใบรับรองจริง)
 */
final class LegacyTrust {

    private LegacyTrust() {
    }

    static SSLSocketFactory build(Context ctx) throws Exception {
        TrustManagerFactory sysF = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        sysF.init((KeyStore) null);
        X509TrustManager sys = x509(sysF.getTrustManagers());

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        String pem = readRaw(ctx, R.raw.cacerts);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        String begin = "-----BEGIN CERTIFICATE-----";
        int pos = 0;
        int n = 0;
        while ((pos = pem.indexOf(begin, pos)) >= 0) {
            int end = pem.indexOf("-----END CERTIFICATE-----", pos);
            if (end < 0) {
                break;
            }
            end += "-----END CERTIFICATE-----".length();
            try {
                X509Certificate c = (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(pem.substring(pos, end).getBytes("US-ASCII")));
                ks.setCertificateEntry("ca" + (n++), c);
            } catch (Throwable ignored) {   // ใบรับรองที่เครื่องนี้ถอดรหัสไม่ได้ ข้ามไป
            }
            pos = end;
        }

        TrustManagerFactory appF = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        appF.init(ks);
        X509TrustManager app = x509(appF.getTrustManagers());

        SSLContext sc = SSLContext.getInstance("TLSv1.2");
        sc.init(null, new TrustManager[]{new Composite(sys, app)}, null);
        return sc.getSocketFactory();
    }

    private static X509TrustManager x509(TrustManager[] tms) {
        for (TrustManager tm : tms) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("no X509TrustManager");
    }

    private static String readRaw(Context ctx, int id) throws Exception {
        InputStream in = ctx.getResources().openRawResource(id);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                bos.write(buf, 0, r);
            }
            return bos.toString("US-ASCII");
        } finally {
            in.close();
        }
    }

    /** ลองชุดของระบบก่อน -> ชุดของแอป -> ชุดของแอปโดยตัดใบรับรองท้ายสายที่หมดอายุ/ข้ามสาย (cross-sign) ออก */
    private static final class Composite implements X509TrustManager {
        private final X509TrustManager sys;
        private final X509TrustManager app;

        Composite(X509TrustManager sys, X509TrustManager app) {
            this.sys = sys;
            this.app = app;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            sys.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException first;
            try {
                sys.checkServerTrusted(chain, authType);
                return;
            } catch (CertificateException e) {
                first = e;
            }
            try {
                app.checkServerTrusted(chain, authType);
                return;
            } catch (CertificateException ignored) {
            }
            // Let's Encrypt มักส่งสายที่ลงท้ายด้วย ISRG Root X1 แบบ cross-sign ซึ่งหมดอายุแล้ว
            // Android เก่าจะยึดสายนั้นแล้วปฏิเสธ จึงลองตัดท้ายสายออกให้ไปจบที่ราก "ตัวจริง" ในชุดของแอป
            for (int k = chain.length - 1; k >= 1; k--) {
                try {
                    app.checkServerTrusted(Arrays.copyOf(chain, k), authType);
                    return;
                } catch (CertificateException ignored) {
                }
            }
            throw first;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = sys.getAcceptedIssuers();
            X509Certificate[] b = app.getAcceptedIssuers();
            X509Certificate[] all = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }
    }
}
