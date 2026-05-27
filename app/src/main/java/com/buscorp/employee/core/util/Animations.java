package com.buscorp.employee.core.util;

import android.view.HapticFeedbackConstants;
import android.view.View;

public final class Animations {

    private Animations() {
    }

    public static void press(View view, Runnable afterPress) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(90L)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(110L)
                        .withEndAction(afterPress)
                        .start())
                .start();
    }
}
