package com.bmi.view.util;

import com.bmi.i18n.AppConfig;
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 全局页面路由工具（线性跳转链路，删除 MainView 侧边栏）。
 *
 * <p>集中管理 Scene 切换，所有页面统一经本类跳转，避免各视图直接持有 Stage。
 * 每次切换都会注入全局样式表 styles.css 并应用当前主题（默认清爽蓝 FRESH_BLUE）。
 *
 * <p>页面实例由 {@link NavigationHost} 工厂构建（由 BmiApplication 实现，
 * 因为控制器仅在启动器处装配）。本类只负责「拿到 Parent → 套样式 → 换 Scene」。
 *
 * <p>主线 8 跳：Login ↔ Register → UserInfoInput → Input ↔ History ↔ AiAnalysis；
 * 分支 3 跳：Input → Photo / Report / Settings（返回均至 Input）。
 */
public final class PageNavigator {

    /** 页面工厂：由启动器实现，装配控制器后产出各视图 Parent。 */
    public interface NavigationHost {
        Parent buildRegister();
        Parent buildLogin();
        Parent buildUserInfoInput(User user);
        Parent buildInput(User user);
        Parent buildInputEdit(User user, BodyRecord record);
        Parent buildHistory(User user);
        Parent buildAiAnalysis(User user);
        Parent buildPhoto(User user);
        Parent buildReport(User user);
        Parent buildSettings(User user);
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

    /** 跳转：登录页（启动首屏）。 */
    public static void toLogin() {
        System.out.println("[BMI] navigate -> LoginView");
        setScene(host.buildLogin());
    }

    /** 跳转：体质录入页（携带已登录用户）。 */
    public static void toUserInfoInput(User user) {
        System.out.println("[BMI] navigate -> UserInfoInputView");
        setScene(host.buildUserInfoInput(user));
    }

    /** 跳转：数据录入页（新增记录）。 */
    public static void toInput(User user) {
        System.out.println("[BMI] navigate -> InputView");
        setScene(host.buildInput(user));
    }

    /** 跳转：数据录入页（编辑既有记录）。 */
    public static void toInputEdit(User user, BodyRecord record) {
        System.out.println("[BMI] navigate -> InputView(edit)");
        setScene(host.buildInputEdit(user, record));
    }

    /** 跳转：历史记录页。 */
    public static void toHistory(User user) {
        System.out.println("[BMI] navigate -> HistoryView");
        setScene(host.buildHistory(user));
    }

    /** 跳转：AI 分析页。 */
    public static void toAiAnalysis(User user) {
        System.out.println("[BMI] navigate -> AiAnalysisView");
        setScene(host.buildAiAnalysis(user));
    }

    /** 跳转：体型照片页（分支）。 */
    public static void toPhoto(User user) {
        System.out.println("[BMI] navigate -> PhotoView");
        setScene(host.buildPhoto(user));
    }

    /** 跳转：健康报告页（分支）。 */
    public static void toReport(User user) {
        System.out.println("[BMI] navigate -> ReportView");
        setScene(host.buildReport(user));
    }

    /** 跳转：系统设置页（分支）。 */
    public static void toSettings(User user) {
        System.out.println("[BMI] navigate -> SettingsView");
        setScene(host.buildSettings(user));
    }

    /**
     * 统一换 Scene：套样式 + 应用主题 + 显示主舞台。
     *
     * <p><b>关键修复</b>：每次切换都必须调用 {@code stage.show()}，
     * 否则 JavaFX 隐式退出永不触发，启动器 {@code launch()} 会无限阻塞（卡死）。
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
            System.err.println("[BMI][WARN] styles.css not found on classpath; continuing without theme stylesheet");
        }
        ThemeConstant.apply(scene, ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));
        stage.setScene(scene);
        if (!stage.isShowing()) {
            stage.show(); // 根治卡死：显示主舞台
        }
        System.out.println("[BMI] setScene done, stage.isShowing=" + stage.isShowing());
    }
}
