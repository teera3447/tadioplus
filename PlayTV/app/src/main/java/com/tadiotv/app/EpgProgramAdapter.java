package com.tadiotv.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** รายการผังรายการ (EPG) ของช่องหนึ่ง */
public class EpgProgramAdapter extends RecyclerView.Adapter<EpgProgramAdapter.VH> {

    private final List<EpgProgram> data = new ArrayList<EpgProgram>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);

    public void setData(List<EpgProgram> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    public int indexOfNow() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).isNow(now)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_programme, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        EpgProgram p = data.get(position);
        Context ctx = holder.itemView.getContext();
        boolean now = p.isNow(System.currentTimeMillis());

        holder.tvTime.setText(fmt.format(new Date(p.start)) + " - " + fmt.format(new Date(p.stop)));
        holder.tvTitle.setText(p.title);
        holder.tvDesc.setText(p.desc == null || p.desc.length() == 0 ? "" : p.desc);
        holder.tvDesc.setVisibility(p.desc == null || p.desc.length() == 0 ? View.GONE : View.VISIBLE);
        holder.tvBadge.setVisibility(now ? View.VISIBLE : View.GONE);
        holder.tvBadge.setText(ctx.getString(R.string.epg_now_badge));

        holder.itemView.setBackgroundResource(now ? R.drawable.bg_card_selected : R.drawable.bg_card);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTime;
        final TextView tvTitle;
        final TextView tvDesc;
        final TextView tvBadge;

        VH(View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            tvBadge = itemView.findViewById(R.id.tvBadge);
        }
    }
}
