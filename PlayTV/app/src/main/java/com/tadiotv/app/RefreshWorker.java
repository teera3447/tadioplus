package com.tadiotv.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * งานเบื้องหลัง: อัปเดตรายการช่อง + ผังรายการ EPG จากอินเทอร์เน็ต
 * ตั้งให้ทำงานทุกวัน (ดู MainActivity.scheduleDailyRefresh)
 */
public class RefreshWorker extends Worker {

    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            boolean ok = ChannelStore.refreshAll(getApplicationContext());
            return ok ? Result.success() : Result.retry();
        } catch (Throwable t) {
            return Result.retry();
        }
    }
}
