package com.bmi.model.ai;

public class BodyFatCalculator {
    public static double predictBodyFat(double bmi, int age, int gender) {
        if (bmi <= 0 || age <= 0) return 0;
        double base = 1.20 * bmi + 0.23 * age - 16.2;
        if (gender == 1) base -= 5.4;
        return Math.round(Math.max(0, base) * 10) / 10.0;
    }
}
