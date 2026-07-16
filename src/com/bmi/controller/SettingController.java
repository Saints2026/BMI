package com.bmi.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 系统设置控制器（对齐 ui_design.md 第八章「系统设置页面」+ db_design.md §8.3.3）。
 *
 * 职责：
 *  - 全局配色 / 语言默认：持久化到 {@code app-config.properties}（ui.theme / ui.lang.default）；
 *  - 个人健康资料：基线身高/体重、目标体重、年龄、性别，按用户 ID 隔离存入 {@code user-profile.properties}。
 *
 * 安全与分层（宪章第 4/7 节密钥安全 + 配置外置）：
 *  - 本控制器不读取/展示任何密钥明文（db-config.properties / ai-key.properties），仅管理 UI 偏好与资料；
 *  - 个人资料采用「配置外置」而非改动 user 表结构（user 表仅含身份要素），避免无谓 schema 变更；
 *  - 两个 properties 文件属本地数据（建议加入 gitignore），不入库、不提交仓库。
 */
public class SettingController {

    private static final Path CONFIG = Paths.get("app-config.properties");
    private static final Path PROFILE = Paths.get("user-profile.properties");

    public SettingController() {
        // 无状态，无需依赖注入；配置按需读写文件
    }

    // ============ 全局配色 ============

    /** 设置全局主题（light / dark 等），覆盖 CSS 变量即时生效。 */
    public void setTheme(String theme) {
        setProp(CONFIG, "ui.theme", theme);
    }

    public String getTheme() {
        return getProp(CONFIG, "ui.theme", "light");
    }

    // ============ 语言默认 ============

    /** 设置默认语言（zh / en），写 app-config.properties，下次启动/当前会话生效。 */
    public void setLangDefault(String lang) {
        setProp(CONFIG, "ui.lang.default", lang);
    }

    public String getLangDefault() {
        return getProp(CONFIG, "ui.lang.default", "zh");
    }

    // ============ 个人健康资料（按用户隔离，配置外置） ============

    /**
     * 更新个人健康资料基线（不改动 user 表）。
     *
     * @return 是否保存成功
     */
    public boolean updateProfile(long userId, UserProfile profile) {
        if (profile == null) {
            return false;
        }
        Properties p = load(PROFILE);
        p.setProperty("profile." + userId + ".baselineHeight", String.valueOf(profile.baselineHeight));
        p.setProperty("profile." + userId + ".baselineWeight", String.valueOf(profile.baselineWeight));
        p.setProperty("profile." + userId + ".targetWeight", String.valueOf(profile.targetWeight));
        p.setProperty("profile." + userId + ".age", String.valueOf(profile.age));
        p.setProperty("profile." + userId + ".gender", String.valueOf(profile.gender));
        return store(PROFILE, p);
    }

    public UserProfile getProfile(long userId) {
        Properties p = load(PROFILE);
        UserProfile pf = new UserProfile();
        pf.baselineHeight = d(p.getProperty("profile." + userId + ".baselineHeight"));
        pf.baselineWeight = d(p.getProperty("profile." + userId + ".baselineWeight"));
        pf.targetWeight = d(p.getProperty("profile." + userId + ".targetWeight"));
        pf.age = (int) d(p.getProperty("profile." + userId + ".age"));
        pf.gender = (int) d(p.getProperty("profile." + userId + ".gender"));
        return pf;
    }

    /** 个人健康资料值对象（基线数据，非身份要素）。 */
    public static class UserProfile {
        public double baselineHeight; // 基线身高 cm
        public double baselineWeight; // 基线体重 kg
        public double targetWeight;   // 目标体重 kg
        public int age;               // 年龄 1..120
        public int gender;            // 1=男 0=女
    }

    // ============ 属性文件读写辅助 ============

    private Properties load(Path file) {
        Properties p = new Properties();
        if (!Files.isRegularFile(file)) {
            return p;
        }
        try (InputStream is = Files.newInputStream(file)) {
            p.load(is);
        } catch (IOException ignored) {
            // 读取失败返回空属性，不阻断
        }
        return p;
    }

    private boolean store(Path file, Properties p) {
        try {
            Files.createDirectories(file.getParent() == null ? Paths.get(".") : file.getParent());
            try (java.io.OutputStream os = Files.newOutputStream(file)) {
                p.store(os, "BMI local config - do not commit");
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void setProp(Path file, String key, String value) {
        if (value == null) {
            return;
        }
        Properties p = load(file);
        p.setProperty(key, value);
        store(file, p);
    }

    private String getProp(Path file, String key, String def) {
        String v = load(file).getProperty(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    /** 容错解析 double（null/非法返回 0）。 */
    private double d(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
