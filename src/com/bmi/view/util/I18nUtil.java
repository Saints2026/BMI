package com.bmi.view.util;

import com.bmi.i18n.AppConfig;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 双语文案工具（对齐 ui_design.md 一.1 / 用户需求「双语国际化」）。
 *
 * 资源文件位于 classpath 的 com/bmi/i18n/ui_zh.properties 与 ui_en.properties。
 * 当前语言取自 {@link AppConfig}（单例，语言切换时广播 LangChangeListener 给所有视图）。
 * 支持 {0}{1} 占位符；缺失 key 时回退中文，再回退为 key 本身（便于发现遗漏）。
 *
 * 用法：I18nUtil.t("login.title") 或带占位符 I18nUtil.t("record.saved", n)。
 */
public final class I18nUtil {

    private static final Properties ZH = load("ui_zh.properties");
    private static final Properties EN = load("ui_en.properties");

    private I18nUtil() {
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
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                v = v.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return v;
    }

    /** 透传设置当前语言（触发 AppConfig 全局广播，所有已注册视图即时刷新文案）。 */
    public static void setLang(Lang l) {
        AppConfig.getInstance().setLang(l);
    }

    /** 同 {@link #setLang(Lang)} 的语义化别名：应用并广播新语言。 */
    public static void applyLang(Lang l) {
        setLang(l);
    }

    public static Lang currentLang() {
        return AppConfig.getInstance().getLang();
    }

    /**
     * 注册语言变更监听器（观察者模式核心）。语言切换后 AppConfig 会回调
     * {@link LangChangeListener#onLangChange()}，视图在该方法内用 {@link #t(String)} 重绑全部文字，
     * 从而实现「全局刷新页面所有文字」。视图应在构造时调用本方法、销毁时调用 {@link #removeLangListener}。
     */
    public static void addLangListener(LangChangeListener l) {
        AppConfig.getInstance().addListener(l);
    }

    /** 注销语言变更监听器（视图销毁时调用，避免内存泄漏）。 */
    public static void removeLangListener(LangChangeListener l) {
        AppConfig.getInstance().removeListener(l);
    }

    private static Properties load(String name) {
        Properties p = new Properties();
        try (InputStream is = I18nUtil.class.getClassLoader()
                .getResourceAsStream("com/bmi/i18n/" + name)) {
            if (is != null) {
                // 关键：.properties 含中文，必须以 UTF-8 读取，否则默认 ISO-8859-1 会乱码。
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 资源缺失不阻断启动，t() 会回退返回 key
        }
        return p;
    }
}
