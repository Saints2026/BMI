package com.bmi.view;

import com.bmi.controller.UserController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.I18n;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 登录 & 注册页面（对齐 ui_design.md 第二章「LoginView」）。
 * 左：登录区（用户名/密码/本地4位验证码/语言切换）；右：注册折叠面板（账号/基础体质/围度/体征/疾病）。
 * 注册自动计算初始 BMI；重复账号拦截；注册成功提示并返回登录区。
 */
public class LoginView extends BorderPane implements LangChangeListener {

    private final UserController userController;
    private final Consumer<User> onLoginSuccess;

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField captchaField = new TextField();
    private final Label captchaLabel = new Label();
    private final Label loginMsg = new Label();
    private final Label loginTitle = new Label();
    private final Button loginBtn = new Button();
    private final Button refreshCodeBtn = new Button();
    private final ComboBox<Lang> langCombo = ViewUtil.langCombo();

    // 登录区标签（需随语言切换刷新）
    private final Label lblUsername = new Label();
    private final Label lblPassword = new Label();
    private final Label lblVerifycode = new Label();
    private final Label lblLang = new Label();

    // 注册区
    private final TextField regUserField = new TextField();
    private final PasswordField regPwdField = new PasswordField();
    private final PasswordField regPwd2Field = new PasswordField();
    private final TextField regHeight = ViewUtil.numberField("register.basicHeight");
    private final TextField regWeight = ViewUtil.numberField("register.basicWeight");
    private final TextField regAge = ViewUtil.numberField("register.basicAge");
    private final ComboBox<String> regGender = new ComboBox<>();
    private final DatePicker regTime = new DatePicker();
    private final Label regBmiLabel = new Label();
    private final TextField regWaist = ViewUtil.numberField("input.waist");
    private final TextField regHip = ViewUtil.numberField("input.hip");
    private final TextField regWrist = ViewUtil.numberField("input.wrist");
    private final TextField regNeck = ViewUtil.numberField("input.neck");
    private final TextField regSys = ViewUtil.numberField("input.systolic");
    private final TextField regDia = ViewUtil.numberField("input.diastolic");
    private final TextField regHr = ViewUtil.numberField("input.heart");
    private final TextField regVisc = ViewUtil.numberField("input.visceral");
    private final CheckBox cbNone = new CheckBox();
    private final List<CheckBox> diseaseBoxes = new ArrayList<>();
    private final Button regBtn = new Button();
    private final Label regMsg = new Label();

    private TitledPane tpAccount, tpBasic, tpCircum, tpVital, tpDisease;

    private String captcha = "";

    public LoginView(UserController userController, Consumer<User> onLoginSuccess) {
        this.userController = userController;
        this.onLoginSuccess = onLoginSuccess;
        buildLeft();
        buildRight();
        refreshCaptcha();
        refreshTexts();
        AppConfig.getInstance().addListener(this);
    }

    private void buildLeft() {
        VBox left = new VBox(10);
        left.setPadding(new Insets(24));
        left.setPrefWidth(320);
        left.getStyleClass().add("bmi-login-left");

        HBox captchaRow = new HBox(8);
        captchaRow.setAlignment(Pos.CENTER_LEFT);
        captchaRow.getChildren().addAll(captchaField, captchaLabel, refreshCodeBtn);

        loginBtn.setOnAction(e -> doLogin());
        refreshCodeBtn.setOnAction(e -> refreshCaptcha());

        left.getChildren().addAll(
                loginTitle,
                lblUsername, usernameField,
                lblPassword, passwordField,
                lblVerifycode, captchaRow,
                loginBtn,
                new HBox(8, lblLang, langCombo),
                loginMsg);
        setLeft(left);
    }

    private void buildRight() {
        VBox right = new VBox(10);
        right.setPadding(new Insets(24));
        right.setPrefWidth(360);

        // 账号
        tpAccount = pane("register.account",
                new VBox(6, regUserField, regPwdField, regPwd2Field));
        // 基础体质
        regGender.getItems().addAll(I18n.t("input.male"), I18n.t("input.female"));
        regGender.setValue(I18n.t("input.male"));
        regTime.setValue(LocalDate.now());
        regHeight.textProperty().addListener((o, a, b) -> updateRegBmi());
        regWeight.textProperty().addListener((o, a, b) -> updateRegBmi());
        VBox basic = new VBox(6, regHeight, regWeight, regAge, regGender, regTime, regBmiLabel);
        tpBasic = pane("register.basic", basic);
        // 身体围度
        tpCircum = pane("register.circum",
                new VBox(6, regWaist, regHip, regWrist, regNeck));
        // 健康指标
        tpVital = pane("register.vital",
                new VBox(6, regSys, regDia, regHr, regVisc));
        // 既往疾病
        for (String key : new String[]{"input.disease.hypertension", "input.disease.diabetes",
                "input.disease.heart", "input.disease.hyperlipid", "input.disease.fattyliver"}) {
            CheckBox cb = new CheckBox(I18n.t(key));
            cb.setOnAction(e -> onDiseaseToggle(cb, true));
            diseaseBoxes.add(cb);
        }
        cbNone.setText(I18n.t("input.disease.none"));
        cbNone.setOnAction(e -> onDiseaseToggle(cbNone, false));
        VBox diseaseBox = new VBox(6, diseaseBoxes.toArray(new CheckBox[0]));
        diseaseBox.getChildren().add(cbNone);
        tpDisease = pane("register.disease", diseaseBox);

        Accordion accordion = new Accordion(tpAccount, tpBasic, tpCircum, tpVital, tpDisease);
        accordion.setExpandedPane(tpAccount);

        regBtn.setOnAction(e -> doRegister());
        right.getChildren().addAll(accordion, regBtn, regMsg);
        setRight(right);
    }

