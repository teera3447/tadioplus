package com.tadiotv.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Tadio TV - หน้าจอหลัก
 * - โหลดรายการช่องจากเพลย์ลิสต์ M3U (อัปเดตอัตโนมัติทุกวัน)
 * - โหลดผังรายการ EPG (XMLTV) อัปเดตทุกวัน
 * - ค้นหาช่อง, ช่องโปรด, เต็มจอหมุนแนวนอน, เลือกคุณภาพสตรีม
 * รองรับ Android 4.2 (API 17) ขึ้นไป
 */
public class MainActivity extends AppCompatActivity {

    /* Android 4.2/4.3 ยังไม่เปิด TLS 1.2 ให้เอง ทำให้สตรีม https เล่นไม่ได้ */
    static {
        try {
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Throwable ignored) {
        }
    }

    private static final String PREF = "tadio_pref";
    private static final String KEY_FAV = "favorites";
    private static final String KEY_LAST = "last_channel";
    private static final String ALL = "__ALL__";
    private static final long DAY_MS = 24L * 3600 * 1000;

    private ExoPlayer player;
    private PlayerView playerView;
    private DefaultTrackSelector trackSelector;

    private LinearLayout contentRoot;
    private View topPanel;
    private TextView tvEmpty;
    private LinearLayout tabBar;
    private RecyclerView recycler;
    private ChannelAdapter adapter;
    private EditText etSearch;

    private View playerContainer, infoRow, buttonsRow, sectionLabel, bottomBar, listArea;
    private TextView tvChannelName, tvNow, tvChno, tvSection, tvUpdated;
    private TextView btnFavorites, btnAll, btnFavIcon, btnFullscreen, btnQuality;

    private final List<Channel> all = new ArrayList<Channel>();
    private final List<Channel> shown = new ArrayList<Channel>();
    private final Set<String> favorites = new HashSet<String>();
    private Map<String, List<EpgProgram>> epg = new LinkedHashMap<String, List<EpgProgram>>();
    private final Set<String> groups = new LinkedHashSet<String>();

    private SharedPreferences prefs;
    private EpgMap epgMap;
    private Channel current;
    private String activeGroup = ALL;
    private String query = "";
    private boolean favoritesMode = false;
    private boolean fullscreen = false;
    private boolean destroyed = false;
    private boolean topCollapsed = false;
    private long ignoreScrollUntil = 0;
    private int retryCount = 0;
    private static final int MAX_RETRY = 3;

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
    private final Runnable nowTicker = new Runnable() {
        @Override
        public void run() {
            updateNowLine();
            ui.postDelayed(this, 30000);
        }
    };

