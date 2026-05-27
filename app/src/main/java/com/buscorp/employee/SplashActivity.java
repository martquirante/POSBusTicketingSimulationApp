package com.buscorp.employee;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private ImageView ivBus;
    private View trackLine;
    private View trackFill;
    private View busGlow;
    private LinearLayout logoBlock;
    private View neonUnderline;
    private LinearLayout footerBlock;
    private TextView tvVersion;

    // Dots
    private View dot1, dot2, dot3;
    private int currentDot = 0;
    private Handler dotHandler = new Handler(Looper.getMainLooper());
    private Runnable dotRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_BusCorp_Splash);
        setContentView(R.layout.activity_splash);

        initViews();
        setVersion();
        startCinematicAnimation();
    }

    private void initViews() {
        ivBus = findViewById(R.id.ivBus);
        trackLine = findViewById(R.id.trackLine);
        trackFill = findViewById(R.id.trackFill);
        busGlow = findViewById(R.id.busGlow);
        logoBlock = findViewById(R.id.logoBlock);
        neonUnderline = findViewById(R.id.neonUnderline);
        footerBlock = findViewById(R.id.footerBlock);
        tvVersion = findViewById(R.id.tvVersion);
        
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
    }

    private void setVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText("v" + pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("v1.0.0");
        }
    }

    private void startCinematicAnimation() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;

        // 1. Fade in track line
        trackLine.animate().alpha(0.6f).setDuration(400).start();

        // 2. Bus traversing animation
        // Start from off-screen left
        ivBus.setTranslationX(-screenWidth);
        busGlow.setTranslationX(-screenWidth);
        
        ivBus.setAlpha(1f);
        busGlow.setAlpha(0.6f);

        // Animate bus and glow crossing the screen to center
        ObjectAnimator busAnim = ObjectAnimator.ofFloat(ivBus, "translationX", -screenWidth, 0f);
        ObjectAnimator glowAnim = ObjectAnimator.ofFloat(busGlow, "translationX", -screenWidth, 0f);
        
        // Track fill follows the bus
        ValueAnimator fillAnim = ValueAnimator.ofInt(0, screenWidth / 2 + 200); // Approximate center
        fillAnim.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            trackFill.getLayoutParams().width = val;
            trackFill.requestLayout();
        });

        AnimatorSet busSet = new AnimatorSet();
        busSet.playTogether(busAnim, glowAnim, fillAnim);
        busSet.setDuration(1200);
        busSet.setInterpolator(new AccelerateDecelerateInterpolator());
        busSet.setStartDelay(200);
        
        busSet.start();

        // 3. Fade in logo block and footer after bus arrives
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            logoBlock.animate().alpha(1f).setDuration(500).start();
            neonUnderline.animate().scaleX(1f).setDuration(600).setInterpolator(new DecelerateInterpolator()).start();
            
            footerBlock.animate().alpha(1f).translationY(0f).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
            startDotsAnimation();
            
            // 4. Route to Login after cinematic delay
            routeToLogin();
        }, 1400); // After bus set duration
    }

    private void startDotsAnimation() {
        dotRunnable = new Runnable() {
            @Override
            public void run() {
                dot1.setBackgroundResource(currentDot == 0 ? R.drawable.splash_dot_active : R.drawable.splash_dot_inactive);
                dot2.setBackgroundResource(currentDot == 1 ? R.drawable.splash_dot_active : R.drawable.splash_dot_inactive);
                dot3.setBackgroundResource(currentDot == 2 ? R.drawable.splash_dot_active : R.drawable.splash_dot_inactive);
                currentDot = (currentDot + 1) % 3;
                dotHandler.postDelayed(this, 300);
            }
        };
        dotHandler.post(dotRunnable);
    }

    private void routeToLogin() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 1500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dotHandler != null && dotRunnable != null) {
            dotHandler.removeCallbacks(dotRunnable);
        }
    }
}
