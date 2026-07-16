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
import com.bmi.model.db.InMemoryUserDao;
import com.bmi.model.db.JdbcRecordDao;
import com.bmi.model.db.RecordDao;
import com.bmi.model.db.UserDao;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class BmiApplication extends Application {

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle(I18n.t("app.title"));

        UserDao userDao = new InMemoryUserDao();
        RecordDao recordDao = new JdbcRecordDao();
        UserController userController = new UserController(userDao);
        RecordController recordController = new RecordController(recordDao);
        ChartController chartController = new ChartController(recordDao);

        // ========== 硬编码 API Key（测试用） ==========
        String apiKey = "sk-ed48946785d249e3b8349812416ef30e";
        String apiUrl = "https://api.deepseek.com/v1/chat/completions";
        // ==========================================

        AiController aiController;
        System.out.println("开始构造 AiController... apiKey=" + apiKey + ", apiUrl=" + apiUrl);
        try {
            aiController = new AiController(apiKey, apiUrl);
            System.out.println("AiController 构造成功！");
        } catch (Exception e) {
            System.err.println("AiController 构造失败！");
            e.printStackTrace();
            aiController = null;
        }

        PhotoController photoController = new PhotoController(recordDao);
        ReportController reportController = new ReportController(recordController, chartController, aiController);
        SettingController settingController = new SettingController();

        User fake = new User();
        fake.setId(1L);
        fake.setUsername("test");

        MainView main = new MainView(fake, userController, recordController, chartController,
                aiController, photoController, reportController, settingController, u -> {
                    System.exit(0);
                });

        stage.setScene(new Scene(main, 1024, 700));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}