package com.bmi.model.ai;

import com.bmi.client.AiHealthClient;
import com.bmi.exception.AiException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * AI 服务手动测试入口（开发联调用，非 JUnit 单元测试）。
 * 从 ai-key.properties 读取密钥，构造模拟历史数据调用 AiHealthClient。
 */
public class TestAiService {
    public static void main(String[] args) {
        try {
            // 1. 从 ai-key.properties 读取配置（禁止硬编码密钥）
            Properties props = new Properties();
            try (InputStream is = new FileInputStream("ai-key.properties")) {
                props.load(is);
            } catch (IOException e) {
                System.err.println("未找到 ai-key.properties，请创建该文件并填写 api.key");
                return;
            }
            String apiKey = props.getProperty("api.key", "");
            String apiUrl = props.getProperty("api.url", "https://api.deepseek.com/v1/chat/completions");

            // 2. 构造模拟历史数据（使用 com.bmi.model.BodyRecord）
            List<com.bmi.model.BodyRecord> history = new ArrayList<>();
            history.add(makeRecord(22.5, 18.0, "2026-07-01 10:00:00"));
            history.add(makeRecord(23.1, 18.5, "2026-07-07 10:00:00"));
            history.add(makeRecord(22.8, 17.5, "2026-07-14 10:00:00"));

            // 3. 创建客户端并获取建议
            AiHealthClient client = new AiHealthClient(apiKey, apiUrl);
            AiHealthClient.AiHealthResult result = client.getHealthAdvice(history);

            // 4. 打印结果
            System.out.println("【饮食】" + result.getDietAdvice());
            System.out.println("【运动】" + result.getExerciseAdvice());
            System.out.println("【健康】" + result.getHealthAdvice());

        } catch (AiException e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 构造一条简化测试记录 */
    private static com.bmi.model.BodyRecord makeRecord(double bmi, double bodyFat, String time) {
        com.bmi.model.BodyRecord r = new com.bmi.model.BodyRecord();
        r.setBmi(bmi);
        r.setBodyFat(bodyFat);
        r.setMeasureTime(Timestamp.valueOf(time));
        return r;
    }
}
