package com.bmi.view;

import com.bmi.controller.UserController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.I18n;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

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

    private final Label lblUsername = new Label();
    private final Label lblPassword = new Label();
    private final Label lblVerifycode = new Label();
    private final Label lblLang = new Label();

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

        tpAccount = pane("register.account",
                new VBox(6, regUserField, regPwdField, regPwd2Field));
        regGender.getItems().addAll(I18n.t("input.male"), I18n.t("input.female"));
        regGender.setValue(I18n.t("input.male"));
        regTime.setValue(LocalDate.now());
        regHeight.textProperty().addListener((o, a, b) -> updateRegBmi());
        regWeight.textProperty().addListener((o, a, b) -> updateRegBmi());
        VBox basic = new VBox(6, regHeight, regWeight, regAge, regGender, regTime, regBmiLabel);
        tpBasic = pane("register.basic", basic);
        tpCircum = pane("register.circum",
                new VBox(6, regWaist, regHip, regWrist, regNeck));
        tpVital = pane("register.vital",
                new VBox(6, regSys, regDia, regHr, regVisc));
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
        tp.setUserData(key);
        return tp;
    }

    private void onDiseaseToggle(CheckBox self, boolean isDisease) {
        if (self.isSelected() && isDisease) {
            cbNone.setSelected(false);
        } else if (self.isSelected() && !isDisease) {
            diseaseBoxes.forEach(cb -> cb.setSelected(false));
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
        // 测试跳过登录
        System.out.println("登录跳过");
        User fake = new User();
        fake.setId(1L);
        fake.setUsername("test");
        onLoginSuccess.accept(fake);
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
            regUserField.clear();
            regPwdField.clear();
            regPwd2Field.clear();
        } else {
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

        if (tpAccount != null) {
            tpAccount.setText(I18n.t("register.account"));
            tpBasic.setText(I18n.t("register.basic"));
            tpCircum.setText(I18n.t("register.circum"));
            tpVital.setText(I18n.t("register.vital"));
            tpDisease.setText(I18n.t("register.disease"));
        }
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
        langCombo.setValue(AppConfig.getInstance().getLang());
    }
}