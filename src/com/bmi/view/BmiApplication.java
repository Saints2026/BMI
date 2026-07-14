package com.bmi.view;

import com.bmi.controller.AiController;
import com.bmi.controller.ChartController;
import com.bmi.controller.PhotoController;
import com.bmi.controller.RecordController;
import com.bmi.controller.ReportController;
import com.bmi.controller.SettingController;
import com.bmi.controller.UserController;
import com.bmi.i18n.I18n;
import com.bmi.model.User;
import com.bmi.model.ai.AiService;
import com.bmi.model.db.InMemoryUserDao;
import com.bmi.model.db.JdbcRecordDao;
import com.bmi.model.db.RecordDao;
import com.bmi.model.db.UserDao;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
    private final AiService aiService = new AiService();
    private final AiController aiController = new AiController(recordDao, userDao, aiService);
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

    public static void main(String[] args) {
        launch(args);
    }
}
