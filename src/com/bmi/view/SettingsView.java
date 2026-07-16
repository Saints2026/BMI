package com.bmi.view;

import com.bmi.controller.SettingController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.Lang;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class SettingsView extends VBox implements LangChangeListener, ThemeChangeListener {

    private final long userId;
    private final SettingController settingController;
    private final ToastBar toast;

    private final Button closeBtn = new Button("\u2715");
    private final ComboBox<Lang> langCombo = new ComboBox<>();
    private final ComboBox<String> fontSizeCombo = new ComboBox<>();
    private final HBox themeBlocks = new HBox(16);
    private final List<Button> blockButtons = new ArrayList<>();
    private final TextField storagePathField = new TextField();
    private final Button btnClearPath = StyleFactory.secondaryButton("setting.path.clear");
    private final TextField pfName = StyleFactory.textField("setting.profile.name");
    private final Button pfEditBtn = StyleFactory.secondaryButton("setting.profile.edit");
    private final Button btnSave = StyleFactory.primaryButton("setting.save");

    public SettingsView(long userId, SettingController settingController, ToastBar toast) {
        this.userId = userId; this.settingController = settingController; this.toast = toast;

        setSpacing(0);
        setPadding(new Insets(0));
        rebuild();
        AppConfig.getInstance().addListener(this);
        AppConfig.getInstance().addThemeListener(this);
    }

    public void dispose() {
        AppConfig.getInstance().removeListener(this);
        AppConfig.getInstance().removeThemeListener(this);
    }

    private void rebuild() {
        getChildren().clear();

        Label pageTitle = StyleFactory.title("setting.title");
        pageTitle.setPadding(new Insets(0, 20, 12, 0));

        closeBtn.getStyleClass().add("bmi-close-x");
        closeBtn.setOnAction(e -> dispose());

        HBox headerRow = new HBox(pageTitle, new Region(), closeBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerRow.getChildren().get(1), Priority.ALWAYS);

        HBox splitContent = new HBox(20);
        splitContent.setPadding(new Insets(0, 20, 12, 20));

        VBox leftPanel = buildPhotoPanel();
        VBox rightPanel = buildSettingsPanel();

        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        splitContent.getChildren().addAll(leftPanel, rightPanel);

        btnSave.setMaxWidth(Double.MAX_VALUE);
        btnSave.setOnAction(e -> doSave());
        HBox bottomBar = new HBox(btnSave);
        bottomBar.setPadding(new Insets(12, 20, 16, 20));
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setStyle("-fx-background-color:-bmi-panel-solid; -fx-border-color:-bmi-border;"
                + "-fx-border-width:1 0 0 0;");
        HBox.setHgrow(btnSave, Priority.NEVER);

        getChildren().addAll(headerRow, splitContent, bottomBar);
    }

    private VBox buildPhotoPanel() {
        VBox panel = new VBox(14);
        panel.setStyle("-fx-background-color:-bmi-panel-solid; -fx-background-radius:10;"
                + "-fx-border-color:-bmi-border; -fx-border-width:1; -fx-border-radius:10;"
                + "-fx-padding:16 20 16 20;");

        Label panelTitle = StyleFactory.sectionTitle("setting.photo.title");

        Button btnAddPhoto = StyleFactory.primaryButton("photo.upload");
        btnAddPhoto.setMaxWidth(200);
        btnAddPhoto.setOnAction(e -> ToastBar.showWarning(I18nUtil.t("page.todo")));

        HBox tableHeader = new HBox(10,
                new Label(I18nUtil.t("photo.filename")),
                new Label(I18nUtil.t("photo.path")),
                new Label(I18nUtil.t("history.table.time")),
                new Label(I18nUtil.t("common.delete")));
        tableHeader.setPadding(new Insets(6, 8, 6, 8));
        tableHeader.setStyle("-fx-font-size:12px; -fx-font-weight:bold;"
                + "-fx-background-color:-bmi-panel; -fx-background-radius:4;");
        tableHeader.setAlignment(Pos.CENTER_LEFT);

        Label emptyTableHint = new Label(I18nUtil.t("setting.photo.empty"));
        emptyTableHint.setStyle("-fx-text-fill:-bmi-muted; -fx-font-size:12px; -fx-padding:24;");
        emptyTableHint.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(emptyTableHint, Priority.ALWAYS);

        Label previewLabel = StyleFactory.sectionTitle("setting.photo.preview");

        Label plusIcon = new Label("\uFF0B");
        plusIcon.setStyle("-fx-font-size:32px; -fx-text-fill:-bmi-muted; -fx-opacity:0.5;");
        Label previewHint = new Label(I18nUtil.t("setting.photo.hint"));
        previewHint.setStyle("-fx-font-size:11px; -fx-text-fill:-bmi-muted;");
        VBox previewBox = new VBox(8, plusIcon, previewHint);
        previewBox.setAlignment(Pos.CENTER);
        previewBox.setMinHeight(100);
        previewBox.setStyle("-fx-background-color:-bmi-panel; -fx-background-radius:6;"
                + "-fx-border-color:-bmi-border; -fx-border-width:1px dashed; -fx-border-radius:6;");

        VBox previewArea = new VBox(6, previewLabel, previewBox);
        previewArea.setPadding(new Insets(10));
        previewArea.setStyle("-fx-background-color:-bmi-panel; -fx-background-radius:6; -fx-min-height:100;"
                + "-fx-alignment:center;");

        panel.getChildren().addAll(panelTitle, btnAddPhoto, tableHeader, emptyTableHint, previewArea);
        return panel;
    }

    private VBox buildSettingsPanel() {
        VBox column = new VBox(14);

        column.getChildren().add(buildCard("setting.card.lang", "\uD83C\uDF10", buildLanguageCard()));
        column.getChildren().add(buildCard("setting.theme", "\uD83C\uDF3A", buildThemeCard()));
        column.getChildren().add(buildCard("setting.storage", "\uD83D\uDCC1", buildStorageCard()));
        column.getChildren().add(buildCard("setting.profile.title", "\uD83D\uDC64", buildProfileCard()));

        return column;
    }

    private VBox buildCard(String titleKey, String emoji, Node content) {
        VBox card = new VBox(12);
        card.getStyleClass().add("bmi-settings-card");
        Label cardTitle = new Label(emoji + " " + I18nUtil.t(titleKey));
        cardTitle.getStyleClass().add("bmi-settings-card-title");
        card.getChildren().addAll(cardTitle, content);
        return card;
    }

    private Node buildLanguageCard() {
        langCombo.getItems().addAll(Lang.ZH, Lang.EN);
        langCombo.setValue(AppConfig.getInstance().getLang());
        langCombo.setCellFactory(lv -> langCell());
        langCombo.setButtonCell(langCell());
        langCombo.setOnAction(e -> {
            Lang l = langCombo.getValue();
            if (l != null) { I18nUtil.setLang(l); settingController.setLangDefault(l.getCode()); }
        });

        fontSizeCombo.getItems().addAll("S", "M", "L", "XL");
        fontSizeCombo.setValue("M");
        fontSizeCombo.setOnAction(e -> ToastBar.showSuccess(I18nUtil.t("setting.applied")));

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.addRow(0, new Label(I18nUtil.t("setting.lang.default")), langCombo);
        grid.addRow(1, new Label(I18nUtil.t("setting.font.size")), fontSizeCombo);
        return grid;
    }

    private Node buildThemeCard() {
        themeBlocks.getChildren().clear();
        themeBlocks.setAlignment(Pos.CENTER_LEFT);
        blockButtons.clear();

        for (ThemeConstant.Theme t : ThemeConstant.ALL) {
            VBox cell = new VBox(4);
            cell.setAlignment(Pos.CENTER);

            Button sw = new Button();
            sw.setPrefSize(80, 52); sw.setMinSize(80, 52);
            sw.getStyleClass().add("bmi-theme-block");
            sw.setUserData(t);
            sw.setTooltip(new javafx.scene.control.Tooltip(I18nUtil.t(t.nameKey())));
            sw.setOnAction(e -> applyTheme(t));

            HBox fnBars = new HBox(2);
            fnBars.setAlignment(Pos.CENTER);
            String[] fns = {t.success(), t.warning(), t.danger()};
            for (String fc : fns) {
                Region bar = new Region();
                bar.setPrefSize(24, 4); bar.setMaxSize(24, 4);
                bar.setStyle("-fx-background-color:" + fc + "; -fx-background-radius:2;");
                fnBars.getChildren().add(bar);
            }

            Label name = new Label(I18nUtil.t(t.nameKey()));
            name.getStyleClass().add("bmi-theme-name");

            cell.getChildren().addAll(sw, fnBars, name);
            themeBlocks.getChildren().add(cell);
            blockButtons.add(sw);
        }
        highlightThemeBlocks();

        ScrollPane sp = new ScrollPane(themeBlocks);
        sp.setFitToWidth(true); sp.setFitToHeight(true); sp.setPannable(false);
        return sp;
    }

    private void applyTheme(ThemeConstant.Theme t) {
        AppConfig.getInstance().setTheme(t.cssClass());
        settingController.setTheme(t.cssClass());
        highlightThemeBlocks();
        toast.success(I18nUtil.t("setting.applied"));
    }

    private void highlightThemeBlocks() {
        String cur = AppConfig.getInstance().getTheme();
        for (Button b : blockButtons) {
            ThemeConstant.Theme t = (ThemeConstant.Theme) b.getUserData();
            b.setStyle(swatchStyle(t, t.cssClass().equals(cur)));
        }
    }

    private String swatchStyle(ThemeConstant.Theme t, boolean active) {
        String border = active ? t.primaryDark() : "rgba(0,0,0,0.15)";
        int bw = active ? 3 : 1;
        return "-fx-background-color:" + t.primary() + ";"
                + "-fx-background-radius:8; -fx-border-radius:8;"
                + "-fx-border-width:" + bw + "; -fx-border-color:" + border + ";";
    }

    private Node buildStorageCard() {
        storagePathField.setPromptText(I18nUtil.t("storage.default"));
        storagePathField.setEditable(false);

        btnClearPath.setOnAction(e -> { storagePathField.clear(); toast.success(I18nUtil.t("setting.applied")); });

        HBox row = new HBox(8, storagePathField, btnClearPath);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(storagePathField, Priority.ALWAYS);
        return row;
    }

    private Node buildProfileCard() {
        pfName.setPromptText(I18nUtil.t("setting.profile.name"));

        pfEditBtn.setOnAction(e -> ToastBar.showWarning(I18nUtil.t("page.todo")));

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8);
        grid.addRow(0, new Label(I18nUtil.t("setting.profile.name")), pfName);
        grid.addRow(1, new Label(""), pfEditBtn);
        return grid;
    }

    private void doSave() {
        Lang l = langCombo.getValue();
        if (l != null) settingController.setLangDefault(l.getCode());
        toast.success(I18nUtil.t("setting.applied"));
    }

    private javafx.scene.control.ListCell<Lang> langCell() {
        return new javafx.scene.control.ListCell<Lang>() {
            @Override protected void updateItem(Lang item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplay());
            }
        };
    }

    @Override public void onLangChange() { rebuild(); }
    @Override public void onThemeChange() { highlightThemeBlocks(); }
}
