package com.tadiotv.app;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * วิทยุออนไลน์ มี 2 หน้าในแอคทิวิตีเดียว
 *  1) รายการสถานี (ค้นหา / แตะเพื่อเล่น)
 *  2) หน้า Player หน้าตาเครื่องวิทยุ: จอ LCD ชื่อสถานี+คลื่น, visualizer, หน้าปัดจูนคลื่น, ลูกบิดควบคุม
 * ลิงก์สถานีดึงใหม่จาก GitHub ทุกครั้งที่เปิดหน้านี้ และเมื่อสถานีที่เล่นเสีย
 */
public class RadioActivity extends AppCompatActivity {

    private static final int MAX_RETRY = 3;
    private static final Pattern FREQ = Pattern.compile("(\\d{2,3}(?:\\.\\d)?)");

    private ExoPlayer player;
    private final SpectrumAnalyzer analyzer = new SpectrumAnalyzer();

    // หน้ารายการ
    private View listPage, miniBar;
    private RecyclerView recycler;
    private ChannelAdapter adapter;
    private EditText etSearch;
    private TextView tvEmpty, miniName, miniStatus, miniPlay;

    // หน้า player
    private View playerPage, led;
    private TextView pName, pBand, pFreq, pUnit, pStatus, pOnAir, pPlay;
    private SpectrumView spectrum;
    private RadioDialView dial;

