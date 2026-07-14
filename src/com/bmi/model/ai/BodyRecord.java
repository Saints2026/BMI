package com.bmi.model.ai;

/**
 * 身体记录数据对象
 * 对应 docs/ai_design.md 中 userMetrics 字段
 */
public class BodyRecord {
    private double height;   // 身高(cm)
    private double weight;   // 体重(kg)
    private int age;         // 年龄
    private int gender;      // 性别：1=男，0=女

    public BodyRecord(double height, double weight, int age, int gender) {
        this.height = height;
        this.weight = weight;
        this.age = age;
        this.gender = gender;
    }

    public double getHeight() { return height; }
    public double getWeight() { return weight; }
    public int getAge() { return age; }
    public int getGender() { return gender; }

    /**
     * 生成缓存Key（用所有字段拼接）
     */
    public String toCacheKey() {
        return height + "," + weight + "," + age + "," + gender;
    }
}