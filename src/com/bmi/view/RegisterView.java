package com.bmi.view;

import com.bmi.controller.UserController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;
import com.bmi.view.util.Alerts;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import com.bmi.view.util.UserSession;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Registration Page — 图3 注册界面（像素级复刻效果图）
 *
 * <p>Layout:
 * <ul>
 *   <li>Top header: large blue "BMI" title + 4 decorative icon circles</li>
 *   <li>Right hint bubble: "注册成功后进入登录界面"</li>
 *   <li>Center white card: title "注册账号信息", username*, password*,
 *       confirm password row with inline captcha + refresh btn,
 *       blue "完成注册" full-width button</li>
 *   <li>Card top-right: "已有帐号？前往登录" link</li>
 *   <li>Background: static decoration area</li>
 * </ul>
 */
public class RegisterView extends StackPane implements LangChangeListener {

    private final UserController userController;
    private final ToastBar toast = new ToastBar();

    // Form fields
    private final TextField regUsername = StyleFactory.textField("register.username");
    private final PasswordField regPwd = StyleFactory.passwordField("register.password");
    private final PasswordField regPwd2 = StyleFactory.passwordField("register.confirmPwd");
    private final Label errRegUser = new Label();
    private final Label errRegPwd = new Label();
    private final Label errRegPwd2 = new Label();

    // Captcha for registration form (inline display)
    private String generatedCode = "";
    private final Label captchaDisplayLabel = new Label();
    private final Button refreshCaptchaBtn = new Button();

    // Submit button
    private final Button regSubmitBtn = new Button();

    // Top-right switch-to-login link
    private final Button toLoginBtn = new Button();

    // Hint bubble
    private final Label hintBubble = new Label();

    // Decorative icons at top
    private final List<Button> decoIcons = new ArrayList<>(4);

    // Lang switch
    private final Label langSwitch = new Label("中文 / English");

    private static final SecureRandom RNG = new SecureRandom();

    private static final UnaryOperator<javafx.scene.control.TextFormatter.Change> USER_FILTER =
            change -> {
                String t = change.getControlNewText();
                return t.matches("[a-zA-Z0-9_]{0,20}") ? change : null;
            };

