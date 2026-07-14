package com.bmi.model.ai;

public class TestAiService {
    public static void main(String[] args) {
        AiHealthClient client = new AiHealthClient();
        BodyRecord record = new BodyRecord(175, 70, 25, 1);
        AiRequest req = client.buildRequest(record, "无历史数据");
        String advice = client.requestAdvice(req);
        System.out.println("健康建议：");
        System.out.println(advice);
    }
}