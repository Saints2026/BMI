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
import com.bmi.model.User;
import com.bmi.model.BodyRecord;
import com.bmi.view.util.MockUserDao;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.UserSession;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * BMI 桌面应用启动器（线性跳转链路：Login → Register / UserInfoInput → Input ↔ History ↔ AiAnalysis）。
 *
 * <p>构建全部控制器并装配（UserDao=InMemory 或 Mock，RecordDao=JdbcRecordDao）；
 * 启动时加载持久化语言/主题（缺省中文 ZH / 清爽蓝 fresh）；
 * 首屏为 LoginView，经 {@link PageNavigator} 全局路由在各页间切换。退出登录回到 LoginView。
 *
 * <p>运行需 JavaFX SDK（javafx.controls / javafx.graphics）位于模块路径；
 * 全局样式表 styles.css 由 PageNavigator 注入每个 Scene 并应用当前主题。
 */
public class BmiApplication extends Application implements PageNavigator.NavigationHost {

    private final com.bmi.model.db.RecordDao recordDao = new com.bmi.model.db.JdbcRecordDao();
    // Mock 模式开关开启时（app-config.properties 的 mock.dao.enabled=true）使用 MockUserDao
    // 脱离后端自测；否则沿用原 InMemoryUserDao。两者均实现 UserDao，不改动后端业务文件。
    private final UserController userController = new UserController(
            AppConfig.getInstance().isMockDaoEnabled()
                    ? new MockUserDao()
                    : new com.bmi.model.db.InMemoryUserDao());
    private final SettingController settingController = new SettingController();

    // 以下重控制器延迟到进入主业务（buildInput/History/Ai/Photo/Report）时才构造
    private RecordController recordController;
    private ChartController chartController;
    private AiController aiController;
    private PhotoController photoController;
    private ReportController reportController;

    @Override
    public void start(Stage stage) {
        System.out.println("[BMI] BmiApplication.start: entering start(), FX thread="
                + javafx.application.Platform.isFxApplicationThread());
        AppConfig.getInstance().loadConfig();
        System.out.println("[BMI] lang/theme initialized: lang="
                + AppConfig.getInstance().getLang() + " theme=" + AppConfig.getInstance().getTheme());

        stage.setTitle(I18n.t("app.title"));
        PageNavigator.init(stage, this, 1000, 700);
        System.out.println("[BMI] first screen -> LoginView (PageNavigator will call stage.show())");
        PageNavigator.toLogin();
        System.out.println("[BMI] start() returning, stage.isShowing=" + stage.isShowing());
    }

    /** 进入主业务时才装配重控制器（延迟构造，减少启动开销）。 */
    private void ensureMainControllers() {
        if (recordController == null) {
            recordController = new RecordController(recordDao);
            chartController = new ChartController(recordDao);
            aiController = new AiController(recordDao);
            photoController = new PhotoController(recordDao);
            reportController = new ReportController(recordController, chartController, aiController);
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
    public Parent buildUserInfoInput(User user) {
        return new UserInfoInputView(user);
    }

    @Override
    public Parent buildInput(User user) {
        ensureMainControllers();
        return new InputView(user, recordController, chartController, null);
    }

    @Override
    public Parent buildInputEdit(User user, BodyRecord record) {
        ensureMainControllers();
        return new InputView(user, recordController, chartController, record);
    }

    @Override
    public Parent buildHistory(User user) {
        ensureMainControllers();
        return new HistoryView(user, recordController);
    }

    @Override
    public Parent buildAiAnalysis(User user) {
        ensureMainControllers();
        return new AiAnalysisView(aiController, user);
    }

    @Override
    public Parent buildPhoto(User user) {
        ensureMainControllers();
        return new PhotoView(photoController, user);
    }

    @Override
    public Parent buildReport(User user) {
        ensureMainControllers();
        return new ReportView(reportController, user);
    }

    @Override
    public Parent buildSettings(User user) {
        return new SettingsView(user, settingController);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
