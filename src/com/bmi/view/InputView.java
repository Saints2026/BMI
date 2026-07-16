package com.bmi.view;

import com.bmi.controller.ChartController;
import com.bmi.controller.RecordController;
import com.bmi.i18n.AppConfig;
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.i18n.LangChangeListener;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.BmiFloatingCard;
import com.bmi.view.util.Responsive;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.scene.control.Alert;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 体检数据录入页面（V17 标准单页：合并原 UserInfoInputView 与 InputView）。
 *
 * <p>外层 BorderPane；顶部标题 + 实时 BMI 浮动卡片 + 操作按钮；中心 ScrollPane 内含 4 个
 * TitledPane 折叠录入面板（基础必填 / 身体围度 / 拓展健康 / 既往疾病），每个面板内部为响应式
 * GridPane（窗口 &gt;1200 三列 / 800-1200 两列 / &lt;800 单列）。
 *
 * <p>校验标记统一：必填项标签后红色「*」，选填项标签后灰色「选填」（height/weight/age/gender/waist
 * 为必填，其余为选填）。实时 BMI（FR-03）/ 体脂率（FR-04）计算；保存多条独立历史记录；
 * 支持加载并修改历史旧记录（loadRecord）。吸烟 / 饮酒 / 运动频率迁移自原录入页。
 */
public class InputView extends BorderPane implements LangChangeListener {

    private final long userId;
    private final User user;
    private final RecordController recordController;
    private final ChartController chartController;
    private final ToastBar toast;

    // 提交防抖（互斥锁，非定时 debounce）：避免短时间内重复点击造成重复提交 / 重复跳转。
    // 锁的释放统一在 doSave/doModify 的 finally 块中完成（含校验失败与异常分支），绝不会卡死拦截跳转。
    private volatile boolean submitting = false;

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

    // 拓展健康：生活习惯（UI 字段，无独立后端列，仅前端收集）
    private final ComboBox<String> cbSmoking = StyleFactory.comboBox();
    private final ComboBox<String> cbDrinking = StyleFactory.comboBox();
    private final ComboBox<String> cbExercise = StyleFactory.comboBox();

    private final List<javafx.scene.control.CheckBox> diseaseBoxes = new ArrayList<>();
    private final javafx.scene.control.CheckBox cbNone = StyleFactory.checkBox("input.disease.none");

    // 顶部实时 BMI 浮动卡片（复用全局工具类 BmiFloatingCard）
    private final BmiFloatingCard bmiCard = BmiFloatingCard.create();

    // 选填「选填」小标签集合（语言切换时刷新）
    private final List<Label> optionalLabels = new ArrayList<>();

    private final Label lblResult = new Label();
    private final Label status = new Label();

    private final Button btnSave = StyleFactory.primaryButton("input.save");
    private final Button btnLoad = StyleFactory.secondaryButton("input.loadHistory");
    private final Button btnModify = StyleFactory.secondaryButton("input.modify");

    private BodyRecord selectedRecord = null;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public InputView(User user, RecordController recordController, ChartController chartController,
                     ToastBar toast) {
        this.user = user;
        this.userId = user.getId();
        this.recordController = recordController;
        this.chartController = chartController;
        this.toast = toast;
        buildTop();
        buildCenter();
        installRealtime();
        AppConfig.getInstance().addListener(this);
    }

    // ---------------- 顶部：标题 + BMI 浮动卡片 + 操作 ----------------
    private void buildTop() {
        Label title = StyleFactory.title("input.title");
        HBox ops = new HBox(10, btnSave, btnLoad, btnModify);
        VBox left = new VBox(8, title, lblResult, ops, status);

        VBox card = bmiCard.node();
        HBox top = new HBox(16, left, card);
        top.setPadding(new Insets(12));
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setAlignment(Pos.CENTER_RIGHT);
        setTop(top);

        btnSave.setOnAction(e -> doSave());
        btnLoad.setOnAction(e -> loadHistoryOptions());
        btnModify.setOnAction(e -> doModify());
    }

    // BMI 浮动卡片由全局 BmiFloatingCard（字段 bmiCard）提供，不在此处重复构建。

