package com.bmi.model.ai;

import com.bmi.client.AiHealthClient;
import com.bmi.exception.AiException;
import java.util.ArrayList;
import java.util.List;

public class TestAiService {
    public static void main(String[] args) {
        try {
            // 1. 构造模拟历史数据（用你真实的 BodyRecord）
            List<BodyRecord> history = new ArrayList<>();
            history.add(new BodyRecord(22.5, 18.0, "2026-07-01"));
            history.add(new BodyRecord(23.1, 18.5, "2026-07-07"));
            history.add(new BodyRecord(22.8, 17.5, "2026-07-14"));

            // 2. 创建客户端（需要从 ai-key.properties 读 Key，这里简单硬编码测试）
            // 实际使用时应该从配置文件读取
            String apiKey = "sk-ed48946785d249e3b8349812416ef30e"; // 替换成你的真实 Key
            String apiUrl = "https://api.deepseek.com/v1/chat/completions"; // 根据实际平台修改

            AiHealthClient client = new AiHealthClient(apiKey, apiUrl);

            // 3. 获取建议
            AiHealthClient.AiHealthResult result = client.getHealthAdvice(history);

            // 4. 打印结果
            System.out.println("【饮食】" + result.getDietAdvice());
            System.out.println("【运动】" + result.getExerciseAdvice());
            System.out.println("【健康】" + result.getHealthAdvice());

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}