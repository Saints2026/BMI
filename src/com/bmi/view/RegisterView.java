package com.bmi.view;

import com.bmi.controller.UserController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.User;
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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.security.SecureRandom;
import java.util.function.UnaryOperator;

/**
 * Registration Page — 图3 注册界面（像素级复刻效果图）
 *
 * <p>Layout:
 * <ul>
 *   <li>Top header: large blue "BMI" title + 4 decorative icon circles</li>
 *   <li>Right hint bubble: "注册成功后进入登录界面"</li>
 *   <li>Center white card: title "注册账号信息", then FOUR equal-width inputs
 *       stacked vertically: 用户名 → 密码 → 确认密码 → 验证码</li>
 *   <li>Captcha row: numeric captcha input (left) + generated 6-digit code (right)
 *       + refresh button — NO virtual keypad on this page</li>
 *   <li>Blue "完成注册" full-width button</li>
 *   <li>Card top-right: "已有帐号？前往登录" link</li>
 *   <li>Background: static decoration area</li>
 * </ul>
 *
 * <p>Captcha: only on this page. Success path shows NO popup — silent 3s jump to login.
 */
public class RegisterView extends StackPane implements LangChangeListener {

    private final UserController userController;
    private final ToastBar toast = new ToastBar();

    // Four equal-width inputs (fixed vertical order)
    private final TextField regUsername = StyleFactory.textField("register.username");
    private final PasswordField regPwd = StyleFactory.passwordField("register.password");
    private final PasswordField regPwd2 = StyleFactory.passwordField("register.confirmPwd");
    private final TextField captchaInput = StyleFactory.textField("register.captcha");
    private final Label errRegUser = new Label();
    private final Label errRegPwd = new Label();
    private final Label errRegPwd2 = new Label();
    private final Label errCaptcha = new Label();

    // Captcha system (only on register page)
    private String generatedCode = "";
    private final Label captchaDisplayLabel = new Label(); // shows the 6-digit code
    private final Button refreshCaptchaBtn = new Button();

    // Submit button
    private final Button regSubmitBtn = new Button();

    // Top-right switch-to-login link
    private final Button toLoginBtn = new Button();

    // Hint bubble
    private final Label hintBubble = new Label();

    // Decorative icons at top
    private final HBox iconsRow = new HBox(12);

    // Lang switch (bilingual toggle label, routed through i18n)
    private final Label langSwitch = new Label(I18nUtil.t("lang.toggle"));

    private static final SecureRandom RNG = new SecureRandom();

    private static final UnaryOperator<javafx.scene.control.TextFormatter.Change> USER_FILTER =
            change -> {
                String t = change.getControlNewText();
                return t.matches("[a-zA-Z0-9_]{0,20}") ? change : null;
            };

    /** Numeric-only filter for captcha (max 6 digits). */
    private static final UnaryOperator<javafx.scene.control.TextFormatter.Change> DIGIT_FILTER =
            change -> {
                String t = change.getControlNewText();
                return t.matches("\\d{0,6}") ? change : null;
            };

    /**
     * Normalize password: strip all whitespace and control/invisible characters
     * (including zero-width spaces, BOM, line breaks), fixing the bug where
     * visually identical passwords were reported as mismatched due to hidden chars.
     */
    private static String normalize(String s) {
        if (s == null) { return ""; }
        return s.replaceAll("[\\s\\p{C}]", "");
    }

    public RegisterView(UserController userController) {
        this.userController = userController;
        regUsername.setTextFormatter(new javafx.scene.control.TextFormatter<>(USER_FILTER));
        captchaInput.setTextFormatter(new javafx.scene.control.TextFormatter<>(DIGIT_FILTER));
        // Equal width: stretch to card content width
        regUsername.setMaxWidth(Double.MAX_VALUE);
        regPwd.setMaxWidth(Double.MAX_VALUE);
        regPwd2.setMaxWidth(Double.MAX_VALUE);
        captchaInput.setMaxWidth(Double.MAX_VALUE);

        // Real-time listen on both password fields: clear mismatch red border when normalized values match
        regPwd.textProperty().addListener((o, oldV, newV) -> onPwdChanged());
        regPwd2.textProperty().addListener((o, oldV, newV) -> onPwdChanged());

        buildUi();
        generateNewCode();
        refreshTexts();
        AppConfig.getInstance().addListener(this);
    }

    /* ==================== UI Build ==================== */

