package com.example.possantransbusticketingsystemapp;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TicketHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<TicketTransaction> ticketList;

    private DatabaseReference mDatabase;
    private ValueEventListener mDataListener;

    private String deviceModel, sessionID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_history);

        recyclerView = findViewById(R.id.recyclerHistory);
        Button btnExport = findViewById(R.id.btnExportExcel); // This generates PDF now
        Button btnBack = findViewById(R.id.btnBackHistory);

        ticketList = new ArrayList<>();
        adapter = new HistoryAdapter(ticketList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        deviceModel = android.os.Build.MODEL.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();

        // Generate PDF Report on click
        btnExport.setOnClickListener(v -> generatePdfReport());

        btnBack.setOnClickListener(v -> finish());
    }

    // =========================================================================
    //  NEW TRIP LOGIC: SYNC WITH CONDUCTOR MENU
    // =========================================================================
    // This ensures that when "NEW TRIP" is clicked in Menu, this list CLEARS.
    @Override
    protected void onResume() {
        super.onResume();
        refreshDataForCurrentTrip();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Detach listener to save resources
        if (mDatabase != null && mDataListener != null) {
            mDatabase.removeEventListener(mDataListener);
        }
    }

    private void refreshDataForCurrentTrip() {
        // 1. Get the LATEST Session ID (Updated by "NEW TRIP" button in Menu)
        SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
        sessionID = prefs.getString("currentSessionId", "UNKNOWN_TRIP");

        // 2. Remove old listener if exists
        if (mDatabase != null && mDataListener != null) {
            mDatabase.removeEventListener(mDataListener);
        }

        // 3. WIPE OLD DATA INSTANTLY
        ticketList.clear();
        adapter.notifyDataSetChanged();

        // 4. Connect to Firebase using the NEW Session ID
        // Path: POS_Devices -> [DeviceID] -> Trips -> [CurrentSessionID] -> Transactions
        mDatabase = FirebaseDatabase.getInstance().getReference("POS_Devices")
                .child(deviceModel).child("Trips").child(sessionID).child("Transactions");

        mDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ticketList.clear(); // Clear again to be safe

                if (!snapshot.exists()) {
                    // If no data (New Trip), list remains empty
                    adapter.notifyDataSetChanged();
                    return;
                }

                // Load new data
                for (DataSnapshot data : snapshot.getChildren()) {
                    TicketTransaction t = data.getValue(TicketTransaction.class);
                    if (t != null) {
                        ticketList.add(t);
                    }
                }

                // Sort: Newest first
                Collections.sort(ticketList, (t1, t2) -> t2.getTicketID().compareTo(t1.getTicketID()));
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TicketHistoryActivity.this, "Sync Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        mDatabase.addValueEventListener(mDataListener);
    }

    // =========================================================================
    //  GENERATE REPORT (PDF) - Uses DataExporter
    // =========================================================================
    private void generatePdfReport() {
        if (ticketList.isEmpty()) {
            Toast.makeText(this, "⚠️ Wala pang tickets sa trip na ito.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Calls DataExporter to generate the report
        DataExporter.generateReport(TicketHistoryActivity.this, ticketList, sessionID);
    }

    // =========================================================================
    //  VIEW TICKET RECEIPT (PDF Preview)
    // =========================================================================
    private void showPdfInApp(TicketTransaction t) {
        SharedPreferences prefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
        // Ensure we look in the correct session folder
        String currentSessionID = prefs.getString("currentSessionId", "UNKNOWN_TRIP");

        File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File appDir = new File(docsDir, "POS BUS TICKETING SIMULATION");
        File customerTicketDir = new File(appDir, "CUSTOMER_TICKET");
        File sessionDir = new File(customerTicketDir, currentSessionID);

        // Try to find Cash or GCash file
        File fileCash = new File(sessionDir, "TICKET_" + t.getTicketID() + "_CASH.pdf");
        File fileGcash = new File(sessionDir, "TICKET_" + t.getTicketID() + "_GCASH.pdf");

        File targetFile = null;
        if (fileCash.exists()) targetFile = fileCash;
        else if (fileGcash.exists()) targetFile = fileGcash;

        if (targetFile == null) {
            Toast.makeText(this, "Receipt PDF not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(fd);
            PdfRenderer.Page page = renderer.openPage(0);

            Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            showFloatingDialog(bitmap, t.getTicketID());

            page.close();
            renderer.close();
            fd.close();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Cannot open PDF preview", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFloatingDialog(Bitmap bitmap, String ticketID) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pdf_preview, null);
        builder.setView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tvPreviewTitle);
        ImageView ivPreview = dialogView.findViewById(R.id.ivPdfPreview);
        Button btnClose = dialogView.findViewById(R.id.btnClosePreview);

        tvTitle.setText("TICKET #" + ticketID);
        ivPreview.setImageBitmap(bitmap);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // --- ADAPTER ---
    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        List<TicketTransaction> list;
        public HistoryAdapter(List<TicketTransaction> list) { this.list = list; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ticket_transaction, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TicketTransaction t = list.get(position);
            holder.tvId.setText("TICKET #: " + t.getTicketID());

            String time = t.getTimestamp();
            try {
                time = new SimpleDateFormat("hh:mm a", Locale.US)
                        .format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(t.getTimestamp()));
            } catch(Exception e){}

            holder.tvTime.setText(time);
            holder.tvRoute.setText(t.getOrigin() + " -> " + t.getDestination());
            holder.tvDetails.setText(t.getPassengerType() + " (" + t.getPassengerCount() + ") • " + t.getPaymentMethod());
            holder.tvAmount.setText("₱" + String.format("%.2f", t.getTotalAmount()));

            holder.itemView.setOnClickListener(v -> showPdfInApp(t));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvId, tvTime, tvRoute, tvDetails, tvAmount;
            public ViewHolder(View itemView) {
                super(itemView);
                tvId = itemView.findViewById(R.id.itemTicketId);
                tvTime = itemView.findViewById(R.id.itemTime);
                tvRoute = itemView.findViewById(R.id.itemRoute);
                tvDetails = itemView.findViewById(R.id.itemDetails);
                tvAmount = itemView.findViewById(R.id.itemAmount);
            }
        }
    }
}