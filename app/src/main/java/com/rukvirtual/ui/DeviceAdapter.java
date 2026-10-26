package com.rukvirtual.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rukvirtual.R;
import com.rukvirtual.model.VirtualDeviceItem;
import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    public interface OnDeviceActionListener {
        void onLaunch(VirtualDeviceItem device);
        void onRestart(VirtualDeviceItem device);
    }

    private final List<VirtualDeviceItem> items = new ArrayList<>();
    private final OnDeviceActionListener listener;

    public DeviceAdapter(OnDeviceActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<VirtualDeviceItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VirtualDeviceItem item = items.get(position);
        holder.tvName.setText(item.getName());
        holder.tvSpecs.setText(item.getAndroidVersion() + " • " + item.getAbi() + " • User " + item.getUserId());
        holder.tvResources.setText("Installed Apps: " + item.getAppCount() + " • Storage: Isolated");
        
        if (item.isRunning()) {
            holder.tvBadge.setText("● RUNNING");
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_emerald);
            holder.tvBadge.setTextColor(holder.itemView.getContext().getColor(R.color.accent_emerald));
        } else {
            holder.tvBadge.setText("● STOPPED");
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_rose);
            holder.tvBadge.setTextColor(holder.itemView.getContext().getColor(R.color.accent_rose));
        }

        holder.btnLaunch.setOnClickListener(v -> {
            if (listener != null) listener.onLaunch(item);
        });

        holder.btnRestart.setOnClickListener(v -> {
            if (listener != null) listener.onRestart(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvBadge;
        TextView tvSpecs;
        TextView tvResources;
        Button btnLaunch;
        Button btnRestart;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_env_name);
            tvBadge = itemView.findViewById(R.id.tv_env_badge);
            tvSpecs = itemView.findViewById(R.id.tv_env_specs);
            tvResources = itemView.findViewById(R.id.tv_env_resources);
            btnLaunch = itemView.findViewById(R.id.btn_env_open);
            btnRestart = itemView.findViewById(R.id.btn_env_restart);
        }
    }
}