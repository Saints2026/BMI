package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.controller.ChartController;
import com.bmi.controller.PhotoController;
import com.bmi.controller.RecordController;
import com.bmi.controller.ReportController;
import com.bmi.controller.SettingController;
import com.bmi.controller.UserController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.I18n;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 系统首页 / 全局侧边导航壳（对齐 ui_design.md 第二章「MainView」）。
 * 左侧固定侧边导航，顶部栏（用户名 / 语言 / 配色 / 退出登录），
 * 中心区切换功能页：首页卡片、数据录入（InputView）、图表分析（ChartView），
 * 其余页面（历史/AI/照片/报告/设置）本版本以占位页呈现（待接入）。
 * 数据卡片加载最新 BodyRecord；录入/修改后联动刷新卡片与图表。
 */
public class MainView extends BorderPane implements LangChangeListener {

    private final User user;
    private final UserController userController;
    private final RecordController recordController;
    private final ChartController chartController;
    private final AiController aiController;
    private final PhotoController photoController;
    private final ReportController reportController;
    private final SettingController settingController;
    private final Consumer<User> onLogout;

    private final VBox sidebar = new VBox(6);
    private final Label userNameLabel = new Label();
    private final ComboBox<Lang> langCombo = ViewUtil.langCombo();
    private final ComboBox<String> themeCombo = new ComboBox<>();
    private final Button logoutBtn = new Button();

    // 顶部栏标签（需随语言切换刷新）
    private final Label lblTopUser = new Label();
    private final Label lblTopLang = new Label();
    private final Label lblTopTheme = new Label();

    private InputView inputView;
    private ChartView chartView;
    private AiAnalysisView aiAnalysisView;  // AI 分析视图
    private final VBox center = new VBox(16);
    private String currentPage = "home";

    public MainView(User user, UserController userController, RecordController recordController,
                    ChartController chartController, AiController aiController,
                    PhotoController photoController, ReportController reportController,
                    SettingController settingController, Consumer<User> onLogout) {
        this.user = user;
        this.userController = userController;
        this.recordController = recordController;
        this.chartController = chartController;
        this.aiController = aiController;
        this.photoController = photoController;
        this.reportController = reportController;
        this.settingController = settingController;
        this.onLogout = onLogout;

        buildSidebar();
        buildTopBar();
        setLeft(sidebar);
        setCenter(center);
        showHome();
        refreshTexts();
        AppConfig.getInstance().addListener(this);
    }

    private void buildSidebar() {
        sidebar.setPadding(new Insets(12));
        sidebar.setPrefWidth(180);
        sidebar.getStyleClass().add("bmi-sidebar");
        String[] navKeys = {"nav.home", "nav.input", "nav.history", "nav.chart",
                "nav.ai", "nav.photo", "nav.report", "nav.setting"};
        Runnable[] actions = {
                this::showHome, this::showInput, () -> showPlaceholder("nav.history"),
                this::showChart, this::showAi,  // AI 分析改为真实页面
                () -> showPlaceholder("nav.photo"), () -> showPlaceholder("nav.report"),
                () -> showPlaceholder("nav.setting")};
        for (int i = 0; i < navKeys.length; i++) {
            Button b = new Button();
            final int idx = i;
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("bmi-nav-btn");
            b.setUserData(navKeys[i]);
            b.setOnAction(e -> actions[idx].run());
            sidebar.getChildren().add(b);
        }
    }

    private void buildTopBar() {
        HBox top = new HBox(12);
        top.setPadding(new Insets(8, 12, 8, 12));
        top.setAlignment(Pos.CENTER_RIGHT);
        top.getStyleClass().add("bmi-topbar");

        themeCombo.getItems().addAll(I18n.t("setting.light"), I18n.t("setting.dark"));
        themeCombo.setValue(AppConfig.getInstance().getTheme().equals("dark")
                ? I18n.t("setting.dark") : I18n.t("setting.light"));
        themeCombo.setOnAction(e -> AppConfig.getInstance()
                .setTheme(themeCombo.getValue().equals(I18n.t("setting.dark")) ? "dark" : "light"));

        logoutBtn.setOnAction(e -> {
            AppConfig.getInstance().removeListener(this);
            onLogout.accept(user);
        });

        userNameLabel.setText(user.getUsername());
        lblTopUser.setText(I18n.t("topbar.username") + "：" + user.getUsername());
        lblTopLang.setText(I18n.t("topbar.lang"));
        lblTopTheme.setText(I18n.t("topbar.theme"));
        top.getChildren().addAll(
                lblTopUser,
                lblTopLang, langCombo,
                lblTopTheme, themeCombo,
                logoutBtn);
        setTop(top);
    }