    // ---------------- 中心：折叠面板 + 响应式栅格 ----------------
    private void buildCenter() {
        cbGender.getItems().addAll(I18nUtil.t("input.male"), I18nUtil.t("input.female"));
        cbGender.setValue(I18nUtil.t("input.male"));
        dpTime.setValue(LocalDate.now());

        String[] habitOptions = {I18nUtil.t("input.habit.never"), I18nUtil.t("input.habit.sometimes"),
                I18nUtil.t("input.habit.often"), I18nUtil.t("input.habit.daily")};
        for (ComboBox<String> cb : new ComboBox[]{cbSmoking, cbDrinking, cbExercise}) {
            cb.getItems().addAll(habitOptions);
            cb.setValue(cb.getItems().get(0));
        }

        for (Label l : new Label[]{errHeight, errWeight, errAge}) {
            l.setStyle("-fx-text-fill:#f44336; -fx-font-size:11px;");
        }

        TitledPane tpBasic = StyleFactory.titledPane("input.basic",
                responsiveGrid(
                        row("input.height", tfHeight, errHeight, true),
                        row("input.weight", tfWeight, errWeight, true),
                        row("input.age", tfAge, errAge, true),
                        row("input.gender", cbGender, null, true),
                        row("input.time", dpTime, null, false)));
        TitledPane tpCircum = StyleFactory.titledPane("input.circum",
                responsiveGrid(
                        row("input.waist", tfWaist, null, true),
                        row("input.hip", tfHip, null, false),
                        row("input.wrist", tfWrist, null, false),
                        row("input.neck", tfNeck, null, false)));
        TitledPane tpExtra = StyleFactory.titledPane("input.extra",
                responsiveGrid(
                        row("input.systolic", tfSys, null, false),
                        row("input.diastolic", tfDia, null, false),
                        row("input.heart", tfHr, null, false),
                        row("input.visceral", tfVisc, null, false),
                        row("input.smoking", cbSmoking, null, false),
                        row("input.drinking", cbDrinking, null, false),
                        row("input.exercise", cbExercise, null, false)));

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

        javafx.scene.control.Accordion acc = new javafx.scene.control.Accordion(tpBasic, tpCircum, tpExtra, tpDisease);
        acc.setExpandedPane(tpBasic);
        StyleFactory.styleAccordion(acc);

        ScrollPane scroll = new ScrollPane(acc);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(8));
        setCenter(scroll);
    }

    /** 标准化录入行：必填项标签后红色「*」，选填项标签后灰色「选填」。 */
    private VBox row(String key, javafx.scene.Node node, Label err, boolean required) {
        Label label = new Label(I18nUtil.t(key));
        HBox labelBox = new HBox(4, label);
        if (required) {
            Label star = new Label("*");
            star.setStyle("-fx-text-fill:#f76b6c; -fx-font-weight:bold; -fx-font-size:13px;");
            labelBox.getChildren().add(star);
        } else {
            Label opt = new Label(I18nUtil.t("input.optional"));
            opt.setStyle("-fx-font-size:10px; -fx-text-fill:#999999;");
            optionalLabels.add(opt);
            labelBox.getChildren().add(opt);
        }
        VBox b = new VBox(2, labelBox, node);
        if (err != null) {
            b.getChildren().add(err);
        }
        // 响应式：控件随栅格列宽拉伸填满（补齐自适应布局）
        if (node instanceof Region) {
            ((Region) node).setMaxWidth(Double.MAX_VALUE);
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
            bmiCard.clear();
            return;
        }
        double bmi = Math.round((w / ((h / 100) * (h / 100))) * 10.0) / 10.0;
        String grade = gradeName(bmi);
        String color = ThemeConstant.bmiGradeColor(bmi);
        StringBuilder sb = new StringBuilder(I18nUtil.t("input.result.bmi", bmi, grade));
        Double age = num(tfAge);
        int gender = I18nUtil.t("input.male").equals(cbGender.getValue()) ? 1 : 0;
        if (age != null && age >= 1 && age <= 120) {
            double bf = Math.round((1.2 * bmi + 0.23 * age - 10.8 * gender - 5.4) * 10.0) / 10.0;
            sb.append("   ").append(I18nUtil.t("input.result.bodyfat", bf));
        }
        lblResult.setText(sb.toString());
        lblResult.setStyle("-fx-text-fill:" + color + "; -fx-font-weight:bold;");

        bmiCard.update(bmi);
    }

    // ---------------- 历史加载 / 编辑 ----------------
    private void loadHistoryOptions() {
        List<BodyRecord> list = recordController.queryRecords(userId, null, null);
        if (list.isEmpty()) {
            toast.warning(I18nUtil.t("common.emptyHint"));
            return;
        }
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

    /**
     * 保存本条记录并跳转首页：内置日志埋点 + 双层兜底跳转 + 空指针防护 + 提交锁加固。
     *
     * <p>跳转保证：无论「校验失败 / 数据库报错 / 代码异常 / 成功」哪条路径，方法结束前都会
     * 强制回到 Main 首页——成功路径由 try 内 {@link PageNavigator#toMain} 第一层兜底完成；
     * 其余路径（含提前 return 与 catch）由 finally 内 {@link PageNavigator#forceHome} 第二层兜底完成，
     * finally 末尾无任何 return 阻断兜底逻辑。
     */
    private void doSave() {
        // 防抖：短时间内重复点击只处理一次（锁在 finally 释放，支持重复点击提交）
        if (submitting) {
            logStep("忽略重复点击（提交锁生效）");
            return;
        }
        submitting = true;
        btnSave.setDisable(true);
        boolean navigated = false; // 第一层兜底是否已完成跳转
        try {
            logStep("开始保存 -> 表单校验");
            Double h = num(tfHeight), w = num(tfWeight), age = num(tfAge);
            String hErr = validate(h, 50, 250, "validate.height");
            String wErr = validate(w, 10, 300, "validate.weight");
            String aErr = validate(age, 1, 120, "validate.age");
            clearErr(errHeight, tfHeight);
            clearErr(errWeight, tfWeight);
            clearErr(errAge, tfAge);
            // 以下提前 return 均会落入 finally 的 forceHome 第二层兜底，不会跳过跳转
            if (hErr != null) { markErr(errHeight, tfHeight, hErr); logStep("校验未通过(身高) -> 兜底跳转首页"); return; }
            if (wErr != null) { markErr(errWeight, tfWeight, wErr); logStep("校验未通过(体重) -> 兜底跳转首页"); return; }
            if (aErr != null) { markErr(errAge, tfAge, aErr); logStep("校验未通过(年龄) -> 兜底跳转首页"); return; }

            logStep("表单校验通过");
            int gender = I18nUtil.t("input.male").equals(cbGender.getValue()) ? 1 : 0;
            BodyRecord r = recordController.createRecord(userId, h, w, age.intValue(), gender, measureTimeStr());
            if (r == null) {
                toast.error(I18nUtil.t("input.savedEmpty"));
                logStep("记录创建返回 null -> 兜底跳转首页");
                return;
            }
            logStep("记录创建完成(id=" + r.getId() + ")");
            applyExtensions(r);
            recordController.updateRecord(r);
            logStep("持久化入库完成");
            toast.success(I18nUtil.t("input.savedOk", 1));

            // 第一层兜底：标准跳转首页
            logStep("即将执行 toMain 跳转首页（第一层兜底）");
            PageNavigator.toMain(user);
            navigated = true;
            logStep("toMain 执行完毕（已跳转）");
        } catch (Exception ex) {
            // 全局异常捕获：弹出完整堆栈信息弹窗，确保无静默中断
            logStep("捕获异常 -> 弹窗堆栈并兜底跳转");
            showException(ex);
        } finally {
            // 提交锁加固：所有分支（含校验失败 / 异常）统一在此释放，绝不会卡死拦截跳转
            submitting = false;
            btnSave.setDisable(false);
            logStep("提交锁已释放");
            // 第二层兜底：若第一层未完成跳转，强制切回首页（末尾无 return 阻断）
            if (!navigated) {
                logStep("执行 forceHome 第二层兜底跳转首页");
                PageNavigator.forceHome(user);
            }
            logStep("doSave 流程结束");
        }
    }

    /** 流程节点日志：控制台输出 + 轻量 Toast 弹窗，区分代码阻塞位置。 */
    private void logStep(String msg) {
        String line = "[BMI][doSave] " + msg;
        System.out.println(line);
        ToastBar.showToast(ToastBar.Type.WARNING, line);
    }

    /** 异常弹窗：完整堆栈输出到控制台，并在 GUI 环境弹出堆栈 Alert（无头环境自动跳过，不崩溃）。 */
    private void showException(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String stack = sw.toString();
        System.err.println("[BMI][ERROR] InputView.doSave 异常:\n" + stack);
        ToastBar.showError(I18nUtil.t("input.saveError"));
        if (javafx.application.Platform.isFxApplicationThread()) {
            try {
                Alert alert = new Alert(Alert.AlertType.ERROR, stack, javafx.scene.control.ButtonType.OK);
                alert.setTitle(I18nUtil.t("input.saveError"));
                alert.setHeaderText(I18nUtil.t("input.saveError"));
                alert.showAndWait();
            } catch (Exception ignore) {
                // 弹窗失败不影响主流程兜底跳转
            }
        }
    }

    private void doModify() {
        if (submitting) {
            return;
        }
        submitting = true;
        btnModify.setDisable(true);
        try {
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
        } catch (Exception ex) {
            System.err.println("[BMI][ERROR] InputView.doModify failed: " + ex.getMessage());
            ToastBar.showError(I18nUtil.t("input.saveError"));
        } finally {
            submitting = false;
            btnModify.setDisable(false);
        }
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
        bmiCard.refresh();
        for (Label opt : optionalLabels) {
            opt.setText(I18nUtil.t("input.optional"));
        }
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
