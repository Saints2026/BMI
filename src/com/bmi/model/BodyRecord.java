package com.bmi.model;

import java.time.LocalDateTime;

/**
 * 单次身体测量记录实体，对应 body_record 表。
 * <p>
 * 核心字段（必填）：身高、体重、BMI、体脂率。
 * 扩展字段（选填）：腰围/臀围/颈围/腕围/血压/心率/内脏脂肪/疾病/照片路径，用包装类型允许 null。
 * 瞬时字段（不持久化）：age、gender，仅用于体脂计算与 AI 请求。
 */
public class BodyRecord {

    // ========== 持久化字段（对应 body_record 列） ==========

    private long id;
    private long userId;
    private LocalDateTime measureTime;
    private double height;
    private double weight;
    private double bmi;
    private double bodyFat;

    private Double waistCircum;
    private Double hipCircum;
    private Double neckCircum;
    private Double wristCircum;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer heartRate;
    private Integer visceralFat;
    private String diseases;
    private String photoPath;
    private LocalDateTime createdAt;

    // ========== 瞬时字段（不持久化，仅体脂计算/AI 请求用） ==========

    private int age;
    private int gender;

    public BodyRecord() {
    }

    public BodyRecord(long id, long userId, LocalDateTime measureTime,
                      double height, double weight, double bmi, double bodyFat,
                      Double waistCircum, Double hipCircum, Double neckCircum, Double wristCircum,
                      Integer systolicBp, Integer diastolicBp, Integer heartRate, Integer visceralFat,
                      String diseases, String photoPath, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.measureTime = measureTime;
        this.height = height;
        this.weight = weight;
        this.bmi = bmi;
        this.bodyFat = bodyFat;
        this.waistCircum = waistCircum;
        this.hipCircum = hipCircum;
        this.neckCircum = neckCircum;
        this.wristCircum = wristCircum;
        this.systolicBp = systolicBp;
        this.diastolicBp = diastolicBp;
        this.heartRate = heartRate;
        this.visceralFat = visceralFat;
        this.diseases = diseases;
        this.photoPath = photoPath;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }

    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }

    public void setUserId(long userId) { this.userId = userId; }

    public LocalDateTime getMeasureTime() { return measureTime; }

    public void setMeasureTime(LocalDateTime measureTime) { this.measureTime = measureTime; }

    public double getHeight() { return height; }

    public void setHeight(double height) { this.height = height; }

    public double getWeight() { return weight; }

    public void setWeight(double weight) { this.weight = weight; }

    public double getBmi() { return bmi; }

    public void setBmi(double bmi) { this.bmi = bmi; }

    public double getBodyFat() { return bodyFat; }

    public void setBodyFat(double bodyFat) { this.bodyFat = bodyFat; }

    public Double getWaistCircum() { return waistCircum; }

    public void setWaistCircum(Double waistCircum) { this.waistCircum = waistCircum; }

    public Double getHipCircum() { return hipCircum; }

    public void setHipCircum(Double hipCircum) { this.hipCircum = hipCircum; }

    public Double getNeckCircum() { return neckCircum; }

    public void setNeckCircum(Double neckCircum) { this.neckCircum = neckCircum; }

    public Double getWristCircum() { return wristCircum; }

    public void setWristCircum(Double wristCircum) { this.wristCircum = wristCircum; }

    public Integer getSystolicBp() { return systolicBp; }

    public void setSystolicBp(Integer systolicBp) { this.systolicBp = systolicBp; }

    public Integer getDiastolicBp() { return diastolicBp; }

    public void setDiastolicBp(Integer diastolicBp) { this.diastolicBp = diastolicBp; }

    public Integer getHeartRate() { return heartRate; }

    public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }

    public Integer getVisceralFat() { return visceralFat; }

    public void setVisceralFat(Integer visceralFat) { this.visceralFat = visceralFat; }

    public String getDiseases() { return diseases; }

    public void setDiseases(String diseases) { this.diseases = diseases; }

    public String getPhotoPath() { return photoPath; }

    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getAge() { return age; }

    public void setAge(int age) { this.age = age; }

    public int getGender() { return gender; }

    public void setGender(int gender) { this.gender = gender; }
}
