package com.bmi.model.ai;

public class TestAiService {
    public static void main(String[] args) {
        AiHealthClient client = new AiHealthClient();
        BodyRecord record = new BodyRecord(175, 70, 25, 1, 72, 120, 80);
        // 简化：传入空历史
        AiRequest req = client.buildRequest(record, null);
        String advice = client.requestAdvice(req);
        System.out.println("健康建议：");
        System.out.println(advice);
    }
}
