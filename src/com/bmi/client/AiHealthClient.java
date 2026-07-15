package com.bmi.client;

import com.bmi.exception.AiConfigException;
import com.bmi.exception.AiException;
import com.bmi.model.BodyRecord;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AiHealthClient {
    
    private final String apiKey;
    private final String apiUrl;
    private static final int TIMEOUT = 15000; // 15秒超时，DeepSeek 可能较慢

    // 模型名称，根据 DeepSeek 平台填写
    private static final String MODEL = "deepseek-chat";
    private static final java.util.Map<String, AiHealthResult> cache = new java.util.concurrent.ConcurrentHashMap<>();
private static final long CACHE_TTL = 10 * 60 * 1000; // 10分钟（目前只存储，未做过期清理，简单实现）
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
    // 缓存key（简单用历史数据的hashCode）
    String cacheKey = String.valueOf(historyTrend.hashCode());
    AiHealthResult cached = cache.get(cacheKey);
    if (cached != null) {
        System.out.println("命中缓存，跳过API调用");
        return cached;
    }
    String requestBody = buildJsonBody(historyTrend);
    String response = doPost(requestBody);
    AiHealthResult result = parseResponse(response);
    cache.put(cacheKey, result);
    return result;
}

    // 构造符合 DeepSeek / OpenAI 规范的请求体
    private String buildJsonBody(List<BodyRecord> historyTrend) {
        // 构造 system prompt
        String systemPrompt = 
            "你是一位专业的健康顾问。请根据用户的历史BMI和体脂率数据，提供三方面建议：" +
            "【饮食】建议、【运动】建议、【健康】建议。请严格按照以下格式返回：" +
            "【饮食】...内容... 【运动】...内容... 【健康】...内容... " +
            "每段内容要具体、可操作，不要有额外说明。";

        // 构造 user 消息：包含历史数据
        StringBuilder userContent = new StringBuilder("以下是用户近期的BMI和体脂率记录（按时间顺序）：\n");
        if (historyTrend != null && !historyTrend.isEmpty()) {
            for (BodyRecord r : historyTrend) {
                userContent.append("日期：").append(r.getMeasureTime() != null ? r.getMeasureTime() : "N/A")
                           .append("，BMI：").append(r.getBmi())
                           .append("，体脂率：").append(r.getBodyFat()).append("%\n");
            }
        } else {
            userContent.append("暂无历史数据，请提供通用健康建议。");
        }

        // 手动拼接 JSON（避免引入第三方库）
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(MODEL).append("\",");
        json.append("\"messages\":[");
        // system 消息
        json.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"},");
        // user 消息
        json.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userContent.toString())).append("\"}");
        json.append("],");
        json.append("\"temperature\":0.7,");
        json.append("\"max_tokens\":800");
        json.append("}");
        return json.toString();
    }

    // 简单的 JSON 字符串转义（处理引号和换行）
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

    // 解析 DeepSeek 返回的 JSON，提取 content 并切割三段
    private AiHealthResult parseResponse(String json) throws AiException {
        // 提取 "content": "..." 中的内容
        String content = extractContent(json);
        if (content == null || content.isEmpty()) {
            System.err.println("AI返回内容为空");
            return getFallbackResult();
        }

        // 提取三段（支持 【饮食】、【运动】、【健康】 或 可能的变体）
        String diet = extractSection(content, "【饮食】");
        String exercise = extractSection(content, "【运动】");
        String health = extractSection(content, "【健康】");

        // 如果三段不全，尝试用更宽松的匹配（如 饮食：、运动：、健康：）
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

    private String extractContent(String json) {
        // 找 "content":" 的位置
        String key = "\"content\":\"";
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        // 找结束的引号，但要处理转义
        int end = json.indexOf("\"", start);
        // 简单处理：如果遇到 \\" 之类的，要跳过，但这里简单起见，直接找第一个未转义的引号
        // 更严谨的做法是遍历，但为了简化，我们采用快速方式：找最后一个 " 前
        // 因为 content 末尾可能还有逗号，所以找 " 再向前
        // 这里简单处理：找到 start 之后第一个未转义的 "
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                // 跳过转义符，保留下一个字符
                sb.append(json.charAt(i+1));
                i++;
                continue;
            }
            if (c == '"') {
                // 结束
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String extractSection(String text, String tag) {
        int start = text.indexOf(tag);
        if (start == -1) return null;
        // 从 tag 后面开始找下一个 tag 或结尾
        int end = text.length();
        // 找下一个标签，可以是 【饮食】或饮食等，但更直接：从 start+tag.length 开始找下一个中文方括号或特殊字符
        // 简单做法：找下一个中文方括号 【 或 】
        String[] possibleNextTags = {"【饮食】", "【运动】", "【健康】", "饮食", "运动", "健康"};
        int nextTagPos = end;
        for (String t : possibleNextTags) {
            int pos = text.indexOf(t, start + tag.length());
            if (pos != -1 && pos < nextTagPos) {
                nextTagPos = pos;
            }
        }
        String section = text.substring(start + tag.length(), nextTagPos).trim();
        // 去除可能的前缀冒号
        if (section.startsWith(":") || section.startsWith("：")) {
            section = section.substring(1).trim();
        }
        // 去除多余的标点
        if (section.endsWith("。") || section.endsWith(".")) {
            section = section.substring(0, section.length()-1);
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

    // 内部结果类
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