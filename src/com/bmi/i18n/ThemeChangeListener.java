package com.bmi.i18n;

/**
 * 主题变更监听器（与 LangChangeListener 对称）。
 *
 * 全局主题变更（用户在设置页 / 顶部栏切换配色）时，AppConfig 会广播给所有已注册监听者，
 * 监听者可据此刷新自身 CSS（换肤）与图表配色，实现「无需重启、实时换主题」。
 */
public interface ThemeChangeListener {
    /** 主题已变更，回调中可通过 AppConfig.getInstance().getTheme() 取得最新 cssClass。 */
    void onThemeChange();
}
