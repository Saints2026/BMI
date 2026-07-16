package com.bmi.view;

import com.bmi.controller.ChartController;
import com.bmi.controller.RecordController;
import com.bmi.i18n.AppConfig;
import com.bmi.view.util.I18nUtil;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.Lang;
import com.bmi.model.BodyRecord;
import com.bmi.view.util.Responsive;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 体检数据录入页面（对齐 ui_design.md 第三章「InputView」+ 用户需求「自适应布局 / 标准化控件」）。
 *
 * 外层 BorderPane；中心 ScrollPane 内含 4 个 TitledPane 可折叠录入面板，
 * 每个面板内部为响应式 GridPane（窗口 &gt;1200 三列 / 800-1200 两列 / &lt;800 单列）。
 * 实时 BMI（FR-03）/ 体脂率（FR-04）计算；保存多条独立历史记录；
 * 支持加载并修改历史旧记录（loadRecord）；保存 / 修改经 ToastBar 反馈并联动刷新首页。
 */
public class InputView extends BorderPane implements LangChangeListener {

    private final long userId;
    private final RecordController recordController;
    private final ChartController chartController;
    private final Runnable onDataChanged;
    private final ToastBar toast;

    private final TextField tfHeight = StyleFactory.numberField("input.height");
    private final TextField tfWeight = StyleFactory.numberField("input.weight");
    private final TextField tfAge = StyleFactory.numberField("input.age");
    private final ComboBox<String> cbGender = StyleFactory.comboBox();
    private final DatePicker dpTime = StyleFactory.datePicker();
    private final Label errHeight = new Label();
    private final Label errWeight = new Label();
    private final Label errAge = new Label();

    private final TextField tfWaist = StyleFactory.numberField("input.waist");
    private final TextField tfHip = StyleFactory.numberField("input.hip");
    private final TextField tfWrist = StyleFactory.numberField("input.wrist");
    private final TextField tfNeck = StyleFactory.numberField("input.neck");
    private final TextField tfSys = StyleFactory.numberField("input.systolic");
    private final TextField tfDia = StyleFactory.numberField("input.diastolic");
    private final TextField tfHr = StyleFactory.numberField("input.heart");
    private final TextField tfVisc = StyleFactory.numberField("input.visceral");

    private final List<javafx.scene.control.CheckBox> diseaseBoxes = new ArrayList<>();
    private final javafx.scene.control.CheckBox cbNone = StyleFactory.checkBox("input.disease.none");

    private final Label lblResult = new Label();
    private final Label status = new Label();

    private final Button btnSave = StyleFactory.primaryButton("input.save");
    private final Button btnLoad = StyleFactory.secondaryButton("input.loadHistory");
    private final Button btnModify = StyleFactory.secondaryButton("input.modify");

    private BodyRecord selectedRecord = null;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public InputView(long userId, RecordController recordController, ChartController chartController,
                     Runnable onDataChanged, ToastBar toast) {
        this.userId = userId;
        this.recordController = recordController;
        this.chartController = chartController;
        this.onDataChanged = onDataChanged;
        this.toast = toast;
        buildTop();
        buildCenter();
        installRealtime();
        AppConfig.getInstance().addListener(this);
    }

    // ---------------- 顶部：标题 + 结果 + 操作 ----------------
    private void buildTop() {
        Label title = StyleFactory.title("input.title");
        HBox ops = new HBox(10, btnSave, btnLoad, btnModify);
        VBox top = new VBox(8, title, lblResult, ops, status);
        top.setPadding(new Insets(12));
        setTop(top);

        btnSave.setOnAction(e -> doSave());
        btnLoad.setOnAction(e -> loadHistoryOptions());
        btnModify.setOnAction(e -> doModify());
    }

