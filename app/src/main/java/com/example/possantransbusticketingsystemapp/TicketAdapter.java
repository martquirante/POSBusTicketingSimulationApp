package com.example.possantransbusticketingsystemapp;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TicketAdapter extends RecyclerView.Adapter<TicketAdapter.TicketViewHolder> {

    private List<TicketTransaction> transactionList;
    private Context context;

    public TicketAdapter(List<TicketTransaction> transactionList) {
        this.transactionList = transactionList;
    }

    @NonNull
    @Override
    public TicketViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        // Siguraduhin na 'item_ticket_transaction' ang pangalan ng layout file mo
        View view = LayoutInflater.from(context).inflate(R.layout.item_ticket_transaction, parent, false);
        return new TicketViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TicketViewHolder holder, int position) {
        TicketTransaction ticket = transactionList.get(position);

        // 1. TICKET ID
        holder.tvId.setText("TICKET #: " + ticket.getTicketID());

        // 2. DATE & TIME FORMATTING (Safe Catch)
        try {
            String rawDate = ticket.getTimestamp(); // Expecting "yyyy-MM-dd HH:mm:ss"
            if (rawDate != null && !rawDate.isEmpty()) {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("hh:mm a", Locale.US); // Output: "12:30 PM"
                Date date = inputFormat.parse(rawDate);
                holder.tvTime.setText(outputFormat.format(date));
            } else {
                holder.tvTime.setText("--:--");
            }
        } catch (Exception e) {
            // Fallback: Kunin na lang ang substring kung mag-fail ang parse
            try {
                holder.tvTime.setText(ticket.getTimestamp().substring(11, 16));
            } catch (Exception ex) {
                holder.tvTime.setText(ticket.getTimestamp()); // Display raw as last resort
            }
        }

        // 3. ROUTE
        holder.tvRoute.setText(ticket.getOrigin() + " -> " + ticket.getDestination());

        // 4. DETAILS (Type • Count • Payment)
        String details = ticket.getPassengerType() + " (" + ticket.getPassengerCount() + ") • " + ticket.getPaymentMethod();
        holder.tvDetails.setText(details);

        // 5. AMOUNT (Formatted with Peso Sign)
        holder.tvAmount.setText("₱" + String.format("%.2f", ticket.getTotalAmount()));

        // 6. LOCATION PIN Logic
        if (ticket.getLocationName() != null
                && !ticket.getLocationName().equals("Location Unknown")
                && !ticket.getLocationName().isEmpty()) {
            holder.tvLocation.setText("📍 " + ticket.getLocationName());
            holder.tvLocation.setVisibility(View.VISIBLE);
        } else {
            holder.tvLocation.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    // --- VIEW HOLDER CLASS ---
    public static class TicketViewHolder extends RecyclerView.ViewHolder {

        TextView tvId, tvTime, tvRoute, tvDetails, tvAmount, tvLocation;

        public TicketViewHolder(@NonNull View itemView) {
            super(itemView);

            // Mapping IDs from 'item_ticket_transaction.xml'
            tvId = itemView.findViewById(R.id.itemTicketId);
            tvTime = itemView.findViewById(R.id.itemTime);
            tvRoute = itemView.findViewById(R.id.itemRoute);
            tvDetails = itemView.findViewById(R.id.itemDetails);
            tvAmount = itemView.findViewById(R.id.itemAmount);
            tvLocation = itemView.findViewById(R.id.itemLocation);
        }
    }
}