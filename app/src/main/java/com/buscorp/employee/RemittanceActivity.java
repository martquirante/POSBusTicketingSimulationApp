package com.buscorp.employee;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.buscorp.employee.databinding.ActivityRemittanceBinding;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class RemittanceActivity extends AppCompatActivity {

    private ActivityRemittanceBinding binding;
    private RemittanceViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRemittanceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(RemittanceViewModel.class);
        
        setupChart();
        setupClickListeners();
        observeViewModel();
        
        viewModel.loadShiftData();
    }

    private void setupChart() {
        binding.barChart.setDrawBarShadow(false);
        binding.barChart.setDrawValueAboveBar(true);
        binding.barChart.getDescription().setEnabled(false);
        binding.barChart.getLegend().setEnabled(false);
        binding.barChart.setTouchEnabled(false);
        
        XAxis xAxis = binding.barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Cash", "QR/Digital"}));
        
        binding.barChart.getAxisLeft().setTextColor(Color.WHITE);
        binding.barChart.getAxisRight().setEnabled(false);
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnSubmitRemittance.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            viewModel.generateReports();
            binding.btnSubmitRemittance.setEnabled(false);
            binding.btnSubmitRemittance.setText("Generating...");
        });
    }

    private void observeViewModel() {
        viewModel.getState().observe(this, state -> {
            binding.tvTotalCash.setText(String.format("₱%.2f", state.totalCash));
            binding.tvTicketsCount.setText(String.valueOf(state.ticketCount));
            binding.tvQrRevenue.setText(String.format("₱%.2f", state.totalQr));
            
            // Populate Chart
            ArrayList<BarEntry> entries = new ArrayList<>();
            entries.add(new BarEntry(0f, (float) state.totalCash));
            entries.add(new BarEntry(1f, (float) state.totalQr));
            
            BarDataSet dataSet = new BarDataSet(entries, "Revenue Breakdown");
            dataSet.setColors(new int[]{getColor(R.color.buscorp_cyan), getColor(R.color.buscorp_primary)});
            dataSet.setValueTextColor(Color.WHITE);
            dataSet.setValueTextSize(10f);
            
            BarData data = new BarData(dataSet);
            binding.barChart.setData(data);
            binding.barChart.invalidate();
        });
        
        viewModel.getExportState().observe(this, state -> {
            if (state == RemittanceViewModel.ExportState.SUCCESS) {
                showSuccessOverlay();
            } else if (state == RemittanceViewModel.ExportState.ERROR) {
                Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show();
                binding.btnSubmitRemittance.setEnabled(true);
                binding.btnSubmitRemittance.setText("Submit & Export Reports");
            }
        });
    }

    private void showSuccessOverlay() {
        binding.overlaySuccess.setAlpha(0f);
        binding.overlaySuccess.setVisibility(View.VISIBLE);
        binding.overlaySuccess.animate().alpha(1f).setDuration(300).start();
        
        binding.lottieSuccess.playAnimation();
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            binding.overlaySuccess.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                binding.overlaySuccess.setVisibility(View.GONE);
                finish(); // Return to dashboard
            }).start();
        }, 3000);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (binding.lottieSuccess != null) {
            binding.lottieSuccess.removeAllAnimatorListeners();
            binding.lottieSuccess.cancelAnimation();
        }
    }
}
