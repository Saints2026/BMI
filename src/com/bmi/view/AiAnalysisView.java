package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.controller.RecordController;
import com.bmi.model.BodyRecord;
import com.bmi.util.Result;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.util.List;

public class AiAnalysisView extends VBox {

    private final AiController aiController;
    private final RecordController recordController;
    private final long userId;
    private final TextArea resultArea;

    public AiAnalysisView(AiController aiController, RecordController recordController, long userId) {
        this.aiController = aiController;
        this.recordController = recordController;
        this.userId = userId;
        setPadding(new Insets(20));
        setSpacing(15);

        Label title = new Label("AI 健康建议");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        resultArea = new TextArea();
        resultArea.setPromptText("点击下方按钮获取 AI 健康建议...");
        resultArea.setPrefHeight(300);
        resultArea.setWrapText(true);

        Button getAdviceBtn = new Button("获取 AI 建议");
        getAdviceBtn.setStyle("-fx-font-size: 14px; -fx-padding: 8 16;");
        getAdviceBtn.setOnAction(e -> getAdvice());

        getChildren().addAll(title, getAdviceBtn, resultArea);
    }

    private void getAdvice() {
        System.out.println("调用 getAdvice... aiController=" + (aiController != null ? "有效" : "null"));
        resultArea.setText("正在获取 AI 建议，请稍候...");

        if (aiController == null) {
            resultArea.setText("AI 服务未初始化（请检查 API Key 配置）");
            System.err.println("aiController 为 null，无法调用 AI 建议");
            return;
        }

        try {
            // 从数据库获取真实历史数据
            List<BodyRecord> history = recordController.queryRecords(userId, null, null);
            System.out.println("查询到历史记录数: " + history.size());

            if (history.isEmpty()) {
                resultArea.setText("暂无身体数据记录，请先在「数据录入」页面录入身高、体重等信息后再获取 AI 建议。");
                System.out.println("历史数据为空，提示用户先录入数据");
                return;
            }

            Result<String> result = aiController.getAdvice(history);
            System.out.println("AI 返回结果: code=" + result.getCode() + ", msg=" + result.getMsg());

            if (result != null && result.getData() != null) {
                resultArea.setText(result.getData());
            } else {
                resultArea.setText("获取建议失败：" + (result != null ? result.getMsg() : "未知错误"));
            }
        } catch (Exception e) {
            System.err.println("getAdvice 异常: " + e.getClass().getName() + " — " + e.getMessage());
            e.printStackTrace();
            resultArea.setText("获取建议异常：" + e.getMessage());
        }
    }
}