package com.buscorp.employee;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.buscorp.employee.databinding.ActivityTicketHistoryBinding;
import com.google.android.material.chip.Chip;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TicketHistoryActivity extends AppCompatActivity {

    private ActivityTicketHistoryBinding binding;
    private TicketHistoryViewModel viewModel;
    private TicketHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(TicketHistoryViewModel.class);

        setupRecyclerView();
        setupClickListeners();
        observeViewModel();
        
        viewModel.loadHistory();
    }

    private void setupRecyclerView() {
        adapter = new TicketHistoryAdapter();
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnExport.setOnClickListener(v -> {
            Toast.makeText(this, "Batch Exporting... (via Remittance logic)", Toast.LENGTH_SHORT).show();
            // Optional: Can tie this to the RemittanceViewModel export methods later
        });

        binding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip chip = findViewById(checkedIds.get(0));
                if (chip != null) {
                    String filterText = chip.getText().toString();
                    if (filterText.contains("All")) viewModel.filter("All");
                    else if (filterText.contains("Senior/PWD")) viewModel.filter("Discounted");
                    else viewModel.filter(filterText);
                }
            }
        });
    }

    private void observeViewModel() {
        viewModel.getLoadingState().observe(this, isLoading -> {
            if (isLoading) {
                binding.shimmerLayout.setVisibility(View.VISIBLE);
                binding.shimmerLayout.startShimmer();
                binding.rvHistory.setVisibility(View.GONE);
                binding.tvEmptyState.setVisibility(View.GONE);
            } else {
                binding.shimmerLayout.stopShimmer();
                binding.shimmerLayout.setVisibility(View.GONE);
                binding.rvHistory.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getTicketsData().observe(this, tickets -> {
            if (tickets.isEmpty()) {
                binding.rvHistory.setVisibility(View.GONE);
                binding.tvEmptyState.setVisibility(View.VISIBLE);
            } else {
                binding.tvEmptyState.setVisibility(View.GONE);
                binding.rvHistory.setVisibility(View.VISIBLE);
            }
            adapter.submitList(tickets);
        });
    }
}