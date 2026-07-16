package com.bmi.view.util;

import com.bmi.i18n.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * 全局 BMI 浮动卡片（对齐 V17「所有数据页统一 BMI 浮动卡片」规范）。
 *
 * 与 InputView 内置卡片视觉一致，独立为工具类便于在 ChartView / HistoryView / MainView 等
 * 数据页复用，避免重复实现。调用 {@link #update(double)} 实时刷新数值与分级配色。
 */
public final class BmiFloatingCard {

    private final VBox card;
    private final Label header = new Label();
    private final Label statusTag = new Label();
    private final Label value = new Label();
    private final Label grade = new Label();

    private BmiFloatingCard() {
        card = new VBox(6);
        card.getStyleClass().add("bmi-floating-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(200);
        card.setPadding(new Insets(2, 4, 2, 4));

        header.getStyleClass().add("bmi-floating-header");
        statusTag.getStyleClass().add("bmi-floating-status-tag");
        value.getStyleClass().add("bmi-floating-bmi-value");
        grade.getStyleClass().add("bmi-floating-grade-label");

        HBox headerRow = new HBox(8, header, statusTag);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(headerRow, value, grade);
        header.setText(I18n.t("input.bmiRealtime"));
        statusTag.setText(I18n.t("input.statusActive"));
    }

    public static BmiFloatingCard create() {
        return new BmiFloatingCard();
    }

    /** 返回可加入布局的卡片节点。 */
    public VBox node() {
        return card;
    }

    /** 用最新 BMI 刷新卡片（数值 + 分级文字 + 主题分级配色）。 */
    public void update(double bmi) {
        String g = gradeName(bmi);
        String color = ThemeConstant.bmiGradeColor(bmi);
        value.setText("BMI " + Math.round(bmi * 10.0) / 10.0);
        value.setStyle("-fx-font-size:32px; -fx-font-weight:bold; -fx-text-fill:" + color + ";");
        grade.setText(g);
        grade.setStyle("-fx-font-size:14px; -fx-text-fill:" + color + ";");
    }

    /** 无数据时清空卡片。 */
    public void clear() {
        value.setText("");
        grade.setText("");
    }

    /** 语言切换时刷新卡片文案（表头 / 状态标签）。 */
    public void refresh() {
        header.setText(I18n.t("input.bmiRealtime"));
        statusTag.setText(I18n.t("input.statusActive"));
    }

    private String gradeName(double bmi) {
        if (bmi < 18.5) return I18n.t("grade.thin");
        if (bmi < 24)   return I18n.t("grade.normal");
        if (bmi < 28)   return I18n.t("grade.overweight");
        return I18n.t("grade.obese");
    }
}
