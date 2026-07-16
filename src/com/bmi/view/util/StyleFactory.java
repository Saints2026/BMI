package com.bmi.view.util;

import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.UnaryOperator;

public final class StyleFactory {

    private StyleFactory() {}

    public static Button primaryButton(String textKey) {
        Button b = new Button(I18nUtil.t(textKey));
        b.getStyleClass().addAll("bmi-btn", "bmi-btn-primary");
        return b;
    }

    public static Button secondaryButton(String textKey) {
        Button b = new Button(I18nUtil.t(textKey));
        b.getStyleClass().addAll("bmi-btn", "bmi-btn-secondary");
        return b;
    }

    public static Button dangerButton(String textKey) {
        Button b = new Button(I18nUtil.t(textKey));
        b.getStyleClass().addAll("bmi-btn", "bmi-btn-danger");
        return b;
    }

    public static Button switchButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("bmi-btn", "bmi-btn-switch");
        return b;
    }

    public static Button successFnButton(String textKey) {
        Button b = new Button(I18nUtil.t(textKey));
        b.getStyleClass().add("bmi-btn-success");
        return b;
    }

    public static Button warningFnButton(String textKey) {
        Button b = new Button(I18nUtil.t(textKey));
        b.getStyleClass().add("bmi-btn-warning-fn");
        return b;
    }

    public static Button dangerFnButton(String textKey) {
        Button b = new Button(I18nUtil.t(textKey));
        b.getStyleClass().add("bmi-btn-danger-fn");
        return b;
    }

    public static Button sliderButton(String textKey) {
        Button b = new Button(I18nUtil.t(textKey));
        b.getStyleClass().add("bmi-btn-slider");
        return b;
    }

    public static TextField numberField(String promptKey) {
        TextField tf = new TextField();
        tf.getStyleClass().add("bmi-field");
        tf.setPromptText(I18nUtil.t(promptKey));
        tf.setTextFormatter(new TextFormatter<>(onlyNumbers()));
        return tf;
    }

    public static TextField textField(String promptKey) {
        TextField tf = new TextField();
        tf.getStyleClass().add("bmi-field");
        tf.setPromptText(I18nUtil.t(promptKey));
        return tf;
    }

    public static PasswordField passwordField(String promptKey) {
        PasswordField pf = new PasswordField();
        pf.getStyleClass().add("bmi-field");
        pf.setPromptText(I18nUtil.t(promptKey));
        return pf;
    }

    public static void markError(TextField field, Label errLabel, String key) {
        if (errLabel != null) errLabel.setText(I18nUtil.t(key));
        if (field != null && !field.getStyleClass().contains("bmi-field-error"))
            field.getStyleClass().add("bmi-field-error");
    }

    public static void clearError(TextField field, Label errLabel) {
        if (errLabel != null) errLabel.setText("");
        if (field != null) field.getStyleClass().remove("bmi-field-error");
    }

    public static <T> ComboBox<T> comboBox() {
        ComboBox<T> cb = new ComboBox<>();
        cb.getStyleClass().add("bmi-combo");
        return cb;
    }

    public static DatePicker datePicker() {
        DatePicker dp = new DatePicker();
        dp.getStyleClass().add("bmi-date");
        return dp;
    }

    public static CheckBox checkBox(String textKey) {
        CheckBox cb = new CheckBox(I18nUtil.t(textKey));
        cb.getStyleClass().add("bmi-check");
        return cb;
    }

    public static <T> void styleTable(TableView<T> table) {
        if (table != null && !table.getStyleClass().contains("bmi-table"))
            table.getStyleClass().add("bmi-table");
    }

    public static void styleChart(XYChart<?, ?> chart) {
        if (chart != null && !chart.getStyleClass().contains("bmi-chart"))
            chart.getStyleClass().add("bmi-chart");
    }

    public static TitledPane titledPane(String key, Node content) {
        TitledPane tp = new TitledPane(I18nUtil.t(key), content);
        tp.setUserData(key);
        return tp;
    }

    public static void styleAccordion(Accordion accordion) {
        if (accordion != null && !accordion.getStyleClass().contains("bmi-accordion"))
            accordion.getStyleClass().add("bmi-accordion");
    }

    public static Label title(String textKey) {
        Label l = new Label(I18nUtil.t(textKey));
        l.getStyleClass().add("bmi-page-title");
        return l;
    }

    public static Label sectionTitle(String textKey) {
        Label l = new Label(I18nUtil.t(textKey));
        l.getStyleClass().add("bmi-section-title");
        return l;
    }

    public static VBox formRow(String labelKey, Node control, Label errorLabel) {
        VBox row = new VBox(3, new Label(I18nUtil.t(labelKey)), control);
        if (errorLabel != null) row.getChildren().add(errorLabel);
        return row;
    }

    public static HBox hLabeledRow(String labelKey, Node control) {
        HBox row = new HBox(8, new Label(I18nUtil.t(labelKey)), control);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private static UnaryOperator<TextFormatter.Change> onlyNumbers() {
        return change -> {
            String t = change.getControlNewText();
            return t.matches("\\d*(\\.\\d*)?") ? change : null;
        };
    }
}
