package com.bmi.view;

import com.bmi.controller.ChartController;
import com.bmi.controller.RecordController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.I18n;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.BodyRecord;
import javafx.geometry.Insets;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 体检数据录入页面（对齐 ui_design.md 第三章「InputView」）。
 * Accordion 4 组折叠面板（基础必填 / 身体围度 / 健康指标 / 疾病勾选）；
 * 实时 BMI 自动计算；保存多条独立历史记录；支持加载并修改历史旧记录；
 * 保存/修改成功后回调 onDataChanged 刷新首页卡片与图表。
 */
public class InputView extends VBox implements LangChangeListener {

    private final long userId;
    private final RecordController recordController;
    private final ChartController chartController;
    private final Runnable onDataChanged;

    private final TextField tfHeight = ViewUtil.numberField("input.height");
    private final TextField tfWeight = ViewUtil.numberField("input.weight");
    private final TextField tfAge = ViewUtil.numberField("input.age");
    private final ComboBox<String> cbGender = new ComboBox<>();
    private final DatePicker dpTime = new DatePicker();
    private final Label errHeight = new Label();
    private final Label errWeight = new Label();
    private final Label errAge = new Label();

    private final TextField tfWaist = ViewUtil.numberField("input.waist");
    private final TextField tfHip = ViewUtil.numberField("input.hip");
    private final TextField tfWrist = ViewUtil.numberField("input.wrist");
    private final TextField tfNeck = ViewUtil.numberField("input.neck");
    private final TextField tfSys = ViewUtil.numberField("input.systolic");
    private final TextField tfDia = ViewUtil.numberField("input.diastolic");
    private final TextField tfHr = ViewUtil.numberField("input.heart");
    private final TextField tfVisc = ViewUtil.numberField("input.visceral");

    private final List<CheckBox> diseaseBoxes = new ArrayList<>();
    private final CheckBox cbNone = new CheckBox();

    private final Label lblResult = new Label();
    private final ComboBox<BodyRecord> cbHistory = new ComboBox<>();
    private BodyRecord selectedRecord = null;

    private final Button btnSave = new Button();
    private final Button btnLoad = new Button();
    private final Button btnModify = new Button();
    private final Label lblStatus = new Label();

    private TitledPane tpBasic, tpCircum, tpVital, tpDisease;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public InputView(long userId, RecordController recordController, ChartController chartController,
                     Runnable onDataChanged) {
        this.userId = userId;
        this.recordController = recordController;
        this.chartController = chartController;
        this.onDataChanged = onDataChanged;
        setSpacing(12);
        setPadding(new Insets(16));
        build();
        installRealtime();
        refreshTexts();
        AppConfig.getInstance().addListener(this);
    }

    private void build() {
        // 基础必填
        cbGender.getItems().addAll(I18n.t("input.male"), I18n.t("input.female"));
        cbGender.setValue(I18n.t("input.male"));
        dpTime.setValue(LocalDate.now());
        VBox basic = new VBox(4,
                labeled("input.height", tfHeight, errHeight),
                labeled("input.weight", tfWeight, errWeight),
                labeled("input.age", tfAge, errAge),
                labeled("input.gender", cbGender, null),
                labeled("input.time", dpTime, null),
                lblResult);
        // 围度
        VBox circum = new VBox(4, tfWaist, tfHip, tfWrist, tfNeck);
        // 体征
        VBox vital = new VBox(4, tfSys, tfDia, tfHr, tfVisc);
        // 疾病
        for (String key : new String[]{"input.disease.hypertension", "input.disease.diabetes",
                "input.disease.heart", "input.disease.hyperlipid", "input.disease.fattyliver"}) {
            CheckBox cb = new CheckBox(I18n.t(key));
            cb.setOnAction(e -> onDiseaseToggle(cb, true));
            diseaseBoxes.add(cb);
        }
        cbNone.setOnAction(e -> onDiseaseToggle(cbNone, false));
        VBox disease = new VBox(4, diseaseBoxes.toArray(new CheckBox[0]));
        disease.getChildren().add(cbNone);

        tpBasic = pane("input.basic", basic);
        tpCircum = pane("input.circum", circum);
        tpVital = pane("input.vital", vital);
        tpDisease = pane("input.disease", disease);
        Accordion acc = new Accordion(tpBasic, tpCircum, tpVital, tpDisease);
        acc.setExpandedPane(tpBasic);

        // 操作区
        cbHistory.setPromptText(I18n.t("input.loadHistory"));
        cbHistory.setCellFactory(lv -> recordCell());
        cbHistory.setButtonCell(recordCell());
        cbHistory.setOnAction(e -> onSelectHistory());
        btnSave.setOnAction(e -> doSave());
        btnLoad.setOnAction(e -> loadHistoryOptions());
        btnModify.setOnAction(e -> doModify());

        HBox ops = new HBox(10, btnSave, btnLoad, cbHistory, btnModify);
        getChildren().addAll(acc, ops, lblStatus);
    }

