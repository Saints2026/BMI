package com.bmi.view;

import com.bmi.controller.ChartController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.I18n;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.db.DbException;
import com.bmi.view.util.BmiFloatingCard;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 数据图表页面（对齐 ui_design.md 第三章 / 图表专项规范）。
 *
 * 功能：
 *  - 多指标趋势叠加（默认 收缩压+舒张压+心率 三条线，主题配色）+ 单/多 模式切换；
 *  - 真实导出图片（Snapshot → PNG，去除 todo 占位）；
 *  - 无记录 / 数据点 &lt; 2 时居中提示 chart.empty / chart.noData；
 *  - 底部历史数据表格（测量时间 / 身高 / 体重 / BMI / 体脂率 / 血压 / 心率 / 腰围）；
 *  - 「放大查看」按钮打开 ChartPopup 弹窗（滚轮缩放 / 拖拽平移）；
 *  - 监听语言 / 主题切换实时刷新。
 */
public class ChartView extends VBox implements LangChangeListener, ThemeChangeListener {

    private final long userId;
    private final ChartController chartController;

    private final Label statusLabel = new Label();
    private final Button btnRefresh = StyleFactory.secondaryButton("chart.refresh");
    private final Button btnExport = StyleFactory.secondaryButton("chart.export");
    private final Button btnZoom = StyleFactory.secondaryButton("chart.zoom");
    private final Button btnMode = StyleFactory.switchButton("chart.mode.multi");

    /** 多指标叠加模式的各指标勾选框。 */
    private final HBox seriesBox = new HBox(10);
    private final ComboBox<Metric> singleCombo = StyleFactory.comboBox();

    private final VBox chartArea = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(chartArea);

    private final BmiFloatingCard bmiCard = BmiFloatingCard.create();

    private final TableView<RecordRow> historyTable = new TableView<>();

    /** 当前主图表（用于导出 / 主题重绘）。 */
    private LineChart<Number, Number> mainChart;

    /** 单 / 多 模式：true=多指标叠加。 */
    private boolean multiMode = true;

    /** 指标定义：稳定键 + i18n 名称 + 取值函数。 */
    private static class Metric {
        final String key;
        final String nameKey;
        final ToDoubleFunction<BodyRecord> f;

        Metric(String key, String nameKey, ToDoubleFunction<BodyRecord> f) {
            this.key = key;
            this.nameKey = nameKey;
            this.f = f;
        }
    }

    private final List<Metric> METRICS = new ArrayList<>();
    private final List<CheckBox> seriesChecks = new ArrayList<>();

    private static class RecordRow {
        final String date, height, weight, bmi, bodyFat, bp, heart, waist;
        RecordRow(String date, String height, String weight, String bmi,
                  String bodyFat, String bp, String heart, String waist) {
            this.date = date; this.height = height; this.weight = weight; this.bmi = bmi;
            this.bodyFat = bodyFat; this.bp = bp; this.heart = heart; this.waist = waist;
        }
        public String getDate() { return date; }
        public String getHeight() { return height; }
        public String getWeight() { return weight; }
        public String getBmi() { return bmi; }
        public String getBodyFat() { return bodyFat; }
        public String getBp() { return bp; }
        public String getHeart() { return heart; }
        public String getWaist() { return waist; }
    }

    public ChartView(long userId, ChartController chartController) {
        this.userId = userId;
        this.chartController = chartController;

        setSpacing(10);
        setPadding(new Insets(16));

        // 指标清单（默认多指标叠加展示 收缩压+舒张压+心率）
        METRICS.add(new Metric("bmi", "chart.metricBmi", r -> r.getBmi()));
        METRICS.add(new Metric("weight", "chart.metricWeight", r -> r.getWeight()));
        METRICS.add(new Metric("bodyfat", "chart.metricBodyfat", r -> r.getBodyFat()));
        METRICS.add(new Metric("systolic", "input.systolic", r -> safe(r.getSystolicBp())));
        METRICS.add(new Metric("diastolic", "input.diastolic", r -> safe(r.getDiastolicBp())));
        METRICS.add(new Metric("heart", "chart.metricHeart", r -> safe(r.getHeartRate())));
        METRICS.add(new Metric("visceral", "chart.metricVisceral", r -> safe(r.getVisceralFat())));
        METRICS.add(new Metric("waist", "chart.metricWaist", r -> safe(r.getWaistCircum())));

        buildSeriesControls();

        btnMode.setOnAction(e -> toggleMode());
        btnRefresh.setOnAction(e -> refresh());
        btnExport.setOnAction(e -> exportImage());
        btnZoom.setOnAction(e -> {
            ChartPopup popup = new ChartPopup(userId, chartController);
            popup.show();
        });

        setupHistoryTable();

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("bmi-topbar");
        toolbar.getChildren().addAll(btnMode, seriesBox, singleCombo, btnZoom, btnExport, btnRefresh, statusLabel, bmiCard.node());
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(4, 0, 4, 0));

