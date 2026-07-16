package com.bmi.view;

import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.User;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import com.bmi.view.util.UserSession;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Health Info Input Page — 图2 完善个人健康信息（像素级复刻效果图）
 *
 * <p>Layout:
 * <ul>
 *   <li>Top bar: left title "完善个人健康信息" + checkmark icon,
 *       right floating BMI card (real-time)</li>
 *   <li>Center-left: white form card with FLAT grid layout
 *       (gender, age, height, weight, waist, hip, bodyfat,
 *       systolic*, diastolic*, smoking, drinking, exercise)</li>
 *   <li>Bottom: blue "保存健康信息" button</li>
 *   <li>Background: static illustration area</li>
 * </ul>
 *
 * <p>Preserves all business logic: BP range validation,
 * real-time BMI calculation with grade colors, UserSession storage.
 */
public class UserInfoInputView extends BorderPane implements LangChangeListener {

    private final User user;

    // Form fields (flat layout matching mockup #2)
    private final ComboBox<String> cbGender = new ComboBox<>();
    private final TextField tfAge = StyleFactory.numberField("input.age");
    private final TextField tfHeight = StyleFactory.numberField("input.height");
    private final TextField tfWeight = StyleFactory.numberField("input.weight");
    private final TextField tfWaist = StyleFactory.numberField("input.waist");
    private final TextField tfHip = StyleFactory.numberField("input.hip");
    private final TextField tfBodyFat = StyleFactory.numberField("input.bodyfat");
    private final TextField tfSys = StyleFactory.numberField("input.systolic");   // required *
    private final TextField tfDia = StyleFactory.numberField("input.diastolic");   // required *
    private final ComboBox<String> cbSmoking = new ComboBox<>();                   // 吸烟习惯
    private final ComboBox<String> cbDrinking = new ComboBox<>();                  // 饮酒习惯
    private final ComboBox<String> cbExercise = new ComboBox<>();                  // 运动频率

    // Error labels
    private final Label errAge = new Label(), errHeight = new Label(), errWeight = new Label();
    private final Label errSys = new Label(), errDia = new Label();

    // Floating BMI card components (top-right)
    private final Label bmiValueLabel = new Label();     // large "BMI 22.8"
    private final Label bmiGradeLabel = new Label();     // color-coded grade text
    private final Label bmiStatusLabel = new Label();    // "实时计算" header
    private final Label statusTag = new Label();         // green status badge
    private final Label subTextLabel = new Label();      // "实时计算中"

    // Buttons
    private final Button submitBtn = new Button();        // "保存健康信息"
    private final Label titleLabel = new Label();         // page title

    public UserInfoInputView(User user) {
        this.user = user;

        titleLabel.getStyleClass().add("bmi-page-title");

        setTop(buildTop());
        setCenter(buildCenter());
        setBottom(buildBottom());

        bindRealtime();
        AppConfig.getInstance().addListener(this);
    }

