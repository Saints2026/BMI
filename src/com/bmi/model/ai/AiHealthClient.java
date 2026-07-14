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

/**
 * AI 健康建议客户端
 * 对应 docs/ai_design.md §7.1 AiHealthClient
 * 仅使用 HttpURLConnection（原生），无第三方 HTTP 库
 * 四类异常处理：
 *   ① 断网 (ConnectException / UnknownHostException)
 *   ② 超时 (SocketTimeoutException) → 可重试1次
 *   ③ 参数为空 (AiRequest.isValid() 校验)
 *   ④ 服务器报错 (HTTP 5xx / 4xx / 响应异常)
 */
public class AiHealthClient {

    // ---- 超时配置（对应 AC-07 10s） ----
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    // ---- 重试配置 ----
    private static final int MAX_RETRY = 1;

    // ---- AI 配置（从 ai-key.properties 读取） ----
    private String apiUrl;
    private String apiKey;
    private String model;

    public AiHealthClient() {
        loadConfig();
    }

    /**
     * 加载配置（对应规范“密钥安全”）
     */
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

    /**
     * 构建 AiRequest（对应规范 buildRequest）
     * @param record 身体数据
     * @param historySummary 历史趋势摘要（本项目中可传空字符串）
     */
    public AiRequest buildRequest(BodyRecord record, String historySummary) {
        String systemPrompt = "你是一位严谨的中文健康顾问，请按『饮食』『运动』『健康』三段给出建议。";
        String userContent = "最新指标：身高" + record.getHeight() + "cm，体重" + record.getWeight() +
                "kg，年龄" + record.getAge() + "岁，" + (record.getGender() == 1 ? "男" : "女") +
                "。请给出健康建议。";
        if (historySummary != null && !historySummary.isEmpty()) {
            userContent += "\n历史趋势：" + historySummary;
        }
        // 实际使用中，这里调用 AI 模型处理 userContent，但我们直接传 record 给请求
        // 这里简化为：AiRequest 中 systemPrompt + record
        return new AiRequest(systemPrompt, record, 500, 0.7);
    }

    /**
     * 对外主入口（对应规范 requestAdvice）
     * @param req AI 请求
     * @return 建议文本或降级文案
     */
    public String requestAdvice(AiRequest req) {
        AiHealthResult result = send(req);
        if (result.isSuccess()) {
            return result.getAdviceText();
        } else {
            return result.getMessage();
        }
    }

    /**
     * 内部主流程：HTTP 调用 + 四类异常处理
     */
    private AiHealthResult send(AiRequest req) {
        // ---- ③ 参数为空：入口校验，不重试 ----
        if (req == null || !req.isValid()) {
            return AiHealthResult.fail(AiHealthResult.INVALID_PARAM,
                    "当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入");
        }

        String urlStr = apiUrl;
        String key = apiKey;
        if (urlStr == null || key == null) {
            return AiHealthResult.fail(AiHealthResult.CONFIG_ERROR,
                    "AI 服务未配置，请联系管理员");
        }

        int attempt = 0;
        while (attempt <= MAX_RETRY) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                // ---- 构造请求体（手工拼接 JSON，不用第三方库） ----
                String jsonBody = buildJsonBody(req);

                // ---- 打开连接 ----
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                // ---- 发送请求 ----
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes("UTF-8"));
                    os.flush();
                }

                // ---- 读取响应 ----
                int statusCode = conn.getResponseCode();

                // ---- ④ 服务器报错：5xx 可重试 1 次 ----
                if (statusCode >= 500) {
                    if (attempt <= MAX_RETRY) {
                        continue; // 重试
                    }
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }
                // 4xx 直接降级，不重试
                if (statusCode >= 400) {
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }

                // ---- 成功读取响应体 ----
                String responseBody = readResponseBody(conn);
                AiHealthResult result = parseResponse(responseBody);
                if (!result.isSuccess()) {
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }
                return result;

            } catch (SocketTimeoutException e) {
                // ---- ② 接口超时：可重试 1 次 ----
                if (attempt <= MAX_RETRY) {
                    continue;
                }
                return AiHealthResult.fail(AiHealthResult.TIMEOUT,
                        "AI 建议请求超时，请稍后重试");

            } catch (ConnectException | UnknownHostException e) {
                // ---- ① 断网：不重试 ----
                return AiHealthResult.fail(AiHealthResult.NETWORK_ERROR,
                        "暂时无法获取 AI 建议，请检查网络或稍后再试");

            } catch (Exception e) {
                // ---- 其它异常 ----
                return AiHealthResult.fail(AiHealthResult.NETWORK_ERROR,
                        "暂时无法获取 AI 建议，请检查网络或稍后再试");

            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                "AI 服务暂时不可用，请稍后再试");
    }

    /**
     * 手工拼接 JSON 请求体（不依赖任何 JSON 库）
     */
    private String buildJsonBody(AiRequest req) {
        BodyRecord r = req.getRecord();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(model).append("\",");
        sb.append("\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(req.getSystemPrompt())).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":\"");
        sb.append("身高").append(r.getHeight()).append("cm，");
        sb.append("体重").append(r.getWeight()).append("kg，");
        sb.append("年龄").append(r.getAge()).append("岁，");
        sb.append(r.getGender() == 1 ? "男" : "女");
        sb.append("。请给出健康建议。");
        sb.append("\"}");
        sb.append("],");
        sb.append("\"max_tokens\":").append(req.getMaxTokens()).append(",");
        sb.append("\"temperature\":").append(req.getTemperature());
        sb.append("}");
        return sb.toString();
    }

    /**
     * JSON 字符串转义（防注入）
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 读取响应体
     */
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

    /**
     * 手工解析 JSON 响应（不依赖任何 JSON 库）
     * 提取 choices[0].message.content
     */
    private AiHealthResult parseResponse(String jsonBody) {
        if (jsonBody == null || jsonBody.trim().isEmpty()) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
        }
        // 提取 content
        String content = extractContent(jsonBody);
        if (content == null || content.trim().isEmpty()) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
        }
        // 检查是否包含三段（饮食/运动/健康）
        if (!content.contains("饮食") && !content.contains("运动") && !content.contains("健康")) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
        }
        return AiHealthResult.success(content);
    }

    /**
     * 从 JSON 中提取 "content" 字段值（手工解析）
     */
    private String extractContent(String json) {
        // 查找 "content":" 开始
        String key = "\"content\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            // 尝试带转义的情况
            key = "\"content\": \"";
            start = json.indexOf(key);
            if (start < 0) {
                return null;
            }
        }
        start += key.length();
        // 查找结束引号（处理转义）
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') {
                end += 2; // 跳过转义字符
                continue;
            }
            if (c == '"') {
                break;
            }
            end++;
        }
        if (end >= json.length()) {
            return null;
        }
        String content = json.substring(start, end);
        // 处理转义
        return content.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}