    private void showHome() {
        VBox home = new VBox(16);
        home.setPadding(new Insets(16));
        Label title = new Label(I18n.t("nav.home"));
        title.getStyleClass().add("bmi-page-title");

        // 数据卡片：最新 BodyRecord
        List<BodyRecord> all = recordController.queryRecords(user.getId(), null, null);
        BodyRecord latest = all.isEmpty() ? null : all.get(all.size() - 1);
        HBox cards = new HBox(16);
        if (latest == null) {
            cards.getChildren().add(buildCard("card.bmi", I18n.t("home.noData"), "#9e9e9e"));
        } else {
            cards.getChildren().add(buildCard("card.bmi",
                    round1(latest.getBmi()) + " (" + ViewUtil.bmiGradeName(latest.getBmi()) + ")",
                    ViewUtil.gradeColor(latest.getBmi())));
            cards.getChildren().add(buildCard("card.weight", round1(latest.getWeight()) + " kg", "#1565c0"));
            cards.getChildren().add(buildCard("card.bodyfat", round1(latest.getBodyFat()) + " %", "#00897b"));
        }

        // 底部快捷
        Button bInput = new Button(I18n.t("home.quick.input"));
        Button bChart = new Button(I18n.t("home.quick.chart"));
        Button bAi = new Button(I18n.t("home.quick.ai"));
        bInput.setOnAction(e -> showInput());
        bChart.setOnAction(e -> showChart());
        bAi.setOnAction(e -> showAi());
        HBox quick = new HBox(10, bInput, bChart, bAi);

        home.getChildren().addAll(title, cards, quick);
        setCenter(home);
        currentPage = "home";
    }

    private VBox buildCard(String titleKey, String value, String color) {
        VBox c = new VBox(6);
        c.setPadding(new Insets(14));
        c.setPrefSize(160, 90);
        c.setStyle("-fx-background-color:" + color + "; -fx-background-radius:8;");
        Label t = new Label(I18n.t(titleKey));
        t.setStyle("-fx-text-fill:white; -fx-font-size:12px;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill:white; -fx-font-size:20px; -fx-font-weight:bold;");
        c.getChildren().addAll(t, v);
        return c;
    }

    private void showInput() {
        if (inputView == null) {
            inputView = new InputView(user.getId(), recordController, chartController, this::onDataChanged);
        }
        setCenter(inputView);
        currentPage = "input";
    }

    private void showChart() {
        if (chartView == null) {
            chartView = new ChartView(user.getId(), chartController);
        } else {
            chartView.refresh();
        }
        setCenter(chartView);
        currentPage = "chart";
    }

    /**
     * 显示 AI 分析页面
     */
    private void showAi() {
        if (aiAnalysisView == null) {
            aiAnalysisView = new AiAnalysisView(aiController, recordController, user.getId());
        }
        setCenter(aiAnalysisView);
        currentPage = "ai";
    }

    private void showPlaceholder(String key) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(24));
        box.getChildren().addAll(new Label(I18n.t(key)), new Label(I18n.t("page.todo")));
        setCenter(box);
        currentPage = key;
    }

    /** 录入/修改成功后联动刷新：图表实时同步 + 首页卡片刷新。 */
    private void onDataChanged() {
        if (currentPage.equals("chart") && chartView != null) {
            chartView.refresh();
        }
        if (currentPage.equals("home")) {
            showHome();
        }
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private void refreshTexts() {
        // 侧边导航文案
        for (javafx.scene.Node n : sidebar.getChildren()) {
            if (n instanceof Button) {
                Object k = n.getUserData();
                if (k instanceof String) {
                    ((Button) n).setText(I18n.t((String) k));
                }
            }
        }
        userNameLabel.setText(user.getUsername());
        logoutBtn.setText(I18n.t("topbar.logout"));
        lblTopUser.setText(I18n.t("topbar.username") + "：" + user.getUsername());
        lblTopLang.setText(I18n.t("topbar.lang"));
        lblTopTheme.setText(I18n.t("topbar.theme"));
        themeCombo.getItems().setAll(I18n.t("setting.light"), I18n.t("setting.dark"));
        // 当前页若为首页则重渲染卡片；Input/Chart 已自行监听语言变化，无需重建
        if (currentPage.equals("home")) {
            showHome();
        }
        // AI 分析页面语言刷新
        if (currentPage.equals("ai") && aiAnalysisView != null) {
            // AiAnalysisView 内部自行刷新
        }
    }

    @Override
    public void onLangChange() {
        refreshTexts();
        langCombo.setValue(AppConfig.getInstance().getLang());
    }
}