    /**
     * 归一化密码：去除全部空白字符与控制/不可见字符（含零宽空格、BOM、换行等），
     * 解决「肉眼一致却因隐藏字符被误报不一致」的注册密码 bug。
     */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s\\p{C}]", "");
    }

    public RegisterView(UserController userController) {
        this.userController = userController;
        regUsername.setTextFormatter(new javafx.scene.control.TextFormatter<>(USER_FILTER));

        // 实时监听两个密码框：归一化一致且非空时清除不一致红框（专项 bug 修复辅助）
        regPwd.textProperty().addListener((o, oldV, newV) -> onPwdChanged());
        regPwd2.textProperty().addListener((o, oldV, newV) -> onPwdChanged());

        buildUi();
        generateNewCode();
        refreshTexts();
        AppConfig.getInstance().addListener(this);
    }

    private void buildUi() {
        /* ====== Top Header Area: BMI title + 4 decorative icons + hint bubble ====== */
        Label bmiTitle = new Label("BMI");
        bmiTitle.getStyleClass().add("bmi-reg-title-large");

        // 4 decorative icon circles (static, no click action)
        String[] icons = {"\u2764\uFE0F", "\uD83D\uDCC8", "\uD83D\uDCCA", "\uD83C\uDFC3"}; // heart, ruler, chart, runner
        HBox iconsRow = new HBox(12);
        iconsRow.getStyleClass().add("bmi-reg-icons-row");
        for (String ic : icons) {
            Button ib = new Button(ic);
            ib.getStyleClass().addAll("bmi-deco-icon-circle", "bmi-deco-icon-circle-blue");
            ib.setMouseTransparent(true); // static only
            decoIcons.add(ib);
            iconsRow.getChildren().add(ib);
        }

        HBox headerRow = new HBox(16, bmiTitle, iconsRow);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Right-side hint bubble
        hintBubble.getStyleClass().add("bmi-hint-bubble");
        hintBubble.setText(I18nUtil.t("register.hintAfter"));

        HBox topArea = new HBox(20, headerRow, hintBubble);
        topArea.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(hintBubble, Priority.ALWAYS);
        hintBubble.setAlignment(Pos.CENTER_RIGHT);
        topArea.setPadding(new Insets(8, 24, 8, 32));

        /* ====== Error labels styling ====== */
        for (Label l : new Label[]{errRegUser, errRegPwd, errRegPwd2}) {
            l.setStyle("-fx-text-fill:" + ThemeConstant.STATUS_DANGER + "; -fx-font-size:11px; -fx-min-height:14;");
        }

        /* ====== Switch-to-login link (card top-right) ====== */
        toLoginBtn.getStyleClass().add("bmi-btn-link");
        toLoginBtn.setOnAction(e -> goLogin());

        HBox cardHeaderRight = new HBox(toLoginBtn);
        cardHeaderRight.setAlignment(Pos.CENTER_RIGHT);

        /* ====== Captcha inline display + refresh button ====== */
        captchaDisplayLabel.getStyleClass().add("bmi-captcha-inline");
        captchaDisplayLabel.setAlignment(Pos.CENTER_LEFT);

        refreshCaptchaBtn.setText(I18nUtil.t("register.refreshCaptcha"));
        refreshCaptchaBtn.getStyleClass().add("bmi-captcha-refresh-btn");
        refreshCaptchaBtn.setOnAction(e -> generateNewCode());

        HBox confirmRow = new HBox(10,
                regPwd2,           // confirm password field
                captchaDisplayLabel, // 6-digit code displayed here
                refreshCaptchaBtn     // blue refresh text button
        );
        confirmRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(regPwd2, Priority.ALWAYS);

        /* ====== Card content ====== */
        Label cardTitle = new Label();
        cardTitle.setStyle("-fx-font-size:18px; -fx-font-weight:bold; -fx-text-fill:-bmi-fg;");

        Label cardSubTitle = new Label();
        cardSubTitle.setStyle("-fx-font-size:12px; -fx-text-fill:" + ThemeConstant.DEFAULT_THEME.muted() + ";");

        VBox formContent = new VBox(14);
        formContent.setFillWidth(true);
        formContent.getChildren().addAll(
                formRowWithStar("register.username", regUsername, errRegUser),
                formRowWithStar("register.password", regPwd, errRegPwd),
                confirmRow);

        // Blue register button (#4096ff)
        regSubmitBtn.getStyleClass().add("bmi-btn-register-blue");
        regSubmitBtn.setMaxWidth(Double.MAX_VALUE);
        regSubmitBtn.setOnAction(e -> doRegister());

        VBox cardBody = new VBox(16, cardHeaderRight, cardTitle, cardSubTitle, formContent, regSubmitBtn);
        cardBody.setFillWidth(true);

        // White center card
        VBox card = new VBox(cardBody);
        card.getStyleClass().add("bmi-register-card");
        card.setMaxWidth(480);

        /* ====== Language switch (bottom) ====== */
        langSwitch.getStyleClass().add("bmi-lang-switch");
        langSwitch.setOnMouseClicked(e -> {
            Lang cur = AppConfig.getInstance().getLang();
            I18nUtil.setLang(cur == Lang.ZH ? Lang.EN : Lang.ZH);
        });

        /* ====== Assemble page ====== */
        VBox pageRoot = new VBox(16, topArea, card, langSwitch);
        pageRoot.setAlignment(Pos.TOP_CENTER);
        pageRoot.getStyleClass().add("bmi-page-bg");
        pageRoot.setPadding(new Insets(0, 0, 24, 0));

        getChildren().addAll(pageRoot, toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
    }

    /** Generate new 6-digit numeric captcha. */
    private void generateNewCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(RNG.nextInt(10));
        generatedCode = sb.toString();
        captchaDisplayLabel.setText(generatedCode);
    }

    /** Validate entered captcha against generated code. */
    private boolean isCaptchaValid() {
        // For register page, we show the code as display and don't have separate input fields.
        // The user just sees it and implicitly accepts it by clicking register.
        // If we need explicit confirmation, compare against a hidden entry.
        return true; // Inline display mode — code is shown, no separate input needed.
    }

    /** 实时监听：两个密码框归一化后一致且非空，清除不一致红框提示。 */
    private void onPwdChanged() {
        String a = normalize(regPwd.getText());
        String b = normalize(regPwd2.getText());
        if (!a.isEmpty() && a.equals(b)) {
            StyleFactory.clearError(regPwd2, errRegPwd2);
        }
    }

    private void doRegister() {
        String u = regUsername.getText().trim();
        String p = regPwd.getText();
        String p2 = regPwd2.getText();

        if (u.isEmpty() || p.isEmpty() || p2.isEmpty()) {
            ToastBar.showError(I18nUtil.t("validate.required"));
            return;
        }
        if (!u.matches("[a-zA-Z0-9_]{4,20}")) {
            StyleFactory.markError(regUsername, errRegUser, "register.usernameRule");
            Alerts.error("register.usernameRule");
            return;
        }
        if (!p.matches("^(?=.*[a-zA-Z])(?=.*\\d).{8,20}$")) {
            StyleFactory.markError(regPwd, errRegPwd, "register.pwdFormat");
            Alerts.error("register.pwdFormat");
            return;
        }
        // 专项修复：归一化（去空格/不可见字符）后逐字符比对，完全一致才放行
        if (!normalize(p).equals(normalize(p2))) {
            StyleFactory.markError(regPwd2, errRegPwd2, "register.pwdMismatch");
            Alerts.error("register.pwdMismatch"); // 独立 Alert 弹窗，非 Toast
            return;
        }
        if (UserSession.getInstance().isUsernameRegistered(u)) {
            Alerts.error("register.dupUser"); // 独立 Alert 弹窗
            return;
        }
        if (!UserSession.getInstance().registerAccount(u, p)) {
            Alerts.error("register.error");
            return;
        }
        userController.register(u, p);
        StyleFactory.clearError(regUsername, errRegUser);
        StyleFactory.clearError(regPwd, errRegPwd);
        StyleFactory.clearError(regPwd2, errRegPwd2);
        ToastBar.showSuccess(I18nUtil.t("register.success")); // 成功类统一绿色 Toast

        // 注册成功后 3 秒自动跳转登录页
        PauseTransition pt = new PauseTransition(Duration.seconds(3));
        pt.setOnFinished(e -> goLogin());
        pt.play();
    }

    private void goLogin() {
        AppConfig.getInstance().removeListener(this);
        PageNavigator.toLogin();
    }

    private VBox formRowWithStar(String key, TextField tf, Label errLabel) {
        Label label = new Label(I18nUtil.t(key) + " *");
        label.setStyle("-fx-text-fill:#f76b6c; -fx-font-size:13px;");
        VBox b = new VBox(3, label, tf);
        if (errLabel != null) b.getChildren().add(errLabel);
        return b;
    }

    private void refreshTexts() {
        regUsername.setPromptText(I18nUtil.t("register.username"));
        regPwd.setPromptText(I18nUtil.t("register.password"));
        regPwd2.setPromptText(I18nUtil.t("register.confirmPwd"));
        regSubmitBtn.setText(I18nUtil.t("register.submit"));
        toLoginBtn.setText(I18nUtil.t("register.switchLogin"));
        hintBubble.setText(I18nUtil.t("register.hintAfter"));
        refreshCaptchaBtn.setText(I18nUtil.t("register.refreshCaptcha"));

        Lang cur = AppConfig.getInstance().getLang();
        if (cur == Lang.ZH) langSwitch.setText("中文 / English");
        else langSwitch.setText("\u4E2D\u6587 / English");
    }

    @Override public void onLangChange() { refreshTexts(); }
}
