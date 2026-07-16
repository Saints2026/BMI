package com.bmi.model.ai;

import com.bmi.i18n.I18n;

public class BmiCalculator {
    public static double calcBmi(double heightCm, double weightKg) {
        if (heightCm <= 0 || weightKg <= 0) return 0;
        double heightM = heightCm / 100;
        return Math.round((weightKg / (heightM * heightM)) * 10) / 10.0;
    }

    public static String classify(double bmi) {
        if (bmi <= 0) return I18n.t("grade.unknown");
        if (bmi < 18.5) return I18n.t("grade.thin");
        if (bmi < 24) return I18n.t("grade.normal");
        if (bmi < 28) return I18n.t("grade.overweight");
        return I18n.t("grade.obese");
    }
}