    private VBox labeled(String key, javafx.scene.Node node, Label err) {
        VBox b = new VBox(2, new Label(I18n.t(key)), node);
        if (err != null) {
            b.getChildren().add(err);
        }
        return b;
    }

    private TitledPane pane(String key, javafx.scene.Node content) {
        TitledPane tp = new TitledPane(I18n.t(key), content);
        tp.setUserData(key);
        return tp;
    }

    private javafx.scene.control.ListCell<BodyRecord> recordCell() {
        return new javafx.scene.control.ListCell<BodyRecord>() {
            @Override
            protected void updateItem(BodyRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String t = item.getMeasureTime() != null ? FMT.format(item.getMeasureTime().toLocalDateTime()) : "#" + item.getId();
                    setText(t + "  BMI=" + item.getBmi());
                }
            }
        };
    }

    private void onDiseaseToggle(CheckBox self, boolean isDisease) {
        if (self.isSelected() && isDisease) {
            cbNone.setSelected(false);
        } else if (self.isSelected() && !isDisease) {
            diseaseBoxes.forEach(cb -> cb.setSelected(false));
        }
    }

    private void installRealtime() {
        Runnable calc = this::recalc;
        tfHeight.textProperty().addListener((o, a, b) -> calc.run());
        tfWeight.textProperty().addListener((o, a, b) -> calc.run());
        tfAge.textProperty().addListener((o, a, b) -> calc.run());
        cbGender.valueProperty().addListener((o, a, b) -> calc.run());
    }

    /** 实时 BMI（FR-03）/ 体脂率（FR-04）计算刷新。 */
    private void recalc() {
        Double h = ViewUtil.parseDouble(tfHeight);
        Double w = ViewUtil.parseDouble(tfWeight);
        if (h == null || h <= 0 || w == null || w <= 0) {
            lblResult.setText("");
            return;
        }
        double bmi = Math.round((w / ((h / 100) * (h / 100))) * 10.0) / 10.0;
        StringBuilder sb = new StringBuilder(I18n.t("input.result.bmi", bmi, ViewUtil.bmiGradeName(bmi)));
        Double age = ViewUtil.parseDouble(tfAge);
        int gender = I18n.t("input.male").equals(cbGender.getValue()) ? 1 : 0;
        if (age != null && age >= 1 && age <= 120) {
            double bf = Math.round((1.2 * bmi + 0.23 * age - 10.8 * gender - 5.4) * 10.0) / 10.0;
            sb.append("   ").append(I18n.t("input.result.bodyfat", bf));
        }
        lblResult.setText(sb.toString());
        lblResult.setStyle("-fx-text-fill:" + ViewUtil.gradeColor(bmi) + "; -fx-font-weight:bold;");
    }

    private void loadHistoryOptions() {
        List<BodyRecord> list = recordController.queryRecords(userId, null, null);
        cbHistory.getItems().setAll(list);
        if (list.isEmpty()) {
            lblStatus.setText(I18n.t("common.emptyHint"));
        }
    }

    private void onSelectHistory() {
        selectedRecord = cbHistory.getValue();
        if (selectedRecord == null) {
            return;
        }
        BodyRecord r = selectedRecord;
        tfHeight.setText(String.valueOf(r.getHeight()));
        tfWeight.setText(String.valueOf(r.getWeight()));
        tfAge.setText(String.valueOf(r.getAge()));
        cbGender.setValue(r.getGender() == 1 ? I18n.t("input.male") : I18n.t("input.female"));
        if (r.getMeasureTime() != null) {
            dpTime.setValue(r.getMeasureTime().toLocalDateTime().toLocalDate());
        }
        tfWaist.setText(fmt(r.getWaistCircum()));
        tfHip.setText(fmt(r.getHipCircum()));
        tfWrist.setText(fmt(r.getWristCircum()));
        tfNeck.setText(fmt(r.getNeckCircum()));
        tfSys.setText(fmt(r.getSystolicBp()));
        tfDia.setText(fmt(r.getDiastolicBp()));
        tfHr.setText(fmt(r.getHeartRate()));
        tfVisc.setText(fmt(r.getVisceralFat()));
        syncDiseases(r.getDiseases());
    }

    private void syncDiseases(String diseases) {
        diseaseBoxes.forEach(cb -> cb.setSelected(false));
        cbNone.setSelected(true);
        if (diseases != null && !diseases.isEmpty()) {
            cbNone.setSelected(false);
            for (CheckBox cb : diseaseBoxes) {
                if (diseases.contains(cb.getText())) {
                    cb.setSelected(true);
                }
            }
        }
    }

    private String gatherDiseases() {
        List<String> sel = diseaseBoxes.stream()
                .filter(CheckBox::isSelected).map(CheckBox::getText).collect(Collectors.toList());
        return sel.isEmpty() ? null : String.join(",", sel);
    }

    private String measureTimeStr() {
        LocalDate d = dpTime.getValue();
        if (d == null) {
            return null;
        }
        return FMT.format(LocalDateTime.of(d, java.time.LocalTime.MIN));
    }

    private void doSave() {
        Double h = ViewUtil.parseDouble(tfHeight);
        Double w = ViewUtil.parseDouble(tfWeight);
        Double age = ViewUtil.parseDouble(tfAge);
        String hErr = ViewUtil.validateRange(h, 50, 250, "validate.height");
        String wErr = ViewUtil.validateRange(w, 10, 300, "validate.weight");
        String aErr = ViewUtil.validateRange(age, 1, 120, "validate.age");
        ViewUtil.clearError(errHeight, tfHeight);
        ViewUtil.clearError(errWeight, tfWeight);
        ViewUtil.clearError(errAge, tfAge);
        if (hErr != null) {
            ViewUtil.setError(errHeight, tfHeight, hErr);
            return;
        }
        if (wErr != null) {
            ViewUtil.setError(errWeight, tfWeight, wErr);
            return;
        }
        if (aErr != null) {
            ViewUtil.setError(errAge, tfAge, aErr);
            return;
        }
        int gender = I18n.t("input.male").equals(cbGender.getValue()) ? 1 : 0;
        BodyRecord r = recordController.createRecord(userId, h, w, age.intValue(), gender, measureTimeStr());
        if (r == null) {
            lblStatus.setText(I18n.t("input.savedEmpty"));
            return;
        }
        // 扩展字段经 updateRecord 持久化（db_design.md v1.1：扩展列走 update）
        applyExtensions(r);
        recordController.updateRecord(r);
        lblStatus.setText(I18n.t("input.savedOk", 1));
        onDataChanged.run();
    }

    private void doModify() {
        if (selectedRecord == null) {
            lblStatus.setText(I18n.t("input.noneSelected"));
            return;
        }
        Double h = ViewUtil.parseDouble(tfHeight);
        Double w = ViewUtil.parseDouble(tfWeight);
        Double age = ViewUtil.parseDouble(tfAge);
        if (ViewUtil.validateRange(h, 50, 250, "validate.height") != null
                || ViewUtil.validateRange(w, 10, 300, "validate.weight") != null
                || ViewUtil.validateRange(age, 1, 120, "validate.age") != null) {
            lblStatus.setText(I18n.t("input.savedEmpty"));
            return;
        }
        BodyRecord r = new BodyRecord();
        r.setId(selectedRecord.getId());
        r.setUserId(userId);
        int gender = I18n.t("input.male").equals(cbGender.getValue()) ? 1 : 0;
        r.setAge(age.intValue());
        r.setGender(gender);
        r.setMeasureTime(selectedRecord.getMeasureTime());
        applyExtensions(r);
        // 通过临时设高度体重触发校验后再 updateRecord
        r.setHeight(h);
        r.setWeight(w);
        recordController.updateRecord(r);
        lblStatus.setText(I18n.t("input.savedOk", 1));
        onDataChanged.run();
    }

    /** 将表单扩展字段写入记录（包装类型，null 表示未录入）。 */
    private void applyExtensions(BodyRecord r) {
        r.setWaistCircum(ViewUtil.parseDouble(tfWaist));
        r.setHipCircum(ViewUtil.parseDouble(tfHip));
        r.setWristCircum(ViewUtil.parseDouble(tfWrist));
        r.setNeckCircum(ViewUtil.parseDouble(tfNeck));
        r.setSystolicBp(parseInt(tfSys));
        r.setDiastolicBp(parseInt(tfDia));
        r.setHeartRate(parseInt(tfHr));
        r.setVisceralFat(parseInt(tfVisc));
        r.setDiseases(gatherDiseases());
    }

    private Integer parseInt(TextField tf) {
        String s = tf.getText().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String fmt(Double v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String fmt(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    private void refreshTexts() {
        cbGender.getItems().setAll(I18n.t("input.male"), I18n.t("input.female"));
        cbHistory.setPromptText(I18n.t("input.loadHistory"));
        btnSave.setText(I18n.t("input.save"));
        btnLoad.setText(I18n.t("input.loadHistory"));
        btnModify.setText(I18n.t("input.modify"));
        tpBasic.setText(I18n.t("input.basic"));
        tpCircum.setText(I18n.t("input.circum"));
        tpVital.setText(I18n.t("input.vital"));
        tpDisease.setText(I18n.t("input.disease"));
        for (int i = 0; i < diseaseBoxes.size(); i++) {
            String[] keys = {"input.disease.hypertension", "input.disease.diabetes",
                    "input.disease.heart", "input.disease.hyperlipid", "input.disease.fattyliver"};
            diseaseBoxes.get(i).setText(I18n.t(keys[i]));
        }
        cbNone.setText(I18n.t("input.disease.none"));
        recalc();
    }

    @Override
    public void onLangChange() {
        refreshTexts();
    }
}
