package com.bmi.i18n;

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

    private static Properties load(String name) {
        Properties p = new Properties();
        try (InputStream is = I18n.class.getClassLoader().getResourceAsStream("com/bmi/i18n/" + name)) {
            if (is != null) {
                // 关键：.properties 含中文，必须以 UTF-8 读取，否则默认 ISO-8859-1 会乱码。
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 资源缺失不阻断启动，I18n.t 会回退返回 key
        }
        return p;
    }
}
