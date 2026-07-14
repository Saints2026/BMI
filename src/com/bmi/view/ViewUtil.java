package com.bmi.view;

import com.bmi.i18n.AppConfig;
import com.bmi.i18n.I18n;
import com.bmi.i18n.Lang;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.UnaryOperator;

/**
 * 视图层公共工具（对齐 ui_design.md 一.2 控件标准 / 一.3 数值校验 / FR-03 分级）。
 * 提供：数字输入格式化、解析、区间校验、错误提示、BMI 分级。
 */
public final class ViewUtil {

    private ViewUtil() {
    }

    /** 数字输入框：仅允许数字与小数点（禁止字母/负号，呼应一.3 禁止负数/字母）。 */
    public static TextField numberField(String promptKey) {
        TextField tf = new TextField();
        tf.getStyleClass().add("bmi-field");
        tf.setPromptText(I18n.t(promptKey));
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String t = change.getControlNewText();
            if (t.isEmpty()) {
                return change;
            }
            return t.matches("\\d*(\\.\\d*)?") ? change : null;
        };
        tf.setTextFormatter(new TextFormatter<>(filter));
        return tf;
    }

    /** 解析数字；空或非法返回 null（选填项允许空白，呼应一.3）。 */
    public static Double parseDouble(TextField tf) {
        String s = tf.getText().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 区间校验：返回错误 key（I18n key）或 null 表示通过。
     * 负值为 validate.negative，越界为 rangeKey（如 validate.height）。
     */
    public static String validateRange(Double v, double min, double max, String rangeKey) {
        if (v == null) {
            return "validate.required";
        }
        if (v < 0) {
            return "validate.negative";
        }
        if (v < min || v > max) {
            return rangeKey;
        }
        return null;
    }

    public static void setError(Label errLabel, TextField field, String key) {
        if (errLabel != null) {
            errLabel.setText(I18n.t(key));
        }
        if (field != null) {
            field.getStyleClass().add("bmi-field-error");
        }
    }

    public static void clearError(Label errLabel, TextField field) {
        if (errLabel != null) {
            errLabel.setText("");
        }
        if (field != null) {
            field.getStyleClass().remove("bmi-field-error");
        }
    }

    /** 标签 + 控件 + 错误提示 的三行组合（标准化录入行）。 */
    public static VBox fieldRow(String labelKey, TextField field, Label errLabel) {
        VBox box = new VBox(2);
        box.getChildren().addAll(new Label(I18n.t(labelKey)), field, errLabel);
        return box;
    }

    /** 语言下拉框（双语切换，对齐一.1）。 */
    public static ComboBox<Lang> langCombo() {
        ComboBox<Lang> cb = new ComboBox<>();
        cb.getStyleClass().add("bmi-combo");
        cb.getItems().addAll(Lang.ZH, Lang.EN);
        cb.setValue(AppConfig.getInstance().getLang());
        cb.setCellFactory(lv -> langCell());
        cb.setButtonCell(langCell());
        cb.setOnAction(e -> AppConfig.getInstance().setLang(cb.getValue()));
        return cb;
    }

    private static javafx.scene.control.ListCell<Lang> langCell() {
        return new javafx.scene.control.ListCell<Lang>() {
            @Override
            protected void updateItem(Lang item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplay());
            }
        };
    }

    /** BMI 中国标准分级名（返回 I18n key 对应的当前语言文本）。 */
    public static String bmiGradeName(double bmi) {
        if (bmi < 18.5) {
            return I18n.t("grade.thin");
        }
        if (bmi < 24) {
            return I18n.t("grade.normal");
        }
        if (bmi < 28) {
            return I18n.t("grade.overweight");
        }
        return I18n.t("grade.obese");
    }

    /** 分级 → 主题色（卡片色块，呼应 FR-03）。 */
    public static String gradeColor(double bmi) {
        if (bmi < 18.5) {
            return "#2196f3"; // 偏瘦蓝
        }
        if (bmi < 24) {
            return "#4caf50"; // 正常绿
        }
        if (bmi < 28) {
            return "#ff9800"; // 超重橙
        }
        return "#f44336"; // 肥胖红
    }
}