    // ---------------- 中心：折叠面板 + 响应式栅格 ----------------
    private void buildCenter() {
        cbGender.getItems().addAll(I18nUtil.t("input.male"), I18nUtil.t("input.female"));
        cbGender.setValue(I18nUtil.t("input.male"));
        dpTime.setValue(LocalDate.now());
        for (Label l : new Label[]{errHeight, errWeight, errAge}) {
            l.setStyle("-fx-text-fill:#f44336; -fx-font-size:11px;");
        }

        TitledPane tpBasic = StyleFactory.titledPane("input.basic",
                responsiveGrid(row("input.height", tfHeight, errHeight),
                        row("input.weight", tfWeight, errWeight),
                        row("input.age", tfAge, errAge),
                        row("input.gender", cbGender, null),
                        row("input.time", dpTime, null)));
        TitledPane tpCircum = StyleFactory.titledPane("input.circum",
                responsiveGrid(row("input.waist", tfWaist, null),
                        row("input.hip", tfHip, null),
                        row("input.wrist", tfWrist, null),
                        row("input.neck", tfNeck, null)));
        TitledPane tpVital = StyleFactory.titledPane("input.vital",
                responsiveGrid(row("input.systolic", tfSys, null),
                        row("input.diastolic", tfDia, null),
                        row("input.heart", tfHr, null),
                        row("input.visceral", tfVisc, null)));

        // 疾病勾选（选填，互斥「无」）
        for (String key : new String[]{"input.disease.hypertension", "input.disease.diabetes",
                "input.disease.heart", "input.disease.hyperlipid", "input.disease.fattyliver"}) {
            javafx.scene.control.CheckBox cb = StyleFactory.checkBox(key);
            cb.setOnAction(e -> onDiseaseToggle(cb, true));
            diseaseBoxes.add(cb);
        }
        cbNone.setOnAction(e -> onDiseaseToggle(cbNone, false));
        VBox diseaseBox = new VBox(6, diseaseBoxes.toArray(new javafx.scene.control.CheckBox[0]));
        diseaseBox.getChildren().add(cbNone);
        TitledPane tpDisease = StyleFactory.titledPane("input.disease", diseaseBox);

        javafx.scene.control.Accordion acc = new javafx.scene.control.Accordion(tpBasic, tpCircum, tpVital, tpDisease);
        acc.setExpandedPane(tpBasic);
        StyleFactory.styleAccordion(acc);

        ScrollPane scroll = new ScrollPane(acc);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(8));
        setCenter(scroll);
    }

    /** 标准化录入行：标签 + 控件 + 错误提示（选填项 err 为 null）。 */
    private VBox row(String key, javafx.scene.Node node, Label err) {
        VBox b = new VBox(2, new Label(I18nUtil.t(key)), node);
        if (err != null) {
            b.getChildren().add(err);
        }
        return b;
    }

    /** 构建响应式栅格（3/2/1 列自适应）。 */
    private GridPane responsiveGrid(VBox... rows) {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        List<javafx.scene.Node> cells = new ArrayList<>();
        for (VBox r : rows) {
            cells.add(r);
        }
        Responsive.bind(grid, cells);
        return grid;
    }

    private void onDiseaseToggle(javafx.scene.control.CheckBox self, boolean isDisease) {
        if (self.isSelected() && isDisease) {
            cbNone.setSelected(false);
        } else if (self.isSelected() && !isDisease) {
            diseaseBoxes.forEach(cb -> cb.setSelected(false));
        }
    }

    // ---------------- 实时 BMI / 体脂率 ----------------
    private void installRealtime() {
        Runnable calc = this::recalc;
        tfHeight.textProperty().addListener((o, a, b) -> calc.run());
        tfWeight.textProperty().addListener((o, a, b) -> calc.run());
        tfAge.textProperty().addListener((o, a, b) -> calc.run());
        cbGender.valueProperty().addListener((o, a, b) -> calc.run());
    }

    private void recalc() {
        Double h = num(tfHeight), w = num(tfWeight);
        if (h == null || h <= 0 || w == null || w <= 0) {
            lblResult.setText("");
            return;
        }
        double bmi = Math.round((w / ((h / 100) * (h / 100))) * 10.0) / 10.0;
        StringBuilder sb = new StringBuilder(I18nUtil.t("input.result.bmi", bmi, gradeName(bmi)));
        Double age = num(tfAge);
        int gender = I18nUtil.t("input.male").equals(cbGender.getValue()) ? 1 : 0;
        if (age != null && age >= 1 && age <= 120) {
            double bf = Math.round((1.2 * bmi + 0.23 * age - 10.8 * gender - 5.4) * 10.0) / 10.0;
            sb.append("   ").append(I18nUtil.t("input.result.bodyfat", bf));
        }
        lblResult.setText(sb.toString());
        lblResult.setStyle("-fx-text-fill:" + ThemeConstant.bmiGradeColor(bmi) + "; -fx-font-weight:bold;");
    }

    // ---------------- 历史加载 / 编辑 ----------------
    private void loadHistoryOptions() {
        List<BodyRecord> list = recordController.queryRecords(userId, null, null);
        if (list.isEmpty()) {
            toast.warning(I18nUtil.t("common.emptyHint"));
            return;
        }
        // 简单起见，弹出一个选择对话框
        javafx.scene.control.ChoiceDialog<BodyRecord> dlg =
                new javafx.scene.control.ChoiceDialog<>(list.get(0), list);
        dlg.setTitle(I18nUtil.t("input.loadHistory"));
        dlg.setHeaderText(null);
        dlg.setContentText(I18nUtil.t("input.loadHistory"));
        dlg.showAndWait().ifPresent(this::loadRecord);
    }

    /** 将一条历史记录回填表单（供「修改选中记录」使用）。 */
    public void loadRecord(BodyRecord r) {
        selectedRecord = r;
        tfHeight.setText(String.valueOf(r.getHeight()));
        tfWeight.setText(String.valueOf(r.getWeight()));
        tfAge.setText(String.valueOf(r.getAge()));
        cbGender.setValue(r.getGender() == 1 ? I18nUtil.t("input.male") : I18nUtil.t("input.female"));
        if (r.getMeasureTime() != null) {
            dpTime.setValue(r.getMeasureTime().toLocalDate());
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
        recalc();
    }

    private void syncDiseases(String diseases) {
        diseaseBoxes.forEach(cb -> cb.setSelected(false));
        cbNone.setSelected(true);
        if (diseases != null && !diseases.isEmpty()) {
            cbNone.setSelected(false);
            for (javafx.scene.control.CheckBox cb : diseaseBoxes) {
                if (diseases.contains(cb.getText())) {
                    cb.setSelected(true);
                }
            }
        }
    }

    private String gatherDiseases() {
        List<String> sel = diseaseBoxes.stream()
                .filter(javafx.scene.control.CheckBox::isSelected)
                .map(javafx.scene.control.CheckBox::getText).collect(Collectors.toList());
        return sel.isEmpty() ? null : String.join(",", sel);
    }

    // ---------------- 保存 / 修改 ----------------
    private void doSave() {
        Double h = num(tfHeight), w = num(tfWeight), age = num(tfAge);
        String hErr = validate(h, 50, 250, "validate.height");
        String wErr = validate(w, 10, 300, "validate.weight");
        String aErr = validate(age, 1, 120, "validate.age");
        clearErr(errHeight, tfHeight);
        clearErr(errWeight, tfWeight);
        clearErr(errAge, tfAge);
        if (hErr != null) { markErr(errHeight, tfHeight, hErr); return; }
        if (wErr != null) { markErr(errWeight, tfWeight, wErr); return; }
        if (aErr != null) { markErr(errAge, tfAge, aErr); return; }

        int gender = I18nUtil.t("input.male").equals(cbGender.getValue()) ? 1 : 0;
        BodyRecord r = recordController.createRecord(userId, h, w, age.intValue(), gender, measureTimeStr());
        if (r == null) {
            toast.error(I18nUtil.t("input.savedEmpty"));
            return;
        }
        applyExtensions(r);
        recordController.updateRecord(r);
        toast.success(I18nUtil.t("input.savedOk", 1));
        onDataChanged.run();
    }

    private void doModify() {
        if (selectedRecord == null) {
            toast.warning(I18nUtil.t("input.noneSelected"));
            return;
        }
        Double h = num(tfHeight), w = num(tfWeight), age = num(tfAge);
        if (validate(h, 50, 250, "validate.height") != null
                || validate(w, 10, 300, "validate.weight") != null
                || validate(age, 1, 120, "validate.age") != null) {
            toast.error(I18nUtil.t("input.savedEmpty"));
            return;
        }
        BodyRecord r = new BodyRecord();
        r.setId(selectedRecord.getId());
        r.setUserId(userId);
        int gender = I18nUtil.t("input.male").equals(cbGender.getValue()) ? 1 : 0;
        r.setAge(age.intValue());
        r.setGender(gender);
        r.setMeasureTime(selectedRecord.getMeasureTime());
        applyExtensions(r);
        r.setHeight(h);
        r.setWeight(w);
        recordController.updateRecord(r);
        toast.success(I18nUtil.t("input.savedOk", 1));
        onDataChanged.run();
    }

    private void applyExtensions(BodyRecord r) {
        r.setWaistCircum(num(tfWaist));
        r.setHipCircum(num(tfHip));
        r.setWristCircum(num(tfWrist));
        r.setNeckCircum(num(tfNeck));
        r.setSystolicBp(intNum(tfSys));
        r.setDiastolicBp(intNum(tfDia));
        r.setHeartRate(intNum(tfHr));
        r.setVisceralFat(intNum(tfVisc));
        r.setDiseases(gatherDiseases());
    }

    // ---------------- 校验 / 解析辅助 ----------------
    private Double num(TextField tf) {
        String s = tf.getText().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intNum(TextField tf) {
        Double d = num(tf);
        return d == null ? null : d.intValue();
    }

    private String validate(Double v, double min, double max, String rangeKey) {
        if (v == null) {
            return "validate.required";
        }
        if (v < 0) {
            return "validate.negative";
        }
        if (v < min || v > max) {
            return rangeKey;
        }
        return null;
    }

    private void markErr(Label err, TextField field, String key) {
        StyleFactory.markError(field, err, key);
    }

    private void clearErr(Label err, TextField field) {
        StyleFactory.clearError(field, err);
    }

    private String measureTimeStr() {
        LocalDate d = dpTime.getValue();
        if (d == null) {
            return null;
        }
        return FMT.format(LocalDateTime.of(d, java.time.LocalTime.MIN));
    }

    private String fmt(Double v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String fmt(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String gradeName(double bmi) {
        if (bmi < 18.5) {
            return I18nUtil.t("grade.thin");
        }
        if (bmi < 24) {
            return I18nUtil.t("grade.normal");
        }
        if (bmi < 28) {
            return I18nUtil.t("grade.overweight");
        }
        return I18nUtil.t("grade.obese");
    }

    // ---------------- 语言刷新 ----------------
    private void refreshTexts() {
        cbGender.getItems().setAll(I18nUtil.t("input.male"), I18nUtil.t("input.female"));
        btnSave.setText(I18nUtil.t("input.save"));
        btnLoad.setText(I18nUtil.t("input.loadHistory"));
        btnModify.setText(I18nUtil.t("input.modify"));
        for (int i = 0; i < diseaseBoxes.size(); i++) {
            String[] keys = {"input.disease.hypertension", "input.disease.diabetes",
                    "input.disease.heart", "input.disease.hyperlipid", "input.disease.fattyliver"};
            diseaseBoxes.get(i).setText(I18nUtil.t(keys[i]));
        }
        cbNone.setText(I18nUtil.t("input.disease.none"));
        recalc();
    }

    @Override
    public void onLangChange() {
        refreshTexts();
    }
}
