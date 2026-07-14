package com.bmi.i18n;

/**
 * 语言变更监听器（观察者模式，对齐 ui_design.md 一.1「切换即时生效」）。
 * 语言下拉框变更后，AppConfig 通知所有已注册视图调用 applyLang() 刷新文案。
 */
public interface LangChangeListener {
    void onLangChange();
}
