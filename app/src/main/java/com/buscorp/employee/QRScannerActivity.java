package com.buscorp.employee;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.buscorp.employee.databinding.ActivityQrScannerBinding;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class QRScannerActivity extends AppCompatActivity {

    private ActivityQrScannerBinding binding;
    private ScannerViewModel viewModel;
    private DecoratedBarcodeView barcodeScannerView;
    private boolean isScanning = true;
    private ValueAnimator beamAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ViewBinding setup
        binding = ActivityQrScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // ViewModel setup
        viewModel = new ViewModelProvider(this).get(ScannerViewModel.class);

        initScanner();
        setupNeonBeamAnimation();
        observeViewModel();
        
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void initScanner() {
        barcodeScannerView = binding.barcodeScanner;
        barcodeScannerView.setStatusText("");
        
        barcodeScannerView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (isScanning && result.getText() != null) {
                    isScanning = false; // Pause scanning
                    binding.barcodeScanner.pause();
                    binding.tvScanStatus.setText("Verifying ticket...");
                    
                    // Trigger ViewModel to handle the business logic
                    viewModel.verifyTicket(result.getText());
                }
            }
        });
    }

    private void setupNeonBeamAnimation() {
        // The beam is part of custom_barcode_scanner.xml which is inflated by ZXing inside DecoratedBarcodeView
        View neonBeam = barcodeScannerView.findViewById(R.id.neonBeam);
        if (neonBeam != null) {
            beamAnimator = ValueAnimator.ofFloat(0f, 240f); // 250dp height minus beam height
            beamAnimator.setDuration(1500);
            beamAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            beamAnimator.setRepeatCount(ValueAnimator.INFINITE);
            beamAnimator.setRepeatMode(ValueAnimator.REVERSE);
            
            // Adjust for dp to px
            final float density = getResources().getDisplayMetrics().density;
            beamAnimator.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                neonBeam.setTranslationY(value * density);
            });
            beamAnimator.start();
        }
    }

    private void observeViewModel() {
        viewModel.getScanResultState().observe(this, state -> {
            switch (state.getStatus()) {
                case SUCCESS:
                    handleSuccessFeedback();
                    break;
                case ERROR:
                    handleErrorFeedback(state.getMessage());
                    break;
            }
        });
    }

    private void handleSuccessFeedback() {
        binding.getRoot().performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        binding.tvScanStatus.setText("Ticket Valid! Passenger Boarded.");
        binding.tvScanStatus.setTextColor(getColor(R.color.buscorp_cyan));

        binding.lottieFeedback.setAnimation(R.raw.lottie_success);
        binding.lottieFeedback.setVisibility(View.VISIBLE);
        binding.lottieFeedback.playAnimation();

        // Resume scanning after delay
        new Handler(Looper.getMainLooper()).postDelayed(this::resumeScanning, 2500);
    }

    private void handleErrorFeedback(String errorMsg) {
        binding.getRoot().performHapticFeedback(HapticFeedbackConstants.REJECT);
        binding.tvScanStatus.setText(errorMsg);
        binding.tvScanStatus.setTextColor(getColor(R.color.buscorp_danger));

        // Shake animation on the status panel
        ObjectAnimator shake = ObjectAnimator.ofFloat(binding.statusPanel, "translationX", 0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
        shake.setDuration(500);
        shake.start();

        // Play error lottie
        binding.lottieFeedback.setAnimation(R.raw.lottie_red_shake);
        binding.lottieFeedback.setVisibility(View.VISIBLE);
        binding.lottieFeedback.playAnimation();

        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();

        // Resume scanning after delay
        new Handler(Looper.getMainLooper()).postDelayed(this::resumeScanning, 2500);
    }

    private void resumeScanning() {
        binding.lottieFeedback.setVisibility(View.GONE);
        binding.lottieFeedback.cancelAnimation();
        
        binding.tvScanStatus.setText("Align QR code within the frame");
        binding.tvScanStatus.setTextColor(getColor(R.color.text_secondary));
        
        isScanning = true;
        binding.barcodeScanner.resume();
    }

    @Override
    protected void onResume() {
        super.onResume();
        barcodeScannerView.resume();
        if (beamAnimator != null) beamAnimator.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeScannerView.pause();
        if (beamAnimator != null) beamAnimator.pause();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (beamAnimator != null) beamAnimator.cancel();
        if (binding.lottieFeedback != null) {
            binding.lottieFeedback.removeAllAnimatorListeners();
            binding.lottieFeedback.cancelAnimation();
        }
    }
}
