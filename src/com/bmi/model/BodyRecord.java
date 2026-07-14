package com.bmi.model;

import java.sql.Timestamp;

/**
 * 身体测量记录实体（对应 db_design.md 的 body_record 表）。
 *
 * 注意：height/weight/bmi/body_fat/measure_time 及 10 个扩展字段为持久化字段（对应 db_design.md v1.1 的 body_record 表）；
 * age/gender 为「录入时的瞬时输入」，仅用于体脂计算（Deurenberg 公式）
 * 与 AI 请求构造，按 db_design.md 约定【不落库】（仅持久化计算结果的 bmi/body_fat）。
 * 扩展字段（腰围/臀围/颈围/腕围/高压/低压/心率/内脏脂肪/疾病/照片路径）为选填，
 * 使用包装类型（Double/Integer）以区分「未录入(null)」与「录入 0 值」。
 */
public class BodyRecord {

    private long id;
    private long userId;
    private Timestamp measureTime;
    private double height; // cm，区间 [50,250]
    private double weight; // kg，区间 [10,300]
    private double bmi;    // 由 BmiCalculator 公式计算，保留 1 位小数
    private double bodyFat; // 体脂率%，由 Deurenberg 公式估算
    private Timestamp createdAt;

    // —— 扩展字段（v1.1，选填，包装类型允许 null —— 对应 body_record 扩展列）——
    private Double waistCircum;   // 腰围 cm
    private Double hipCircum;     // 臀围 cm
    private Double neckCircum;    // 颈围 cm
    private Double wristCircum;   // 腕围 cm
    private Integer systolicBp;   // 收缩压·高压 mmHg
    private Integer diastolicBp;  // 舒张压·低压 mmHg
    private Integer heartRate;    // 静息心率 bpm
    private Integer visceralFat;  // 内脏脂肪等级
    private String diseases;      // 既往疾病（逗号分隔，如 "高血压,糖尿病"）
    private String photoPath;     // 体型照片本地路径（仅存路径，不存二进制）

    // —— 瞬时输入字段（不持久化）——
    private int age;     // 年龄 [1,120]
    private int gender;  // 1=男 0=女

    public BodyRecord() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Timestamp getMeasureTime() {
        return measureTime;
    }

    public void setMeasureTime(Timestamp measureTime) {
        this.measureTime = measureTime;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getBmi() {
        return bmi;
    }

    public void setBmi(double bmi) {
        this.bmi = bmi;
    }

    public double getBodyFat() {
        return bodyFat;
    }

    public void setBodyFat(double bodyFat) {
        this.bodyFat = bodyFat;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    // —— 扩展字段 get/set（v1.1）——
    public Double getWaistCircum() {
        return waistCircum;
    }

    public void setWaistCircum(Double waistCircum) {
        this.waistCircum = waistCircum;
    }

    public Double getHipCircum() {
        return hipCircum;
    }

    public void setHipCircum(Double hipCircum) {
        this.hipCircum = hipCircum;
    }

    public Double getNeckCircum() {
        return neckCircum;
    }

    public void setNeckCircum(Double neckCircum) {
        this.neckCircum = neckCircum;
    }

    public Double getWristCircum() {
        return wristCircum;
    }

    public void setWristCircum(Double wristCircum) {
        this.wristCircum = wristCircum;
    }

    public Integer getSystolicBp() {
        return systolicBp;
    }

    public void setSystolicBp(Integer systolicBp) {
        this.systolicBp = systolicBp;
    }

    public Integer getDiastolicBp() {
        return diastolicBp;
    }

    public void setDiastolicBp(Integer diastolicBp) {
        this.diastolicBp = diastolicBp;
    }

    public Integer getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(Integer heartRate) {
        this.heartRate = heartRate;
    }

    public Integer getVisceralFat() {
        return visceralFat;
    }

    public void setVisceralFat(Integer visceralFat) {
        this.visceralFat = visceralFat;
    }

    public String getDiseases() {
        return diseases;
    }

    public void setDiseases(String diseases) {
        this.diseases = diseases;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    // —— 瞬时字段 get/set（不持久化）——
    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getGender() {
        return gender;
    }

    public void setGender(int gender) {
        this.gender = gender;
    }
}