    /* ==================== Top Bar: title left + floating BMI card right ==================== */
    private Node buildTop() {
        // Left: title + checkmark icon
        Label checkIcon = new Label("\u2705"); // checkmark
        checkIcon.setStyle("-fx-font-size:18px;");
        HBox titleArea = new HBox(8, checkIcon, titleLabel);
        titleArea.setAlignment(Pos.CENTER_LEFT);

        // Right: floating BMI card
        VBox bmiCard = buildFloatingBmiCard();

        HBox topBar = new HBox(20, titleArea, bmiCard);
        topBar.setPadding(new Insets(16, 24, 12, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(bmiCard, Priority.ALWAYS);
        bmiCard.setAlignment(Pos.CENTER_RIGHT);
        return topBar;
    }

    /** Build floating BMI card (white card with shadow, top-right position). */
    private VBox buildFloatingBmiCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("bmi-floating-card");
        card.setAlignment(Pos.CENTER_LEFT);

        // Header row: "实时计算" + status tag
        bmiStatusLabel.setText(I18nUtil.t("input.bmiRealtime"));
        bmiStatusLabel.getStyleClass().add("bmi-floating-header");

        statusTag.setText(I18nUtil.t("input.statusActive"));
        statusTag.getStyleClass().add("bmi-floating-status-tag");

        HBox headerRow = new HBox(8, bmiStatusLabel, statusTag);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Large BMI value
        bmiValueLabel.setStyle("-fx-font-size:32px; -fx-font-weight:bold; -fx-text-fill:#4096ff;");
        bmiValueLabel.getStyleClass().add("bmi-floating-bmi-value");

        // Subtext "实时计算中"
        subTextLabel.setText(I18nUtil.t("input.calculating"));
        subTextLabel.getStyleClass().add("bmi-floating-subtext");

        // Grade label (color-coded)
        bmiGradeLabel.getStyleClass().add("bmi-floating-grade-label");

        card.getChildren().addAll(headerRow, bmiValueLabel, subTextLabel, bmiGradeLabel);
        return card;
    }

    /* ==================== Center: flat form grid (white card) ==================== */
    private Node buildCenter() {
        // Error label styling
        for (Label l : new Label[]{errHeight, errWeight, errAge, errSys, errDia}) {
            l.setStyle("-fx-text-fill:" + ThemeConstant.STATUS_DANGER + "; -fx-font-size:11px; -fx-min-height:14;");
        }

        // Setup combo boxes
        cbGender.getItems().addAll(I18nUtil.t("input.male"), I18nUtil.t("input.female"));
        cbGender.setValue(cbGender.getItems().get(0));
        cbGender.getStyleClass().add("bmi-combo");

        String[] habitOptions = {I18nUtil.t("input.habit.never"), I18nUtil.t("input.habit.sometimes"),
                I18nUtil.t("input.habit.often"), I18nUtil.t("input.habit.daily")};
        for (ComboBox<String> cb : new ComboBox[]{cbSmoking, cbDrinking, cbExercise}) {
            cb.getItems().addAll(habitOptions);
            cb.setValue(cb.getItems().get(0));
            cb.getStyleClass().add("bmi-combo");
        }

        // Build flat form grid (2 columns, matches mockup #2 exactly)
        GridPane formGrid = new GridPane();
        formGrid.setPadding(new Insets(4));
        formGrid.setVgap(14);
        formGrid.setHgap(18);

        // Column constraints (equal width)
        ColumnConstraints cc1 = new ColumnConstraints();
        cc1.setPercentWidth(50);
        cc1.setFillWidth(true);
        ColumnConstraints cc2 = new ColumnConstraints();
        cc2.setPercentWidth(50);
        cc2.setFillWidth(true);
        formGrid.getColumnConstraints().addAll(cc1, cc2);

        int row = 0;

        // Row 0: 性别* | 年龄*
        addFormCell(formGrid, row++, 0, "input.gender", cbGender, null, true);
        addFormCell(formGrid, row, 1, "input.age", tfAge, errAge, true);

        // Row 1: 身高cm* | 体重kg*
        addFormCell(formGrid, row++, 0, "input.height", tfHeight, errHeight, true);
        addFormCell(formGrid, row, 1, "input.weight", tfWeight, errWeight, true);

        // Row 2: 腰围cm | 臀围cm
        addFormCell(formGrid, row++, 0, "input.waist", tfWaist, null);
        addFormCell(formGrid, row, 1, "input.hip", tfHip, null);

        // Row 3: 体脂率% | (spacer)
        addFormCell(formGrid, row++, 0, "input.bodyfat", tfBodyFat, null);

        // Row 4: 收缩压mmHg | 舒张压mmHg — optional fields (no red asterisk, empty allowed)
        addFormCell(formGrid, row++, 0, "input.systolic", tfSys, errSys);
        addFormCell(formGrid, row, 1, "input.diastolic", tfDia, errDia);

        // Row 5: 吸烟习惯 | 饮酒习惯
        addComboCell(formGrid, row++, 0, "input.smoking", cbSmoking);
        addComboCell(formGrid, row, 1, "input.drinking", cbDrinking);

        // Row 6: 运动频率 | (spacer)
        addComboCell(formGrid, row++, 0, "input.exercise", cbExercise);

        // Wrap in white form card
        VBox formCard = new VBox(4, formGrid);
        formCard.getStyleClass().add("bmi-form-card");
        formCard.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(formCard);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(8));
        scroll.getStyleClass().add("bmi-scroll-content");
        scroll.setStyle("-fx-background-color:transparent; -fx-border-color:transparent;");

        return scroll;
    }

    /** Add a standard (optional) form cell: label + grey "选填" small text. */
    private void addFormCell(GridPane grid, int row, int col, String key, Node control, Label err) {
        addFormCell(grid, row, col, key, control, err, false);
    }

