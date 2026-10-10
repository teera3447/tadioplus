package com.tadiotv.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** ตารางช่อง (การ์ดขาว + โลโก้ช่อง + เลขช่อง + ชื่อช่อง) */
public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface OnChannelClick {
        void onClick(Channel channel);
    }

    private static final String[] PALETTE = {
            "#F26F21", "#E53935", "#1E88E5", "#43A047", "#6A1B9A",
            "#00897B", "#FB8C00", "#3949AB", "#D81B60", "#5E35B1"
    };

    private final List<Channel> data = new ArrayList<Channel>();
    private OnChannelClick listener;
    private String selectedKey = "";

    public void setListener(OnChannelClick listener) {
        this.listener = listener;
    }

    public void setData(List<Channel> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    /** อัปเดตเฉพาะการ์ดที่เปลี่ยน (เดิมรีเฟรชทั้งตารางทุกครั้ง ทำให้กระพริบ/เลื่อนสะดุด) */
    public void setSelectedKey(String key) {
        String old = selectedKey;
        this.selectedKey = key == null ? "" : key;
        if (old.equals(selectedKey)) {
            return;
        }
        for (int i = 0; i < data.size(); i++) {
            String k = data.get(i).key();
            if (k.equals(old) || k.equals(selectedKey)) {
                notifyItemChanged(i);
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Channel c = data.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvName.setText(c.name);
        holder.tvChno.setText(c.chno.length() > 0 ? ctx.getString(R.string.ch_no, c.chno) : c.group);
        holder.tvFallback.setText(initial(c.name));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(colorOf(c.name));
        holder.tvFallback.setBackground(bg);

        if (!c.playable()) {
            holder.tvName.setTextColor(Color.parseColor("#B00020"));
        } else {
            holder.tvName.setTextColor(Color.parseColor("#333333"));
        }

        LogoLoader.load(ctx, holder.ivLogo, holder.tvFallback, c.logo);

        holder.itemView.setBackgroundResource(
                c.key().equals(selectedKey) ? R.drawable.bg_card_selected : R.drawable.bg_card);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(c);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private static String initial(String name) {
        if (name == null || name.trim().length() == 0) {
            return "?";
        }
        return name.trim().substring(0, 1);
    }

    private static int colorOf(String name) {
        if (name == null) {
            return Color.parseColor(PALETTE[0]);
        }
        int h = 0;
        for (int i = 0; i < name.length(); i++) {
            h = h * 31 + name.charAt(i);
        }
        int idx = Math.abs(h) % PALETTE.length;
        return Color.parseColor(PALETTE[idx]);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivLogo;
        final TextView tvFallback;
        final TextView tvName;
        final TextView tvChno;

        VH(View itemView) {
            super(itemView);
            ivLogo = itemView.findViewById(R.id.ivLogo);
            tvFallback = itemView.findViewById(R.id.tvFallback);
            tvName = itemView.findViewById(R.id.tvName);
            tvChno = itemView.findViewById(R.id.tvChno);
        }
    }
}