    private final List<Channel> all = new ArrayList<Channel>();
    private final List<Channel> shown = new ArrayList<Channel>();
    private Channel current;
    private String query = "";
    private int retryCount = 0;
    private boolean destroyed = false;
    private boolean refreshedOnError = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && current != null && !destroyed) {
                player.prepare();
                player.setPlayWhenReady(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio);
        bindViews();
        setupList();
        setupPlayerPage();
        setupPlayer();
        loadCached();
    }

    private void bindViews() {
        listPage = findViewById(R.id.radioListPage);
        miniBar = findViewById(R.id.radioMini);
        recycler = findViewById(R.id.radioList);
        etSearch = findViewById(R.id.radioSearch);
        tvEmpty = findViewById(R.id.radioEmpty);
        miniName = findViewById(R.id.radioMiniName);
        miniStatus = findViewById(R.id.radioMiniStatus);
        miniPlay = findViewById(R.id.radioMiniPlay);

        playerPage = findViewById(R.id.radioPlayerPage);
        led = findViewById(R.id.pLed);
        pName = findViewById(R.id.pName);
        pBand = findViewById(R.id.pBand);
        pFreq = findViewById(R.id.pFreq);
        pUnit = findViewById(R.id.pUnit);
        pStatus = findViewById(R.id.pStatus);
        pOnAir = findViewById(R.id.pOnAir);
        pPlay = findViewById(R.id.pPlay);
        spectrum = findViewById(R.id.pSpectrum);
        dial = findViewById(R.id.pDial);
        spectrum.setAnalyzer(analyzer);
        pName.setSelected(true); // ให้ชื่อยาว ๆ วิ่งเป็น marquee
    }

    /* ============================== หน้ารายการ ============================== */

    private void setupList() {
        adapter = new ChannelAdapter();
        adapter.setListener(new ChannelAdapter.OnChannelClick() {
            @Override
            public void onClick(Channel channel) {
                hideKeyboard();
                playChannel(channel);
                if (current != null) {
                    showPlayerPage(true);
                }
            }
        });
        recycler.setLayoutManager(new GridLayoutManager(this, 3));
        recycler.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim().toLowerCase(Locale.getDefault());
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        findViewById(R.id.radioBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.radioRefresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(RadioActivity.this, R.string.refreshing, Toast.LENGTH_SHORT).show();
                refreshFromNet(true);
            }
        });
        miniBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (current != null) {
                    showPlayerPage(true);
                }
            }
        });
        miniPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
    }

    private void setupPlayerPage() {
        findViewById(R.id.pBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPlayerPage(false);
            }
        });
        pPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        findViewById(R.id.pPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playByOffset(-1);
            }
        });
        findViewById(R.id.pNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playByOffset(1);
            }
        });
        ((SeekBar) findViewById(R.id.pVolume)).setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        if (player != null) {
                            player.setVolume(progress / 100f);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                    }
                });
    }

    private void showPlayerPage(boolean show) {
        playerPage.setVisibility(show ? View.VISIBLE : View.GONE);
        listPage.setVisibility(show ? View.GONE : View.VISIBLE);
        pName.setSelected(true);
    }

    @Override
    public void onBackPressed() {
        if (playerPage.getVisibility() == View.VISIBLE) {
            showPlayerPage(false);
        } else {
            super.onBackPressed();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }

    /* ============================== รายการสถานี ============================== */

    /** แสดงรายการจากแคช/ไฟล์สำรองทันที แล้วดึงลิงก์ใหม่จากเน็ตตามหลังอัตโนมัติ */
    private void loadCached() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Channel> list = ChannelStore.loadRadio(RadioActivity.this, false);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        all.clear();
                        all.addAll(list);
                        applyFilter();
                        refreshFromNet(false);
                    }
                });
            }
        }).start();
    }

    /**
     * ดึงรายการสถานีล่าสุดจาก GitHub (ทุกครั้งที่เปิดหน้านี้ / กดอัปเดต / เมื่อสถานีที่เล่นเสีย)
     * userAction = true จะแจ้งผลด้วย Toast
     */
    private void refreshFromNet(final boolean userAction) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = ChannelStore.refreshRadio(RadioActivity.this);
                final List<Channel> list = ok ? ChannelStore.loadRadio(RadioActivity.this, false) : null;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        boolean replayed = false;
                        if (list != null && !list.isEmpty()) {
                            all.clear();
                            all.addAll(list);
                            applyFilter();
                            replayed = replayIfLinkChanged();
                        }
                        // เล่นไม่ได้และไม่มีลิงก์ใหม่ให้ลอง -> แจ้งว่าสถานีนี้เสีย
                        if (!replayed && refreshedOnError && player != null
                                && player.getPlaybackState() == Player.STATE_IDLE) {
                            setStatus(R.string.radio_error);
                            setPlayGlyph(false);
                            Toast.makeText(RadioActivity.this, R.string.err_play,
                                    Toast.LENGTH_LONG).show();
                        }
                        if (userAction) {
                            Toast.makeText(RadioActivity.this,
                                    ok ? R.string.refresh_done : R.string.refresh_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    /** ถ้าสถานีที่กำลังเล่น/เพิ่งเล่นไม่ได้ มีลิงก์ใหม่ในรายการที่เพิ่งดึงมา ให้เล่นด้วยลิงก์ใหม่ */
    private boolean replayIfLinkChanged() {
        if (current == null) {
            return false;
        }
        for (Channel c : all) {
            if (c.name.equals(current.name)) {
                if (!c.url.equals(current.url)) {
                    boolean wasRefreshed = refreshedOnError;
                    playChannel(c);
                    refreshedOnError = wasRefreshed;
                    return true;
                }
                current = c;
                return false;
            }
        }
        return false;
    }

    private void applyFilter() {
        shown.clear();
        for (Channel c : all) {
            if (query.length() == 0
                    || c.name.toLowerCase(Locale.getDefault()).contains(query)
                    || c.group.toLowerCase(Locale.getDefault()).contains(query)) {
                shown.add(c);
            }
        }
        adapter.setData(shown);
        adapter.setSelectedKey(current == null ? "" : current.key());
        tvEmpty.setText(R.string.no_result);
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /* ================================ ตัวเล่น ================================ */

    private void setupPlayer() {
        // ดักสัญญาณเสียง PCM ที่กำลังเล่น ส่งให้ตัววิเคราะห์สเปกตรัม (ไม่ต้องขอสิทธิ์ไมค์)
        RenderersFactory renderers = new DefaultRenderersFactory(this) {
            @Override
            protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                               boolean enableAudioTrackPlaybackParams,
                                               boolean enableOffload) {
                return new DefaultAudioSink.Builder()
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new AudioProcessor[]{new TeeAudioProcessor(analyzer)})
                        .build();
            }
        };

        player = new ExoPlayer.Builder(this, renderers).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(), true);              // จัดการ audio focus (หยุดเมื่อมีสายเข้า)
        player.setHandleAudioBecomingNoisy(true); // ถอดหูฟังแล้วหยุดเล่น
        player.setWakeMode(C.WAKE_MODE_NETWORK);  // ไม่ให้ CPU/WiFi หลับขณะฟัง
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    retryCount = 0;
                }
                updateStatus();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateStatus();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (destroyed || player == null) {
                    return;
                }
                Throwable cause = error.getCause();
                if (cause instanceof BehindLiveWindowException) {
                    player.seekToDefaultPosition();
                    player.prepare();
                    return;
                }
                if (retryCount < MAX_RETRY && current != null) {
                    retryCount++;
                    setStatus(R.string.radio_connecting);
                    ui.removeCallbacks(retryRunnable);
                    ui.postDelayed(retryRunnable, 1500L * retryCount);
                    return;
                }
                if (!refreshedOnError) {
                    // ลิงก์อาจเปลี่ยน: ดึงรายการใหม่จาก GitHub หนึ่งครั้ง แล้วเล่นด้วยลิงก์ใหม่ถ้ามี
                    refreshedOnError = true;
                    setStatus(R.string.radio_relinking);
                    refreshFromNet(false);
                    return;
                }
                setStatus(R.string.radio_error);
                setPlayGlyph(false);
                spectrum.setActive(false);
                Toast.makeText(RadioActivity.this, R.string.err_play, Toast.LENGTH_LONG).show();
            }
        });
    }

    private DefaultHttpDataSource.Factory dataSource(Channel c) {
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android) TadioTV/2.0")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true);
        if (!c.headers.isEmpty()) {
            f.setDefaultRequestProperties(c.headers);
        }
        return f;
    }

    private void playChannel(Channel c) {
        if (c == null || player == null) {
            return;
        }
        if (!c.playable()) {
            Toast.makeText(this, R.string.err_play, Toast.LENGTH_LONG).show();
            return;
        }
        current = c;
        retryCount = 0;
        refreshedOnError = false;
        ui.removeCallbacks(retryRunnable);

        MediaItem item = MediaItem.fromUri(Uri.parse(c.url));
        MediaSource source;
        if (c.kind == Channel.KIND_HLS) {
            source = new HlsMediaSource.Factory(dataSource(c))
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(item);
        } else {
            source = new ProgressiveMediaSource.Factory(dataSource(c), new DefaultExtractorsFactory())
                    .createMediaSource(item);
        }
        player.setMediaSource(source);
        player.prepare();
        player.setPlayWhenReady(true);

        showStationInfo(c);
        setStatus(R.string.radio_connecting);
        setPlayGlyph(true);
        adapter.setSelectedKey(c.key());
    }

    /** ชื่อสถานี + เลขคลื่น (ถ้าชื่อมีเลข 88-108 เช่น FM97, 105.5, จส.100) */
    private void showStationInfo(Channel c) {
        miniName.setText(c.name);
        pName.setText(c.name);
        pName.setSelected(true);

        float f = parseFrequency(c.name);
        if (f > 0) {
            pBand.setText("FM");
            pFreq.setText(String.format(Locale.US, f == Math.round(f) ? "%.0f" : "%.1f", f));
            pFreq.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
            pUnit.setText("MHz");
        } else {
            pBand.setText("NET");
            pFreq.setText("ONLINE");
            pFreq.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            pUnit.setText("");
        }
        dial.setFrequency(f);
    }

    /** คืนค่าคลื่น 87.5-108 MHz จากชื่อสถานี หรือ 0 ถ้าไม่พบ */
    static float parseFrequency(String name) {
        Matcher m = FREQ.matcher(name);
        while (m.find()) {
            try {
                float f = Float.parseFloat(m.group(1));
                if (f >= 87.5f && f <= 108f) {
                    return f;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 0f;
    }

    private void togglePlay() {
        if (player == null) {
            return;
        }
        if (current == null) {
            if (!shown.isEmpty()) {
                playChannel(shown.get(0));
                showPlayerPage(true);
            }
            return;
        }
        if (player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_IDLE) {
            player.setPlayWhenReady(false);
        } else {
            retryCount = 0;
            if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare(); // หลังหยุดแล้วหรือเกิด error ให้เชื่อมต่อสตรีมสดใหม่
            }
            player.setPlayWhenReady(true);
        }
        updateStatus();
    }

    private void playByOffset(int delta) {
        if (shown.isEmpty()) {
            return;
        }
        int idx = -1;
        if (current != null) {
            for (int i = 0; i < shown.size(); i++) {
                if (shown.get(i).key().equals(current.key())) {
                    idx = i;
                    break;
                }
            }
        }
        int next = idx < 0 ? 0 : (idx + delta + shown.size()) % shown.size();
        playChannel(shown.get(next));
        recycler.scrollToPosition(next);
    }

    /* ================================ แสดงผล ================================ */

    private void setStatus(int res) {
        miniStatus.setText(res);
        pStatus.setText(res);
    }

    private void setPlayGlyph(boolean playing) {
        String g = playing ? "❚❚" : "▶";
        miniPlay.setText(g);
        pPlay.setText(g);
    }

    private void updateStatus() {
        if (player == null || current == null) {
            return;
        }
        int st = player.getPlaybackState();
        boolean wantPlay = player.getPlayWhenReady() && st != Player.STATE_IDLE && st != Player.STATE_ENDED;
        boolean audible = player.isPlaying();

        setPlayGlyph(wantPlay);
        spectrum.setActive(audible);
        led.setBackgroundResource(audible ? R.drawable.bg_led_on : R.drawable.bg_led_off);
        pOnAir.setTextColor(audible ? 0xFFFFE9B0 : 0xFF8A7A5A);

        if (wantPlay && st == Player.STATE_BUFFERING) {
            setStatus(R.string.radio_buffering);
        } else if (audible) {
            setStatus(R.string.radio_playing);
        } else if (!wantPlay && st == Player.STATE_READY) {
            setStatus(R.string.radio_paused);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
