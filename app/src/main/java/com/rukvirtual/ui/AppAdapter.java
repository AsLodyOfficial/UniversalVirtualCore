package com.rukvirtual.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rukvirtual.R;
import com.rukvirtual.model.VirtualAppItem;
import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    public interface OnAppActionListener {
        void onLaunch(VirtualAppItem app);
    }

    private final List<VirtualAppItem> items = new ArrayList<>();
    private final OnAppActionListener listener;

    public AppAdapter(OnAppActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<VirtualAppItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VirtualAppItem item = items.get(position);
        holder.tvName.setText(item.getAppName());
        holder.tvPkg.setText(item.getPackageName());
        if (item.getIcon() != null) {
            holder.ivIcon.setImageDrawable(item.getIcon());
        } else {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        holder.btnLaunch.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLaunch(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvPkg;
        Button btnLaunch;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_app_icon);
            tvName = itemView.findViewById(R.id.tv_app_name);
            tvPkg = itemView.findViewById(R.id.tv_app_pkg);
            btnLaunch = itemView.findViewById(R.id.btn_app_launch);
        }
    }
}