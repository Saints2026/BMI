package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.controller.ChartController;
import com.bmi.controller.RecordController;
import com.bmi.controller.ReportController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.db.DbException;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康报告导出页（对齐 V17 高优先级：左筛选 + 右预览）。
 *
 * <p>左侧：章节开关（基础数据 / 趋势图 / AI 建议）+ 时间范围（起止日期）+ 导出按钮；
 * 右侧：预览区（按当前筛选统计记录数与时间跨度，并显示导出后的本地路径）。
 * 无记录时显示空态。导出调用 {@link ReportController#exportHtml} 生成单文件 HTML。
 */
public class ReportView extends StackPane implements LangChangeListener, ThemeChangeListener {

    private final long userId;
    private final ReportController reportController;
    private final RecordController recordController;
    private final ToastBar toast;

    private final BorderPane root = new BorderPane();
    private final VBox leftPanel = new VBox(12);
    private final VBox rightPanel = new VBox(12);
    private final CheckBox cbBasic = new CheckBox();
    private final CheckBox cbTrend = new CheckBox();
    private final CheckBox cbAi = new CheckBox();
    private final DatePicker dpStart = StyleFactory.datePicker();
    private final DatePicker dpEnd = StyleFactory.datePicker();
    private final Button exportBtn = StyleFactory.primaryButton("report.export");
    private final Label previewLabel = new Label();
    private final Label pathLabel = new Label();

    public ReportView(long userId, ReportController reportController, RecordController recordController,
                      ToastBar toast) {
        this.userId = userId;
        this.reportController = reportController;
        this.recordController = recordController;
        this.toast = toast;

        buildLeft();
        buildRight();

        HBox split = new HBox(16, leftPanel, rightPanel);
        split.setPadding(new Insets(16));
        HBox.setHgrow(leftPanel, Priority.NEVER);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        root.setCenter(split);

        getChildren().addAll(root, toast);
        AppConfig.getInstance().addListener(this);
        AppConfig.getInstance().addThemeListener(this);
        refreshPreview();
    }

    private void buildLeft() {
        Label title = StyleFactory.sectionTitle("report.filter");
        cbBasic.setSelected(true);
        cbTrend.setSelected(true);
        cbAi.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, cbBasic, new Label(I18nUtil.t("report.includeBasic")));
        grid.addRow(1, cbTrend, new Label(I18nUtil.t("report.includeTrend")));
        grid.addRow(2, cbAi, new Label(I18nUtil.t("report.includeAi")));
        grid.addRow(3, new Label(I18nUtil.t("report.start")), dpStart);
        grid.addRow(4, new Label(I18nUtil.t("report.end")), dpEnd);

        exportBtn.setMaxWidth(Double.MAX_VALUE);
        exportBtn.setOnAction(e -> export());

        leftPanel.getChildren().addAll(title, grid, exportBtn);
        leftPanel.setStyle("-fx-background-color:-bmi-panel-solid; -fx-background-radius:10;"
                + "-fx-border-color:-bmi-border; -fx-border-width:1; -fx-border-radius:10;"
                + "-fx-padding:16;");
        leftPanel.setMinWidth(300);
        leftPanel.setMaxWidth(340);
    }

    private void buildRight() {
        Label title = StyleFactory.sectionTitle("report.preview");
        previewLabel.setStyle("-fx-font-size:13px; -fx-text-fill:-bmi-fg;");
        previewLabel.setWrapText(true);
        pathLabel.setStyle("-fx-font-size:11px; -fx-text-fill:-bmi-muted; -fx-wrap-text:true;");
        VBox.setVgrow(previewLabel, Priority.ALWAYS);
        rightPanel.getChildren().addAll(title, previewLabel, pathLabel);
        rightPanel.setStyle("-fx-background-color:-bmi-panel-solid; -fx-background-radius:10;"
                + "-fx-border-color:-bmi-border; -fx-border-width:1; -fx-border-radius:10;"
                + "-fx-padding:16;");
    }

    private Timestamp startTs() {
        LocalDate d = dpStart.getValue();
        return d == null ? null : Timestamp.valueOf(LocalDateTime.of(d, LocalDateTime.MIN.toLocalTime()));
    }

    private Timestamp endTs() {
        LocalDate d = dpEnd.getValue();
        return d == null ? null : Timestamp.valueOf(LocalDateTime.of(d, LocalDateTime.MAX.toLocalTime()));
    }

    private void refreshPreview() {
        List<BodyRecord> list;
        try {
            list = recordController.queryRecords(userId, startTs(), endTs());
        } catch (DbException e) {
            list = List.of();
        }
        if (list.isEmpty()) {
            previewLabel.setText(I18nUtil.t("report.noData"));
            pathLabel.setText("");
            exportBtn.setDisable(true);
        } else {
            exportBtn.setDisable(false);
            BodyRecord first = list.get(0);
            BodyRecord last = list.get(list.size() - 1);
            previewLabel.setText(I18nUtil.t("report.preview.summary",
                    list.size(),
                    first.getMeasureTime() == null ? "" : first.getMeasureTime().toString(),
                    last.getMeasureTime() == null ? "" : last.getMeasureTime().toString()));
        }
    }

    private void export() {
        ReportController.ReportOptions opt = new ReportController.ReportOptions();
        opt.includeBasic = cbBasic.isSelected();
        opt.includeTrend = cbTrend.isSelected();
        opt.includeAi = cbAi.isSelected();
        String path = reportController.exportHtml(userId, startTs(), endTs(), opt);
        if (path != null) {
            toast.success(I18nUtil.t("report.exportOk"));
            pathLabel.setText(I18nUtil.t("report.path") + ": " + path);
        } else {
            toast.error(I18nUtil.t("report.exportFail"));
        }
    }

    @Override
    public void onLangChange() {
        exportBtn.setText(I18nUtil.t("report.export"));
        cbBasic.setText("");
        cbTrend.setText("");
        cbAi.setText("");
        refreshPreview();
    }

    @Override
    public void onThemeChange() {
        ThemeConstant.apply(this, ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));
    }
}
