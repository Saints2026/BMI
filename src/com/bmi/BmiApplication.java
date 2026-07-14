package com.bmi;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * BMI 系统 JavaFX 入口（启动烟测用）。
 *
 * 仅负责拉起主窗口，验证 JavaFX 运行时可正常启动（不触发 DB / AI 调用，避免烟测期异常）。
 * 完整视图（LoginView / InputView / ChartView）按 CODEBUDDY.md 视图层约定另行实现。
 */
public class BmiApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("BMI 体质评估与预测系统");
        Label label = new Label("BMI 系统已启动（AI 模块重构完成，编译通过）");
        Scene scene = new Scene(new StackPane(label), 420, 180);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
