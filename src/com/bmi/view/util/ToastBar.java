package com.bmi.view.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * 顶部滑入 Toast 提示条（对齐用户需求「全局交互组件①」）。
 *
 * 三种类型：
 *   - SUCCESS 成功绿：3 秒后自动消失
 *   - ERROR   报错红：需手动关闭（带 ✕ 按钮）
 *   - WARNING 提醒橙：4 秒后自动消失
 *
 * 以覆盖层形式挂载到根容器（setPickOnBounds(false)，仅 Toast 卡片接收事件，
 * 不阻挡下层交互）。典型用法：root.getChildren().add(toastBar) 后 toastBar.show(type, msg)。
 */
public class ToastBar extends StackPane {

    public enum Type {
        SUCCESS, ERROR, WARNING
    }

    /** 全局单例引用；由 {@link #install(Pane)} 在应用启动时挂载一次。 */
    private static ToastBar current;

    /**
     * 全局安装：在应用根容器上挂载一个 ToastBar 单例（每个 Stage 仅需一次）。
     * 之后即可用静态方法 {@link #showSuccess(String)} / {@link #showError(String)} /
     * {@link #showWarning(String)} 全局调用，无需持有实例。
     */
    public static void install(Pane root) {
        if (current == null) {
            current = new ToastBar();
        }
        current.mount(root);
    }

    /** 全局成功提示（绿，3 秒自动消失）。 */
    public static void showSuccess(String msg) {
        if (current != null) {
            current.show(Type.SUCCESS, msg);
        }
    }

    /** 全局错误提示（红，需手动关闭）。 */
    public static void showError(String msg) {
        if (current != null) {
            current.show(Type.ERROR, msg);
        }
    }

    /** 全局警告提示（橙，4 秒自动消失）。 */
    public static void showWarning(String msg) {
        if (current != null) {
            current.show(Type.WARNING, msg);
        }
    }

    /** 全局按类型提示。 */
    public static void showToast(Type type, String msg) {
        if (current != null) {
            current.show(type, msg);
        }
    }

    public ToastBar() {
        // 每次构造即成为「当前全局实例」：各视图构造自身 ToastBar 后，
        // 静态 showSuccess/showError/showWarning 即路由到该活动视图，确保 Toast 可见。
        current = this;
        setAlignment(Pos.TOP_CENTER);
        setPickOnBounds(false); // 仅子节点（Toast 卡片）响应鼠标事件
        setStyle("-fx-padding:12 12 0 12;");
    }

    /** 挂载到根容器（要求 root 为 Pane，如 StackPane / BorderPane）。 */
    public void mount(Pane root) {
        if (!root.getChildren().contains(this)) {
            root.getChildren().add(this);
        }
    }

    public void success(String msg) {
        show(Type.SUCCESS, msg);
    }

    public void error(String msg) {
        show(Type.ERROR, msg);
    }

    public void warning(String msg) {
        show(Type.WARNING, msg);
    }

    public void show(Type type, String message) {
        String cls = type == Type.SUCCESS ? "bmi-toast-success"
                : type == Type.ERROR ? "bmi-toast-error" : "bmi-toast-warning";

        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("bmi-toast", cls);

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-text-fill:white; -fx-font-size:13px;");
        card.getChildren().add(msgLabel);

        if (type == Type.ERROR) {
            Button close = new Button("✕");
            close.getStyleClass().add("bmi-toast-close");
            close.setOnAction(e -> dismiss(card));
            card.getChildren().add(close);
        }

        getChildren().add(card);

        // 滑入动画
        card.setTranslateY(-30);
        card.setOpacity(0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(280), card);
        tt.setToY(0);
        FadeTransition ft = new FadeTransition(Duration.millis(280), card);
        ft.setToValue(1);
        tt.play();
        ft.play();

        if (type != Type.ERROR) {
            PauseTransition pause = new PauseTransition(
                    Duration.seconds(type == Type.SUCCESS ? 3 : 4));
            pause.setOnFinished(e -> dismiss(card));
            pause.play();
        }
    }

    private void dismiss(HBox card) {
        FadeTransition ft = new FadeTransition(Duration.millis(220), card);
        ft.setToValue(0);
        ft.setOnFinished(e -> getChildren().remove(card));
        ft.play();
    }
}
