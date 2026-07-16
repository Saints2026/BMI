package com.bmi.view;

import com.bmi.controller.ChartController;
import com.bmi.controller.RecordController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import com.bmi.view.util.UserSession;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 体检数据录入页面（线性链路：UserInfoInput → Input ↔ History ↔ AiAnalysis）。
 *
 * <p>两列等宽 380px 表单（行间距 22px），顶部实时 BMI 计算卡片（匹配 RGB 规范），
 * 顶部横向 3 个分支按钮（照片 / 报告 / 设置）作为唯一入口；
 * 保存 → History，AI → AiAnalysis；编辑模式通过 loadRecord 回填。
 * 身高/年龄/性别由 UserSession 携带（用户信息页录入），本页聚焦体重与围度/体征。
 */
public class InputView extends StackPane implements LangChangeListener {

    private final User user;
    private final long userId;
    private final RecordController recordController;
    @SuppressWarnings("unused")
    private final ChartController chartController;
    private final BodyRecord editing;
    private final ToastBar toast = new ToastBar();

    private final TextField tfWeight = StyleFactory.numberField("input.weight");
    private final TextField tfWaist = StyleFactory.numberField("input.waist");
    private final TextField tfHip = StyleFactory.numberField("input.hip");
    private final TextField tfBodyFat = StyleFactory.numberField("input.bodyfat");
    private final TextField tfNeck = StyleFactory.numberField("input.neck");
    private final TextField tfWrist = StyleFactory.numberField("input.wrist");
    private final TextField tfSys = StyleFactory.numberField("input.systolic");
    private final TextField tfDia = StyleFactory.numberField("input.diastolic");
    private final TextField tfHr = StyleFactory.numberField("input.heart");
    private final TextField tfVisc = StyleFactory.numberField("input.visceral");
    private final DatePicker dpTime = StyleFactory.datePicker();

    private final Label errWeight = new Label();

    // 实时 BMI 卡片
    private final Label bmiStatusLabel = new Label();
    private final Label statusTag = new Label();
    private final Label bmiValueLabel = new Label();
    private final Label bmiGradeLabel = new Label();
    private final Label subTextLabel = new Label();

    // 顶部 3 个分支按钮 + 主操作
    private final Button btnPhoto = new Button();
    private final Button btnReport = new Button();
    private final Button btnSettings = new Button();
    private final Button btnSave = StyleFactory.primaryButton("input.save");
    private final Button btnAi = StyleFactory.secondaryButton("input.aiAnalysis");

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public InputView(User user, RecordController recordController, ChartController chartController, BodyRecord editing) {
        this.user = user;
        this.userId = user != null ? user.getId() : -1L;
        this.recordController = recordController;
        this.chartController = chartController;
        this.editing = editing;

        BorderPane bp = new BorderPane();
        bp.setTop(buildTop());
        bp.setCenter(buildCenter());
        bp.setBottom(buildBottom());

        getChildren().addAll(bp, toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);

        if (editing != null) {
            loadRecord(editing);
        }
        installRealtime();
        AppConfig.getInstance().addListener(this);
    }

