package com.bmi.model;

/**
 * BMI 与体脂计算工具类。
 * <p>
 * 提供 BMI 计算、中国标准分级、体脂预测功能。
 * <p>
 * 分级结果使用枚举 {@link BmiCategory}，不在业务层硬编码中文文案（中文展示由 view 层 i18n 负责）。
 */
public class CalcUtil {

    /**
     * 计算 BMI = weight(kg) / (height(m) / 100)²，保留 1 位小数。
     *
     * @param weightKg 体重（kg），范围 [10, 300]
     * @param heightCm 身高（cm），范围 [50, 250]
     * @return BMI 值（四舍五入保留 1 位小数）
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public static double calcBmi(double weightKg, double heightCm) {
        if (Double.isNaN(weightKg) || Double.isNaN(heightCm)) {
            throw new IllegalArgumentException("Weight and height must be valid numbers");
        }
        if (weightKg <= 0 || heightCm <= 0) {
            throw new IllegalArgumentException("Weight and height must be positive");
        }
        if (weightKg < 10 || weightKg > 300) {
            throw new IllegalArgumentException("Weight must be between 10 and 300 kg");
        }
        if (heightCm < 50 || heightCm > 250) {
            throw new IllegalArgumentException("Height must be between 50 and 250 cm");
        }
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        return Math.round(bmi * 10.0) / 10.0;
    }

    /**
     * 中国标准 BMI 分级，返回枚举值（不含中文文案）。
     * <p>
     * 中文展示文案由 view 层 i18n 根据枚举值查表获取。
     *
     * @param bmi BMI 值
     * @return 分级枚举值
     * @throws IllegalArgumentException BMI 无效时抛出
     */
    public static BmiCategory classifyBmi(double bmi) {
        if (Double.isNaN(bmi)) {
            throw new IllegalArgumentException("BMI must be a valid number");
        }
        if (bmi <= 0) {
            throw new IllegalArgumentException("BMI must be positive");
        }
        if (bmi >= 50) {
            throw new IllegalArgumentException("BMI out of reasonable range");
        }
        if (bmi < 18.5) {
            return BmiCategory.UNDERWEIGHT;
        } else if (bmi < 24.0) {
            return BmiCategory.NORMAL;
        } else if (bmi < 28.0) {
            return BmiCategory.OVERWEIGHT;
        } else {
            return BmiCategory.OBESE;
        }
    }

    /**
     * 体脂率预测（Deurenberg 公式）。
     * <p>
     * 公式：body_fat = 1.20 × BMI + 0.23 × age − 10.8 × gender − 5.4
     * <p>
     * gender: 1 = 男，0 = 女。结果不低于 0，保留 1 位小数。
     *
     * @param bmi    BMI 值
     * @param age    年龄（1–120）
     * @param gender 性别（1=男，0=女）
     * @return 体脂率百分比
     */
    public static double predictBodyFat(double bmi, int age, int gender) {
        if (bmi <= 0 || age <= 0) {
            return 0;
        }
        double base = 1.20 * bmi + 0.23 * age - 10.8 * gender - 5.4;
        return Math.round(Math.max(0, base) * 10.0) / 10.0;
    }
}
