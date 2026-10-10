package com.tadiotv.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * หน้าจอผังรายการ (EPG) ของช่องที่กำลังดูอยู่
 * แสดงรายการของวันนี้พร้อมไฮไลต์รายการที่กำลังออกอากาศ
 */
public class EpgActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "channel_name";
    public static final String EXTRA_EPG = "epg_id";

    private EpgProgramAdapter adapter;
    private RecyclerView recycler;
    private TextView tvStatus;
    private String epgId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_epg);

        TextView tvTitle = findViewById(R.id.tvEpgChannel);
        tvStatus = findViewById(R.id.tvEpgStatus);
        recycler = findViewById(R.id.recyclerEpg);

        Intent intent = getIntent();
        String name = intent.getStringExtra(EXTRA_NAME);
        epgId = intent.getStringExtra(EXTRA_EPG);

        tvTitle.setText(name == null ? getString(R.string.epg_title) : name);

        adapter = new EpgProgramAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        findViewById(R.id.btnEpgBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        load();
    }

    private void load() {
        if (epgId == null || epgId.length() == 0) {
            tvStatus.setText(R.string.epg_none);
            return;
        }
        tvStatus.setText(R.string.epg_loading);
        final Activity self = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, List<EpgProgram>> map = ChannelStore.loadEpg(self, false);
                final List<EpgProgram> list = map.get(epgId);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (list == null || list.isEmpty()) {
                            tvStatus.setText(R.string.epg_none);
                            return;
                        }
                        long now = System.currentTimeMillis();
                        List<EpgProgram> today = new ArrayList<EpgProgram>();
                        for (EpgProgram p : list) {
                            if (p.stop > now - 6L * 3600 * 1000) {
                                today.add(p);
                            }
                        }
                        adapter.setData(today);
                        tvStatus.setVisibility(View.GONE);
                        int idx = adapter.indexOfNow();
                        if (idx >= 0) {
                            recycler.scrollToPosition(Math.max(0, idx - 1));
                        }
                    }
                });
            }
        }).start();
    }
}
