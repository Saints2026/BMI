package com.bmi.model;

/**
 * BMI 与体脂计算工具类。
 * <p>
 * 提供 BMI 计算、中国标准分级功能。
 * 规划中应将此拆分为 BmiCalculator 与 BodyFatCalculator 两个独立类，归属 model.ai 层。
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
     * 中国标准 BMI 分级。
     *
     * @param bmi BMI 值
     * @return 分级结果：偏瘦 / 正常 / 超重 / 肥胖
     * @throws IllegalArgumentException BMI 无效时抛出
     */
    public static String classifyBmi(double bmi) {
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
            return "偏瘦";
        } else if (bmi < 24.0) {
            return "正常";
        } else if (bmi < 28.0) {
            return "超重";
        } else {
            return "肥胖";
        }
    }
}
