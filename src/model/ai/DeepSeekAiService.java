package model.ai;

import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DeepSeekAiService implements AiService {
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private final String apiKey;
    private final HttpClient client;

    public DeepSeekAiService(String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String getHealthAdvice(BodyRecord record) {
        if (record == null) {
            return "错误：身体数据为空";
        }

        String cacheKey = record.toString();
        if (AiCacheUtil.contains(cacheKey)) {
            return AiCacheUtil.get(cacheKey);
        }

        try {
            // 构建请求 JSON
            JSONObject json = new JSONObject();
            json.put("model", "deepseek-chat");
            json.put("messages", new org.json.JSONArray()
                    .put(new JSONObject().put("role", "system").put("content", "你是健康顾问。"))
                    .put(new JSONObject().put("role", "user").put("content",
                            "身高" + record.getHeight() + "cm，体重" + record.getWeight() + "kg，年龄" + record.getAge() + "岁，心率" + record.getHeartRate() + "，血压" + record.getSystolicBP() + "/" + record.getDiastolicBP() + "。请给健康建议。"))
            );
            json.put("max_tokens", 500);
            json.put("temperature", 0.7);

            // 创建 HTTP 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            // 发送请求
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "错误：服务器返回 " + response.statusCode();
            }

            // 解析响应
            JSONObject result = new JSONObject(response.body());
            String advice = result.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            // 缓存结果（10分钟）
            AiCacheUtil.put(cacheKey, advice);
            return advice;

        } catch (java.net.ConnectException e) {
            return "网络异常：无法连接到 DeepSeek 服务，请检查网络。";
        } catch (java.net.SocketTimeoutException e) {
            return "超时异常：请求超时，请稍后重试。";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "请求被中断。";
        } catch (Exception e) {
            return "系统异常：" + e.getMessage();
        }
    }
}