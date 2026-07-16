package com.bmi.view.util;

import com.bmi.i18n.AppConfig;
import com.bmi.model.User;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 全局页面路由工具（对齐用户需求「新增全局页面路由跳转工具 PageNavigator」）。
 *
 * <p>集中管理 Scene 切换，所有页面（注册 / 登录 / 体质录入 / 主界面）统一经本类跳转，
 * 避免各视图直接持有 Stage 造成的耦合。每次切换都会注入全局样式表 styles.css
 * 并应用当前主题（默认清爽蓝 FRESH_BLUE），确保「页面底色纯白、无灰色」约束落地。
 *
 * <p>页面实例由 {@link NavigationHost} 工厂构建（由 BmiApplication 实现，
 * 因为控制器仅在启动器处装配）。本类只负责「拿到 Parent → 套样式 → 换 Scene」。
 */
public final class PageNavigator {

    /** 页面工厂：由启动器实现，装配控制器后产出各视图 Parent。 */
    public interface NavigationHost {
        Parent buildRegister();

        Parent buildLogin();

        Parent buildUserInfoInput(User user);

        Parent buildMain(User user);
    }

    private static Stage stage;
    private static NavigationHost host;
    private static double width = 1000;
    private static double height = 700;

    private PageNavigator() {
    }

    /** 启动器在 start() 中调用一次：注入舞台、工厂与默认窗口尺寸。 */
    public static void init(Stage stage, NavigationHost host, double width, double height) {
        PageNavigator.stage = stage;
        PageNavigator.host = host;
        PageNavigator.width = width;
        PageNavigator.height = height;
        System.out.println("[BMI] PageNavigator.init: stage prepared, size=" + width + "x" + height);
    }

    /** 跳转：注册首屏。 */
    public static void toRegister() {
        System.out.println("[BMI] navigate -> RegisterView");
        setScene(host.buildRegister());
    }

    /** 跳转：登录页。 */
    public static void toLogin() {
        System.out.println("[BMI] navigate -> LoginView");
        setScene(host.buildLogin());
    }

    /** 跳转：体质录入页（携带已登录用户）。 */
    public static void toUserInfoInput(User user) {
        System.out.println("[BMI] navigate -> UserInfoInputView");
        setScene(host.buildUserInfoInput(user));
    }

    /** 跳转：系统主界面。 */
    public static void toMain(User user) {
        System.out.println("[BMI] navigate -> MainView");
        setScene(host.buildMain(user));
    }

    /**
     * 统一换 Scene：套样式 + 应用主题 + 显示主舞台。
     *
     * <p><b>关键修复</b>：每次切换都必须调用 {@code stage.show()}。
     * 若始终不显示任何窗口，JavaFX 的隐式退出（implicit-exit）永远不会被触发，
     * 启动器的 {@code launch()} 会无限阻塞，表现为「无 GUI、无输出、进程卡死」。
     * 这里在 {@code setScene} 之后对尚未显示的舞台调用一次 {@code show()} 即可根治。
     */
    private static void setScene(Parent root) {
        if (stage == null || host == null) {
            System.err.println("[BMI][ERROR] PageNavigator not initialized (stage/host is null)");
            return;
        }
        Scene scene = new Scene(root, width, height);
        java.net.URL css = PageNavigator.class.getResource("/com/bmi/view/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            // 资源缺失不阻断启动，仅告警（避免此前 getResource(...).toExternalForm() 空指针崩溃）
            System.err.println("[BMI][WARN] styles.css not found on classpath; continuing without theme stylesheet");
        }
        // 应用当前主题（默认 fresh → 纯白底色），覆盖所有页面根容器
        ThemeConstant.apply(scene, ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));
        stage.setScene(scene);
        if (!stage.isShowing()) {
            stage.show(); // 根治卡死：显示主舞台
        }
        System.out.println("[BMI] setScene done, stage.isShowing=" + stage.isShowing());
    }
}
