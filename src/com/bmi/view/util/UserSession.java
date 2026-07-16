package com.bmi.view.util;

import com.bmi.model.User;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局内存用户会话与账号注册表（UI 层临时存储）。
 *
 * <p>由于后端 {@code model/dao/controller} 受冻结约束、{@code User} 实体不可改动，
 * 用户在 {@code UserInfoInputView} 录入的体质 / 围度 / 健康指标 / 既往疾病数据
 * 统一暂存于本 UI 层单例，供首页、BMI 图表、AI 健康评估等页面在内存中读取。
 *
 * <p><b>注册账号管理</b>：注册成功后账号信息（用户名 + 密码散列 + 盐）存入
 * 内存 {@link #accountRegistry}；登录时先查本地注册表，再回退到后端 {@code UserController}。
 *
 * <p>仅做 UI 层内存临时存储，<b>不调用任何后端持久化方法</b>；
 * {@link #syncToDatabase()} 为「预留同步数据库空接口」，当前为空实现。
 */
public final class UserSession {

    /**
     * 懒加载持有者（Holder 内部类）。
     * 借助 JVM 类初始化锁保证线程安全，且只在首次 {@link #getInstance()} 时
     * 才实例化 {@link UserSession}——无 {@code synchronized}、无阻塞、无静态代码块死锁。
     */
    private static final class Holder {
        private static final UserSession INSTANCE = new UserSession();
    }

    public static UserSession getInstance() {
        return Holder.INSTANCE;
    }

    // ---- 当前活跃用户 ----
    private User user;

    // —— 基础体质（必填）——
    private Double height;   // cm
    private Double weight;   // kg
    private Integer age;     // 岁
    private String gender;   // M / F

    // —— 身体围度（选填）——
    private Double waist;
    private Double hip;
    private Double wrist;
    private Double neck;

    // —— 健康指标（选填）——
    private Integer systolic;
    private Integer diastolic;
    private Integer heartRate;
    private Integer visceralFat;

    // —— 既往疾病（选填，展示名列表）——
    private final List<String> diseases = new ArrayList<>();

    // ==================== 注册账号注册表（内存） ====================
    
    /** 
     * 账号注册表：username → [passwordHash(sha256), salt] 。
     * 仅 UI 层内存，不落库。注册成功时写入，登录时查询。
     */
    private final Map<String, String[]> accountRegistry = new HashMap<>();

    private static final SecureRandom RNG = new SecureRandom();

    private UserSession() {
    }

    // ===================== 注册/查找账号 =====================

    /**
     * 检查用户名是否已被注册（仅查 UI 层内存注册表）。
     */
    public boolean isUsernameRegistered(String username) {
        return accountRegistry.containsKey(username);
    }

    /**
     * 注册新账号到内存注册表。
     * @return true = 注册成功, false = 用户名已存在或参数非法
     */
    public boolean registerAccount(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) return false;
        if (isUsernameRegistered(username)) return false;

        String salt = generateSalt();
        String hash = sha256(salt + password);
        accountRegistry.put(username, new String[]{hash, salt});
        return true;
    }

    /**
     * 在注册表中查找已注册用户并验证密码。
     * 匹配成功返回一个轻量 User 对象（id=-1 表示纯内存账号），失败返回 null。
     */
    public User findRegisteredUser(String username, String password) {
        String[] entry = accountRegistry.get(username);
        if (entry == null) return null;
        String expectedHash = entry[0];
        String salt = entry[1];
        String inputHash = sha256(salt + password);
        if (!expectedHash.equals(inputHash)) return null;

        // 构造一个内存 User 对象用于后续流程
        User u = new User();
        u.setUsername(username);
        try { u.setId(-1L); } catch (Exception ignored) { /* id setter may not exist */ }
        return u;
    }

    // ===================== 写入体质数据 =====================

    public void setUser(User user) { this.user = user; }

    public void setBasicProfile(double height, double weight, int age, String gender) {
        this.height = height;
        this.weight = weight;
        this.age = age;
        this.gender = gender;
    }

    public void setCircumferences(Double waist, Double hip, Double wrist, Double neck) {
        this.waist = waist;
        this.hip = hip;
        this.wrist = wrist;
        this.neck = neck;
    }

    public void setVitals(Integer systolic, Integer diastolic, Integer heartRate, Integer visceralFat) {
        this.systolic = systolic;
        this.diastolic = diastolic;
        this.heartRate = heartRate;
        this.visceralFat = visceralFat;
    }

    public void setDiseases(List<String> diseases) {
        this.diseases.clear();
        if (diseases != null) this.diseases.addAll(diseases);
    }

    // ===================== 读取体质数据 =====================

    public User getUser() { return user; }
    public Double getHeight() { return height; }
    public Double getWeight() { return weight; }
    public Integer getAge() { return age; }
    public String getGender() { return gender; }
    public Double getWaist() { return waist; }
    public Double getHip() { return hip; }
    public Double getWrist() { return wrist; }
    public Double getNeck() { return neck; }
    public Integer getSystolic() { return systolic; }
    public Integer getDiastolic() { return diastolic; }
    public Integer getHeartRate() { return heartRate; }
    public Integer getVisceralFat() { return visceralFat; }
    public List<String> getDiseases() { return new ArrayList<>(diseases); }

    /** 实时 BMI（国际标准）：weight(kg) / (height(m)^2)。数据不全返回 NaN。 */
    public double calcBmi() {
        if (height == null || height <= 0 || weight == null || weight <= 0) return Double.NaN;
        double m = height / 100.0;
        return Math.round((weight / (m * m)) * 10.0) / 10.0;
    }

    // ===================== 预留空接口 =====================

    /**
     * 预留同步数据库空接口：当前仅 UI 层内存存储，不实现任何持久化逻辑。
     * 后续若后端开放持久化能力，可在此桥接（保持 UI 层调用点不变）。
     */
    public void syncToDatabase() { /* reserved: no-op */ }

    /** 清空当前会话（退出登录时调用）。保留注册表（跨会话持久）。 */
    public void clear() {
        user = null;
        height = weight = null;
        age = null;
        gender = null;
        waist = hip = wrist = neck = null;
        systolic = diastolic = heartRate = visceralFat = null;
        diseases.clear();
    }

    // ===================== 内部密码学辅助 =====================

    private static String generateSalt() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private static String sha256(String input) {
        return Sha256Util.hash(input); // 委托统一 SHA-256 工具，避免重复实现
    }
}
