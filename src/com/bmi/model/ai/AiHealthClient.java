package com.bmi.model.ai;

import com.bmi.model.BodyRecord;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * AI 健康建议客户端（model.ai 层，对齐 ai_design.md §2 / §6 / §7）。
 *
 * 职责：
 *  - 对上层（AiController）仅暴露 {@link #getHealthAdvice(BodyRecord, List)} → {@link AiHealthResult}；
 *  - 内部完成 HTTP 调用（原生 HttpURLConnection，无第三方库）、四类异常处理
 *    （断网 / 超时 / 参数为空 / 服务器报错）以及 429 限流、400 参数错误的分层降级（P1-F7）；
 *  - 密钥 / api.url / api.model 仅从 ai-key.properties 读取（宪章白名单，源码零硬编码）。
 *
 * 不向 Controller 抛出传输类异常；仅密钥缺失抛 {@link AiConfigException}（由 Controller 转文案）。
 */
public class AiHealthClient {

    static final int CONNECT_TIMEOUT_MS = 10000;
    static final int READ_TIMEOUT_MS = 10000;
    static final int MAX_RETRY = 1;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String SYSTEM_PROMPT =
            "你是一位严谨的中文健康顾问，请按『饮食/运动/健康』三段给出建议。";

    // ============ 对外 API ============

    /**
     * 获取 AI 健康建议（对齐 plan.md §4.4：返回建议文本或降级文案的载体）。
     *
     * @param latest  用户最新一次测量记录（userMetrics 来源）；为 null 时直接降级（数据不完整）
     * @param history 近 N 次历史记录（historyTrend 来源，应为时间升序）
     * @return AiHealthResult：成功含 adviceText / 三段；失败含 code + 降级 message
     * @throws AiConfigException 仅当 ai-key.properties 缺失或 api.key 为空时
     */
    public AiHealthResult getHealthAdvice(BodyRecord latest, List<BodyRecord> history)
            throws AiConfigException {
        // ③ 参数为空：入口校验（latest 缺失即视为数据不完整），不重试
        if (latest == null || !isValidMetrics(latest)) {
            return AiHealthResult.fail(AiHealthResult.INVALID_PARAM,
                    "当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入");
        }

        String apiKey = loadApiKey();        // 缺失 → 抛 AiConfigException
        String apiUrl = readApiUrl();
        String model = readModelDefault();
        String json = buildJsonBody(latest, history, model);

        int attempt = 0;
        while (attempt <= MAX_RETRY) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                URL url = URI.create(apiUrl).toURL();
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int status = conn.getResponseCode();

                // ④ 服务器报错 / 限流 / 参数错误（分层降级，P1-F7）
                if (status == 429) {                       // 429 限流：可重试 1 次
                    if (attempt <= MAX_RETRY) continue;
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 请求过于频繁，请稍后再试");
                }
                if (status >= 500) {                       // 5xx：可重试 1 次
                    if (attempt <= MAX_RETRY) continue;
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }
                if (status == 400) {                       // 400 参数错误：不重试
                    return AiHealthResult.fail(AiHealthResult.INVALID_PARAM,
                            "AI 请求参数有误，请检查配置后重试");
                }
                if (status >= 400) {                       // 其它 4xx：不重试
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }

                String body = readStream(conn.getInputStream());
                AiHealthResult r = parseResponse(body);
                if (!r.isSuccess() || !r.hasThreeSections()) {   // content 缺失 / 无三段
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR,
                            "AI 服务暂时不可用，请稍后再试");
                }
                return r;

            } catch (SocketTimeoutException e) {           // ② 接口超时：可重试 1 次
                if (attempt <= MAX_RETRY) continue;
                return AiHealthResult.fail(AiHealthResult.TIMEOUT, "AI 建议请求超时，请稍后重试");

            } catch (ConnectException | UnknownHostException e) { // ① 断网：不重试
                return AiHealthResult.fail(AiHealthResult.NETWORK_ERROR,
                        "暂时无法获取 AI 建议，请检查网络或稍后再试");

            } catch (IOException e) {                      // 其它 IO（读响应失败等）
                return AiHealthResult.fail(AiHealthResult.NETWORK_ERROR,
                        "暂时无法获取 AI 建议，请检查网络或稍后再试");

            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
    }

    // ============ 请求体构造（P1-F6：model / messages / userMetrics + 保留 historyTrend）============

    /**
     * 构造 OpenAI Chat Completions 兼容请求体，并保留 userMetrics / historyTrend 结构字段。
     * 必含 model、messages、userMetrics，避免 OpenAI / DeepSeek 400；historyTrend 数组保留用于趋势上下文。
     */
    private String buildJsonBody(BodyRecord latest, List<BodyRecord> history, String model) {
        // userMetrics 对象（ai_design.md §3.2）
        String userMetrics = "{"
                + "\"bmi\":" + round1(latest.getBmi()) + ","
                + "\"bmiGrade\":\"" + escapeJson(classify(latest.getBmi())) + "\","
                + "\"bodyFat\":" + round1(latest.getBodyFat()) + ","
                + "\"weight\":" + latest.getWeight() + ","
                + "\"height\":" + latest.getHeight() + ","
                + "\"age\":" + latest.getAge() + ","
                + "\"gender\":" + latest.getGender() + ","
                + "\"measureTime\":\"" + escapeJson(format(latest.getMeasureTime())) + "\""
                + "}";

        // historyTrend 数组（时间升序，最多 10 条）
        List<BodyRecord> recent = history == null ? new ArrayList<>()
                : history.subList(0, Math.min(history.size(), 10));
        StringBuilder trend = new StringBuilder("[");
        for (int i = 0; i < recent.size(); i++) {
            BodyRecord r = recent.get(i);
            if (i > 0) trend.append(",");
            trend.append("{")
                    .append("\"measureTime\":\"").append(escapeJson(format(r.getMeasureTime()))).append("\",")
                    .append("\"bmi\":").append(round1(r.getBmi())).append(",")
                    .append("\"weight\":").append(r.getWeight()).append(",")
                    .append("\"bodyFat\":").append(round1(r.getBodyFat()))
                    .append("}");
        }
        trend.append("]");

        // 自然语言 user message（对齐 ai_design.md §3.6 映射）
        String direction = computeDirection(recent);
        String userContent = String.format(
                "最新指标：BMI=%.1f(%s)，体重=%.1fkg，体脂率=%.1f%%，身高=%.1fcm，年龄=%d，性别=%s，测量时间=%s。"
                        + " 历史趋势：近%d次，整体%s。"
                        + " 请按【饮食】【运动】【健康】三段给出可执行的建议。",
                latest.getBmi(), classify(latest.getBmi()), latest.getWeight(), latest.getBodyFat(),
                latest.getHeight(), latest.getAge(), latest.getGender() == 1 ? "男" : "女",
                format(latest.getMeasureTime()),
                recent.size(), direction);

        return "{"
                + "\"model\":\"" + escapeJson(model) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escapeJson(SYSTEM_PROMPT) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escapeJson(userContent) + "\"}"
                + "],"
                + "\"userMetrics\":" + userMetrics + ","
                + "\"historyTrend\":" + trend.toString() + ","
                + "\"temperature\":0.7,"
                + "\"max_tokens\":800"
                + "}";
    }

    // ============ 响应解析（手工 JSON，对齐 §4.2 / §4.3）============

    private AiHealthResult parseResponse(String body) {
        if (body == null || body.isEmpty()) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
        }
        try {
            String content = extractJsonString(body, "content");
            if (content == null || content.isEmpty()) {
                return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
            }
            content = unescapeJson(content);

            String diet = extractSection(content, "【饮食】");
            String exercise = extractSection(content, "【运动】");
            String health = extractSection(content, "【健康】");
            if (diet == null || exercise == null || health == null) {
                return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
            }

            AiHealthResult r = AiHealthResult.ok(content, diet, exercise, health);
            r.setFinishReason(extractJsonString(body, "finish_reason"));

            AiHealthResult.Usage usage = new AiHealthResult.Usage();
            String pt = extractJsonString(body, "prompt_tokens");
            String ct = extractJsonString(body, "completion_tokens");
            String tt = extractJsonString(body, "total_tokens");
            if (pt != null) usage.setPromptTokens(Integer.parseInt(pt));
            if (ct != null) usage.setCompletionTokens(Integer.parseInt(ct));
            if (tt != null) usage.setTotalTokens(Integer.parseInt(tt));
            r.setUsage(usage);
            return r;
        } catch (Exception e) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
        }
    }

    // ============ 密钥与配置（仅读 ai-key.properties，不硬编码）============

    private String loadApiKey() throws AiConfigException {
        String key = readProp("api.key");
        if (key == null || key.trim().isEmpty()) {
            throw new AiConfigException("ai-key.properties 缺失 api.key");
        }
        return key.trim();
    }

    private String readApiUrl() {
        return readProp("api.url", "https://api.openai.com/v1/chat/completions");
    }

    private String readModelDefault() {
        return readProp("api.model", "gpt-4o-mini");
    }

    private String readProp(String key) throws AiConfigException {
        return loadProps().getProperty(key);
    }

    private String readProp(String key, String def) {
        try {
            String v = readProp(key);
            return (v == null || v.trim().isEmpty()) ? def : v.trim();
        } catch (AiConfigException e) {
            return def;   // 配置缺失时回退默认值（不影响非关键参数）
        }
    }

    private Properties loadProps() throws AiConfigException {
        Properties p = new Properties();
        // 1) classpath 资源（随 jar 打包，已 gitignore）
        try (InputStream is = AiHealthClient.class.getClassLoader().getResourceAsStream("ai-key.properties")) {
            if (is != null) {
                p.load(is);
                return p;
            }
        } catch (IOException ignored) {
            // 继续尝试文件系统
        }
        // 2) 工作目录文件
        try (InputStream is = new FileInputStream("ai-key.properties")) {
            p.load(is);
            return p;
        } catch (IOException e) {
            throw new AiConfigException("未找到 ai-key.properties（请将配置文件置于 classpath 或工作目录）", e);
        }
    }

    // ============ 辅助 ============

    private boolean isValidMetrics(BodyRecord r) {
        return r.getBmi() > 0 && r.getBmi() <= 100
                && r.getWeight() >= 10 && r.getWeight() <= 300
                && r.getHeight() >= 50 && r.getHeight() <= 250
                && r.getAge() >= 1 && r.getAge() <= 120
                && (r.getGender() == 0 || r.getGender() == 1);
    }

    private String computeDirection(List<BodyRecord> recent) {
        if (recent == null || recent.size() < 2) return "无数据";
        double first = recent.get(0).getBmi();
        double last = recent.get(recent.size() - 1).getBmi();
        if (last - first > 0.1) return "上升";
        if (first - last > 0.1) return "下降";
        return "平稳";
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String classify(double bmi) {
        if (bmi < 18.5) return "偏瘦";
        if (bmi < 24.0) return "正常";
        if (bmi < 28.0) return "超重";
        return "肥胖";
    }

    private String format(java.time.LocalDateTime ldt) {
        return ldt == null ? "" : ISO.format(ldt);
    }

    private String readStream(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // —— 极简手工 JSON 取值（仅满足本项目需要，非通用解析器）——
    private String extractJsonString(String json, String key) {
        String token = "\"" + key + "\":";
        int idx = json.indexOf(token);
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + token.length());
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    private String extractSection(String text, String tag) {
        int s = text.indexOf(tag);
        if (s < 0) return null;
        int from = text.indexOf("】", s);
        from = (from > 0 ? from + 1 : s + tag.length());
        int to = text.length();
        int e = text.indexOf("【", from);
        if (e > 0) to = e;
        return text.substring(from, to).trim();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String unescapeJson(String s) {
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
