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
import com.bmi.view.util.MockUserDao;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.UserSession;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

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

    private final com.bmi.model.db.RecordDao recordDao = new com.bmi.model.db.JdbcRecordDao();
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
        System.out.println("[BMI] start() returning, stage.isShowing=" + stage.isShowing());
    }

    /** 进入主界面时才装配重控制器（延迟构造，减少启动开销）。 */
    private void ensureMainControllers() {
        if (recordController == null) {
            recordController = new RecordController(recordDao);
            chartController = new ChartController(recordDao);
            // AI 控制器：从 ai-key.properties 读取密钥（禁止硬编码）
            System.out.println("[BMI] 开始构造 AiController...");
            try {
                Properties aiProps = new Properties();
                try (InputStream is = AiController.class.getClassLoader()
                        .getResourceAsStream("ai-key.properties")) {
                    if (is != null) {
                        aiProps.load(is);
                    } else {
                        try (InputStream fis = new FileInputStream("ai-key.properties")) {
                            aiProps.load(fis);
                        }
                    }
                }
                String apiKey = aiProps.getProperty("api.key", "");
                String apiUrl = aiProps.getProperty("api.url",
                        "https://api.deepseek.com/v1/chat/completions");
                System.out.println("[BMI] apiKey=" + apiKey + ", apiUrl=" + apiUrl);
                aiController = new AiController(apiKey, apiUrl);
                System.out.println("[BMI] AiController 构造成功！");
            } catch (Exception e) {
                System.err.println("[BMI] AiController 构造失败！");
                e.printStackTrace();
                aiController = null;
            }
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
