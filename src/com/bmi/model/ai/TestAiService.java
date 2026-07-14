package com.bmi.model.ai;

import java.util.ArrayList;

public class TestAiService {
    public static void main(String[] args) {
        AiHealthClient client = new AiHealthClient();
        // 构造参数: height, weight, age, gender, heartRate, systolicBP, diastolicBP
        BodyRecord record = new BodyRecord(175, 70, 25, 1, 72, 120, 80);
        AiRequest req = client.buildRequest(record, new ArrayList<>());
        String advice = client.requestAdvice(req);
        System.out.println("健康建议：");
        System.out.println(advice);
    }
}
