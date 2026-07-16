package com.bmi.view.util;

import com.bmi.i18n.AppConfig;
import com.bmi.model.User;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.bmi.view.MainView;

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

        @Deprecated
        Parent buildUserInfoInput(User user);

        Parent buildInput(User user);

        Parent buildMain(User user);

        Parent buildAiAnalysis(User user);

        Parent buildPhoto(User user);

        Parent buildReport(User user);

        Parent buildChart(User user);

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

    /** 跳转：登录页。 */
    public static void toLogin() {
        System.out.println("[BMI] navigate -> LoginView");
        setScene(host.buildLogin());
    }

    /** @deprecated 冗余页面已废弃，登录后统一跳转 {@link #toInput(User)}。 */
    @Deprecated
    public static void toUserInfoInput(User user) {
        System.out.println("[BMI] navigate -> UserInfoInputView (deprecated)");
        setScene(host.buildUserInfoInput(user));
    }

    /** 跳转：标准录入页 InputView（登录后统一入口）。 */
    public static void toInput(User user) {
        System.out.println("[BMI] navigate -> InputView");
        setScene(host.buildInput(user));
    }

    /** 跳转：AI 健康分析页。 */
    public static void toAiAnalysis(User user) {
        System.out.println("[BMI] navigate -> AiAnalysisView");
        setScene(host.buildAiAnalysis(user));
    }

    /** 跳转：体型照片管理页。 */
    public static void toPhoto(User user) {
        System.out.println("[BMI] navigate -> PhotoView");
        setScene(host.buildPhoto(user));
    }

    /** 跳转：体型照片管理页（toPhotoView 为 toPhoto 的等价别名，便于语义化调用）。 */
    public static void toPhotoView(User user) {
        System.out.println("[BMI] navigate -> PhotoView");
        setScene(host.buildPhoto(user));
    }

    /** 跳转：健康报告导出页。 */
    public static void toReport(User user) {
        System.out.println("[BMI] navigate -> ReportView");
        setScene(host.buildReport(user));
    }

    /** 跳转：数据图表页。 */
    public static void toChart(User user) {
        System.out.println("[BMI] navigate -> ChartView");
        setScene(host.buildChart(user));
    }

    /** 跳转：系统设置页。 */
    public static void toSettings(User user) {
        System.out.println("[BMI] navigate -> SettingsView");
        setScene(host.buildSettings(user));
    }

    /** 跳转：AI 健康分析页（toAi 为 toAiAnalysis 的等价别名）。 */
    public static void toAi(User user) {
        System.out.println("[BMI] navigate -> AiAnalysisView");
        setScene(host.buildAiAnalysis(user));
    }

    /**
     * 跳转：系统主界面（null 安全）。
     *
     * <p>若 {@code user} 为空，主动回退到 {@link UserSession#getInstance()} 的当前用户兜底；
     * 若会话也无用户，则记告警并降级为「无参首页跳转」（仅路由跳转，不传用户数据），
     * 避免在入口处因 user 为 null 直接 {@code host.buildMain(null)} 触发 NPE 而中断跳转链。
     */
    /**
     * 跳转：系统主界面（空指针 + 宿主防护）。
     *
     * <p>空指针防护：{@code user} 为空时优先回退到 {@link UserSession#getInstance()} 的当前用户；
     * 若会话也无用户（未登录），弹出 I18n「未登录」提示并终止跳转，避免
     * {@code host.buildMain(null)} 触发 NPE 而中断跳转链。宿主 {@code host} 为 null 时同样安全终止。
     */
    public static void toMain(User user) {
        if (user == null) {
            user = UserSession.getInstance().getUser();
        }
        if (user == null) {
            System.err.println("[BMI][WARN] toMain: 未登录（user 为空），终止跳转");
            ToastBar.showError(I18nUtil.t("session.notLoggedIn"));
            return;
        }
        if (host == null) {
            System.err.println("[BMI][ERROR] toMain: host 宿主为 null，无法构建首页，终止跳转");
            return;
        }
        System.out.println("[BMI] navigate -> MainView");
        setScene(host.buildMain(user));
    }

    /** 跳转：系统主界面（无参重载，等价于 {@code toMain(UserSession 当前用户)}）。 */
    public static void toMain() {
        toMain(UserSession.getInstance().getUser());
    }

    /**
     * 双层兜底跳转（强制回首页）：优先走标准 {@link #toMain(User)}；
     * 若 {@code user} 为空或 {@link #toMain(User)} 内部终止，则复用当前 {@link MainView}
     * 实例将其 center 强制切回首页组件。用于 InputView 保存流程的 finally 兜底，
     * 保证「无论成功 / 校验失败 / 数据库报错 / 代码异常」最终都回到主界面。
     */
    public static void forceHome(User user) {
        if (user != null) {
            toMain(user);
            return;
        }
        MainView mv = MainView.getCurrent();
        if (mv != null) {
            System.out.println("[BMI] forceHome: 复用当前 MainView 实例 -> showHome()");
            mv.forceHome();
        } else {
            System.err.println("[BMI][WARN] forceHome: 无可用 MainView 实例，无法强制切回首页");
        }
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
