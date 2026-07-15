package com.bmi.model.ai;

public class BodyFatEstimator {

    /**
     * 根据身高、体重、腰围、年龄估算体脂率（适用于成年人）
     * 公式参考：男性：体脂率 = (1.20 × BMI) + (0.23 × 年龄) - (10.8 × 性别) - 5.4
     *          女性：性别参数为0，男性为1
     * BMI = 体重(kg) / 身高(m)^2
     */
    public static double estimate(double heightCm, double weightKg, double waistCm, int age, boolean isMale) {
        if (heightCm <= 0 || weightKg <= 0 || waistCm <= 0 || age <= 0) {
            throw new IllegalArgumentException("参数必须大于0");
        }
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        int sexFactor = isMale ? 1 : 0;
        // 体脂率 = 1.2 * BMI + 0.23 * 年龄 - 10.8 * 性别 - 5.4
        double bodyFat = 1.20 * bmi + 0.23 * age - 10.8 * sexFactor - 5.4;
        // 限制范围在 3% ~ 45% 之间
        if (bodyFat < 3) bodyFat = 3;
        if (bodyFat > 45) bodyFat = 45;
        return Math.round(bodyFat * 100) / 100.0; // 保留两位小数
    }
}