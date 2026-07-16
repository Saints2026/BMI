package com.bmi.i18n;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 双语文案工具（对齐 ui_design.md 一.1 / 一.2）。
 *
 * 规则：
 *  - 每个界面字符串通过 key 绑定，运行时按 AppConfig 当前语言取值，禁止硬编码中英文；
 *  - 资源文件位于 classpath 的 com/bmi/i18n/ui_zh.properties 与 ui_en.properties；
 *  - 开发模式下 javac 不复制 .properties，回退从 src/ 目录直接读取；
 *  - 缺失 key 时回退中文，仍缺失则返回 key 本身（便于发现遗漏）。
 *
 * 用法：I18n.t("login.title") 或带占位符 I18n.t("record.saved", n) → "已保存 {0} 条记录"。
 */
public final class I18n {

    private static final Properties ZH = load("ui_zh.properties");
    private static final Properties EN = load("ui_en.properties");

    private I18n() {
    }

    public static String t(String key) {
        return t(key, (Object[]) null);
    }

    public static String t(String key, Object... args) {
        Lang lang = AppConfig.getInstance().getLang();
        Properties p = (lang == Lang.EN) ? EN : ZH;
        String v = p.getProperty(key);
        if (v == null) {
            v = ZH.getProperty(key); // 回退中文
        }
        if (v == null) {
            return key; // 最终兜底：返回 key
        }
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                v = v.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return v;
    }

    /**
     * 加载 properties 资源文件。
     * 优先从 classpath 读取（生产模式），若为空则回退 src/ 目录（开发模式，javac 不复制 .properties）。
     * 使用 UTF-8 InputStreamReader 显式编码，避免中文乱码。
     */
    private static Properties load(String name) {
        Properties p = new Properties();
        // 1. Classpath 优先（生产部署时 .properties 与 .class 同目录）
        try (InputStream is = I18n.class.getClassLoader().getResourceAsStream("com/bmi/i18n/" + name)) {
            if (is != null) {
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                if (!p.isEmpty()) {
                    return p;
                }
            }
        } catch (Exception ignored) {
            // classpath 加载失败，继续尝试文件系统
        }
        // 2. 回退源码目录（开发模式：javac -d . 不复制 .properties 文件）
        try (InputStream is = new FileInputStream("src/com/bmi/i18n/" + name)) {
            p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 资源缺失不阻断启动，I18n.t 会回退返回 key
        }
        return p;
    }
}