    // ---------------- 顶部：标题 + 3 分支按钮 + 实时 BMI 卡片 ----------------
    private Node buildTop() {
        Label title = StyleFactory.title("input.title");
        HBox titleArea = new HBox(title);
        titleArea.setAlignment(Pos.CENTER_LEFT);

        btnPhoto.getStyleClass().addAll("bmi-btn", "bmi-btn-secondary");
        btnReport.getStyleClass().addAll("bmi-btn", "bmi-btn-secondary");
        btnSettings.getStyleClass().addAll("bmi-btn", "bmi-btn-secondary");
        btnPhoto.setOnAction(e -> PageNavigator.toPhoto(user));
        btnReport.setOnAction(e -> PageNavigator.toReport(user));
        btnSettings.setOnAction(e -> PageNavigator.toSettings(user));

        HBox branchBox = new HBox(10, btnPhoto, btnReport, btnSettings);
        branchBox.setAlignment(Pos.CENTER_LEFT);

        HBox left = new HBox(16, titleArea, branchBox);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(left, Priority.ALWAYS);

        VBox bmiCard = buildFloatingBmiCard();

        HBox topBar = new HBox(20, left, bmiCard);
        topBar.setPadding(new Insets(12, 24, 12, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color:-bmi-panel-solid; -fx-border-color:-bmi-border;"
                + "-fx-border-width:0 0 1 0;");
        return topBar;
    }

    private VBox buildFloatingBmiCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("bmi-floating-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(220);

        bmiStatusLabel.getStyleClass().add("bmi-floating-header");
        statusTag.getStyleClass().add("bmi-floating-status-tag");
        bmiValueLabel.getStyleClass().add("bmi-floating-bmi-value");
        subTextLabel.getStyleClass().add("bmi-floating-subtext");
        bmiGradeLabel.getStyleClass().add("bmi-floating-grade-label");

        HBox headerRow = new HBox(8, bmiStatusLabel, statusTag);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(headerRow, bmiValueLabel, subTextLabel, bmiGradeLabel);
        return card;
    }

    // ---------------- 中心：两列等宽 380px 表单 ----------------
    private Node buildCenter() {
        errWeight.setStyle("-fx-text-fill:rgb(220,40,40); -fx-font-size:11px;");

        VBox leftCol = new VBox(22,
                labeledField("input.weight", tfWeight, errWeight, true),
                labeledField("input.waist", tfWaist, null, false),
                labeledField("input.hip", tfHip, null, false),
                labeledField("input.bodyfat", tfBodyFat, null, false),
                labeledField("input.neck", tfNeck, null, false));
        leftCol.setPrefWidth(380);
        leftCol.setMinWidth(380);
        leftCol.setMaxWidth(380);

        VBox rightCol = new VBox(22,
                labeledField("input.wrist", tfWrist, null, false),
                labeledField("input.systolic", tfSys, null, false),
                labeledField("input.diastolic", tfDia, null, false),
                labeledField("input.heart", tfHr, null, false),
                labeledField("input.visceral", tfVisc, null, false));
        rightCol.setPrefWidth(380);
        rightCol.setMinWidth(380);
        rightCol.setMaxWidth(380);

        HBox cols = new HBox(40, leftCol, rightCol);
        cols.setAlignment(Pos.CENTER);
        cols.setPadding(new Insets(16, 0, 8, 0));

        VBox dateRow = labeledField("input.time", dpTime, null, false);
        dateRow.setMaxWidth(380);
        dateRow.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(22, cols, new HBox(40, dateRow));
        form.setAlignment(Pos.TOP_CENTER);
        form.setPadding(new Insets(8, 24, 8, 24));

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(8));
        return scroll;
    }

    /** 标准录入行：标签 + 控件 + 错误提示（选填项 err 为 null）。 */
    private VBox labeledField(String key, javafx.scene.Node node, Label err, boolean required) {
        Label label = new Label(I18nUtil.t(key));
        label.setStyle("-fx-font-size:13px; -fx-text-fill:rgb(60,60,60);");
        HBox labelBox = new HBox(6, label);
        if (required) {
            Label star = new Label("*");
            star.setStyle("-fx-text-fill:rgb(220,40,40); -fx-font-weight:bold; -fx-font-size:13px;");
            labelBox.getChildren().add(star);
        } else {
            Label opt = new Label(I18nUtil.t("input.optional"));
            opt.setStyle("-fx-font-size:11px; -fx-text-fill:rgb(150,150,150);");
            labelBox.getChildren().add(opt);
        }
        if (node instanceof TextField) ((TextField) node).setMaxWidth(Double.MAX_VALUE);
        if (node instanceof DatePicker) ((DatePicker) node).setMaxWidth(Double.MAX_VALUE);
        VBox cell = new VBox(3, labelBox, node);
        if (err != null) cell.getChildren().add(err);
        return cell;
    }

