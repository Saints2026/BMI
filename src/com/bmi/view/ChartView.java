package com.bmi.view;

import com.bmi.controller.ChartController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.I18n;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.db.DbException;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 数据折线图表页面（对齐 ui_design.md 第三章 / 图表专项规范）。
 * 多维度独立动态折线图：BMI / 体重 / 体脂率 / 血压(收缩+舒张) / 心率 / 内脏脂肪。
 * 自动从 JdbcRecordDao 历史数据渲染；新增/修改记录后调用 refresh() 实时同步（FR-06）。
 * 数据点 < 2 显示 chart.noData；无记录显示 chart.empty。
 */
public class ChartView extends VBox implements LangChangeListener {

    private final long userId;
    private final ChartController chartController;
    private final Label statusLabel = new Label();
    private final Button btnRefresh = new Button();
    private final VBox chartArea = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(chartArea);

    /** 单系列定义：名称 key + 取值函数。 */
    private static class SeriesDef {
        final String nameKey;
        final ToDoubleFunction<BodyRecord> f;

        SeriesDef(String nameKey, ToDoubleFunction<BodyRecord> f) {
            this.nameKey = nameKey;
            this.f = f;
        }
    }

    public ChartView(long userId, ChartController chartController) {
        this.userId = userId;
        this.chartController = chartController;
        setSpacing(10);
        setPadding(new Insets(16));
        btnRefresh.setOnAction(e -> refresh());
        getChildren().addAll(new HBox(10, btnRefresh, statusLabel), scroll);
        refresh();
        AppConfig.getInstance().addListener(this);
    }

    /** 重新拉取历史数据并渲染全部维度图表（新增/修改记录后调用）。 */
    public void refresh() {
        List<BodyRecord> data;
        try {
            data = chartController.getTrend(userId);
        } catch (DbException e) {
            statusLabel.setText("数据读取失败，请检查数据库或稍后重试");
            chartArea.getChildren().clear();
            return;
        }

        chartArea.getChildren().clear();
        statusLabel.setText("");

        if (data.isEmpty()) {
            chartArea.getChildren().add(new Label(I18n.t("chart.empty")));
            return;
        }

        // 各维度独立折线图
        chartArea.getChildren().add(makeChart("chart.bmi", data,
                new SeriesDef("chart.bmi", r -> r.getBmi())));
        chartArea.getChildren().add(makeChart("chart.weight", data,
                new SeriesDef("chart.weight", r -> r.getWeight())));
        chartArea.getChildren().add(makeChart("chart.bodyfat", data,
                new SeriesDef("chart.bodyfat", r -> r.getBodyFat())));
        chartArea.getChildren().add(makeChart("chart.bp", data,
                new SeriesDef("input.systolic", r -> safe(r.getSystolicBp())),
                new SeriesDef("input.diastolic", r -> safe(r.getDiastolicBp()))));
        chartArea.getChildren().add(makeChart("chart.heart", data,
                new SeriesDef("chart.heart", r -> safe(r.getHeartRate()))));
        chartArea.getChildren().add(makeChart("chart.visceral", data,
                new SeriesDef("chart.visceral", r -> safe(r.getVisceralFat()))));
    }

    private double safe(Integer v) {
        return v == null ? 0.0 : v;
    }

    /** 构造单/多系列折线图；数据点 < 2 显示 chart.noData 而非趋势线。返回 Node 便于统一加入容器。 */
    private javafx.scene.Node makeChart(String titleKey, List<BodyRecord> data, SeriesDef... defs) {
        if (data.size() < 2) {
            VBox box = new VBox(4);
            box.getChildren().add(new Label(I18n.t(titleKey)));
            Label tip = new Label(I18n.t("chart.noData"));
            tip.setStyle("-fx-text-fill:#888;");
            box.getChildren().add(tip);
            return box;
        }
        NumberAxis x = new NumberAxis();
        x.setLabel("#");
        NumberAxis y = new NumberAxis();
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setTitle(I18n.t(titleKey));
        chart.setPrefSize(520, 220);
        chart.getStyleClass().add("bmi-chart");
        for (SeriesDef def : defs) {
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName(I18n.t(def.nameKey));
            for (int i = 0; i < data.size(); i++) {
                s.getData().add(new XYChart.Data<>(i + 1, def.f.applyAsDouble(data.get(i))));
            }
            chart.getData().add(s);
        }
        return chart;
    }

    private void refreshTexts() {
        btnRefresh.setText(I18n.t("chart.refresh"));
        refresh();
    }

    @Override
    public void onLangChange() {
        refreshTexts();
    }
}
