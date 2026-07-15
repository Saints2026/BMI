package com.bmi.controller;

import com.bmi.client.AiHealthClient;
import com.bmi.exception.AiConfigException;
import com.bmi.exception.AiException;
import com.bmi.model.BodyRecord;
import com.bmi.util.Result;
import java.util.List;

public class AiController {
    private AiHealthClient client;

    public AiController(String apiKey, String apiUrl) throws AiConfigException {
        this.client = new AiHealthClient(apiKey, apiUrl);
    }

    public Result<String> getAdvice(List<BodyRecord> history) {
        try {
            AiHealthClient.AiHealthResult result = client.getHealthAdvice(history);
            String advice = "【饮食】" + result.getDietAdvice() +
                            "\n【运动】" + result.getExerciseAdvice() +
                            "\n【健康】" + result.getHealthAdvice();
            return Result.success(advice);
        } catch (AiException e) {
            System.err.println("获取AI建议失败: " + e.getMessage());
            return Result.error("AI服务暂时不可用，请稍后重试。");
        }
    }
}