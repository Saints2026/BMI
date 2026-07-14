package model.ai;

public class TestAiService {
    public static void main(String[] args) {
        // 替换成你的真实 DeepSeek API Key
        String apiKey = "sk-9e243736c6294347bf46dcf7d17d2ec6";
        DeepSeekAiService service = new DeepSeekAiService(apiKey);
        BodyRecord record = new BodyRecord(175, 70, 25, 72, 120, 80);
        String advice = service.getHealthAdvice(record);
        System.out.println("健康建议：");
        System.out.println(advice);
    }
}