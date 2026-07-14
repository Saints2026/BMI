package com.bmi.model.ai;

public class BodyRecord {
    private double bmi;
    private double bodyFat;
    private String measureDate;

    public BodyRecord() {}

    public BodyRecord(double bmi, double bodyFat, String measureDate) {
        this.bmi = bmi;
        this.bodyFat = bodyFat;
        this.measureDate = measureDate;
    }

    public double getBmi() { return bmi; }
    public void setBmi(double bmi) { this.bmi = bmi; }

    public double getBodyFat() { return bodyFat; }
    public void setBodyFat(double bodyFat) { this.bodyFat = bodyFat; }

    public String getMeasureDate() { return measureDate; }
    public void setMeasureDate(String measureDate) { this.measureDate = measureDate; }
}