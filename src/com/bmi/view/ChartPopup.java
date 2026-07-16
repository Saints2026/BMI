package com.bmi.view;

import com.bmi.controller.ChartController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.db.DbException;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ZoomableLineChart;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 独立弹窗趋势图表（对齐用户需求「全局交互组件③」）。
 *
 * - 支持鼠标滚轮缩放、按住拖拽平移（ZoomableLineChart）；
 * - 下拉切换指标：BMI / 体重 / 体脂率 / 血压（收缩压+舒张压）；
 * - 多条序列自动分配蓝 / 绿 / 橙 三色（取自 ThemeConstant 当前主题配色）；
 * - 注册为 ThemeChangeListener：切换主题时同步重绘整张图表配色，无需关闭重开；
 * - 注册为 LangChangeListener：语言切换时刷新全部文案；
 * - 数据点 &lt; 2 显示 chart.noData，无记录显示 chart.empty。
 */
public class ChartPopup implements LangChangeListener, ThemeChangeListener {

    /** 指标稳定键（与 i18n key 映射），不随语言变化。 */
    private static final String[] METRIC_KEYS = {"bmi", "weight", "bodyfat", "bp"};

    private final long userId;
    private final ChartController chartController;

    private final Stage stage = new Stage();
    private final ComboBox<String> metricCombo = StyleFactory.comboBox();
    private final CheckBox cbBmi = new CheckBox();
    private final CheckBox cbWeight = new CheckBox();
    private final CheckBox cbBodyFat = new CheckBox();
    private final Label metricLabel = new Label();
    private final Label hint = new Label();
    private final Label status = new Label();
    private final Button closeBtn = StyleFactory.secondaryButton("chart.close");
    private final Button resetBtn = StyleFactory.secondaryButton("chart.reset");
    private ZoomableLineChart chart;

    public ChartPopup(long userId, ChartController chartController) {
        this.userId = userId;
        this.chartController = chartController;

        // 指标下拉（显示文案随语言刷新，逻辑用稳定键）
        metricCombo.getItems().setAll(metricDisplayList());
        metricCombo.setValue(I18nUtil.t("chart.metricBmi"));
        metricCombo.setOnAction(e -> repaint());

        cbBmi.setText(I18nUtil.t("chart.metricBmi"));
        cbWeight.setText(I18nUtil.t("chart.metricWeight"));
        cbBodyFat.setText(I18nUtil.t("chart.metricBodyfat"));
        cbBmi.setOnAction(e -> repaint());
        cbWeight.setOnAction(e -> repaint());
        cbBodyFat.setOnAction(e -> repaint());

        closeBtn.setOnAction(e -> stage.close());
        resetBtn.setOnAction(e -> chart.resetZoom());

        metricLabel.setText(I18nUtil.t("chart.popup.metric"));
        hint.setText(I18nUtil.t("chart.zoomHint"));

        HBox top = new HBox(10);
        top.setPadding(new Insets(10));
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("bmi-topbar");
        top.getChildren().addAll(metricLabel, metricCombo, cbBmi, cbWeight, cbBodyFat, hint, resetBtn, closeBtn);
        HBox.setHgrow(hint, Priority.ALWAYS);

        NumberAxis x = new NumberAxis();
        NumberAxis y = new NumberAxis();
        x.setLabel("#");
        chart = new ZoomableLineChart(x, y);
        chart.getStyleClass().add("bmi-chart");
        chart.setPrefSize(760, 460);
        chart.setTitle(I18nUtil.t("chart.popup.title"));

        VBox center = new VBox(8, chart, status);
        center.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(center);

        stage.setTitle(I18nUtil.t("chart.popup.title"));
        stage.setScene(new javafx.scene.Scene(root, 820, 560));
        stage.getScene().getStylesheets().add(
                ChartPopup.class.getResource("/com/bmi/view/styles.css").toExternalForm());
        ThemeConstant.apply(stage.getScene(),
                ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));

        // 主题/语言切换时同步刷新（关闭时注销，避免泄漏）
        AppConfig.getInstance().addThemeListener(this);
        AppConfig.getInstance().addListener(this);
        stage.setOnHidden(e -> {
            AppConfig.getInstance().removeThemeListener(this);
            AppConfig.getInstance().removeListener(this);
        });

