package com.bmi.model.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.Properties;
import java.io.InputStream;

public class AiHealthClient {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_RETRY = 1;

    private String apiUrl;
    private String apiKey;
    private String model;

    public AiHealthClient() {
        loadConfig();
    }

    private void loadConfig() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("ai-key.properties")) {
            if (in == null) {
                throw new RuntimeException("ai-key.properties 不存在");
            }
            props.load(in);
            apiUrl = props.getProperty("api.url");
            apiKey = props.getProperty("api.key");
            model = props.getProperty("api.model", "deepseek-chat");
            if (apiUrl == null || apiUrl.trim().isEmpty()) {
                throw new RuntimeException("api.url 未配置");
            }
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new RuntimeException("api.key 未配置");
            }
        } catch (Exception e) {
            throw new RuntimeException("加载 AI 配置失败：" + e.getMessage(), e);
        }
    }

    // 构造 AiRequest（使用 setter）
    public AiRequest buildRequest(BodyRecord record, String historySummary) {
        AiRequest req = new AiRequest();

        // 设置 systemPrompt
        req.setSystemPrompt("你是一位严谨的中文健康顾问，请按『饮食』『运动』『健康』三段给出建议。");

        // 构建 UserMetrics
        AiRequest.UserMetrics metrics = new AiRequest.UserMetrics();
        // 注意：这里需要计算 BMI 和体脂率，但暂时简化，只传基本数据
        metrics.setHeight(record.getHeight());
        metrics.setWeight(record.getWeight());
        metrics.setAge(record.getAge());
        metrics.setGender(record.getGender());
        // 简化：不传 BMI/体脂，让 AI 自己算
        metrics.setBmi(0); // 占位
        metrics.setBmiGrade(""); // 占位
        metrics.setBodyFat(0); // 占位
        metrics.setMeasureTime(java.time.LocalDateTime.now().toString());
        req.setUserMetrics(metrics);

        // 构建 ModelParams
        AiRequest.ModelParams params = new AiRequest.ModelParams();
        params.setModel(model);
        params.setTemperature(0.7);
        params.setMaxTokens(500);
        req.setModelParams(params);

        // 历史趋势（简化）
        AiRequest.HistoryTrend trend = new AiRequest.HistoryTrend();
        trend.setCount(0);
        trend.setDirection("无数据");
        req.setHistoryTrend(trend);

        return req;
    }

    // 对外主入口
    public String requestAdvice(AiRequest req) {
        AiHealthResult result = send(req);
        if (result.isSuccess()) {
            return result.getAdviceText();
        } else {
            return result.getMessage();
        }
    }

    private AiHealthResult send(AiRequest req) {
        if (req == null || !req.isValid()) {
            return AiHealthResult.fail(AiHealthResult.INVALID_PARAM,
                    "当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入");
        }

        if (apiUrl == null || apiKey == null) {
            return AiHealthResult.fail(AiHealthResult.CONFIG_ERROR,
                    "AI 服务未配置，请联系管理员");
        }

        int attempt = 0;
        while (attempt <= MAX_RETRY) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                String jsonBody = buildJsonBody(req);
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes("UTF-8"));
                    os.flush();
                }

                int statusCode = conn.getResponseCode();
                if (statusCode >= 500) {
                    if (attempt <= MAX_RETRY) continue;
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }
                if (statusCode >= 400) {
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }

                String responseBody = readResponseBody(conn);
                AiHealthResult result = parseResponse(responseBody);
                if (!result.isSuccess()) {
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }
                return result;

            } catch (SocketTimeoutException e) {
                if (attempt <= MAX_RETRY) continue;
                return AiHealthResult.fail(AiHealthResult.TIMEOUT,
                        "AI 建议请求超时，请稍后重试");
            } catch (ConnectException | UnknownHostException e) {
                return AiHealthResult.fail(AiHealthResult.NETWORK_ERROR,
                        "暂时无法获取 AI 建议，请检查网络或稍后再试");
            } catch (Exception e) {
                return AiHealthResult.fail(AiHealthResult.NETWORK_ERROR,
                        "暂时无法获取 AI 建议，请检查网络或稍后再试");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                "AI 服务暂时不可用，请稍后再试");
    }

    private String buildJsonBody(AiRequest req) {
        AiRequest.UserMetrics metrics = req.getUserMetrics();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(model).append("\",");
        sb.append("\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(req.getSystemPrompt())).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":\"");
        sb.append("身高").append(metrics.getHeight()).append("cm，");
        sb.append("体重").append(metrics.getWeight()).append("kg，");
        sb.append("年龄").append(metrics.getAge()).append("岁，");
        sb.append(metrics.getGender() == 1 ? "男" : "女");
        sb.append("。请给出健康建议。");
        sb.append("\"}");
        sb.append("],");
        sb.append("\"max_tokens\":").append(req.getModelParams().getMaxTokens()).append(",");
        sb.append("\"temperature\":").append(req.getModelParams().getTemperature());
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String readResponseBody(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private AiHealthResult parseResponse(String jsonBody) {
        if (jsonBody == null || jsonBody.trim().isEmpty()) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
        }
        String content = extractContent(jsonBody);
        if (content == null || content.trim().isEmpty()) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
        }
        if (!content.contains("饮食") && !content.contains("运动") && !content.contains("健康")) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
        }
        return AiHealthResult.success(content);
    }

    private String extractContent(String json) {
        String key = "\"content\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            key = "\"content\": \"";
            start = json.indexOf(key);
            if (start < 0) return null;
        }
        start += key.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') {
                end += 2;
                continue;
            }
            if (c == '"') break;
            end++;
        }
        if (end >= json.length()) return null;
        return json.substring(start, end).replace("\\n", "\n")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