    // ---------------- 底部：保存 + AI ----------------
    private Node buildBottom() {
        btnSave.setPrefWidth(160);
        btnSave.setOnAction(e -> doSave());
        btnAi.setPrefWidth(160);
        btnAi.setOnAction(e -> PageNavigator.toAiAnalysis(user));

        HBox bar = new HBox(16, btnSave, btnAi);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(14, 24, 18, 24));
        bar.setStyle("-fx-background-color:-bmi-panel-solid; -fx-border-color:-bmi-border;"
                + "-fx-border-width:1 0 0 0;");
        return bar;
    }

    // ---------------- 实时 BMI / 体脂率 ----------------
    private void installRealtime() {
        Runnable calc = this::recalc;
        tfWeight.textProperty().addListener((o, a, b) -> calc.run());
    }

    private double profileHeight() {
        UserSession s = UserSession.getInstance();
        if (s.getHeight() != null && s.getHeight() > 0) return s.getHeight();
        if (editing != null && editing.getHeight() > 0) return editing.getHeight();
        return 0;
    }

    private void recalc() {
        Double h = profileHeight() > 0 ? profileHeight() : null;
        Double w = num(tfWeight);
        bmiStatusLabel.setText(I18nUtil.t("input.bmiRealtime"));
        statusTag.setText(I18nUtil.t("input.statusActive"));
        subTextLabel.setText(I18nUtil.t("input.calculating"));
        if (h == null || h <= 0 || w == null || w <= 0) {
            bmiValueLabel.setText("");
            bmiGradeLabel.setText("");
            return;
        }
        double bmi = Math.round((w / ((h / 100) * (h / 100))) * 10.0) / 10.0;
        bmiValueLabel.setText("BMI " + bmi);
        String gradeText = gradeName(bmi);
        String gradeColor = ThemeConstant.bmiGradeColor(bmi);
        bmiGradeLabel.setText(gradeText);
        bmiGradeLabel.setStyle("-fx-font-size:14px; -fx-font-weight:normal; -fx-text-fill:"
                + gradeColor + "; -fx-padding:4 0 0 0;");
        bmiValueLabel.setStyle("-fx-font-size:32px; -fx-font-weight:bold; -fx-text-fill:" + gradeColor + ";");
    }

    // ---------------- 编辑回填 ----------------
    public void loadRecord(BodyRecord r) {
        if (r.getHeight() > 0) tfWeight.requestFocus();
        tfWeight.setText(r.getWeight() > 0 ? String.valueOf(r.getWeight()) : "");
        tfWaist.setText(fmt(r.getWaistCircum()));
        tfHip.setText(fmt(r.getHipCircum()));
        tfBodyFat.setText(fmt(r.getBodyFat()));
        tfNeck.setText(fmt(r.getNeckCircum()));
        tfWrist.setText(fmt(r.getWristCircum()));
        tfSys.setText(fmt(r.getSystolicBp()));
        tfDia.setText(fmt(r.getDiastolicBp()));
        tfHr.setText(fmt(r.getHeartRate()));
        tfVisc.setText(fmt(r.getVisceralFat()));
        if (r.getMeasureTime() != null) {
            dpTime.setValue(r.getMeasureTime().toLocalDate());
        }
        recalc();
    }

    // ---------------- 保存 ----------------
    private void doSave() {
        Double w = num(tfWeight);
        Double h = profileHeight() > 0 ? profileHeight() : null;
        if (h == null || h <= 0) {
            toast.error(I18nUtil.t("input.savedEmpty"));
            return;
        }
        if (w == null || w <= 0) {
            StyleFactory.markError(tfWeight, errWeight, "validate.required");
            toast.error(I18nUtil.t("validate.required"));
            return;
        }
        StyleFactory.clearError(tfWeight, errWeight);

        int age = UserSession.getInstance().getAge() != null ? UserSession.getInstance().getAge() : 0;
        int gender = "M".equals(UserSession.getInstance().getGender()) ? 1 : 0;

        BodyRecord r;
        if (editing == null) {
            r = recordController.createRecord(userId, h, w, age, gender, measureTimeStr());
            if (r == null) {
                toast.error(I18nUtil.t("input.savedEmpty"));
                return;
            }
            applyExtensions(r);
            recordController.updateRecord(r);
        } else {
            r = new BodyRecord();
            r.setId(editing.getId());
            r.setUserId(userId);
            r.setAge(age);
            r.setGender(gender);
            r.setMeasureTime(editing.getMeasureTime());
            r.setHeight(h);
            r.setWeight(w);
            applyExtensions(r);
            recordController.updateRecord(r);
        }
        toast.success(I18nUtil.t("input.savedOk", 1));
        PageNavigator.toHistory(user);
    }

    private void applyExtensions(BodyRecord r) {
        r.setWaistCircum(num(tfWaist));
        r.setHipCircum(num(tfHip));
        r.setBodyFat(num(tfBodyFat));
        r.setNeckCircum(num(tfNeck));
        r.setWristCircum(num(tfWrist));
        r.setSystolicBp(intNum(tfSys));
        r.setDiastolicBp(intNum(tfDia));
        r.setHeartRate(intNum(tfHr));
        r.setVisceralFat(intNum(tfVisc));
    }

    // ---------------- 辅助 ----------------
    private Double num(TextField tf) {
        String s = tf.getText().trim();
        if (s.isEmpty()) return null;
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

    private String measureTimeStr() {
        LocalDate d = dpTime.getValue();
        if (d == null) {
            return FMT.format(LocalDateTime.now());
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
        if (bmi < 18.5) return I18nUtil.t("grade.thin");
        if (bmi < 24) return I18nUtil.t("grade.normal");
        if (bmi < 28) return I18nUtil.t("grade.overweight");
        return I18nUtil.t("grade.obese");
    }

    // ---------------- 语言刷新 ----------------
    private void refreshTexts() {
        btnPhoto.setText(I18nUtil.t("input.photo"));
        btnReport.setText(I18nUtil.t("input.report"));
        btnSettings.setText(I18nUtil.t("input.settings"));
        btnSave.setText(I18nUtil.t("input.save"));
        btnAi.setText(I18nUtil.t("input.aiAnalysis"));
        recalc();
    }

    @Override
    public void onLangChange() {
        refreshTexts();
    }
}
