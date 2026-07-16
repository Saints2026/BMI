package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.controller.ChartController;
import com.bmi.controller.PhotoController;
import com.bmi.controller.RecordController;
import com.bmi.controller.ReportController;
import com.bmi.controller.SettingController;
import com.bmi.controller.UserController;
import com.bmi.i18n.AppConfig;
import com.bmi.view.util.I18nUtil;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MainView extends StackPane implements LangChangeListener, ThemeChangeListener {

    private final User user;
    private final UserController userController;
    private final RecordController recordController;
    private final ChartController chartController;
    private final AiController aiController;
    private final PhotoController photoController;
    private final ReportController reportController;
    private final SettingController settingController;
    private final Consumer<User> onLogout;

    private final ToastBar toast = new ToastBar();
    private final BorderPane root = new BorderPane();
    private final VBox sidebar = new VBox(4);
    private final Label sidebarLogo = new Label("BMI");

    private final ComboBox<Lang> langCombo = StyleFactory.comboBox();
    private final ComboBox<ThemeConstant.Theme> themeCombo = StyleFactory.comboBox();
    private final Button logoutBtn = StyleFactory.secondaryButton("topbar.logout");

    private final List<Button> navButtons = new ArrayList<>();
    private String currentPage = "home";

    private InputView inputView;
    private ChartView chartView;
    private HistoryView historyView;
    private SettingsView settingsView;

    private AiAnalysisView aiAnalysisView;
    private PhotoView photoView;
    private ReportView reportView;

    public MainView(User user, UserController userController, RecordController recordController,
                    ChartController chartController, AiController aiController,
                    PhotoController photoController, ReportController reportController,
                    SettingController settingController, Consumer<User> onLogout) {
        this.user = user; this.userController = userController;
        this.recordController = recordController; this.chartController = chartController;
        this.aiController = aiController; this.photoController = photoController;
        this.reportController = reportController; this.settingController = settingController;
        this.onLogout = onLogout;

        buildSidebar();
        buildTopBar();
        root.setLeft(sidebar);

        getChildren().addAll(root, toast);
        showHome();
        applyTheme(ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));
        AppConfig.getInstance().addListener(this);
        AppConfig.getInstance().addThemeListener(() -> {
            ThemeConstant.Theme t = ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme());
            applyTheme(t);
            if ("home".equals(currentPage)) showHome();
        });
    }

    private void buildSidebar() {
        sidebar.getStyleClass().add("bmi-sidebar");

        sidebarLogo.getStyleClass().add("bmi-sidebar-logo");
        sidebar.getChildren().add(sidebarLogo);

        String[] navKeys = {"nav.home", "nav.input", "nav.history", "nav.ai", "nav.photo", "nav.report", "nav.setting"};
        Runnable[] actions = {
                this::showHome, this::showInput, this::showHistory,
                this::showAi, this::showPhoto, this::showReport, this::showSettings};

        for (int i = 0; i < navKeys.length; i++) {
            Button b = new Button(I18nUtil.t(navKeys[i]));
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("bmi-nav-btn");
            b.setUserData(navKeys[i]);
            final int idx = i;
            b.setOnAction(e -> actions[idx].run());
            sidebar.getChildren().add(b);
            navButtons.add(b);
        }
    }

    private void setActiveNav(String key) {
        for (Button b : navButtons) {
            b.getStyleClass().remove("bmi-nav-active");
            if (key.equals(b.getUserData())) b.getStyleClass().add("bmi-nav-active");
        }
    }

    private void buildTopBar() {
        HBox top = new HBox(12);
        top.setPadding(new Insets(8, 16, 8, 16));
        top.setAlignment(Pos.CENTER_RIGHT);
        top.getStyleClass().add("bmi-topbar");

        langCombo.getItems().addAll(Lang.ZH, Lang.EN);
        langCombo.setButtonCell(langCell());
        langCombo.setCellFactory(lv -> langCell());
        langCombo.setValue(AppConfig.getInstance().getLang());
        langCombo.setOnAction(e -> I18nUtil.setLang(langCombo.getValue()));

        for (ThemeConstant.Theme t : ThemeConstant.ALL) themeCombo.getItems().add(t);
        themeCombo.setButtonCell(themeCell());
        themeCombo.setCellFactory(lv -> themeCell());
        themeCombo.setValue(ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));
        themeCombo.setOnAction(e -> {
            ThemeConstant.Theme t = themeCombo.getValue();
            if (t != null) { applyTheme(t); AppConfig.getInstance().setTheme(t.cssClass()); settingController.setTheme(t.cssClass()); }
        });

        logoutBtn.setOnAction(e -> { AppConfig.getInstance().removeListener(this); onLogout.accept(user); });

        Label userLabel = new Label(I18nUtil.t("topbar.username") + ": " + user.getUsername());

        top.getChildren().addAll(
                userLabel,
                new Label(I18nUtil.t("topbar.lang")), langCombo,
                new Label(I18nUtil.t("topbar.theme")), themeCombo,
                logoutBtn);
        root.setTop(top);
    }

    private void applyTheme(ThemeConstant.Theme t) {
        if (getScene() != null) ThemeConstant.apply(getScene(), t);
        themeCombo.setValue(t);
    }

    private void showHome() {
        VBox home = new VBox(16);
        home.setPadding(new Insets(20));
        home.setStyle("-fx-background-color:" + ThemeConstant.DEFAULT_THEME.bg() + ";");
        home.getStyleClass().add("bmi-home-content");

        Label title = StyleFactory.title("nav.home");

        List<BodyRecord> all = recordController.queryRecords(user.getId(), null, null);
        BodyRecord latest = all.isEmpty() ? null : all.get(all.size() - 1);
        ThemeConstant.Theme curTheme = ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme());

        HBox cards = buildMetricCards(latest, curTheme);
        HBox.setHgrow(cards, Priority.ALWAYS);

        Label bmiChartTitle = StyleFactory.sectionTitle("chart.bmi");
        Label bpChartTitle = StyleFactory.sectionTitle("chart.bp");

        LineChart<Number, Number> bmiChart = makeMiniChart(r -> r.getBmi(),
                ThemeConstant.seriesColor(curTheme, 0), all);
        LineChart<Number, Number> bpChart = makeMiniChart(r ->
                        r.getSystolicBp() != null ? r.getSystolicBp().doubleValue() : 0,
                ThemeConstant.seriesColor(curTheme, 1), all);
        bmiChart.setTitle(null);
        bpChart.setTitle(null);

        VBox bmiCard = new VBox(6, bmiChartTitle, bmiChart);
        VBox bpCard = new VBox(6, bpChartTitle, bpChart);
        VBox.setVgrow(bmiChart, Priority.ALWAYS);
        VBox.setVgrow(bpChart, Priority.ALWAYS);
        HBox charts = new HBox(16, bmiCard, bpCard);
        HBox.setHgrow(bmiCard, Priority.ALWAYS);
        HBox.setHgrow(bpCard, Priority.ALWAYS);

        Button bInput = StyleFactory.successFnButton("home.quick.input");
        Button bPhoto = StyleFactory.warningFnButton("home.quick.photo");
        Button bAi = StyleFactory.dangerFnButton("home.quick.ai");
        bInput.setOnAction(e -> showInput());
        bPhoto.setOnAction(e -> showPhoto());
        bAi.setOnAction(e -> showAi());
        HBox actionBtns = new HBox(16, bInput, bPhoto, bAi);
        actionBtns.setAlignment(Pos.CENTER);
        HBox.setHgrow(bInput, Priority.ALWAYS);
        HBox.setHgrow(bPhoto, Priority.ALWAYS);
        HBox.setHgrow(bAi, Priority.ALWAYS);

        home.getChildren().addAll(title, cards, charts, actionBtns);
        root.setCenter(home);
        setActiveNav("nav.home");
        currentPage = "home";
    }

    private HBox buildMetricCards(BodyRecord latest, ThemeConstant.Theme curTheme) {
        HBox cards = new HBox(16);
        cards.setAlignment(Pos.CENTER_LEFT);

        if (latest == null) {
            cards.getChildren().add(buildMetricCard("card.bmi", "--", I18nUtil.t("home.noData"), "#c9cdd4"));
            return cards;
        }

        double bmi = round1(latest.getBmi());
        String grade = gradeName(bmi);
        double sys = latest.getSystolicBp() != null ? latest.getSystolicBp().doubleValue() : 0;
        double dia = latest.getDiastolicBp() != null ? latest.getDiastolicBp().doubleValue() : 0;
        double bf = round1(latest.getBodyFat());

        String bmiColor = ThemeConstant.bmiGradeColor(bmi);
        cards.getChildren().addAll(
                buildMetricCard("card.bmi", bmi + "", grade, bmiColor),
                buildMetricCard("card.bp", (int)sys + "/" + (int)dia, bpStatus(sys, dia), curTheme.primary() + "33"),
                buildMetricCard("card.bodyfat", bf + "%", gradeNameBf(bf), curTheme.warning() + "44"),
                buildMetricCard("card.waist", waistRiskStatus() + "", waistRiskStatusDetail(), curTheme.success() + "38"));
        return cards;
    }

    private VBox buildMetricCard(String titleKey, String value, String status, String bgColor) {
        VBox c = new VBox(8);
        c.setPadding(new Insets(16, 20, 14, 20));
        c.setMinSize(0, 100);
        c.setStyle("-fx-background-color:" + bgColor + "; -fx-background-radius:10;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");

        Label t = new Label(I18nUtil.t(titleKey));
        t.setStyle("-fx-font-size:12px; -fx-text-fill:rgba(255,255,255,0.85);");

        Label v = new Label(value);
        v.setStyle("-fx-font-size:24px; -fx-font-weight:bold; -fx-text-fill:white;");

        Label s = new Label(status);
        s.setStyle("-fx-font-size:11px; -fx-text-fill:rgba(255,255,255,0.75);");

        c.getChildren().addAll(t, v, s);
        return c;
    }

    private String bpStatus(double sys, double dia) {
        if (sys < 120 && dia < 80) return I18nUtil.t("grade.normal");
        if (sys < 140 && dia < 90) return I18nUtil.t("status.high");
        return I18nUtil.t("grade.obese");
    }
    private String gradeNameBf(double bf) {
        if (bf < 15) return I18nUtil.t("grade.thin");
        if (bf < 25) return I18nUtil.t("grade.normal");
        return I18nUtil.t("grade.overweight");
    }
    private String waistRiskStatus() { return "82"; }
    private String waistRiskStatusDetail() { return I18nUtil.t("status.low") + "cm"; }

    private LineChart<Number, Number> makeMiniChart(java.util.function.ToDoubleFunction<BodyRecord> f,
                                                    String color, List<BodyRecord> all) {
        NumberAxis x = new NumberAxis(); x.setVisible(false);
        NumberAxis y = new NumberAxis(); y.setVisible(false);
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setPrefSize(Integer.MAX_VALUE, 180);
        chart.setMinHeight(140);
        chart.setMaxHeight(220);
        chart.getStyleClass().add("bmi-chart");
        chart.setLegendVisible(false);
        chart.setHorizontalGridLinesVisible(false);
        chart.setVerticalGridLinesVisible(false);
        chart.setCreateSymbols(true);

        if (all.size() >= 2) {
            List<BodyRecord> recent = all.size() > 6 ? all.subList(all.size() - 6, all.size()) : all;
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            for (int i = 0; i < recent.size(); i++)
                s.getData().add(new XYChart.Data<>(i + 1, f.applyAsDouble(recent.get(i))));
            chart.getData().add(s);
        }
        return chart;
    }

    private void showInput() {
        if (inputView == null)
            inputView = new InputView(user.getId(), recordController, chartController,
                    this::onDataChanged, toast);
        root.setCenter(inputView);
        setActiveNav("nav.input");
        currentPage = "input";
    }

    private void showHistory() {
        historyView = new HistoryView(user.getId(), recordController, toast, this::editFromHistory);
        root.setCenter(historyView);
        setActiveNav("nav.history");
        currentPage = "history";
    }

    private void editFromHistory(BodyRecord r) {
        if (inputView == null)
            inputView = new InputView(user.getId(), recordController, chartController,
                    this::onDataChanged, toast);
        inputView.loadRecord(r);
        root.setCenter(inputView);
        setActiveNav("nav.input");
        currentPage = "input";
        toast.success(I18nUtil.t("history.table.edit"));
    }

    private void showChart() {
        ChartPopup popup = new ChartPopup(user.getId(), chartController);
        popup.show();
        setActiveNav("nav.chart");
    }

    private void showSettings() {
        if (settingsView != null) settingsView.dispose();
        settingsView = new SettingsView(user.getId(), settingController, toast);
        root.setCenter(settingsView);
        setActiveNav("nav.setting");
        currentPage = "setting";
    }

    private void showAi() {
        if (aiAnalysisView != null) aiAnalysisView.dispose();
        aiAnalysisView = new AiAnalysisView(aiController, user.getId());
        root.setCenter(aiAnalysisView);
        setActiveNav("nav.ai");
        currentPage = "ai";
    }

    private void showPhoto() {
        if (photoView != null) photoView.dispose();
        photoView = new PhotoView(photoController, user.getId());
        root.setCenter(photoView);
        setActiveNav("nav.photo");
        currentPage = "photo";
    }

    private void showReport() {
        if (reportView != null) reportView.dispose();
        reportView = new ReportView(reportController, user.getId());
        root.setCenter(reportView);
        setActiveNav("nav.report");
        currentPage = "report";
    }

    private void onDataChanged() {
        if ("home".equals(currentPage)) showHome();
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private String gradeName(double bmi) {
        if (bmi < 18.5) return I18nUtil.t("grade.thin");
        if (bmi < 24)   return I18nUtil.t("grade.normal");
        if (bmi < 28)   return I18nUtil.t("grade.overweight");
        return I18nUtil.t("grade.obese");
    }

    private void refreshTexts() {
        for (Button b : navButtons) b.setText(I18nUtil.t((String) b.getUserData()));
        logoutBtn.setText(I18nUtil.t("topbar.logout"));
        if ("home".equals(currentPage)) showHome();
    }

    @Override public void onLangChange() { refreshTexts(); langCombo.setValue(AppConfig.getInstance().getLang()); }
    @Override public void onThemeChange() { }

    private javafx.scene.control.ListCell<ThemeConstant.Theme> themeCell() {
        return new javafx.scene.control.ListCell<ThemeConstant.Theme>() {
            @Override protected void updateItem(ThemeConstant.Theme item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : I18nUtil.t(item.nameKey()));
            }
        };
    }

    private javafx.scene.control.ListCell<Lang> langCell() {
        return new javafx.scene.control.ListCell<Lang>() {
            @Override protected void updateItem(Lang item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplay());
            }
        };
    }
}
