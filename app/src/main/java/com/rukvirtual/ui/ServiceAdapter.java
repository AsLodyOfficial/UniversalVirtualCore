package com.rukvirtual.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rukvirtual.R;
import com.rukvirtual.model.VirtualServiceItem;
import java.util.ArrayList;
import java.util.List;

public class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ViewHolder> {

    private final List<VirtualServiceItem> items = new ArrayList<>();

    public void setItems(List<VirtualServiceItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VirtualServiceItem item = items.get(position);
        holder.tvName.setText(item.getServiceName());
        holder.tvDesc.setText(item.getDescription());
        holder.tvStatus.setText(item.getStatus());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvDesc;
        TextView tvStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_srv_name);
            tvDesc = itemView.findViewById(R.id.tv_srv_desc);
            tvStatus = itemView.findViewById(R.id.tv_srv_status);
        }
    }
}