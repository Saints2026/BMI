package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.controller.ChartController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.model.db.DbException;
import com.bmi.view.util.BmiFloatingCard;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * AI 健康分析页（对齐 V17 高优先级：左筛选 + 右 AI 建议卡片）。
 *
 * <p>左侧为数据来源筛选（从用户历史记录中选择一条作为分析基准，展示其关键指标）；
 * 右侧为 AI 健康建议卡片：点击「生成 AI 建议」调用 {@link AiController#getAdvice(long)}
 * 拉取最新指标与近 10 条历史并生成建议全文。无记录时居中显示 chart.noData 空态。
 */
public class AiAnalysisView extends StackPane implements LangChangeListener, ThemeChangeListener {

    private final User user;
    private final AiController aiController;
    private final ChartController chartController;
    private final ToastBar toast;

    private final BorderPane root = new BorderPane();
    private final HBox topBar = new HBox(12);
    private final BmiFloatingCard bmiCard = BmiFloatingCard.create();
    private final VBox leftPanel = new VBox(12);
    private final VBox rightPanel = new VBox(12);
    private final ComboBox<BodyRecord> recordCombo = StyleFactory.comboBox();
    private final Label dataStatus = new Label();
    private final TextArea adviceArea = new TextArea();
    private final Button genBtn = StyleFactory.primaryButton("ai.generate");

    public AiAnalysisView(User user, AiController aiController, ChartController chartController,
                          ToastBar toast) {
        this.user = user;
        this.aiController = aiController;
        this.chartController = chartController;
        this.toast = toast;

        buildLeft();
        buildRight();
        buildTopBar();

        HBox split = new HBox(16, leftPanel, rightPanel);
        split.setPadding(new Insets(16));
        HBox.setHgrow(leftPanel, Priority.NEVER);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        root.setCenter(split);
        root.setTop(topBar);

        getChildren().addAll(root, toast);
        AppConfig.getInstance().addListener(this);
        AppConfig.getInstance().addThemeListener(this);
        refreshData();
    }

    private void buildTopBar() {
        Button backBtn = StyleFactory.secondaryButton("common.back");
        backBtn.setOnAction(e -> PageNavigator.toMain(user));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 16, 12, 16));
        topBar.getStyleClass().add("bmi-topbar");
        topBar.getChildren().setAll(backBtn, spacer, bmiCard.node());
    }

    private void buildLeft() {
        Label title = StyleFactory.sectionTitle("ai.filter");
        Label srcLabel = new Label(I18nUtil.t("ai.source"));
        srcLabel.getStyleClass().add("bmi-field-label");

        recordCombo.setButtonCell(recordCell());
        recordCombo.setCellFactory(lv -> recordCell());
        recordCombo.setOnAction(e -> updateDataStatus());

        genBtn.setMaxWidth(Double.MAX_VALUE);
        genBtn.setOnAction(e -> generate());

        leftPanel.getChildren().addAll(title, srcLabel, recordCombo, dataStatus, genBtn);
        leftPanel.setStyle("-fx-background-color:-bmi-panel-solid; -fx-background-radius:10;"
                + "-fx-border-color:-bmi-border; -fx-border-width:1; -fx-border-radius:10;"
                + "-fx-padding:16;");
        leftPanel.setMinWidth(280);
        leftPanel.setMaxWidth(320);
    }

    private void buildRight() {
        Label title = StyleFactory.sectionTitle("ai.advice");
        adviceArea.setEditable(false);
        adviceArea.setWrapText(true);
        adviceArea.setPromptText(I18nUtil.t("ai.empty"));
        adviceArea.setStyle("-fx-font-size:13px;");
        VBox.setVgrow(adviceArea, Priority.ALWAYS);
        rightPanel.getChildren().addAll(title, adviceArea);
        rightPanel.setStyle("-fx-background-color:-bmi-panel-solid; -fx-background-radius:10;"
                + "-fx-border-color:-bmi-border; -fx-border-width:1; -fx-border-radius:10;"
                + "-fx-padding:16;");
    }

    private void refreshData() {
        List<BodyRecord> list;
        try {
            list = chartController.getTrend(user.getId());
        } catch (DbException e) {
            list = List.of();
        }
        recordCombo.getItems().setAll(list);
        if (list.isEmpty()) {
            adviceArea.setText(I18nUtil.t("ai.noData"));
            dataStatus.setText("");
            recordCombo.setDisable(true);
            genBtn.setDisable(true);
            bmiCard.clear();
        } else {
            recordCombo.setDisable(false);
            genBtn.setDisable(false);
            recordCombo.setValue(list.get(list.size() - 1));
            updateDataStatus();
        }
    }

    private void updateDataStatus() {
        BodyRecord r = recordCombo.getValue();
        if (r == null) {
            dataStatus.setText("");
            bmiCard.clear();
            return;
        }
        String bp = (r.getSystolicBp() != null && r.getDiastolicBp() != null)
                ? r.getSystolicBp() + "/" + r.getDiastolicBp() : "--";
        dataStatus.setText(I18nUtil.t("ai.basedOn",
                r.getMeasureTime() == null ? "" : r.getMeasureTime().toString())
                + "\n" + I18nUtil.t("ai.metric.bmi") + ": " + round1(r.getBmi())
                + "  " + I18nUtil.t("ai.metric.weight") + ": " + round1(r.getWeight())
                + "  " + I18nUtil.t("ai.metric.bp") + ": " + bp);
        dataStatus.setStyle("-fx-font-size:12px; -fx-text-fill:-bmi-muted;");
        refreshBmiCard();
    }

    /** 用当前选中的记录 BMI 刷新右上角浮动卡（无选中/无数据则清空）。 */
    private void refreshBmiCard() {
        BodyRecord r = recordCombo.getValue();
        if (r != null && r.getBmi() > 0) bmiCard.update(r.getBmi());
        else bmiCard.clear();
    }

    private void generate() {
        String advice = aiController.getAdvice(user.getId());
        adviceArea.setText(advice);
        toast.success(I18nUtil.t("ai.generate"));
    }

    private javafx.scene.control.ListCell<BodyRecord> recordCell() {
        return new javafx.scene.control.ListCell<BodyRecord>() {
            @Override
            protected void updateItem(BodyRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getMeasureTime() == null ? "#" + item.getId()
                            : item.getMeasureTime().toString());
                }
            }
        };
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    @Override
    public void onLangChange() {
        // 文案随语言刷新（按钮/标题通过 refreshTexts 重绑）
        genBtn.setText(I18nUtil.t("ai.generate"));
        adviceArea.setPromptText(I18nUtil.t("ai.empty"));
        bmiCard.refresh();
        refreshData();
    }

    @Override
    public void onThemeChange() {
        ThemeConstant.apply(this, ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));
        refreshBmiCard(); // 主题切换后重绘 BMI 卡片分级配色
    }
}
