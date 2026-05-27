package com.buscorp.employee.core.util;

import java.text.NumberFormat;
import java.util.Locale;

public final class Currency {

    private static final NumberFormat PHP = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));

    private Currency() {
    }

    public static String php(double amount) {
        return PHP.format(amount);
    }
}
