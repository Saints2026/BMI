package com.bmi.view;

import com.bmi.controller.ReportController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 报告导出页面（对齐 ui_design.md 第五章）。
 * 提供 HTML 报告生成与预览功能。
 */
public class ReportView extends VBox implements LangChangeListener, ThemeChangeListener {

    private final ReportController reportController;
    private final long userId;
    private final Label title;
    private final TextArea previewArea;
    private final Button exportBtn;
    private final Label pathLabel;

    public ReportView(ReportController reportController, long userId) {
        this.reportController = reportController;
        this.userId = userId;
        setPadding(new Insets(24));
        setSpacing(15);
        setStyle("-fx-background-color:" + ThemeConstant.DEFAULT_THEME.bg() + ";");

        title = StyleFactory.title("nav.report");

        previewArea = new TextArea();
        previewArea.setPromptText(I18nUtil.t("report.export") + "...");
        previewArea.setPrefHeight(400);
        previewArea.setWrapText(true);
        previewArea.setEditable(false);

        pathLabel = new Label(I18nUtil.t("report.path"));
        pathLabel.setStyle("-fx-font-size: 13px;");

        exportBtn = StyleFactory.primaryButton("report.export");
        exportBtn.setOnAction(e -> onExport());

        HBox btnBox = new HBox(16, exportBtn);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, pathLabel, previewArea, btnBox);

        AppConfig.getInstance().addListener(this);
    }

    private void onExport() {
        previewArea.setText(I18nUtil.t("report.export") + "...");

        try {
            Timestamp start = Timestamp.valueOf("2020-01-01 00:00:00");
            Timestamp end = new Timestamp(System.currentTimeMillis());

            ReportController.ReportOptions opts = new ReportController.ReportOptions();
            opts.includeBasic = true;
            opts.includeTrend = true;
            opts.includeAi = true;

            String html = reportController.exportHtml(userId, start, end, opts);
            if (html != null && !html.isEmpty()) {
                previewArea.setText(html);
                pathLabel.setText(I18nUtil.t("report.path") + "：已生成 " + html.length() + " 字符");
            } else {
                previewArea.setText("报告生成失败，请稍后重试");
            }
        } catch (Exception e) {
            previewArea.setText("报告导出异常：" + e.getMessage());
        }
    }

    @Override
    public void onLangChange() {
        title.setText(I18nUtil.t("nav.report"));
        exportBtn.setText(I18nUtil.t("report.export"));
        pathLabel.setText(I18nUtil.t("report.path"));
    }

    @Override
    public void onThemeChange() {
        setStyle("-fx-background-color:" + ThemeConstant.DEFAULT_THEME.bg() + ";");
    }

    public void dispose() {
        AppConfig.getInstance().removeListener(this);
    }
}
