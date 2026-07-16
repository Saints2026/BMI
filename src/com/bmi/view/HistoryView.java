package com.bmi.view;

import com.bmi.controller.RecordController;
import com.bmi.i18n.AppConfig;
import com.bmi.view.util.I18nUtil;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HistoryView extends BorderPane implements LangChangeListener {

    private final long userId;
    private final RecordController recordController;
    private final ToastBar toast;
    private final Consumer<BodyRecord> onEdit;

    private final DatePicker dpStart = StyleFactory.datePicker();
    private final DatePicker dpEnd = StyleFactory.datePicker();
    private final Button btnQuery = StyleFactory.primaryButton("history.filter.query");
    private final Button btnReset = StyleFactory.secondaryButton("history.filter.reset");

    private final Label selectCountLabel = new Label();
    private final Button btnBatchDelete = StyleFactory.dangerButton("history.batchDelete");
    private final Button btnTrendChart = StyleFactory.secondaryButton("history.generateTrend");

    private final ComboBox<String> metricCombo = new ComboBox<>();

    private final TableView<BodyRecord> table = new TableView<>();
    private final Map<Long, Boolean> selection = new LinkedHashMap<>();
    private final TableColumn<BodyRecord, Void> colSelect = new TableColumn<>();
    private final TableColumn<BodyRecord, Timestamp> colTime = new TableColumn<>();
    private final TableColumn<BodyRecord, Double> colBmi = new TableColumn<>();
    private final TableColumn<BodyRecord, Double> colBp = new TableColumn<>();
    private final TableColumn<BodyRecord, Double> colBodyFat = new TableColumn<>();
    private final TableColumn<BodyRecord, Double> colWaist = new TableColumn<>();
    private final TableColumn<BodyRecord, String> colStatus = new TableColumn<>();
    private final TableColumn<BodyRecord, Void> colAction = new TableColumn<>();

    private final LineChart<Number, Number> trendChart;
    private final Label chartTitle = StyleFactory.sectionTitle("chart.trend");
    private final Button btnExportChart = StyleFactory.primaryButton("chart.export");

    public HistoryView(long userId, RecordController recordController, ToastBar toast,
                       Consumer<BodyRecord> onEdit) {
        this.userId = userId; this.recordController = recordController;
        this.toast = toast; this.onEdit = onEdit;

        trendChart = buildTrendChart();

        buildTopToolbar();
        buildCenterContent();
        buildBottomBar();
        loadData();
        AppConfig.getInstance().addListener(this);
    }

    private void buildTopToolbar() {
        dpStart.getStyleClass().add("bmi-field");
        dpEnd.getStyleClass().add("bmi-field");

        btnQuery.setOnAction(e -> loadData());
        btnReset.setOnAction(e -> { dpStart.setValue(null); dpEnd.setValue(null); loadData(); });

        metricCombo.getItems().addAll(I18nUtil.t("chart.metricBmi"), I18nUtil.t("chart.metricBp"),
                I18nUtil.t("chart.metricBodyfat"), I18nUtil.t("chart.metricWaist"));
        metricCombo.setValue(metricCombo.getItems().get(0));
        metricCombo.setOnAction(e -> refreshTrendChart(table.getItems()));

        HBox leftGroup = new HBox(10,
                selectCountLabel, btnBatchDelete, btnTrendChart);
        leftGroup.setAlignment(Pos.CENTER_LEFT);
        leftGroup.setPadding(new Insets(8, 0, 8, 0));

        HBox rightGroup = new HBox(8,
                new Label(I18nUtil.t("history.filter.start")), dpStart,
                new Label(I18nUtil.t("history.filter.end")), dpEnd,
                metricCombo, btnQuery, btnReset);
        rightGroup.setAlignment(Pos.CENTER_LEFT);
        rightGroup.setPadding(new Insets(8, 16, 8, 8));

        HBox fullBar = new HBox(leftGroup, rightGroup);
        fullBar.setStyle("-fx-background-color:-bmi-panel-solid; -fx-border-color:-bmi-border;"
                + "-fx-border-width:0 0 1 0; -fx-padding:6 0;");
        HBox.setHgrow(leftGroup, Priority.NEVER);
        HBox.setHgrow(rightGroup, Priority.ALWAYS);

        setTop(fullBar);
    }

    private void buildTable() {
        StyleFactory.styleTable(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colSelect.setCellFactory(col -> new TableCell<BodyRecord, Void>() {
            private final CheckBox cb = new CheckBox();
            {
                cb.setOnAction(e -> {
                    BodyRecord r = getTableRow().getItem();
                    if (r != null) selection.put(r.getId(), cb.isSelected());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                BodyRecord r = getTableRow().getItem();
                cb.setSelected(r != null && selection.getOrDefault(r.getId(), false));
                setGraphic(cb);
            }
        });

        colTime.setCellValueFactory(new PropertyValueFactory<>("measureTime"));

        colBmi.setCellValueFactory(new PropertyValueFactory<>("bmi"));

        colBp.setCellFactory(col -> new TableCell<BodyRecord, Double>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                BodyRecord r = getTableRow().getItem();
                double d = r.getDiastolicBp() != null ? r.getDiastolicBp().doubleValue() : 0;
                setText(String.valueOf(item.intValue()) + "/" + (int)d);
            }
        });

        colBodyFat.setCellValueFactory(new PropertyValueFactory<>("bodyFat"));

        colWaist.setCellValueFactory(new PropertyValueFactory<>("waist"));

        colStatus.setCellFactory(col -> new TableCell<BodyRecord, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setText(null); return; }
                BodyRecord r = getTableRow().getItem(); if (r == null) return;
                setText(gradeName(r.getBmi()));
            }
        });

        colAction.setCellFactory(col -> new TableCell<BodyRecord, Void>() {
            private final Button edit = StyleFactory.secondaryButton("history.table.edit");
            private final Button del = StyleFactory.dangerButton("history.table.delete");
            {
                edit.setStyle("-fx-padding:3 8;"); del.setStyle("-fx-padding:3 8;");
                edit.setOnAction(e -> { BodyRecord r = getTableRow().getItem(); if (r != null && onEdit != null) onEdit.accept(r); });
                del.setOnAction(e -> { BodyRecord r = getTableRow().getItem(); if (r != null) confirmAndDeleteOne(r); });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                HBox box = new HBox(6, edit, del); box.setAlignment(Pos.CENTER); setGraphic(box);
            }
        });

        table.getColumns().addAll(colSelect, colTime, colBmi, colBp, colBodyFat, colWaist, colStatus, colAction);

        table.setRowFactory(tv -> new javafx.scene.control.TableRow<BodyRecord>() {
            @Override protected void updateItem(BodyRecord r, boolean empty) {
                super.updateItem(r, empty);
                getStyleClass().removeAll("row-thin", "row-normal", "row-overweight", "row-obese");
                if (!empty && r != null) {
                    double bmi = r.getBmi();
                    if (bmi < 18.5) getStyleClass().add("row-thin");
                    else if (bmi < 24) getStyleClass().add("row-normal");
                    else if (bmi < 28) getStyleClass().add("row-overweight");
                    else getStyleClass().add("row-obese");
                }
            }
        });
    }

    private LineChart<Number, Number> buildTrendChart() {
        NumberAxis x = new NumberAxis();
        NumberAxis y = new NumberAxis();
        x.setLabel(I18nUtil.t("history.table.time"));
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setTitle(I18nUtil.t("chart.trend"));
        chart.getStyleClass().add("bmi-chart");
        chart.setPrefHeight(220);
        chart.setMaxWidth(Double.MAX_VALUE);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(true);
        return chart;
    }

    private void refreshTrendChart(List<BodyRecord> data) {
        trendChart.getData().clear();
        if (data == null || data.size() < 2) return;

        String selectedMetric = metricCombo.getValue();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(selectedMetric);

        ThemeConstant.Theme curTheme = ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme());

        for (int i = 0; i < data.size(); i++) {
            BodyRecord r = data.get(i);
            double val;
            String m = selectedMetric;
            if (I18nUtil.t("chart.metricBp").equals(m)) {
                val = r.getSystolicBp() != null ? r.getSystolicBp().doubleValue() : 0;
            } else if (I18nUtil.t("chart.metricBodyfat").equals(m)) {
                val = r.getBodyFat();
            } else if (I18nUtil.t("chart.metricWaist").equals(m)) {
                val = r.getWaistCircum() != null ? r.getWaistCircum().doubleValue() : 0;
            } else {
                val = r.getBmi();
            }
            series.getData().add(new XYChart.Data<>(i + 1, val));
        }
        trendChart.getData().add(series);
    }

    private void buildCenterContent() {
        buildTable();

        HBox exportBar = new HBox(btnExportChart);
        exportBar.setAlignment(Pos.CENTER);
        exportBar.setPadding(new Insets(4, 0, 4, 0));

        VBox chartCard = new VBox(8, chartTitle, trendChart, exportBar);
        chartCard.setPadding(new Insets(12));
        chartCard.setStyle("-fx-background-color:-bmi-panel-solid; -fx-border-color:-bmi-border;"
                + "-fx-border-width:1; -fx-background-radius:10; -fx-border-radius:10;");
        btnExportChart.setOnAction(e -> ToastBar.showSuccess(I18nUtil.t("chart.export") + " "
                + I18nUtil.t("page.todo")));
        btnExportChart.setMaxWidth(160);
        VBox.setVgrow(trendChart, Priority.ALWAYS);

        VBox tableBox = new VBox(6, table);
        tableBox.setPadding(new Insets(0, 12, 8, 12));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox center = new VBox(10, tableBox, chartCard);
        setCenter(center);
    }

    private void buildBottomBar() {
        btnBatchDelete.setOnAction(e -> confirmAndBatchDelete());
        btnTrendChart.setOnAction(e -> refreshTrendChart(table.getItems()));

        HBox bar = new HBox(10, btnBatchDelete, btnTrendChart);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        for (Button b : new Button[]{btnBatchDelete, btnTrendChart}) HBox.setHgrow(b, Priority.ALWAYS);
        setBottom(bar);
    }

    private List<BodyRecord> fetchRecords() {
        return recordController.queryRecords(userId, toTs(dpStart.getValue(), true), toTs(dpEnd.getValue(), false));
    }

    private void deleteViaController(long id) { recordController.deleteRecord(id); }

    private void loadData() {
        List<BodyRecord> list = fetchRecords();
        table.setItems(javafx.collections.FXCollections.observableArrayList(list));
        refreshSelectionCount();
        refreshTrendChart(list);
        refreshTexts();
    }

    private void refreshSelectionCount() {
        long cnt = selection.values().stream().filter(Boolean::booleanValue).count();
        selectCountLabel.setText(I18nUtil.t("history.selectedCount", String.valueOf(cnt)));
    }

    private Timestamp toTs(LocalDate d, boolean start) {
        if (d == null) return null;
        LocalDateTime ldt = start ? d.atStartOfDay() : d.atTime(23, 59, 59);
        return new Timestamp(ldt.toInstant(ZoneOffset.ofHours(8)).toEpochMilli());
    }

    private void confirmAndDeleteOne(BodyRecord r) {
        if (showConfirm(I18nUtil.t("history.deleteConfirmOne"))) {
            deleteViaController(r.getId()); selection.remove(r.getId());
            loadData(); toast.success(I18nUtil.t("history.batchDeleted", "1"));
        }
    }

    private void confirmAndBatchDelete() {
        List<Long> ids = selectedIds();
        if (ids.isEmpty()) { toast.warning(I18nUtil.t("history.noneSelected")); return; }
        if (showConfirm(I18nUtil.t("history.deleteConfirm", ids.size()))) {
            for (Long id : ids) deleteViaController(id);
            selection.keySet().removeAll(ids); loadData();
            toast.success(I18nUtil.t("history.batchDeleted", String.valueOf(ids.size())));
        }
    }

    private List<Long> selectedIds() {
        return table.getItems().stream()
                .filter(r -> selection.getOrDefault(r.getId(), false))
                .map(BodyRecord::getId).collect(Collectors.toList());
    }

    private boolean showConfirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18nUtil.t("common.confirm")); alert.setHeaderText(null); alert.setContentText(message);
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(HistoryView.class.getResource("/com/bmi/view/styles.css").toExternalForm());
        pane.getStyleClass().add("bmi-dialog");
        Optional<ButtonType> res = alert.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }

    private String gradeName(double bmi) {
        if (bmi < 18.5) return I18nUtil.t("grade.thin");
        if (bmi < 24)   return I18nUtil.t("grade.normal");
        if (bmi < 28)   return I18nUtil.t("grade.overweight");
        return I18nUtil.t("grade.obese");
    }

    private void refreshTexts() {
        colSelect.setText("");
        colTime.setText(I18nUtil.t("history.table.time"));
        colBmi.setText(I18nUtil.t("history.table.bmi"));
        colBp.setText(I18nUtil.t("chart.bp"));
        colBodyFat.setText(I18nUtil.t("history.table.bodyfat"));
        colWaist.setText(I18nUtil.t("input.waist"));
        colStatus.setText(I18nUtil.t("home.status"));
        colAction.setText(I18nUtil.t("history.table.action"));

        btnQuery.setText(I18nUtil.t("history.filter.query"));
        btnReset.setText(I18nUtil.t("history.filter.reset"));
        btnBatchDelete.setText(I18nUtil.t("history.batchDelete"));
        btnTrendChart.setText(I18nUtil.t("history.generateTrend"));
        btnExportChart.setText(I18nUtil.t("chart.export"));
        chartTitle.setText(I18nUtil.t("chart.trend"));
        refreshSelectionCount();
    }

    @Override public void onLangChange() { refreshTexts(); refreshTrendChart(table.getItems()); }
}
