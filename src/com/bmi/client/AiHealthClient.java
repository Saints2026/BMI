package com.bmi.client;

import com.bmi.exception.AiConfigException;
import com.bmi.exception.AiException;
import com.bmi.i18n.I18n;
import com.bmi.model.BodyRecord;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AiHealthClient {

    private final String apiKey;
    private final String apiUrl;
    private static final int TIMEOUT = 15000; // 15秒超时

    private static final String MODEL = "deepseek-chat";
    private static final java.util.Map<String, AiHealthResult> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL = 10 * 60 * 1000; // 10分钟

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
        // 空参数检查
        if (historyTrend == null || historyTrend.isEmpty()) {
            System.out.println(I18n.t("ai.cache.miss") + " — " + I18n.t("ai.error.empty"));
            throw new AiException(I18n.t("ai.error.empty"));
        }

        // 缓存key：基于数据内容生成，确保相同数据命中缓存
        String cacheKey = buildCacheKey(historyTrend);
        AiHealthResult cached = cache.get(cacheKey);
        if (cached != null) {
            System.out.println(I18n.t("ai.cache.hit"));
            return cached;
        }
        System.out.println(I18n.t("ai.cache.miss"));

        String requestBody = buildJsonBody(historyTrend);
        String response = doPost(requestBody);
        AiHealthResult result = parseResponse(response);
        cache.put(cacheKey, result);
        return result;
    }

    /** 基于历史数据内容生成缓存 key（而非对象身份 hashCode） */
    private String buildCacheKey(List<BodyRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (BodyRecord r : records) {
            sb.append(r.getBmi()).append("|").append(r.getBodyFat()).append("|")
              .append(r.getMeasureTime() != null ? r.getMeasureTime().toString() : "null").append(";");
        }
        return sb.toString();
    }

    // 构造符合 DeepSeek / OpenAI 规范的请求体
    private String buildJsonBody(List<BodyRecord> historyTrend) {
        String systemPrompt =
            "你是一位专业的健康顾问。请根据用户的历史BMI和体脂率数据，提供三方面建议：" +
            "【饮食】建议、【运动】建议、【健康】建议。请严格按照以下格式返回：" +
            "【饮食】...内容... 【运动】...内容... 【健康】...内容... " +
            "每段内容要具体、可操作，每段建议控制在50字以内不要有额外说明。";

        StringBuilder userContent = new StringBuilder("以下是用户近期的BMI和体脂率记录（按时间顺序）：\n");
        for (BodyRecord r : historyTrend) {
            userContent.append("日期：").append(r.getMeasureTime() != null ? r.getMeasureTime() : "N/A")
                       .append("，BMI：").append(r.getBmi())
                       .append("，体脂率：").append(r.getBodyFat()).append("%\n");
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(MODEL).append("\",");
        json.append("\"messages\":[");
        json.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"},");
        json.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userContent.toString())).append("\"}");
        json.append("],");
        json.append("\"temperature\":0.7,");
        json.append("\"max_tokens\":4096");
        json.append("}");
        return json.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String doPost(String json) throws AiException {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(apiUrl).toURL();
            // 调用日志：请求URL和参数
            System.out.println("请求URL: " + apiUrl);
            System.out.println("请求参数: " + json);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
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
            String response = (is != null) ? readStream(is) : "";
            if (code != 200) {
                System.err.println("AI接口返回错误: code=" + code + ", msg=" + response);
                throw new AiException(I18n.t("ai.error.server"));
            }
            return response;
        } catch (SocketTimeoutException e) {
            System.err.println("请求超时: " + e.getMessage());
            throw new AiException(I18n.t("ai.error.timeout"), e);
        } catch (UnknownHostException e) {
            System.err.println("网络连接失败: " + e.getMessage());
            throw new AiException(I18n.t("ai.error.network"), e);
        } catch (IOException e) {
            System.err.println("AI接口调用异常: " + e.getMessage());
            throw new AiException(I18n.t("ai.error.server"), e);
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
        // 调用日志：AI返回
        System.out.println("AI返回: " + json);

        String content = extractContent(json);
        if (content == null || content.isEmpty()) {
            System.err.println("AI返回内容为空，使用默认降级建议");
            return getFallbackResult();
        }

        String diet = extractSection(content, "【饮食】");
        String exercise = extractSection(content, "【运动】");
        String health = extractSection(content, "【健康】");

        if (diet == null || exercise == null || health == null) {
            diet = extractSection(content, "饮食");
            exercise = extractSection(content, "运动");
            health = extractSection(content, "健康");
        }

        if (diet == null || exercise == null || health == null) {
            System.err.println("AI返回格式无法解析，使用默认降级建议");
            return getFallbackResult();
        }

        return new AiHealthResult(diet.trim(), exercise.trim(), health.trim());
    }

    /** 从 JSON 中提取 content 字段值，正确处理转义序列 */
    private String extractContent(String json) {
        String key = "\"content\":\"";
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                // 处理 JSON 转义序列
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(next); break;
                }
                i++;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String extractSection(String text, String tag) {
        int start = text.indexOf(tag);
        if (start == -1) return null;
        int end = text.length();
        String[] possibleNextTags = {"【饮食】", "【运动】", "【健康】", "饮食", "运动", "健康"};
        int nextTagPos = end;
        for (String t : possibleNextTags) {
            int pos = text.indexOf(t, start + tag.length());
            if (pos != -1 && pos < nextTagPos) {
                nextTagPos = pos;
            }
        }
        String section = text.substring(start + tag.length(), nextTagPos).trim();
        if (section.startsWith(":") || section.startsWith("：")) {
            section = section.substring(1).trim();
        }
        if (section.endsWith("。") || section.endsWith(".")) {
            section = section.substring(0, section.length() - 1);
        }
        return section;
    }

    private AiHealthResult getFallbackResult() {
        return new AiHealthResult(
            "建议均衡饮食，多吃蔬果，控制糖分摄入。",
            "建议每天步行30分钟，结合有氧运动。",
            "建议保持良好作息，定期体检，关注BMI变化。"
        );
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