        VBox historyWrap = new VBox(8, StyleFactory.sectionTitle("chart.history"), historyTable);
        historyWrap.setPadding(new Insets(8, 0, 0, 0));

        getChildren().addAll(toolbar, scroll, historyWrap);

        refresh();
        AppConfig.getInstance().addListener(this);
        AppConfig.getInstance().addThemeListener(this);
    }

    private void buildSeriesControls() {
        // 多指标勾选框
        for (Metric m : METRICS) {
            CheckBox cb = new CheckBox(I18n.t(m.nameKey));
            // 默认勾选 收缩压 + 舒张压 + 心率
            cb.setSelected(m.key.equals("systolic") || m.key.equals("diastolic") || m.key.equals("heart"));
            cb.setOnAction(e -> { if (multiMode) refresh(); });
            seriesBox.getChildren().add(cb);
            seriesChecks.add(cb);
        }
        // 单指标下拉
        singleCombo.getItems().setAll(METRICS);
        singleCombo.setButtonCell(metricCell());
        singleCombo.setCellFactory(lv -> metricCell());
        singleCombo.setValue(METRICS.get(0));
        singleCombo.setOnAction(e -> { if (!multiMode) refresh(); });
        singleCombo.setVisible(false);
        seriesBox.setVisible(true);
        syncModeControls();
    }

    private javafx.scene.control.ListCell<Metric> metricCell() {
        return new javafx.scene.control.ListCell<Metric>() {
            @Override protected void updateItem(Metric item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : I18n.t(item.nameKey));
            }
        };
    }

    private void toggleMode() {
        multiMode = !multiMode;
        syncModeControls();
        refresh();
    }

    private void syncModeControls() {
        btnMode.setText(I18n.t(multiMode ? "chart.mode.multi" : "chart.mode.single"));
        seriesBox.setVisible(multiMode);
        singleCombo.setVisible(!multiMode);
    }

    /** 重新拉取历史数据并渲染主图表 + 历史表格。 */
    public void refresh() {
        List<BodyRecord> data;
        try {
            data = chartController.getTrend(userId);
        } catch (DbException e) {
            statusLabel.setText(I18n.t("chart.error"));
            chartArea.getChildren().clear();
            historyTable.getItems().clear();
            return;
        }

        statusLabel.setText("");
        chartArea.getChildren().clear();

        if (data.isEmpty()) {
            chartArea.getChildren().add(centeredPlaceholder("chart.empty"));
            mainChart = null;
            bmiCard.clear();
        } else if (data.size() < 2) {
            chartArea.getChildren().add(centeredPlaceholder("chart.noData"));
            mainChart = null;
            bmiCard.update(data.get(data.size() - 1).getBmi());
        } else {
            mainChart = buildMainChart(data);
            chartArea.getChildren().add(mainChart);
            bmiCard.update(data.get(data.size() - 1).getBmi());
        }

        fillHistoryTable(data);
    }

    /** 居中占位提示（无记录 / 数据点不足）。 */
    private VBox centeredPlaceholder(String key) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60, 0, 60, 0));
        Label tip = new Label(I18n.t(key));
        tip.getStyleClass().add("bmi-empty-hint");
        box.getChildren().add(tip);
        return box;
    }

    /** 构造主图表：多模式叠加勾选指标；单模式仅单个指标。 */
    private LineChart<Number, Number> buildMainChart(List<BodyRecord> data) {
        NumberAxis x = new NumberAxis();
        x.setLabel("#");
        NumberAxis y = new NumberAxis();
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setTitle(I18n.t(multiMode ? "chart.overlay" : "chart.single"));
        chart.setPrefSize(640, 320);
        chart.getStyleClass().add("bmi-chart");

        ThemeConstant.Theme theme = ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme());
        int idx = 0;

        if (multiMode) {
            for (int i = 0; i < METRICS.size(); i++) {
                if (seriesChecks.get(i).isSelected()) {
                    Metric m = METRICS.get(i);
                    idx = addSeries(chart, data, m, theme, idx);
                }
            }
            if (idx == 0) {
                // 未勾选任何指标：提示
                statusLabel.setText(I18n.t("chart.noData"));
            }
        } else {
            Metric m = singleCombo.getValue();
            if (m != null) addSeries(chart, data, m, theme, 0);
        }
        return chart;
    }

    /** 新增一条折线，颜色按 index 取当前主题配色；返回下一个可用 index。 */
    private int addSeries(LineChart<Number, Number> chart, List<BodyRecord> data,
                          Metric m, ThemeConstant.Theme theme, int idx) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(I18n.t(m.nameKey));
        for (int i = 0; i < data.size(); i++) {
            s.getData().add(new XYChart.Data<>(i + 1, m.f.applyAsDouble(data.get(i))));
        }
        chart.getData().add(s);
        final String color = ThemeConstant.seriesColor(theme, idx);
        final XYChart.Series<Number, Number> fs = s;
        // 折线节点需待布局后存在，故延迟设置内联配色
        Platform.runLater(() -> {
            if (fs.getNode() == null) return;
            Node line = fs.getNode().lookup(".chart-series-line");
            if (line != null) line.setStyle("-fx-stroke:" + color + "; -fx-stroke-width:2;");
        });
        return idx + 1;
    }

    private double safe(Number v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    /** 导出当前主图表为 PNG（Snapshot → SwingFXUtils）。 */
    private void exportImage() {
        if (mainChart == null) {
            ToastBar.showWarning(I18n.t("chart.exportEmpty"));
            return;
        }
        WritableImage img = mainChart.snapshot(new SnapshotParameters(), null);
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.t("chart.export"));
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG (*.png)", "*.png"));
        File dir = new File(System.getProperty("user.home"), "bmi/charts");
        dir.mkdirs();
        fc.setInitialDirectory(dir);
        fc.setInitialFileName("bmi-chart-" + userId + "-" + System.currentTimeMillis() / 1000 + ".png");
        File file = fc.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", file);
            ToastBar.showSuccess(I18n.t("chart.exportOk", file.getAbsolutePath()));
        } catch (Exception ex) {
            ToastBar.showError(I18n.t("chart.exportFail", ex.getMessage()));
        }
    }

    private void setupHistoryTable() {
        StyleFactory.styleTable(historyTable);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        addColumn("chart.col.date", "date", 140);
        addColumn("chart.col.height", "height", 80);
        addColumn("chart.col.weight", "weight", 90);
        addColumn("chart.col.bmi", "bmi", 70);
        addColumn("chart.col.bodyfat", "bodyFat", 90);
        addColumn("chart.col.bp", "bp", 120);
        addColumn("chart.col.heart", "heart", 80);
        addColumn("chart.col.waist", "waist", 90);
    }

    private void addColumn(String titleKey, String prop, double width) {
        TableColumn<RecordRow, String> col = new TableColumn<>(I18n.t(titleKey));
        col.setCellValueFactory(new PropertyValueFactory<>(prop));
        col.setPrefWidth(width);
        historyTable.getColumns().add(col);
    }

    private void fillHistoryTable(List<BodyRecord> data) {
        historyTable.getItems().clear();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (BodyRecord r : data) {
            String date = r.getMeasureTime() != null
                    ? r.getMeasureTime().format(dtf) : "-";
            String bp = (r.getSystolicBp() != null ? r.getSystolicBp() : "-")
                    + "/" + (r.getDiastolicBp() != null ? r.getDiastolicBp() : "-");
            historyTable.getItems().add(new RecordRow(
                    date,
                    round1(r.getHeight()),
                    round1(r.getWeight()),
                    round1(r.getBmi()),
                    round1(r.getBodyFat()),
                    bp,
                    r.getHeartRate() != null ? r.getHeartRate().toString() : "-",
                    r.getWaistCircum() != null ? round1(r.getWaistCircum()) : "-"));
        }
    }

    private String round1(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }

    /** 切换语言：刷新全部文案。 */
    private void refreshTexts() {
        btnRefresh.setText(I18n.t("chart.refresh"));
        btnExport.setText(I18n.t("chart.export"));
        btnZoom.setText(I18n.t("chart.zoom"));
        syncModeControls();
        for (int i = 0; i < METRICS.size(); i++) {
            seriesChecks.get(i).setText(I18n.t(METRICS.get(i).nameKey));
        }
        refresh();
    }

    @Override
    public void onLangChange() {
        refreshTexts();
    }

    /** 切换主题：重绘主图表配色。 */
    @Override
    public void onThemeChange() {
        refresh();
    }
}
