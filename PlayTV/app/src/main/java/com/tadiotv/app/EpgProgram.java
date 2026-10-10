package com.tadiotv.app;

/** รายการหนึ่งในผังรายการ (EPG) */
public class EpgProgram {

    public final long start;
    public final long stop;
    public final String title;
    public final String desc;

    public EpgProgram(long start, long stop, String title, String desc) {
        this.start = start;
        this.stop = stop;
        this.title = title;
        this.desc = desc;
    }

    public boolean isNow(long now) {
        return now >= start && now < stop;
    }
}
