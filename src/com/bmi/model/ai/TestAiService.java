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
 * AI 服务手动测试入口（全场景联调验证）。
 * 从 ai-key.properties 读取密钥，覆盖正常/断网/空输入/服务器报错 4 类场景。
 */
public class TestAiService {
    public static void main(String[] args) {
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

        System.out.println("===== 场景1：正常调用 =====");
        testNormal(apiKey, apiUrl);

        System.out.println("\n===== 场景2：缓存命中（第二次调用相同数据） =====");
        testNormal(apiKey, apiUrl); // 相同数据，应命中缓存

        System.out.println("\n===== 场景3：空输入 =====");
        testEmptyInput(apiKey, apiUrl);

        System.out.println("\n===== 场景4：断网（模拟 UnknownHostException） =====");
        testNetworkError(apiKey);

        System.out.println("\n===== 场景5：服务器报错（错误URL） =====");
        testServerError(apiKey);
    }

    /** 场景1 & 2：正常调用 + 缓存命中验证 */
    private static void testNormal(String apiKey, String apiUrl) {
        try {
            List<com.bmi.model.BodyRecord> history = new ArrayList<>();
            history.add(makeRecord(22.5, 18.0, "2026-07-01 10:00:00"));
            history.add(makeRecord(23.1, 18.5, "2026-07-07 10:00:00"));
            history.add(makeRecord(22.8, 17.5, "2026-07-14 10:00:00"));

            AiHealthClient client = new AiHealthClient(apiKey, apiUrl);
            AiHealthClient.AiHealthResult result = client.getHealthAdvice(history);

            System.out.println("【饮食】" + result.getDietAdvice());
            System.out.println("【运动】" + result.getExerciseAdvice());
            System.out.println("【健康】" + result.getHealthAdvice());
        } catch (AiException e) {
            System.err.println("正常调用失败: " + e.getMessage());
        }
    }

    /** 场景3：空输入 — 应返回 "数据不完整，请先完成身高体重录入" */
    private static void testEmptyInput(String apiKey, String apiUrl) {
        try {
            AiHealthClient client = new AiHealthClient(apiKey, apiUrl);
            client.getHealthAdvice(new ArrayList<>());
        } catch (AiException e) {
            System.out.println("空输入降级文案: " + e.getMessage());
        }
    }

    /** 场景4：断网 — 使用不存在的域名模拟 */
    private static void testNetworkError(String apiKey) {
        try {
            AiHealthClient client = new AiHealthClient(apiKey, "https://nonexistent.host.invalid/v1/chat/completions");
            List<com.bmi.model.BodyRecord> history = new ArrayList<>();
            history.add(makeRecord(22.5, 18.0, "2026-07-01 10:00:00"));
            client.getHealthAdvice(history);
        } catch (AiException e) {
            System.out.println("断网降级文案: " + e.getMessage());
        }
    }

    /** 场景5：服务器报错 — 使用能连通但返回错误的 URL */
    private static void testServerError(String apiKey) {
        try {
            AiHealthClient client = new AiHealthClient(apiKey, "https://httpbin.org/status/500");
            List<com.bmi.model.BodyRecord> history = new ArrayList<>();
            history.add(makeRecord(22.5, 18.0, "2026-07-01 10:00:00"));
            client.getHealthAdvice(history);
        } catch (AiException e) {
            System.out.println("服务器报错降级文案: " + e.getMessage());
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