    /** Add a form cell. required -> red "*"; optional -> grey "选填" small label. */
    private void addFormCell(GridPane grid, int row, int col, String key, Node control, Label err, boolean required) {
        Label label = new Label(I18nUtil.t(key));
        label.setStyle("-fx-font-size:13px; -fx-text-fill:-bmi-fg;");
        HBox labelBox = new HBox(6, label);
        if (required) {
            Label star = new Label("*");
            star.setStyle("-fx-text-fill:#f76b6c; -fx-font-weight:bold; -fx-font-size:13px;");
            labelBox.getChildren().add(star);
        } else {
            Label opt = new Label(I18nUtil.t("input.optional"));
            opt.setStyle("-fx-font-size:10px; -fx-text-fill:#999999;");
            labelBox.getChildren().add(opt);
        }
        VBox cell = new VBox(3, labelBox, control);
        if (err != null) cell.getChildren().add(err);
        grid.add(cell, col, row);
    }

    /** Add a combo box cell (optional -> grey "选填"). */
    private void addComboCell(GridPane grid, int row, int col, String key, ComboBox<String> cb) {
        Label label = new Label(I18nUtil.t(key));
        label.setStyle("-fx-font-size:13px; -fx-text-fill:-bmi-fg;");
        Label opt = new Label(I18nUtil.t("input.optional"));
        opt.setStyle("-fx-font-size:10px; -fx-text-fill:#999999;");
        HBox labelBox = new HBox(6, label, opt);
        VBox cell = new VBox(3, labelBox, cb);
        grid.add(cell, col, row);
    }

    /* ==================== Bottom: save button ==================== */
    private Node buildBottom() {
        submitBtn.getStyleClass().add("bmi-btn-save-blue");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        submitBtn.setOnAction(e -> doSubmit());

        HBox bottomBar = new HBox(submitBtn);
        bottomBar.setPadding(new Insets(16, 32, 20, 32));
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setStyle("-fx-background-color:transparent;");
        return bottomBar;
    }

    /* ==================== Business Logic ==================== */

    private void bindRealtime() {
        Runnable recalc = () -> { recalcBmi(); refreshBasicErrors(); };
        tfHeight.textProperty().addListener((o,a,b) -> recalc.run());
        tfWeight.textProperty().addListener((o,a,b) -> recalc.run());
        tfAge.textProperty().addListener((o,a,b) -> recalc.run());

        tfSys.textProperty().addListener((o,a,b) -> { validateBpRange(tfSys, errSys); recalcBmi(); });
        tfDia.textProperty().addListener((o,a,b) -> { validateBpRange(tfDia, errDia); recalcBmi(); });

        submitBtn.disableProperty().bind(
                Bindings.createBooleanBinding(this::basicValid,
                        tfHeight.textProperty(), tfWeight.textProperty(), tfAge.textProperty()).not());
    }

    private void recalcBmi() {
        Double h = num(tfHeight), w = num(tfWeight);
        if (h == null || h <= 0 || w == null || w <= 0) {
            bmiValueLabel.setText("");
            bmiGradeLabel.setText("");
            return;
        }
        double bmi = Math.round((w / ((h / 100) * (h / 100))) * 10.0) / 10.0;
        bmiValueLabel.setText("BMI " + bmi);

        String gradeText = I18nUtil.t(gradeKey(bmi));
        String gradeColor = gradeColor(bmi);
        bmiGradeLabel.setText(gradeText);
        bmiGradeLabel.setStyle("-fx-font-size:14px; -fx-font-weight:normal; -fx-text-fill:"
                + gradeColor + "; -fx-padding:4 0 0 0;");
        bmiValueLabel.setStyle("-fx-font-size:32px; -fx-font-weight:bold; -fx-text-fill:" + gradeColor + ";");
    }

    private void refreshBasicErrors() {
        checkRange(tfHeight, errHeight, 100, 250, "validate.height");
        checkRange(tfAge, errAge, 1, 120, "validate.age");
        Double w = num(tfWeight);
        if (w == null) StyleFactory.markError(tfWeight, errWeight, "validate.required");
        else if (w <= 0) StyleFactory.markError(tfWeight, errWeight, "validate.negative");
        else StyleFactory.clearError(tfWeight, errWeight);

        validateBpRange(tfSys, errSys);
        validateBpRange(tfDia, errDia);
    }

