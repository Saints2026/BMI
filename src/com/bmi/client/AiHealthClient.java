package com.bmi.client;

import com.bmi.exception.AiConfigException;
import com.bmi.exception.AiException;
import com.bmi.model.ai.BodyRecord;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.net.URI;

public class AiHealthClient {
    private final String apiKey;
    private final String apiUrl;
    private static final int TIMEOUT = 10000;

    public AiHealthClient(String apiKey, String apiUrl) throws AiConfigException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AiConfigException("API Key 不能为空");
        }
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new AiConfigException("API URL 不能为空");
        }
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    public AiHealthResult getHealthAdvice(List<BodyRecord> historyTrend) throws AiException {
        String requestBody = buildJsonBody(historyTrend);
        String response = doPost(requestBody);
        return parseResponse(response);
    }

    private String buildJsonBody(List<BodyRecord> historyTrend) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"historyTrend\":[");
        if (historyTrend != null && !historyTrend.isEmpty()) {
            for (int i = 0; i < historyTrend.size(); i++) {
                BodyRecord r = historyTrend.get(i);
                sb.append("{");
                sb.append("\"bmi\":").append(r.getBmi()).append(",");
                sb.append("\"bodyFat\":").append(r.getBodyFat()).append(",");
                sb.append("\"measureDate\":\"").append(r.getMeasureDate()).append("\"");
                sb.append("}");
                if (i < historyTrend.size() - 1) sb.append(",");
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String doPost(String json) throws AiException {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(apiUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String response = readStream(is);
            if (code != 200) {
                System.err.println("AI接口返回错误: code=" + code + ", msg=" + response);
                throw new AiException("AI接口返回错误，code=" + code + ", msg=" + response);
            }
            return response;
        } catch (IOException e) {
            System.err.println("AI接口调用异常: " + e.getMessage());
            throw new AiException("AI接口调用失败", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private AiHealthResult parseResponse(String json) throws AiException {
        String diet = extractSection(json, "【饮食】");
        String exercise = extractSection(json, "【运动】");
        String health = extractSection(json, "【健康】");
        if (diet == null || exercise == null || health == null) {
            System.err.println("AI返回格式异常，未找到【饮食】【运动】【健康】三段");
            return new AiHealthResult(
                "建议均衡饮食，多吃蔬果。",
                "建议每天步行30分钟。",
                "建议保持良好作息，定期体检。"
            );
        }
        return new AiHealthResult(diet, exercise, health);
    }

    private String extractSection(String json, String tag) {
        int start = json.indexOf(tag);
        if (start == -1) return null;
        int end = json.indexOf("【", start + tag.length());
        if (end == -1) end = json.length();
        String content = json.substring(start + tag.length(), end).trim();
        if (content.startsWith(":") || content.startsWith("：")) {
            content = content.substring(1).trim();
        }
        if (content.startsWith("\"") && content.endsWith("\"")) {
            content = content.substring(1, content.length() - 1);
        }
        return content;
    }

    public static class AiHealthResult {
        private String dietAdvice;
        private String exerciseAdvice;
        private String healthAdvice;

        public AiHealthResult(String diet, String exercise, String health) {
            this.dietAdvice = diet;
            this.exerciseAdvice = exercise;
            this.healthAdvice = health;
        }

        public String getDietAdvice() { return dietAdvice; }
        public String getExerciseAdvice() { return exerciseAdvice; }
        public String getHealthAdvice() { return healthAdvice; }
    }
}