    private void buildUi() {
        /* ====== Top Header Area: BMI title + 4 decorative icons + hint bubble ====== */
        Label bmiTitle = new Label("BMI");
        bmiTitle.getStyleClass().add("bmi-reg-title-large");

        // 4 decorative icon circles (static, no click action)
        String[] icons = {"\u2764\uFE0F", "\uD83D\uDCC8", "\uD83D\uDCCA", "\uD83C\uDFC3"};
        iconsRow.getStyleClass().add("bmi-reg-icons-row");
        for (String ic : icons) {
            Button ib = new Button(ic);
            ib.getStyleClass().addAll("bmi-deco-icon-circle", "bmi-deco-icon-circle-blue");
            ib.setMouseTransparent(true);
            iconsRow.getChildren().add(ib);
        }

        HBox headerRow = new HBox(16, bmiTitle, iconsRow);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Right-side hint bubble
        hintBubble.getStyleClass().add("bmi-hint-bubble");

        HBox topArea = new HBox(20, headerRow, hintBubble);
        topArea.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(hintBubble, Priority.ALWAYS);
        hintBubble.setAlignment(Pos.CENTER_RIGHT);
        topArea.setPadding(new Insets(8, 24, 8, 32));

        /* ====== Error labels styling ====== */
        for (Label l : new Label[]{errRegUser, errRegPwd, errRegPwd2, errCaptcha}) {
            l.setStyle("-fx-text-fill:" + ThemeConstant.STATUS_DANGER + "; -fx-font-size:11px; -fx-min-height:14;");
        }

        /* ====== Switch-to-login link (card top-right) ====== */
        toLoginBtn.getStyleClass().add("bmi-btn-link");
        toLoginBtn.setOnAction(e -> goLogin());

        HBox cardHeaderRight = new HBox(toLoginBtn);
        cardHeaderRight.setAlignment(Pos.CENTER_RIGHT);

        /* ====== Captcha: displayed code (right) + refresh button ====== */
        captchaDisplayLabel.getStyleClass().add("bmi-captcha-inline");
        captchaDisplayLabel.setAlignment(Pos.CENTER_LEFT);

        refreshCaptchaBtn.getStyleClass().add("bmi-captcha-refresh-btn");
        refreshCaptchaBtn.setOnAction(e -> generateNewCode());

        // Captcha row: [input | generated code | refresh button]
        HBox captchaRow = new HBox(10, captchaInput, captchaDisplayLabel, refreshCaptchaBtn);
        captchaRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(captchaInput, Priority.ALWAYS);

        /* ====== Card content ====== */
        Label cardTitle = new Label();
        cardTitle.setStyle("-fx-font-size:18px; -fx-font-weight:bold; -fx-text-fill:-bmi-fg;");

        Label cardSubTitle = new Label();
        cardSubTitle.setStyle("-fx-font-size:12px; -fx-text-fill:" + ThemeConstant.DEFAULT_THEME.muted() + ";");

        // Four equal-width inputs in fixed order: 用户名 → 密码 → 确认密码 → 验证码
        VBox formContent = new VBox(14);
        formContent.setFillWidth(true);
        formContent.getChildren().addAll(
                formRowWithStar("register.username", regUsername, errRegUser),
                formRowWithStar("register.password", regPwd, errRegPwd),
                formRowWithStar("register.confirmPwd", regPwd2, errRegPwd2),
                captchaRowWithLabel(captchaRow, errCaptcha));

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

    /* ==================== Captcha ==================== */

    /** Generate new 6-digit numeric captcha, display it, clear input. */
    private void generateNewCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(RNG.nextInt(10));
        generatedCode = sb.toString();
        captchaDisplayLabel.setText(generatedCode);
        captchaInput.clear();
        captchaInput.getStyleClass().remove("bmi-field-error");
        errCaptcha.setText("");
    }

    /** Validate entered captcha against generated code. */
    private boolean isCaptchaValid() {
        return generatedCode.equals(captchaInput.getText().trim());
    }

    /* ==================== Password real-time listener ==================== */

    /** Real-time listener: when both normalized passwords match and are non-empty, clear mismatch error. */
    private void onPwdChanged() {
        String a = normalize(regPwd.getText());
        String b = normalize(regPwd2.getText());
        if (!a.isEmpty() && a.equals(b)) {
            StyleFactory.clearError(regPwd2, errRegPwd2);
        }
    }

    /* ==================== Registration Logic ==================== */

