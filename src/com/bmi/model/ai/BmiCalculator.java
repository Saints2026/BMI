package com.bmi.model.ai;

public class BmiCalculator {
    public static double calcBmi(double heightCm, double weightKg) {
        if (heightCm <= 0 || weightKg <= 0) return 0;
        double heightM = heightCm / 100;
        return Math.round((weightKg / (heightM * heightM)) * 10) / 10.0;
    }

    public static String classify(double bmi) {
        if (bmi <= 0) return "未知";
        if (bmi < 18.5) return "偏瘦";
        if (bmi < 24) return "正常";
        if (bmi < 28) return "超重";
        return "肥胖";
    }
}
