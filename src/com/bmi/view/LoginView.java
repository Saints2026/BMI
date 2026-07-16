package com.bmi.view;

import com.bmi.controller.SettingController;
import com.bmi.controller.UserController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.User;
import com.bmi.view.util.Alerts;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.Sha256Util;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import com.bmi.view.util.UserSession;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Login Page — 图1 登录界面（像素级复刻效果图）
 *
 * <p>Layout (NO captcha on this page — captcha is register-only):
 * <ul>
 *   <li>Top navbar: green "BMI" logo left, 4 static icon circles,
 *       language dropdown right</li>
 *   <li>Center white card: title "登录账号", username, password+visibility toggle,
 *       remember checkbox, mint-green login button</li>
 *   <li>Card bottom: "已有帐号？前往注册" link</li>
 *   <li>Bottom hint text about registered accounts</li>
 *   <li>Background: static decoration area</li>
 * </ul>
 */
public class LoginView extends StackPane implements LangChangeListener {

    private final UserController userController;
    private final SettingController settingController;
    private final ToastBar toast = new ToastBar();

    // Form fields
    private final TextField usernameField = StyleFactory.textField("login.username");
    private final PasswordField passwordField = StyleFactory.passwordField("login.password");

    // Password visibility toggle
    private final Button pwdToggleBtn = new Button("\uD83D\uDC41"); // eye icon
    private boolean pwdVisible = false;

    // Remember login checkbox
    private final CheckBox rememberCheck = new CheckBox();

    // Buttons
    private final Button submitBtn = new Button();
    private final Button toRegisterBtn = new Button();
    private final ComboBox<Lang> langCombo = StyleFactory.comboBox();

    // Bottom hint text
    private final Label bottomHint = new Label();

    // Top decorative icons
    private final List<Button> decoIcons = new ArrayList<>(4);

    public LoginView(UserController userController, SettingController settingController) {
        this.userController = userController;
        this.settingController = settingController;

        buildUi();
        refreshTexts();
        // 记住登录：重启自动预填用户名（仅存密文，不预填密码）
        if (AppConfig.getInstance().hasRemembered()) {
            usernameField.setText(AppConfig.getInstance().getRememberedUser());
            rememberCheck.setSelected(true);
        }
        AppConfig.getInstance().addListener(this);
    }