    private void doRegister() {
        String u = regUsername.getText().trim();
        String p = regPwd.getText();
        String p2 = regPwd2.getText();
        String code = captchaInput.getText().trim();

        // (1) Empty required fields -> red border + 3s warning Toast
        if (u.isEmpty()) {
            StyleFactory.markError(regUsername, errRegUser, "validate.required");
            ToastBar.showError(I18nUtil.t("validate.required"));
            return;
        }
        if (p.isEmpty()) {
            StyleFactory.markError(regPwd, errRegPwd, "validate.required");
            ToastBar.showError(I18nUtil.t("validate.required"));
            return;
        }
        if (p2.isEmpty()) {
            StyleFactory.markError(regPwd2, errRegPwd2, "validate.required");
            ToastBar.showError(I18nUtil.t("validate.required"));
            return;
        }
        if (code.isEmpty()) {
            StyleFactory.markError(captchaInput, errCaptcha, "validate.code");
            ToastBar.showError(I18nUtil.t("validate.code"));
            return;
        }

        // (2) Username format rule -> red border + Toast
        if (!u.matches("[a-zA-Z0-9_]{4,20}")) {
            StyleFactory.markError(regUsername, errRegUser, "register.usernameRule");
            ToastBar.showError(I18nUtil.t("register.usernameRule"));
            return;
        }

        // (3) Password strength rule -> red border + Toast
        if (!p.matches("^(?=.*[a-zA-Z])(?=.*\\d).{8,20}$")) {
            StyleFactory.markError(regPwd, errRegPwd, "register.pwdFormat");
            ToastBar.showError(I18nUtil.t("register.pwdFormat"));
            return;
        }

        // (4) Password confirmation (normalized comparison, fix false-mismatch bug) -> red border + Toast
        if (!normalize(p).equals(normalize(p2))) {
            StyleFactory.markError(regPwd2, errRegPwd2, "register.pwdMismatch");
            ToastBar.showError(I18nUtil.t("register.pwdMismatch"));
            return;
        }

        // (5) Captcha validation (must match generated 6-digit code) -> red border + Toast
        if (!isCaptchaValid()) {
            StyleFactory.markError(captchaInput, errCaptcha, "validate.code");
            ToastBar.showError(I18nUtil.t("validate.code"));
            return;
        }

        // (6) Duplicate username -> independent Alert popup
        if (UserSession.getInstance().isUsernameRegistered(u)) {
            Alerts.error("register.dupUser");
            return;
        }

        // (7) Register account
        if (!UserSession.getInstance().registerAccount(u, p)) {
            Alerts.error("register.error");
            return;
        }
        userController.register(u, p);

        // Clear errors
        StyleFactory.clearError(regUsername, errRegUser);
        StyleFactory.clearError(regPwd, errRegPwd);
        StyleFactory.clearError(regPwd2, errRegPwd2);
        StyleFactory.clearError(captchaInput, errCaptcha);

        // === SUCCESS: 注册成功后自动登录并跳转至用户信息录入页（携带登录用户） ===
        AppConfig.getInstance().removeListener(this);
        User regUser = userController.login(u, p);
        if (regUser != null) {
            PageNavigator.toUserInfoInput(regUser);
        } else {
            // 极端兜底：登录未返回用户（理论不会发生），回退到登录页
            PageNavigator.toLogin();
        }
    }

    private void goLogin() {
        AppConfig.getInstance().removeListener(this);
        PageNavigator.toLogin();
    }

    /* ==================== Helpers ==================== */

    private VBox formRowWithStar(String key, TextField tf, Label errLabel) {
        Label label = new Label(I18nUtil.t(key) + " *");
        label.setStyle("-fx-text-fill:rgb(220,40,40); -fx-font-size:13px;");
        tf.setMaxWidth(Double.MAX_VALUE);
        VBox b = new VBox(3, label, tf);
        if (errLabel != null) b.getChildren().add(errLabel);
        return b;
    }

    private VBox captchaRowWithLabel(Node row, Label errLabel) {
        Label label = new Label(I18nUtil.t("register.captcha"));
        label.setStyle("-fx-font-size:13px; -fx-text-fill:-bmi-fg;");
        VBox b = new VBox(3, label, row);
        if (errLabel != null) b.getChildren().add(errLabel);
        return b;
    }

    private void refreshTexts() {
        regUsername.setPromptText(I18nUtil.t("register.username"));
        regPwd.setPromptText(I18nUtil.t("register.password"));
        regPwd2.setPromptText(I18nUtil.t("register.confirmPwd"));
        captchaInput.setPromptText(I18nUtil.t("register.captcha"));
        regSubmitBtn.setText(I18nUtil.t("register.submit"));
        toLoginBtn.setText(I18nUtil.t("register.switchLogin"));
        hintBubble.setText(I18nUtil.t("register.hintAfter"));
        refreshCaptchaBtn.setText(I18nUtil.t("register.refreshCaptcha"));
        langSwitch.setText(I18nUtil.t("lang.toggle"));
    }

    @Override public void onLangChange() { refreshTexts(); }
}