        repaint();
    }

    /** 当前选中指标的稳定键。 */
    private String metricKey() {
        String sel = metricCombo.getValue();
        for (int i = 0; i < METRIC_KEYS.length; i++) {
            if (I18nUtil.t(metricI18nKey(METRIC_KEYS[i])).equals(sel)) {
                return METRIC_KEYS[i];
            }
        }
        return "bmi";
    }

    private static List<String> metricDisplayList() {
        List<String> list = new ArrayList<>();
        for (String k : METRIC_KEYS) {
            list.add(I18nUtil.t(metricI18nKey(k)));
        }
        return list;
    }

    private static String metricI18nKey(String key) {
        switch (key) {
            case "weight": return "chart.metricWeight";
            case "bodyfat": return "chart.metricBodyfat";
            case "bp": return "chart.metricBp";
            default: return "chart.metricBmi";
        }
    }

    /** 重新拉取数据并按当前指标 / 叠加选项绘制（线条色取自当前主题）。 */
    private void repaint() {
        List<BodyRecord> data;
        try {
            data = chartController.getTrend(userId);
        } catch (DbException e) {
            status.setText(I18nUtil.t("chart.error"));
            chart.getData().clear();
            return;
        }
        status.setText("");

        if (data.isEmpty()) {
            chart.getData().clear();
            status.setText(I18nUtil.t("chart.empty"));
            return;
        }
        if (data.size() < 2) {
            chart.getData().clear();
            status.setText(I18nUtil.t("chart.noData"));
            return;
        }

        chart.getData().clear();
        chart.setTitle(I18nUtil.t("chart.popup.title"));

        ThemeConstant.Theme theme = ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme());
        String mk = metricKey();
        int idx = 0;

        // 主指标单线
        if (mk.equals("bmi")) {
            idx = addSeries(data, r -> r.getBmi(), I18nUtil.t("chart.metricBmi"), theme, idx);
        } else if (mk.equals("weight")) {
            idx = addSeries(data, r -> r.getWeight(), I18nUtil.t("chart.metricWeight"), theme, idx);
        } else if (mk.equals("bodyfat")) {
            idx = addSeries(data, r -> r.getBodyFat(), I18nUtil.t("chart.metricBodyfat"), theme, idx);
        } else if (mk.equals("bp")) {
            idx = addSeries(data, r -> safe(r.getSystolicBp()), I18nUtil.t("input.systolic"), theme, idx);
            idx = addSeries(data, r -> safe(r.getDiastolicBp()), I18nUtil.t("input.diastolic"), theme, idx);
        }

        // 多指标叠加（蓝 / 绿 / 橙 三色依次分配）
        if (cbBmi.isSelected() && !mk.equals("bmi")) {
            idx = addSeries(data, r -> r.getBmi(), I18nUtil.t("chart.metricBmi"), theme, idx);
        }
        if (cbWeight.isSelected() && !mk.equals("weight")) {
            idx = addSeries(data, r -> r.getWeight(), I18nUtil.t("chart.metricWeight"), theme, idx);
        }
        if (cbBodyFat.isSelected() && !mk.equals("bodyfat")) {
            idx = addSeries(data, r -> r.getBodyFat(), I18nUtil.t("chart.metricBodyfat"), theme, idx);
        }
    }

    /** 新增一条折线，颜色按 index 取当前主题配色；返回下一个可用 index。 */
    private int addSeries(List<BodyRecord> data, ToDoubleFunction<BodyRecord> f,
                          String name, ThemeConstant.Theme theme, int idx) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(name);
        for (int i = 0; i < data.size(); i++) {
            s.getData().add(new XYChart.Data<>(i + 1, f.applyAsDouble(data.get(i))));
        }
        chart.getData().add(s);
        final String color = ThemeConstant.seriesColor(theme, idx);
        final XYChart.Series<Number, Number> fs = s;
        // 折线节点需待布局后存在，故延迟设置内联配色
        Platform.runLater(() -> {
            if (fs.getNode() == null) {
                return;
            }
            Node line = fs.getNode().lookup(".chart-series-line");
            if (line != null) {
                line.setStyle("-fx-stroke:" + color + "; -fx-stroke-width:2;");
            }
        });
        return idx + 1;
    }

    private double safe(Integer v) {
        return v == null ? 0.0 : v;
    }

    /** 切换主题：重绘整张图表配色。 */
    @Override
    public void onThemeChange() {
        ThemeConstant.apply(stage.getScene(),
                ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));
        repaint();
    }

    /** 切换语言：刷新全部文案并红绘。 */
    @Override
    public void onLangChange() {
        stage.setTitle(I18nUtil.t("chart.popup.title"));
        chart.setTitle(I18nUtil.t("chart.popup.title"));
        metricLabel.setText(I18nUtil.t("chart.popup.metric"));
        String k = metricKey();
        metricCombo.getItems().setAll(metricDisplayList());
        metricCombo.setValue(I18nUtil.t(metricI18nKey(k)));
        cbBmi.setText(I18nUtil.t("chart.metricBmi"));
        cbWeight.setText(I18nUtil.t("chart.metricWeight"));
        cbBodyFat.setText(I18nUtil.t("chart.metricBodyfat"));
        hint.setText(I18nUtil.t("chart.zoomHint"));
        closeBtn.setText(I18nUtil.t("chart.close"));
        resetBtn.setText(I18nUtil.t("chart.reset"));
        repaint();
    }

    public void show() {
        stage.show();
    }
}
