package com.tadiotv.app;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * แปลงเสียงที่ ExoPlayer กำลังเล่นให้เป็นค่าสเปกตรัม (FFT) แบบเรียลไทม์
 * รับ PCM ผ่าน TeeAudioProcessor จึงไม่ต้องขอสิทธิ์ RECORD_AUDIO
 * ปรับความไวของแท่งได้ที่ FLOOR_DB / CEIL_DB / TILT_DB_PER_OCT
 */
public class SpectrumAnalyzer implements TeeAudioProcessor.AudioBufferSink {

    public static final int BARS = 32;
    private static final int N = 1024;                 // จำนวนตัวอย่างต่อการวิเคราะห์ 1 ครั้ง
    private static final float FLOOR_DB = -80f;        // เสียงเบากว่านี้ = แท่งต่ำสุด
    private static final float CEIL_DB = -15f;         // ดังเท่านี้ = แท่งเต็ม
    private static final float TILT_DB_PER_OCT = 2.5f; // ยกความถี่สูงขึ้นเล็กน้อย (เพลงมีพลังงานสูงน้อยกว่า)
    private static final float F_MIN = 50f;

    private final float[] window = new float[N];
    private final float[] cosT = new float[N / 2];
    private final float[] sinT = new float[N / 2];
    private final float[] re = new float[N];
    private final float[] im = new float[N];
    private final float[] frame = new float[N];
    private final int[] binLo = new int[BARS];
    private final int[] binHi = new int[BARS];
    private final float[] tilt = new float[BARS];

    private int filled = 0;
    private int channels = 2;
    private int encoding = C.ENCODING_PCM_16BIT;
    private volatile float[] bars = new float[BARS];
    private volatile long lastNanos = 0;

    public SpectrumAnalyzer() {
        for (int i = 0; i < N; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
        }
        for (int k = 0; k < N / 2; k++) {
            cosT[k] = (float) Math.cos(2 * Math.PI * k / N);
            sinT[k] = (float) -Math.sin(2 * Math.PI * k / N);
        }
        configure(44100);
    }

    private void configure(int sampleRate) {
        float binHz = sampleRate / (float) N;
        float fMax = Math.min(14000f, sampleRate / 2f * 0.9f);
        for (int i = 0; i < BARS; i++) {
            float f0 = (float) (F_MIN * Math.pow(fMax / F_MIN, i / (double) BARS));
            float f1 = (float) (F_MIN * Math.pow(fMax / F_MIN, (i + 1) / (double) BARS));
            int lo = Math.max(1, Math.round(f0 / binHz));
            int hi = Math.max(lo + 1, Math.round(f1 / binHz));
            binLo[i] = Math.min(lo, N / 2 - 2);
            binHi[i] = Math.min(hi, N / 2 - 1);
            float centre = (float) Math.sqrt(f0 * f1);
            tilt[i] = TILT_DB_PER_OCT * (float) (Math.log(centre / F_MIN) / Math.log(2));
        }
    }

    /* ---------- เรียกจาก thread ของตัวเล่นเสียง ---------- */

    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding) {
        this.channels = Math.max(1, channelCount);
        this.encoding = encoding;
        this.filled = 0;
        configure(sampleRateHz > 0 ? sampleRateHz : 44100);
    }

    @Override
    public void handleBuffer(ByteBuffer buffer) {
        boolean pcm16 = encoding == C.ENCODING_PCM_16BIT;
        boolean pcmFloat = encoding == C.ENCODING_PCM_FLOAT;
        if (!pcm16 && !pcmFloat) {
            return;
        }
        int bytesPerSample = pcm16 ? 2 : 4;
        int frameBytes = bytesPerSample * channels;
        ByteBuffer b = buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = b.position(); i + frameBytes <= b.limit(); i += frameBytes) {
            float sum = 0;
            for (int c = 0; c < channels; c++) {
                int p = i + c * bytesPerSample;
                sum += pcm16 ? b.getShort(p) / 32768f : b.getFloat(p);
            }
            frame[filled++] = sum / channels;
            if (filled == N) {
                analyze();
                filled = 0;
            }
        }
    }

    private void analyze() {
        for (int i = 0; i < N; i++) {
            re[i] = frame[i] * window[i];
            im[i] = 0f;
        }
        fft();
        float[] out = new float[BARS];
        for (int b = 0; b < BARS; b++) {
            float max = 0f;
            for (int k = binLo[b]; k < binHi[b]; k++) {
                float mag = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
                if (mag > max) {
                    max = mag;
                }
            }
            float amp = 4f * max / N; // ไซน์เต็มสเกล ~ 1.0 (0 dBFS) เมื่อใช้หน้าต่าง Hann
            float db = 20f * (float) Math.log10(amp + 1e-9f) + tilt[b];
            float level = (db - FLOOR_DB) / (CEIL_DB - FLOOR_DB);
            out[b] = level < 0f ? 0f : (level > 1f ? 1f : level);
        }
        bars = out;
        lastNanos = System.nanoTime();
    }

    /** FFT radix-2 แบบ in-place */
    private void fft() {
        int j = 0;
        for (int i = 1; i < N; i++) {
            int bit = N >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= N; len <<= 1) {
            int half = len >> 1;
            int step = N / len;
            for (int i = 0; i < N; i += len) {
                for (int k = 0; k < half; k++) {
                    float wr = cosT[k * step];
                    float wi = sinT[k * step];
                    int a = i + k;
                    int c = a + half;
                    float vr = re[c] * wr - im[c] * wi;
                    float vi = re[c] * wi + im[c] * wr;
                    re[c] = re[a] - vr;
                    im[c] = im[a] - vi;
                    re[a] += vr;
                    im[a] += vi;
                }
            }
        }
    }

    /* ---------- เรียกจาก UI thread ---------- */

    /** true ถ้ามีเสียงไหลผ่านในช่วง 0.25 วินาทีล่าสุด */
    public boolean isFresh() {
        return System.nanoTime() - lastNanos < 250000000L;
    }

    public void copyBars(float[] dst) {
        float[] src = bars;
        System.arraycopy(src, 0, dst, 0, Math.min(dst.length, src.length));
    }
}
