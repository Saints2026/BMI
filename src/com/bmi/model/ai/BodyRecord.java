package com.bmi.model.ai;

public class BodyRecord {
    private double height, weight, systolicBP, diastolicBP;
    private int age, heartRate, gender;

    public BodyRecord(double height, double weight, int age, int gender, int heartRate, double systolicBP, double diastolicBP) {
        this.height = height;
        this.weight = weight;
        this.age = age;
        this.gender = gender;
        this.heartRate = heartRate;
        this.systolicBP = systolicBP;
        this.diastolicBP = diastolicBP;
    }

    public double getHeight() { return height; }
    public double getWeight() { return weight; }
    public int getAge() { return age; }
    public int getGender() { return gender; }
    public int getHeartRate() { return heartRate; }
    public double getSystolicBP() { return systolicBP; }
    public double getDiastolicBP() { return diastolicBP; }

    @Override
    public String toString() {
        return height + "," + weight + "," + age + "," + gender + "," + heartRate + "," + systolicBP + "," + diastolicBP;
    }
}