    private void buildUi() {
        /* ====== Top Navigation Bar ====== */
        Label logoText = new Label("BMI");
        logoText.getStyleClass().add("bmi-nav-logo-text");

        // 4 static decorative icons (heart, ruler, chart, person)
        String[] icons = {"\u2764\uFE0F", "\uD83D\uDCC8", "\uD83D\uDCCA", "\uD83C\uDFC3"};
        HBox iconsRow = new HBox(12);
        for (String ic : icons) {
            Button ib = new Button(ic);
            ib.getStyleClass().addAll("bmi-deco-icon-circle", "bmi-deco-icon-circle-green");
            ib.setMouseTransparent(true); // static decoration only
            decoIcons.add(ib);
            iconsRow.getChildren().add(ib);
        }

        HBox leftArea = new HBox(14, logoText, iconsRow);
        leftArea.setAlignment(Pos.CENTER_LEFT);

        // Language dropdown (right side)
        langCombo.getStyleClass().add("bmi-lang-combo");
        langCombo.getItems().addAll(Lang.ZH, Lang.EN);
        langCombo.setValue(AppConfig.getInstance().getLang());
        langCombo.setCellFactory(lv -> langCell());
        langCombo.setButtonCell(langCell());
        langCombo.setOnAction(e -> {
            Lang l = langCombo.getValue();
            if (l != null) {
                I18nUtil.setLang(l);
            }
        });

        HBox topNavbar = new HBox(20, leftArea, langCombo);
        topNavbar.getStyleClass().add("bmi-top-navbar");
        topNavbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(langCombo, Priority.ALWAYS);

        /* ====== Center White Card (NO captcha — removed per requirement) ====== */

        // Title & subtitle
        Label cardTitle = new Label();
        cardTitle.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:-bmi-fg;");
        Label cardSubTitle = new Label();
        cardSubTitle.setStyle("-fx-font-size:12px; -fx-text-fill:" + ThemeConstant.DEFAULT_THEME.muted() + ";");

        // Username field
        VBox usernameRow = formRow("login.username", usernameField);

        // Password field with visibility toggle
        pwdToggleBtn.getStyleClass().add("bmi-pwd-toggle");
        pwdToggleBtn.setOnAction(e -> togglePasswordVisibility());

        HBox passwordRow = new HBox(8, passwordField, pwdToggleBtn);
        passwordRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(passwordField, Priority.ALWAYS);
        VBox passwordBox = new VBox(3, new Label(I18nUtil.t("login.password")), passwordRow);

        // Remember login checkbox
        rememberCheck.setText(I18nUtil.t("login.remember"));
        rememberCheck.getStyleClass().add("bmi-check");

        // Switch-to-register link (card bottom)
        toRegisterBtn.getStyleClass().add("bmi-btn-link");
        toRegisterBtn.setOnAction(e -> goRegister());

        // 主色提交按钮 RGB(45,140,220)：高度36px / 最小宽110px / 内边距8px 16px / 文字居中
        // 宽度由内容与内边距共同决定（maxWidth=USE_PREF_SIZE），不被拉伸也不被压缩，文字不截断
        submitBtn.getStyleClass().addAll("bmi-btn", "bmi-btn-primary");
        submitBtn.setMaxWidth(Region.USE_PREF_SIZE);
        submitBtn.setStyle("-fx-min-height:36px; -fx-pref-height:36px; -fx-max-height:36px;"
                + " -fx-min-width:110px; -fx-padding:8px 16px; -fx-alignment:CENTER;"
                + " -fx-background-color:rgb(45,140,220); -fx-text-fill:rgb(255,255,255);");
        submitBtn.setOnAction(e -> doLogin());

        // Card content (centered, no captcha)
        HBox submitBox = new HBox(submitBtn);
        submitBox.setAlignment(Pos.CENTER); // 按钮按内容宽度居中，不拉伸
        VBox formContent = new VBox(12,
                usernameRow,
                passwordBox,
                rememberCheck,
                toRegisterBtn,
                submitBox);
        formContent.setFillWidth(true);

        VStack cardBody = new VStack(20,
                cardTitle,
                cardSubTitle,
                formContent);
        cardBody.setPadding(new Insets(28, 32, 24, 32));
        cardBody.setMaxWidth(420);
        cardBody.setStyle("-fx-background-color:rgb(255,255,255); -fx-background-radius:10;"
                + "-fx-effect:dropshadow(three-pass-box, rgba(0,0,0,0.08), 12, 0, 0, 4);");

        /* ====== Bottom Hint Text ====== */
        bottomHint.getStyleClass().add("bmi-bottom-hint");
        bottomHint.setAlignment(Pos.CENTER);

        /* ====== Assemble page: card fully centered (horizontal + vertical) ====== */
        // Center area grows to fill the space and centers the card both axes,
        // so the form stays centered on window resize.
        StackPane centerArea = new StackPane(cardBody);
        centerArea.setAlignment(Pos.CENTER);
        VBox.setVgrow(centerArea, Priority.ALWAYS);

        VBox pageRoot = new VStack(0, topNavbar, centerArea, bottomHint);
        pageRoot.setFillWidth(true);
        pageRoot.getStyleClass().add("bmi-page-bg");
        pageRoot.setPadding(new Insets(0, 0, 16, 0));

        getChildren().addAll(pageRoot, toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
    }

    /** Toggle password visibility (simple icon toggle, no field swap for stability). */
    private void togglePasswordVisibility() {
        pwdVisible = !pwdVisible;
        if (pwdVisible) {
            pwdToggleBtn.setText("\u2715"); // X mark = hide
        } else {
            pwdToggleBtn.setText("\uD83D\uDC41"); // eye = show
        }
    }

    private VBox formRow(String key, Node node) {
        return new VBox(3, new Label(I18nUtil.t(key)), node);
    }

    /* ==================== Login Logic (NO captcha validation) ==================== */

    private void doLogin() {
        String u = usernameField.getText().trim();
        String p = passwordField.getText();

        if (u.isEmpty() || p.isEmpty()) {
            ToastBar.showWarning(I18nUtil.t("login.errorEmpty"));
            return;
        }
        // No captcha check — captcha removed from login page

        User okUser = UserSession.getInstance().findRegisteredUser(u, p);
        if (okUser == null) {
            okUser = userController.login(u, p);
        }
        if (okUser == null) {
            // 账号或密码错误：独立 Alert 弹窗（非 Toast）
            Alerts.error("login.errorCredential");
            return;
        }

        // 记住登录：勾选则写用户名 + SHA-256 密文；否则清空本地凭据
        if (rememberCheck.isSelected()) {
            AppConfig.getInstance().setRemember(u, Sha256Util.hash(p));
        } else {
            AppConfig.getInstance().clearRemember();
        }

        loginSuccess(okUser);
    }

    private void loginSuccess(User user) {
        ToastBar.showSuccess(I18nUtil.t("login.success"));
        User finalUser = user;
        PauseTransition pt = new PauseTransition(Duration.millis(900));
        pt.setOnFinished(e -> goInput(finalUser));
        pt.play();
    }

    private void goInput(User user) {
        AppConfig.getInstance().removeListener(this);
        PageNavigator.toUserInfoInput(user);
    }

    private void goRegister() {
        AppConfig.getInstance().removeListener(this);
        PageNavigator.toRegister();
    }

    private javafx.scene.control.ListCell<Lang> langCell() {
        return new javafx.scene.control.ListCell<Lang>() {
            @Override protected void updateItem(Lang item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplay());
            }
        };
    }

    private void refreshTexts() {
        usernameField.setPromptText(I18nUtil.t("login.username"));
        passwordField.setPromptText(I18nUtil.t("login.password"));
        submitBtn.setText(I18nUtil.t("login.submit"));
        toRegisterBtn.setText(I18nUtil.t("register.title"));
        rememberCheck.setText(I18nUtil.t("login.remember"));
        bottomHint.setText(I18nUtil.t("login.bottomHint"));
    }

    /** Bidirectional sync: align combo selected value with in-memory language variable. */
    private void syncLangCombo() {
        Lang cur = AppConfig.getInstance().getLang();
        if (langCombo.getValue() != cur) {
            langCombo.setValue(cur);
        }
    }

    @Override public void onLangChange() {
        refreshTexts();
        syncLangCombo();
    }

    /* ========== Helper: VBox that fills width ========== */
    private static class VStack extends VBox {
        VStack(double spacing, Node... children) {
            super(spacing, children);
            super.setFillWidth(true);
        }
    }
}
