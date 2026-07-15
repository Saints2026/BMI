package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.controller.ChartController;
import com.bmi.controller.PhotoController;
import com.bmi.controller.RecordController;
import com.bmi.controller.ReportController;
import com.bmi.controller.SettingController;
import com.bmi.controller.UserController;
import com.bmi.exception.AiConfigException;
import com.bmi.i18n.I18n;
import com.bmi.model.User;
import com.bmi.model.db.InMemoryUserDao;
import com.bmi.model.db.JdbcRecordDao;
import com.bmi.model.db.RecordDao;
import com.bmi.model.db.UserDao;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * BMI 桌面应用启动器（整合 LoginView 与 MainView 的导航衔接）。
 * 构建全部控制器并装配 DAO（UserDao=InMemory，RecordDao=JdbcRecordDao），
 * 登录成功后切换到 MainView，退出登录返回 LoginView。
 *
 * 运行需 JavaFX SDK（javafx.controls / javafx.graphics）位于模块路径。
 */
public class BmiApplication extends Application {

    private final UserDao userDao = new InMemoryUserDao();
    private final RecordDao recordDao = new JdbcRecordDao();
    private final UserController userController = new UserController(userDao);
    private final RecordController recordController = new RecordController(recordDao);
    private final ChartController chartController = new ChartController(recordDao);
    private final AiController aiController = createAiController();
    private final PhotoController photoController = new PhotoController(recordDao);
    private final ReportController reportController =
            new ReportController(recordController, chartController, aiController);
    private final SettingController settingController = new SettingController();

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle(I18n.t("app.title"));
        stage.setScene(new Scene(new LoginView(userController, this::onLogin), 920, 620));
        stage.show();
    }

    private void onLogin(User user) {
        MainView main = new MainView(user, userController, recordController, chartController,
                aiController, photoController, reportController, settingController, u -> backToLogin());
        stage.setScene(new Scene(main, 1024, 700));
    }

    private void backToLogin() {
        stage.setScene(new Scene(new LoginView(userController, this::onLogin), 920, 620));
    }

    /**
     * 从 ai-key.properties 读取 api.key 和 api.url，构造 AiController。
     * 配置文件缺失或密钥为空时返回 null（AI 功能不可用，不阻断主流程）。
     */
    private static AiController createAiController() {
        Properties props = new Properties();
        // classpath 优先
        try (InputStream is = BmiApplication.class.getClassLoader().getResourceAsStream("ai-key.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException ignored) {
            // 继续尝试文件系统
        }
        // 回退工作目录
        if (props.isEmpty()) {
            try (InputStream is = new FileInputStream("ai-key.properties")) {
                props.load(is);
            } catch (IOException e) {
                System.err.println("未找到 ai-key.properties，AI 功能将不可用");
                return null;
            }
        }
        String apiKey = props.getProperty("api.key", "");
        String apiUrl = props.getProperty("api.url", "https://api.deepseek.com/v1/chat/completions");
        try {
            return new AiController(apiKey, apiUrl);
        } catch (AiConfigException e) {
            System.err.println("AI 配置异常: " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
