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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONObject;

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

    // P1-3：抛出 AiConfigException 而不是 RuntimeException
    private void loadConfig() throws AiConfigException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("ai-key.properties")) {
            if (in == null) {
                throw new AiConfigException("ai-key.properties 不存在");
            }
            props.load(in);
            apiUrl = props.getProperty("api.url");
            apiKey = props.getProperty("api.key");
            model = props.getProperty("api.model", "deepseek-chat");
            if (apiUrl == null || apiUrl.trim().isEmpty()) {
                throw new AiConfigException("api.url 未配置");
            }
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new AiConfigException("api.key 未配置");
            }
        } catch (AiConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new AiConfigException("加载 AI 配置失败：" + e.getMessage(), e);
        }
    }

    // P1-7：使用 BmiCalculator 和 BodyFatCalculator
    public AiRequest buildRequest(BodyRecord record, List<BodyRecord> history) {
        AiRequest req = new AiRequest();
        req.setSystemPrompt("你是一位严谨的中文健康顾问，请按『饮食』『运动』『健康』三段给出建议。");

        // P1-2：补全 bmi、bmiGrade、bodyFat、measureTime
        AiRequest.UserMetrics metrics = new AiRequest.UserMetrics();
        double bmi = BmiCalculator.calcBmi(record.getHeight(), record.getWeight());
        String bmiGrade = BmiCalculator.classify(bmi);
        double bodyFat = BodyFatCalculator.predictBodyFat(bmi, record.getAge(), record.getGender());

        metrics.setHeight(record.getHeight());
        metrics.setWeight(record.getWeight());
        metrics.setAge(record.getAge());
        metrics.setGender(record.getGender());
        metrics.setBmi(bmi);
        metrics.setBmiGrade(bmiGrade);
        metrics.setBodyFat(bodyFat);
        metrics.setMeasureTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        req.setUserMetrics(metrics);

        // P1-1：历史趋势取最新 10 条（不是最旧）
        AiRequest.HistoryTrend trend = new AiRequest.HistoryTrend();
        if (history != null && !history.isEmpty()) {
            // 取最新10条
            List<AiRequest.HistoryTrend.Point> points = new ArrayList<>();
            int size = history.size();
            int start = Math.max(0, size - 10);
            for (int i = start; i < size; i++) {
                BodyRecord r = history.get(i);
                AiRequest.HistoryTrend.Point p = new AiRequest.HistoryTrend.Point();
                p.setBmi(BmiCalculator.calcBmi(r.getHeight(), r.getWeight()));
                p.setWeight(r.getWeight());
                p.setBodyFat(BodyFatCalculator.predictBodyFat(p.getBmi(), r.getAge(), r.getGender()));
                p.setMeasureTime(""); // 简化
                points.add(p);
            }
            trend.setCount(points.size());
            // 计算方向（简化）
            if (points.size() >= 2) {
                double first = points.get(0).getBmi();
                double last = points.get(points.size() - 1).getBmi();
                if (last < first) trend.setDirection("下降");
                else if (last > first) trend.setDirection("上升");
                else trend.setDirection("平稳");
            } else {
                trend.setDirection("无数据");
            }
            trend.setPoints(points);
        } else {
            trend.setCount(0);
            trend.setDirection("无数据");
        }
        req.setHistoryTrend(trend);

        AiRequest.ModelParams params = new AiRequest.ModelParams();
        params.setModel(model);
        params.setTemperature(0.7);
        params.setMaxTokens(500);
        req.setModelParams(params);

        return req;
    }

    public String requestAdvice(AiRequest req) {
        try {
            AiHealthResult result = send(req);
            if (result.isSuccess()) {
                return result.getAdviceText();
            } else {
                return result.getMessage();
            }
        } catch (AiConfigException e) {
            return "AI 服务未配置，请联系管理员";
        }
    }

    private AiHealthResult send(AiRequest req) throws AiConfigException {
        if (req == null || !req.isValid()) {
            return AiHealthResult.fail(AiHealthResult.INVALID_PARAM,
                    "当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入");
        }

        if (apiUrl == null || apiKey == null) {
            throw new AiConfigException("AI 服务未配置");
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
                if (!result.isSuccess() || !result.hasThreeSections()) {
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
        sb.append("BMI:").append(metrics.getBmi()).append("(").append(metrics.getBmiGrade()).append(")，");
        sb.append("体脂率:").append(metrics.getBodyFat()).append("%，");
        sb.append("身高:").append(metrics.getHeight()).append("cm，");
        sb.append("体重:").append(metrics.getWeight()).append("kg，");
        sb.append("年龄:").append(metrics.getAge()).append("岁，");
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

    // P1-5：解析 usage
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
        AiHealthResult result = AiHealthResult.success(content);
        // 解析 usage
        try {
            JSONObject root = new JSONObject(jsonBody);
            if (root.has("usage")) {
                result.parseUsage(root.getJSONObject("usage"));
            }
            if (root.has("finish_reason")) {
                result.setFinishReason(root.getString("finish_reason"));
            }
        } catch (Exception e) {
            // usage 解析失败不影响主流程
        }
        return result;
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