    private TitledPane pane(String key, javafx.scene.Node content) {
        TitledPane tp = new TitledPane();
        tp.setContent(content);
        // 标题在 refreshTexts 中设置
        tp.setUserData(key);
        return tp;
    }

    private void onDiseaseToggle(CheckBox self, boolean isDisease) {
        if (self.isSelected() && isDisease) {
            cbNone.setSelected(false); // 选了具体疾病则取消「无」
        } else if (self.isSelected() && !isDisease) {
            diseaseBoxes.forEach(cb -> cb.setSelected(false)); // 选了「无」则清空具体疾病
        }
    }

    private void updateRegBmi() {
        Double h = ViewUtil.parseDouble(regHeight);
        Double w = ViewUtil.parseDouble(regWeight);
        if (h != null && h > 0 && w != null && w > 0) {
            double bmi = Math.round((w / ((h / 100) * (h / 100))) * 10.0) / 10.0;
            regBmiLabel.setText(I18n.t("register.initialBmi", bmi, ViewUtil.bmiGradeName(bmi)));
        } else {
            regBmiLabel.setText("");
        }
    }

    private void refreshCaptcha() {
        captcha = String.format("%04d", new Random().nextInt(10000));
        captchaLabel.setText(captcha);
        captchaLabel.setStyle("-fx-font-size:18px; -fx-font-weight:bold; -fx-letter-spacing:4px;");
    }

    private void doLogin() {
        if (!captchaField.getText().trim().equals(captcha)) {
            loginMsg.setText(I18n.t("login.errorCode"));
            loginMsg.setStyle("-fx-text-fill:#f44336;");
            return;
        }
        String u = usernameField.getText().trim();
        String p = passwordField.getText();
        if (u.isEmpty() || p.isEmpty()) {
            loginMsg.setText(I18n.t("login.errorEmpty"));
            loginMsg.setStyle("-fx-text-fill:#f44336;");
            return;
        }
        User user = userController.login(u, p);
        if (user != null) {
            loginMsg.setText(I18n.t("login.success"));
            loginMsg.setStyle("-fx-text-fill:#4caf50;");
            onLoginSuccess.accept(user);
        } else {
            loginMsg.setText(I18n.t("login.errorEmpty"));
            loginMsg.setStyle("-fx-text-fill:#f44336;");
        }
    }

    private void doRegister() {
        String u = regUserField.getText().trim();
        String p1 = regPwdField.getText();
        String p2 = regPwd2Field.getText();
        if (u.isEmpty() || p1.isEmpty() || !p1.equals(p2) || p1.length() < 8 || p1.length() > 20) {
            regMsg.setText(I18n.t("register.pwdRule"));
            regMsg.setStyle("-fx-text-fill:#f44336;");
            return;
        }
        if (userController.register(u, p1)) {
            regMsg.setText(I18n.t("register.success"));
            regMsg.setStyle("-fx-text-fill:#4caf50;");
            // 清空并切回登录区（左侧始终可见，仅提示）
            regUserField.clear();
            regPwdField.clear();
            regPwd2Field.clear();
        } else {
            // 视图校验已通过，失败多为用户名重复（FR-01 重复账号拦截）
            regMsg.setText(I18n.t("register.dupUser"));
            regMsg.setStyle("-fx-text-fill:#f44336;");
        }
    }

    private void refreshTexts() {
        loginTitle.setText(I18n.t("login.title"));
        loginBtn.setText(I18n.t("login.submit"));
        refreshCodeBtn.setText(I18n.t("login.refreshCode"));
        usernameField.setPromptText(I18n.t("login.username"));
        passwordField.setPromptText(I18n.t("login.password"));
        captchaField.setPromptText(I18n.t("login.verifycode"));
        lblUsername.setText(I18n.t("login.username"));
        lblPassword.setText(I18n.t("login.password"));
        lblVerifycode.setText(I18n.t("login.verifycode"));
        lblLang.setText(I18n.t("login.lang"));

        regUserField.setPromptText(I18n.t("login.username"));
        regPwdField.setPromptText(I18n.t("login.password"));
        regPwd2Field.setPromptText(I18n.t("register.confirmPwd"));
        regBtn.setText(I18n.t("register.submit"));
        regGender.getItems().setAll(I18n.t("input.male"), I18n.t("input.female"));

        // 折叠面板标题（通过字段引用直接设置，避免依赖场景图）
        if (tpAccount != null) {
            tpAccount.setText(I18n.t("register.account"));
            tpBasic.setText(I18n.t("register.basic"));
            tpCircum.setText(I18n.t("register.circum"));
            tpVital.setText(I18n.t("register.vital"));
            tpDisease.setText(I18n.t("register.disease"));
        }
        // 疾病复选框文案
        for (int i = 0; i < diseaseBoxes.size(); i++) {
            String[] keys = {"input.disease.hypertension", "input.disease.diabetes",
                    "input.disease.heart", "input.disease.hyperlipid", "input.disease.fattyliver"};
            diseaseBoxes.get(i).setText(I18n.t(keys[i]));
        }
        cbNone.setText(I18n.t("input.disease.none"));
    }

    @Override
    public void onLangChange() {
        refreshTexts();
        // 语言下拉自身随 AppConfig 刷新
        langCombo.setValue(AppConfig.getInstance().getLang());
    }
}
