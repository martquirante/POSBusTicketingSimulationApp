package com.buscorp.employee;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.buscorp.employee.core.db.TicketEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TicketHistoryAdapter extends ListAdapter<TicketEntity, TicketHistoryAdapter.ViewHolder> {

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);

    protected TicketHistoryAdapter() {
        super(new DiffUtil.ItemCallback<TicketEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull TicketEntity oldItem, @NonNull TicketEntity newItem) {
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull TicketEntity oldItem, @NonNull TicketEntity newItem) {
                return oldItem.getTimestamp() == newItem.getTimestamp() &&
                       oldItem.getFare() == newItem.getFare();
            }
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ticket_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TicketEntity ticket = getItem(position);
        holder.bind(ticket);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvRoute, tvType, tvTime, tvFare;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRoute = itemView.findViewById(R.id.tvRoute);
            tvType = itemView.findViewById(R.id.tvType);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvFare = itemView.findViewById(R.id.tvFare);
        }

        public void bind(TicketEntity ticket) {
            tvRoute.setText(ticket.getOrigin() + " - " + ticket.getDestination());
            tvType.setText(ticket.getPassengerType());
            tvTime.setText(timeFormat.format(new Date(ticket.getTimestamp())));
            tvFare.setText(String.format("₱%.2f", ticket.getFare()));
        }
    }
}
