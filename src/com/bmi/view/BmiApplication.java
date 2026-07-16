package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.controller.ChartController;
import com.bmi.controller.PhotoController;
import com.bmi.controller.RecordController;
import com.bmi.controller.ReportController;
import com.bmi.controller.SettingController;
import com.bmi.controller.UserController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.I18n;
import com.bmi.view.util.I18nUtil;
import javafx.scene.control.Alert;
import com.bmi.model.User;
import com.bmi.view.util.MockUserDao;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.ToastBar;
import com.bmi.view.util.UserSession;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * BMI 桌面应用启动器（重写启动链路：注册 → 登录 → 体质录入 → 主界面）。
 *
 * <p>构建全部控制器并装配（UserDao=InMemory，RecordDao=JdbcRecordDao）；
 * 启动时强制初始化默认主题清爽蓝 FRESH_BLUE（页面底色纯白），语言沿用持久化偏好；
 * 首屏为 RegisterView，经 {@link PageNavigator} 全局路由在三页间切换。
 * 退出登录回到 LoginView。
 *
 * <p>运行需 JavaFX SDK（javafx.controls / javafx.graphics）位于模块路径；
 * 全局样式表 styles.css 由 PageNavigator 注入每个 Scene 并应用当前主题。
 */
public class BmiApplication extends Application implements PageNavigator.NavigationHost {

    // Mock 模式：使用 MockRecordDao（内存），完全跳过 JdbcUtil / JdbcRecordDao / db-config.properties；
    // 真实 DB 模式：使用 JdbcRecordDao（按需懒加载 JdbcUtil，配置缺失时已非致命降级）。
    private final com.bmi.model.db.RecordDao recordDao =
            AppConfig.getInstance().isMockDaoEnabled()
                    ? new com.bmi.view.util.MockRecordDao()
                    : new com.bmi.model.db.JdbcRecordDao();
    // Mock 模式开关开启时（app-config.properties 的 mock.dao.enabled=true）使用 MockUserDao
    // 脱离后端自测；否则沿用原 InMemoryUserDao。两者均实现 UserDao，不改动后端业务文件。
    private final UserController userController = new UserController(
            AppConfig.getInstance().isMockDaoEnabled()
                    ? new MockUserDao()
                    : new com.bmi.model.db.InMemoryUserDao());
    private final SettingController settingController = new SettingController();

    // 以下重控制器延迟到进入主界面（buildMain）时才构造，避免启动期不必要的后端耦合与阻塞
    private RecordController recordController;
    private ChartController chartController;
    private AiController aiController;
    private PhotoController photoController;
    private ReportController reportController;

    @Override
    public void start(Stage stage) {
        System.out.println("[BMI] BmiApplication.start: entering start(), FX thread="
                + javafx.application.Platform.isFxApplicationThread());
        // 启动即加载持久化语言/主题（缺省中文 ZH / 清爽蓝 fresh），重启保留上次选择；
        // 不再强制覆盖，解决「下拉切换被单向写回、重启丢失」问题。
        AppConfig.getInstance().loadConfig();
        System.out.println("[BMI] lang/theme initialized: lang="
                + AppConfig.getInstance().getLang() + " theme=" + AppConfig.getInstance().getTheme());

        stage.setTitle(I18n.t("app.title"));
        PageNavigator.init(stage, this, 1000, 700);
        System.out.println("[BMI] first screen -> RegisterView (PageNavigator will call stage.show())");
        PageNavigator.toRegister();
        // 真实 DB 模式：启动期校验 db-config.properties 是否存在；缺失则友好提示，不抛致命异常
        if (!AppConfig.getInstance().isMockDaoEnabled()
                && !com.bmi.model.db.JdbcUtil.isConfigured()) {
            Alert warn = new Alert(Alert.AlertType.WARNING);
            warn.setTitle(I18nUtil.t("app.title"));
            warn.setHeaderText(I18nUtil.t("db.configMissing.title"));
            warn.setContentText(I18nUtil.t("db.configMissing"));
            warn.showAndWait();
        }
        System.out.println("[BMI] start() returning, stage.isShowing=" + stage.isShowing());
    }

    /** 进入主界面时才装配重控制器（延迟构造，减少启动开销）。 */
    private void ensureMainControllers() {
        if (recordController == null) {
            recordController = new RecordController(recordDao);
            chartController = new ChartController(recordDao);
            aiController = new AiController(recordDao);
            photoController = new PhotoController(recordDao);
            reportController = new ReportController(recordController, chartController, aiController);
            // 与 UserSession 共享同一 RecordController，确保登录前置录入的持久化与主界面同源
            UserSession.getInstance().setRecordController(recordController);
            System.out.println("[BMI] main controllers lazily wired (Record/Chart/AI/Photo/Report)");
        }
    }

    // ---------------- NavigationHost：各页面工厂 ----------------

    @Override
    public Parent buildRegister() {
        return new RegisterView(userController);
    }

    @Override
    public Parent buildLogin() {
        return new LoginView(userController, settingController);
    }

    @Override
    @Deprecated
    public Parent buildUserInfoInput(User user) {
        return new UserInfoInputView(user);
    }

    @Override
    public Parent buildInput(User user) {
        ensureMainControllers();
        ToastBar toast = new ToastBar();
        InputView input = new InputView(user, recordController, chartController, toast);
        StackPane root = new StackPane(input, toast);
        return root;
    }

    @Override
    public Parent buildAiAnalysis(User user) {
        ensureMainControllers();
        return new AiAnalysisView(user, aiController, chartController, new ToastBar());
    }

    @Override
    public Parent buildPhoto(User user) {
        ensureMainControllers();
        return new PhotoView(user, photoController, recordController, new ToastBar());
    }

    @Override
    public Parent buildReport(User user) {
        ensureMainControllers();
        return new ReportView(user.getId(), reportController, recordController, new ToastBar());
    }

    @Override
    public Parent buildChart(User user) {
        ensureMainControllers();
        return new ChartView(user.getId(), chartController);
    }

    @Override
    public Parent buildSettings(User user) {
        ensureMainControllers();
        return new SettingsView(user.getId(), settingController, new ToastBar(),
                () -> PageNavigator.toPhoto(user));
    }

    @Override
    public Parent buildMain(User user) {
        ensureMainControllers();
        return new MainView(user, userController, recordController, chartController,
                aiController, photoController, reportController, settingController,
                u -> {
                    UserSession.getInstance().clear();
                    PageNavigator.toLogin();
                });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
