package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.model.User;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/**
 * AI 健康分析页面（对齐 ai_design.md §2）。
 * 调用 {@link AiController#getAdvice(long)} 获取建议文案，展示三段建议。
 * 线性链路：由 InputView 进入，返回亦至 InputView。
 */
public class AiAnalysisView extends VBox implements LangChangeListener, ThemeChangeListener {

    private final AiController aiController;
    private final User user;
    private final long userId;
    private final TextArea resultArea;
    private final Label title;
    private final Button getAdviceBtn;
    private final Button backBtn;

    public AiAnalysisView(AiController aiController, User user) {
        this.aiController = aiController;
        this.user = user;
        this.userId = user != null ? user.getId() : -1L;
        setPadding(new Insets(24));
        setSpacing(15);
        setStyle("-fx-background-color:" + ThemeConstant.DEFAULT_THEME.bg() + ";");

        title = StyleFactory.title("ai.adviceText");

        resultArea = new TextArea();
        resultArea.setPromptText(I18nUtil.t("ai.genAdvice") + "...");
        resultArea.setPrefHeight(400);
        resultArea.setWrapText(true);
        resultArea.setEditable(false);

        getAdviceBtn = StyleFactory.primaryButton("ai.genAdvice");
        getAdviceBtn.setOnAction(e -> getAdvice());

        backBtn = StyleFactory.secondaryButton("input.backToInput");
        backBtn.setOnAction(e -> PageNavigator.toInput(user));

        VBox wrapper = new VBox(15, title, getAdviceBtn, resultArea, backBtn);
        wrapper.setAlignment(Pos.TOP_LEFT);
        getChildren().add(wrapper);

        AppConfig.getInstance().addListener(this);
    }

    private void getAdvice() {
        resultArea.setText(I18nUtil.t("ai.genAdvice") + "...");

        if (aiController == null) {
            resultArea.setText("AI 服务未配置，请联系管理员");
            return;
        }

        try {
            String advice = aiController.getAdvice(userId);
            if (advice != null && !advice.isEmpty()) {
                resultArea.setText(advice);
            } else {
                resultArea.setText("AI 服务暂时不可用，请稍后再试");
            }
        } catch (Exception e) {
            resultArea.setText("获取建议异常：" + e.getMessage());
        }
    }

    @Override
    public void onLangChange() {
        title.setText(I18nUtil.t("ai.adviceText"));
        getAdviceBtn.setText(I18nUtil.t("ai.genAdvice"));
        backBtn.setText(I18nUtil.t("input.backToInput"));
    }

    @Override
    public void onThemeChange() {
        setStyle("-fx-background-color:" + ThemeConstant.DEFAULT_THEME.bg() + ";");
    }

    public void dispose() {
        AppConfig.getInstance().removeListener(this);
    }
}