    /* ================================ lifecycle ================================ */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet(KEY_FAV, new HashSet<String>()));

        bindViews();
        setupPlayer();
        applyPlayerHeight(false);
        setupGrid();
        setupButtons();
        setupSearch();
        scheduleDailyRefresh();
        loadData();

        ui.post(nowTicker);
    }

    private void bindViews() {
        contentRoot = findViewById(R.id.contentRoot);
        topPanel = findViewById(R.id.topPanel);
        tvEmpty = findViewById(R.id.tvEmpty);
        playerContainer = findViewById(R.id.playerContainer);
        infoRow = findViewById(R.id.infoRow);
        buttonsRow = findViewById(R.id.buttonsRow);
        sectionLabel = findViewById(R.id.sectionLabel);
        bottomBar = findViewById(R.id.bottomBar);
        listArea = findViewById(R.id.listArea);

        playerView = findViewById(R.id.playerView);
        tabBar = findViewById(R.id.tabBar);
        recycler = findViewById(R.id.recycler);
        etSearch = findViewById(R.id.etSearch);

        tvChannelName = findViewById(R.id.tvChannelName);
        tvNow = findViewById(R.id.tvNow);
        tvChno = findViewById(R.id.tvChno);
        tvSection = findViewById(R.id.tvSection);
        tvUpdated = findViewById(R.id.tvUpdated);

        btnFavorites = findViewById(R.id.btnFavorites);
        btnAll = findViewById(R.id.btnAll);
        btnFavIcon = findViewById(R.id.btnFavIcon);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        btnQuality = findViewById(R.id.btnQuality);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && current != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.setPlayWhenReady(false);
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

    /** กลับเข้าแอป/ปิดไดอะล็อกแล้ว Android มักเอาโหมดเต็มจอออก จึงตั้งซ้ำทุกครั้งที่หน้าต่างได้โฟกัส */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreen) {
            setSystemUiVisible(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            setFullscreen(false);
            return;
        }
        super.onBackPressed();
    }

    /* ============================== โหลดข้อมูล ============================== */

    private void loadData() {
        tvUpdated.setText(R.string.loading_data);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean firstRun = ChannelStore.lastPlaylistTime(MainActivity.this) == 0;
                boolean stale = System.currentTimeMillis() - ChannelStore.lastPlaylistTime(MainActivity.this) > DAY_MS;
                final List<Channel> list = ChannelStore.loadChannels(MainActivity.this, firstRun || stale);

                boolean epgStale = System.currentTimeMillis() - ChannelStore.lastEpgTime(MainActivity.this) > DAY_MS;
                final Map<String, List<EpgProgram>> epgData =
                        ChannelStore.loadEpg(MainActivity.this, firstRun || epgStale);
                final EpgMap map = EpgMap.load(MainActivity.this);

                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        all.clear();
                        all.addAll(list);
                        epg = epgData;
                        epgMap = map;

                        Set<String> epgIds = new HashSet<String>(epg.keySet());
                        for (Channel c : all) {
                            c.epgId = map.resolve(c.id, c.name, epgIds);
                        }

                        groups.clear();
                        for (Channel c : all) {
                            groups.add(c.group);
                        }

                        buildTabs();
                        refreshList();
                        updateUpdatedLabel();

                        if (current == null) {
                            Channel start = restoreLast();
                            if (start != null) {
                                playChannel(start);
                            }
                        }
                        updateNowLine();
                    }
                });
            }
        }).start();
    }

    private Channel restoreLast() {
        String key = prefs.getString(KEY_LAST, "");
        if (key.length() > 0) {
            for (Channel c : all) {
                if (c.key().equals(key) && c.playable()) {
                    return c;
                }
            }
        }
        for (Channel c : all) {
            if (c.playable()) {
                return c;
            }
        }
        return null;
    }

    private void updateUpdatedLabel() {
        long t = ChannelStore.lastPlaylistTime(this);
        long e = ChannelStore.lastEpgTime(this);
        String when = t == 0
                ? getString(R.string.never_updated)
                : new SimpleDateFormat("d MMM HH:mm", new Locale("th", "TH")).format(new Date(t));
        String epgWhen = e == 0 ? "-"
                : new SimpleDateFormat("d MMM HH:mm", new Locale("th", "TH")).format(new Date(e));
        tvUpdated.setText(getString(R.string.updated_at, when, epgWhen));
    }

    /** ตั้งให้อัปเดตรายการช่อง + ผังรายการทุกวันผ่าน WorkManager */
    private void scheduleDailyRefresh() {
        try {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(RefreshWorker.class, 24, TimeUnit.HOURS)
                    .setInitialDelay(2, TimeUnit.HOURS)
                    .addTag("tadio_daily")
                    .build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "tadio_daily_refresh", ExistingPeriodicWorkPolicy.KEEP, req);
        } catch (Throwable ignored) {
        }
    }

    /* ================================ ผู้เล่น ================================ */

    private void setupPlayer() {
        trackSelector = new DefaultTrackSelector(this);
        player = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    retryCount = 0;
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (destroyed || player == null) {
                    return;
                }
                // สตรีมสดเลื่อนเลยช่วงที่เก็บไว้ -> กระโดดไปปัจจุบันแล้วเล่นต่อ
                Throwable cause = error.getCause();
                if (cause instanceof BehindLiveWindowException) {
                    player.seekToDefaultPosition();
                    player.prepare();
                    return;
                }
                // ความผิดพลาดชั่วคราว (เน็ตหลุด/สตรีมสะดุด) ลองใหม่อัตโนมัติสูงสุด 3 ครั้ง
                if (retryCount < MAX_RETRY && current != null) {
                    retryCount++;
                    ui.removeCallbacks(retryRunnable);
                    ui.postDelayed(retryRunnable, 1500L * retryCount);
                    return;
                }
                Toast.makeText(MainActivity.this, R.string.err_play, Toast.LENGTH_LONG).show();
            }
        });
        playerView.setPlayer(player);
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
        if (c == null) {
            return;
        }
        if (!c.playable()) {
            new AlertDialog.Builder(this)
                    .setTitle(c.name)
                    .setMessage(getString(R.string.cannot_play, c.problem()))
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        current = c;
        retryCount = 0;
        ui.removeCallbacks(retryRunnable);
        hideKeyboard();
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

        applyQuality(0, 0);

        tvChannelName.setText(c.name);
        tvChno.setText(c.chno.length() > 0 ? getString(R.string.ch_no, c.chno) : c.group);
        tvChno.setVisibility(View.VISIBLE);
        btnQuality.setText(R.string.quality_auto);

        adapter.setSelectedKey(c.key());
        updateFavoriteIcon();
        updateNowLine();
        prefs.edit().putString(KEY_LAST, c.key()).apply();
    }

    private void playByOffset(int step) {
        List<Channel> pool = shown.isEmpty() ? all : shown;
        int n = pool.size();
        if (n == 0) {
            return;
        }
        int index = pool.indexOf(current);
        if (index < 0) {
            index = step > 0 ? -1 : 0;
        }
        // ข้ามช่องที่เล่นไม่ได้ จะได้ไม่เด้งกล่องข้อผิดพลาดตอนกดก่อนหน้า/ถัดไป
        for (int i = 0; i < n; i++) {
            index = ((index + step) % n + n) % n;
            Channel c = pool.get(index);
            if (c.playable()) {
                playChannel(c);
                scrollListTo(index);
                return;
            }
        }
    }

    private void scrollListTo(int index) {
        if (recycler != null && !fullscreen && index >= 0 && index < adapter.getItemCount()) {
            recycler.scrollToPosition(index);
        }
    }

    /* ============================ เต็มจอ / คุณภาพ ============================ */

    /**
     * เข้า/ออกโหมดเต็มจอ
     * - ซ่อนทุกส่วนที่ไม่ใช่วิดีโอ (รวมถึง topPanel ที่มีช่องค้นหา ซึ่งเดิมหลุดซ่อนไม่ครบ)
     * - ให้ playerContainer สูงแบบ MATCH_PARENT จึงเต็มจอพอดี ไม่ต้องคำนวณความสูงจอ
     *   (เดิมคำนวณก่อนหมุนจอเสร็จ จึงได้ความสูงแนวตั้งไปใช้ในแนวนอน ภาพเลยถูกตัด/มีแถบดำ)
     */
    private void setFullscreen(boolean on) {
        if (fullscreen == on) {
            return;
        }
        fullscreen = on;
        hideKeyboard();
        View[] others = {topPanel, infoRow, buttonsRow, sectionLabel, listArea, bottomBar};
        if (on) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setCutoutShortEdges(true);
            for (View v : others) {
                v.setVisibility(View.GONE);
            }
            btnFullscreen.setText(R.string.icon_exit_fullscreen);
            applyPlayerHeight(true);
            setSystemUiVisible(false);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setCutoutShortEdges(false);
            setSystemUiVisible(true);
            for (View v : others) {
                v.setVisibility(View.VISIBLE);
            }
            topCollapsed = false;
            btnFullscreen.setText(R.string.icon_fullscreen);
            applyPlayerHeight(false);
        }
        playerContainer.requestLayout();
    }

    /** ตั้งความสูงพื้นที่วิดีโอทันที: เต็มจอ = MATCH_PARENT, ปกติ = 16:9 ของ "ความกว้างแนวตั้ง" */
    private void applyPlayerHeight(boolean full) {
        ViewGroup.LayoutParams lp = playerContainer.getLayoutParams();
        if (full) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            // ใช้ด้านสั้นของจอเสมอ จึงถูกต้องแม้จังหวะนั้นจอยังหมุนไม่เสร็จ
            int portraitWidth = Math.min(dm.widthPixels, dm.heightPixels);
            lp.height = Math.round(portraitWidth * 9f / 16f);
        }
        playerContainer.setLayoutParams(lp);
    }

    /** จอที่มีรอยบาก (notch) ให้วิดีโอเต็มจอจริง ๆ (Android 9+) */
    private void setCutoutShortEdges(boolean on) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                WindowManager.LayoutParams wl = getWindow().getAttributes();
                wl.layoutInDisplayCutoutMode = on
                        ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                getWindow().setAttributes(wl);
            } catch (Throwable ignored) {
            }
        }
    }

    private void setSystemUiVisible(boolean visible) {
        View decor = getWindow().getDecorView();
        if (visible) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }

    private void showQualityDialog() {
        final List<int[]> opts = new ArrayList<int[]>();   // {height, bitrate}
        final List<String> labels = new ArrayList<String>();
        opts.add(new int[]{0, 0});
        labels.add(getString(R.string.quality_auto));

        MappingTrackSelector.MappedTrackInfo info = trackSelector == null ? null : trackSelector.getCurrentMappedTrackInfo();
        if (info != null) {
            int videoRenderer = -1;
            for (int i = 0; i < info.getRendererCount(); i++) {
                if (info.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                    videoRenderer = i;
                }
            }
            if (videoRenderer >= 0) {
                TrackGroupArray tg = info.getTrackGroups(videoRenderer);
                Map<Integer, Integer> found = new LinkedHashMap<Integer, Integer>();
                for (int g = 0; g < tg.length; g++) {
                    for (int t = 0; t < tg.get(g).length; t++) {
                        Format f = tg.get(g).getFormat(t);
                        if (f.height > 0 && !found.containsKey(f.height)) {
                            found.put(f.height, f.bitrate > 0 ? f.bitrate : 0);
                        }
                    }
                }
                List<Integer> heights = new ArrayList<Integer>(found.keySet());
                Collections.sort(heights, new Comparator<Integer>() {
                    @Override
                    public int compare(Integer a, Integer b) {
                        return b - a;
                    }
                });
                for (Integer h : heights) {
                    opts.add(new int[]{h, found.get(h)});
                    labels.add(getString(R.string.quality_item, String.valueOf(h)));
                }
            }
        }

        if (labels.size() == 1) {
            Toast.makeText(this, R.string.quality_no_tracks, Toast.LENGTH_SHORT).show();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.quality_title)
                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int[] o = opts.get(which);
                        applyQuality(o[0], o[1]);
                        btnQuality.setText(which == 0
                                ? getString(R.string.quality_auto)
                                : getString(R.string.quality_item, String.valueOf(o[0])));
                    }
                })
                .setPositiveButton(R.string.cancel, null)
                .show();
    }

    private void applyQuality(int height, int bitrate) {
        DefaultTrackSelector.Parameters.Builder b = trackSelector.getParameters().buildUpon();
        if (height <= 0) {
            b.setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
            b.setMaxVideoBitrate(Integer.MAX_VALUE);
        } else {
            b.setMaxVideoSize(10000, height);
            b.setMaxVideoBitrate(bitrate > 0 ? bitrate : Integer.MAX_VALUE);
        }
        trackSelector.setParameters(b.build());
    }

    /* ============================== ตารางช่อง ============================== */

    private void setupGrid() {
        adapter = new ChannelAdapter();
        adapter.setListener(new ChannelAdapter.OnChannelClick() {
            @Override
            public void onClick(Channel channel) {
                playChannel(channel);
            }
        });
        recycler.setLayoutManager(new GridLayoutManager(this, 3));
        recycler.setHasFixedSize(true);
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                onListScrolled(rv, dy);
            }
        });
    }

    /**
     * เลื่อนรายการลง -> ยุบแถบค้นหา/แท็บเพื่อเพิ่มพื้นที่ให้ตารางช่อง
     * เลื่อนขึ้น/ถึงบนสุด -> แสดงกลับ (มีหน่วงเวลากันกระพริบ และไม่ยุบถ้ารายการสั้น)
     */
    private void onListScrolled(RecyclerView rv, int dy) {
        if (fullscreen || System.currentTimeMillis() < ignoreScrollUntil) {
            return;
        }
        int threshold = dp(6);
        if (!topCollapsed && dy > threshold && adapter.getItemCount() > 15) {
            setTopCollapsed(true);
        } else if (topCollapsed && (dy < -threshold || !rv.canScrollVertically(-1))) {
            setTopCollapsed(false);
        }
    }

    private void setTopCollapsed(boolean collapsed) {
        topCollapsed = collapsed;
        ignoreScrollUntil = System.currentTimeMillis() + 450;
        topPanel.setVisibility(collapsed ? View.GONE : View.VISIBLE);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim().toLowerCase(Locale.US);
                refreshList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard();
                    return true;
                }
                return false;
            }
        });
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private void buildTabs() {
        tabBar.removeAllViews();
        addTab(getString(R.string.tab_all), ALL);
        for (String g : groups) {
            addTab(g, g);
        }
    }

    private void addTab(final String label, final String value) {
        final TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setSingleLine(true);
        int pad = dp(14);
        tv.setPadding(pad, 0, pad, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        lp.rightMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeGroup = value;
                favoritesMode = false;
                hideKeyboard();
                styleTabs();
                refreshList();
                styleModeButtons();
            }
        });
        tabBar.addView(tv);
        styleTab(tv, value.equals(activeGroup));
    }

    private void styleTabs() {
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            View v = tabBar.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                String label = tv.getText().toString();
                boolean active = label.equals(getString(R.string.tab_all))
                        ? ALL.equals(activeGroup)
                        : label.equals(activeGroup);
                styleTab(tv, active);
            }
        }
    }

    private void styleTab(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tv.setTextColor(active ? Color.WHITE : Color.parseColor("#F26F21"));
    }

    private void refreshList() {
        shown.clear();
        for (Channel c : all) {
            if (!ALL.equals(activeGroup) && !activeGroup.equals(c.group)) {
                continue;
            }
            if (favoritesMode && !favorites.contains(c.id)) {
                continue;
            }
            if (query.length() > 0 && !matches(c)) {
                continue;
            }
            shown.add(c);
        }
        adapter.setData(shown);

        String label = favoritesMode ? getString(R.string.favorites)
                : (ALL.equals(activeGroup) ? getString(R.string.tab_all) : activeGroup);
        tvSection.setText(getString(R.string.section_count, label, String.valueOf(shown.size())));

        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (!shown.isEmpty()) {
            recycler.scrollToPosition(0);
        }
    }

    private boolean matches(Channel c) {
        return c.name.toLowerCase(Locale.US).contains(query)
                || c.group.toLowerCase(Locale.US).contains(query)
                || c.chno.contains(query)
                || c.id.toLowerCase(Locale.US).contains(query);
    }

    /* ================================ ปุ่มต่าง ๆ ================================ */

    private void setupButtons() {
        btnFavorites.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                favoritesMode = true;
                refreshList();
                styleModeButtons();
            }
        });

        btnAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                favoritesMode = false;
                refreshList();
                styleModeButtons();
            }
        });

        btnFavIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (current == null) {
                    return;
                }
                if (favorites.contains(current.id)) {
                    favorites.remove(current.id);
                    toast(R.string.fav_removed);
                } else {
                    favorites.add(current.id);
                    toast(R.string.fav_added);
                }
                prefs.edit().putStringSet(KEY_FAV, new HashSet<String>(favorites)).apply();
                updateFavoriteIcon();
                if (favoritesMode) {
                    refreshList();
                }
            }
        });

        btnFullscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFullscreen(!fullscreen);
            }
        });

        btnQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQualityDialog();
            }
        });

        findViewById(R.id.btnInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChannelInfo();
            }
        });

        findViewById(R.id.btnShare).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareChannel();
            }
        });

        findViewById(R.id.btnPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playByOffset(-1);
            }
        });

        findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playByOffset(1);
            }
        });

        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.about_title)
                        .setMessage(R.string.about_msg)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        });

        findViewById(R.id.btnList).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChannelPicker();
            }
        });

        findViewById(R.id.btnEpg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openEpg();
            }
        });

        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshNow();
            }
        });
    }

    private void styleModeButtons() {
        if (favoritesMode) {
            btnFavorites.setBackgroundResource(R.drawable.bg_button_right);
            btnFavorites.setTextColor(Color.WHITE);
            btnAll.setBackgroundResource(R.drawable.bg_button_left);
            btnAll.setTextColor(Color.parseColor("#F26F21"));
        } else {
            btnFavorites.setBackgroundResource(R.drawable.bg_button_left);
            btnFavorites.setTextColor(Color.parseColor("#F26F21"));
            btnAll.setBackgroundResource(R.drawable.bg_button_right);
            btnAll.setTextColor(Color.WHITE);
        }
    }

    private void updateFavoriteIcon() {
        boolean fav = current != null && favorites.contains(current.id);
        btnFavIcon.setText(fav ? "♥" : "♡");
        btnFavIcon.setTextColor(Color.parseColor(fav ? "#E53935" : "#F26F21"));
    }

    private void refreshNow() {
        toast(R.string.refreshing);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = ChannelStore.refreshAll(MainActivity.this);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            toast(R.string.refresh_done);
                        } else {
                            toast(R.string.refresh_failed);
                        }
                        loadData();
                    }
                });
            }
        }).start();
    }

    private void openEpg() {
        if (current == null) {
            return;
        }
        Intent i = new Intent(this, EpgActivity.class);
        i.putExtra(EpgActivity.EXTRA_NAME, current.name);
        i.putExtra(EpgActivity.EXTRA_EPG, current.epgId);
        startActivity(i);
    }

    private void updateNowLine() {
        if (current == null) {
            tvNow.setText(R.string.no_channel_selected);
            return;
        }
        if (current.epgId == null || current.epgId.length() == 0) {
            tvNow.setText(R.string.no_epg_for_channel);
            return;
        }
        List<EpgProgram> list = epg.get(current.epgId);
        if (list == null || list.isEmpty()) {
            tvNow.setText(R.string.no_epg_for_channel);
            return;
        }
        long now = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);
        for (EpgProgram p : list) {
            if (p.isNow(now)) {
                tvNow.setText(getString(R.string.now_playing,
                        p.title, fmt.format(new Date(p.start)) + "-" + fmt.format(new Date(p.stop))));
                return;
            }
        }
        for (EpgProgram p : list) {
            if (p.start > now) {
                tvNow.setText(getString(R.string.next_program,
                        p.title, fmt.format(new Date(p.start))));
                return;
            }
        }
        tvNow.setText(R.string.no_epg_for_channel);
    }

    private void showChannelInfo() {
        if (current == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.info_name)).append(' ').append(current.name).append('\n');
        sb.append(getString(R.string.info_ch_no)).append(' ')
                .append(current.chno.length() == 0 ? "-" : current.chno).append('\n');
        sb.append(getString(R.string.info_group)).append(' ').append(current.group).append('\n');
        sb.append(getString(R.string.info_id)).append(' ').append(current.id).append('\n');
        sb.append(getString(R.string.info_epg)).append(' ')
                .append(current.epgId == null || current.epgId.length() == 0
                        ? getString(R.string.no_epg_short) : current.epgId).append('\n');
        sb.append(getString(R.string.info_source)).append('\n').append(current.url).append("\n\n");
        sb.append(getString(R.string.info_hint));
        new AlertDialog.Builder(this)
                .setTitle(current.name)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void shareChannel() {
        if (current == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, current.name);
        intent.putExtra(Intent.EXTRA_TEXT, current.name + "\n" + current.url);
        startActivity(Intent.createChooser(intent, getString(R.string.share_title)));
    }

    private void showChannelPicker() {
        if (all.isEmpty()) {
            return;
        }
        final String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Channel c = all.get(i);
            names[i] = (c.chno.length() > 0 ? c.chno + ". " : "") + c.name
                    + (c.playable() ? "" : "  (เล่นไม่ได้)");
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_channel)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playChannel(all.get(which));
                    }
                })
                .show();
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