    private void validateBpRange(TextField tf, Label err) {
        Double v = num(tf);
        if (v == null) { StyleFactory.clearError(tf, err); return; } // optional: empty is OK
        if (tf == tfSys && (v < 60 || v > 220)) {
            StyleFactory.markError(tf, err, "validate.sysRange"); return;
        }
        if (tf == tfDia && (v < 40 || v > 140)) {
            StyleFactory.markError(tf, err, "validate.diaRange"); return;
        }
        StyleFactory.clearError(tf, err);
    }

    private void checkRange(TextField tf, Label err, double min, double max, String key) {
        Double v = num(tf);
        if (v == null) StyleFactory.markError(tf, err, "validate.required");
        else if (v < min || v > max) StyleFactory.markError(tf, err, key);
        else StyleFactory.clearError(tf, err);
    }

    private void doSubmit() {
        refreshBasicErrors();
        String ek = basicErrorKey();
        if (ek != null) { ToastBar.showError(I18nUtil.t(ek)); return; }
        storeToSession();
        AppConfig.getInstance().removeListener(this);
        PageNavigator.toMain(user);
    }

    private boolean basicValid() { return basicErrorKey() == null; }

    private String basicErrorKey() {
        Double h = num(tfHeight), w = num(tfWeight), a = num(tfAge);
        Double s = num(tfSys), d = num(tfDia);
        if (h == null) return "validate.required";
        if (h < 100 || h > 250) return "validate.height";
        if (a == null) return "validate.required";
        if (a < 1 || a > 120) return "validate.age";
        if (w == null) return "validate.required";
        if (w <= 0) return "validate.negative";
        // BP is optional: only validate range if value is entered
        if (s != null && (s < 60 || s > 220)) return "validate.sysRange";
        if (d != null && (d < 40 || d > 140)) return "validate.diaRange";
        return null;
    }

    private void storeToSession() {
        UserSession s = UserSession.getInstance();
        s.setUser(user);
        s.setBasicProfile(num(tfHeight), num(tfWeight), num(tfAge).intValue(),
                I18nUtil.t("input.male").equals(cbGender.getValue()) ? "M" : "F");
        s.setCircumferences(num(tfWaist), num(tfHip), null, null);
        s.setVitals(intNum(tfSys), intNum(tfDia), null, null);
        s.syncToDatabase();
    }

    private Double num(TextField tf) {
        String s = tf.getText().trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private Integer intNum(TextField tf) { Double d = num(tf); return d == null ? null : d.intValue(); }

    private String gradeKey(double bmi) {
        if (bmi < 18.5) return "grade.thin";
        if (bmi < 24)   return "grade.normal";
        if (bmi < 28)   return "grade.overweight";
        return "grade.obese";
    }

    private String gradeColor(double bmi) {
        if (bmi < 18.5) return "#4096ff";      // thin — blue
        if (bmi < 24)   return "#52c41a";      // normal — green
        if (bmi < 28)   return "#faad14";      // overweight — orange
        return "#f76b6c";                       // obese — red
    }

    private void refreshTexts() {
        titleLabel.setText(I18nUtil.t("input.profileTitleFull"));
        submitBtn.setText(I18nUtil.t("input.saveHealthInfo"));
        bmiStatusLabel.setText(I18nUtil.t("input.bmiRealtime"));
        subTextLabel.setText(I18nUtil.t("input.calculating"));

        // Refresh combo prompts (rebuild items in current language)
        String savedGender = cbGender.getValue();
        cbGender.getItems().clear();
        cbGender.getItems().addAll(I18nUtil.t("input.male"), I18nUtil.t("input.female"));
        cbGender.setValue(savedGender != null && savedGender.contains(I18nUtil.t("input.male").charAt(0)+"")
                ? cbGender.getItems().get(0) : (cbGender.getItems().size() > 1 ? cbGender.getItems().get(1) : cbGender.getItems().get(0)));

        String[] habitOptions = {I18nUtil.t("input.habit.never"), I18nUtil.t("input.habit.sometimes"),
                I18nUtil.t("input.habit.often"), I18nUtil.t("input.habit.daily")};
        for (ComboBox<String> cb : new ComboBox[]{cbSmoking, cbDrinking, cbExercise}) {
            String savedVal = cb.getValue();
            cb.getItems().clear();
            cb.getItems().addAll(habitOptions);
            if (savedVal != null) cb.setValue(savedVal);
            else cb.setValue(cb.getItems().get(0));
        }

        recalcBmi(); refreshBasicErrors();
    }

    @Override public void onLangChange() { refreshTexts(); }
}
