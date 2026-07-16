package com.bmi.i18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局应用配置单例（对齐 ui_design.md 一.1）。
 * 持有当前语言与主题，语言变更时广播给所有已注册的 LangChangeListener。
 *
 * 语言默认值的持久化由 SettingController 写入 app-config.properties（ui.lang.default）；
 * 本类在运行时仅持有内存态，启动时可由 MainView 读取配置回填。
 */
public final class AppConfig {

    private static final AppConfig INSTANCE = new AppConfig();

    private Lang lang = Lang.ZH;
    private String theme = "light";
    private final List<LangChangeListener> listeners = new ArrayList<>();

    private AppConfig() {
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    public Lang getLang() {
        return lang;
    }

    public void setLang(Lang l) {
        if (l == null || l == lang) {
            return;
        }
        this.lang = l;
        for (LangChangeListener listener : new ArrayList<>(listeners)) {
            listener.onLangChange();
        }
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String t) {
        if (t != null) {
            this.theme = t;
        }
    }

    public void addListener(LangChangeListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(LangChangeListener l) {
        listeners.remove(l);
    }
}
