package com.buscorp.employee.core.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateUtils {

    private static final SimpleDateFormat DISPLAY = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US);

    private DateUtils() {
    }

    public static String display(long millis) {
        return DISPLAY.format(new Date(millis));
    }
}
