package com.bmi.view.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * 独立 Alert 弹窗工具（对齐专项约束：用户名重复 / 账号密码错误 / 两次密码不一致
 * 一律弹 JavaFX Alert，不使用 Toast；成功类提示仍走 ToastBar 绿色 Toast）。
 *
 * <p>所有文案经 {@link I18nUtil#t(String)} 绑定 i18n，无硬编码中文；
 * 缺 key 时显示 {@code {key}} 占位符，便于发现遗漏。
 */
public final class Alerts {

    private Alerts() {
    }

    /** 错误型 Alert（标题取 app.title，内容取 key 对应文案）。 */
    public static void error(String key) {
        show(AlertType.ERROR, key);
    }

    /** 信息型 Alert。 */
    public static void info(String key) {
        show(AlertType.INFORMATION, key);
    }

    /** 警告型 Alert。 */
    public static void warning(String key) {
        show(AlertType.WARNING, key);
    }

    private static void show(AlertType type, String key) {
        Alert alert = new Alert(type);
        alert.setTitle(I18nUtil.t("app.title"));
        alert.setHeaderText(null);
        alert.setContentText(I18nUtil.t(key));
        alert.showAndWait();
    }
}
