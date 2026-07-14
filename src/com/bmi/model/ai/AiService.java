package com.bmi.model.ai;

import com.bmi.model.BodyRecord;
import com.bmi.model.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * AI 健康建议服务（对齐 ai_design.md 整体设计，命名以用户指定为准：AiService）。
 *
 * 职责（model.ai 层）：
 *  - buildRequest：由实体构造 AiRequest 信封（对齐 ai_design.md §3 签名 buildRequest(User,Record,List)）。
 *  - requestAdvice：对外主入口，返回建议文本或降级文案（对齐 plan.md §4.4）。
 *  - send：HttpURLConnection 调用 + 四类异常处理（断网/超时/参数为空/服务器报错），含 1 次重试。
 *
 * 约定：仅用 JDK 原生 HttpURLConnection + 手工 JSON 拼接/解析，不引入任何第三方 HTTP/JSON 库（宪章白名单）。
 * 密钥从 ai-key.properties 读取，源码零硬编码。
 */
public class AiService {

    static final int CONNECT_TIMEOUT_MS = 10000;
    static final int READ_TIMEOUT_MS = 10000;
    static final int MAX_RETRY = 1;

    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    // ============ 对外 API ============

    /**
     * 由最新记录 + 历史记录构造 AI 请求 DTO（对齐 ai_design.md §3.5）。
     * age/gender 取自最新记录的瞬时字段（BodyRecord 中不持久化，仅供计算与请求）。
     */
    public AiRequest buildRequest(User u, BodyRecord latest, List<BodyRecord> history) {
        AiRequest req = new AiRequest();
        req.setSystemPrompt("你是一位严谨的中文健康顾问，请按『饮食/运动/健康』三段给出建议。");

        AiRequest.UserMetrics um = new AiRequest.UserMetrics();
        um.setBmi(round1(latest.getBmi()));
        um.setBmiGrade(classify(latest.getBmi()));
        um.setBodyFat(round1(latest.getBodyFat()));
        um.setWeight(latest.getWeight());
        um.setHeight(latest.getHeight());
        um.setAge(latest.getAge());
        um.setGender(latest.getGender());
        um.setMeasureTime(latest.getMeasureTime() != null ? ISO.format(latest.getMeasureTime()) : "");
        req.setUserMetrics(um);

        AiRequest.HistoryTrend ht = new AiRequest.HistoryTrend();
        List<BodyRecord> recent = history == null ? new ArrayList<>()
                : history.subList(0, Math.min(history.size(), 10));
        ht.setCount(recent.size());
        ht.setDirection(computeDirection(recent));
        List<AiRequest.MetricPoint> pts = new ArrayList<>();
        for (BodyRecord r : recent) {
            AiRequest.MetricPoint p = new AiRequest.MetricPoint();
            p.setMeasureTime(r.getMeasureTime() != null ? ISO.format(r.getMeasureTime()) : "");
            p.setBmi(round1(r.getBmi()));
            p.setWeight(r.getWeight());
            p.setBodyFat(round1(r.getBodyFat()));
            pts.add(p);
        }
        ht.setPoints(pts);
        req.setHistoryTrend(ht);

        AiRequest.ModelParams mp = new AiRequest.ModelParams();
        mp.setModel(readModelDefault());
        mp.setTemperature(0.7);
        mp.setMaxTokens(800);
        req.setModelParams(mp);
        return req;
    }

    /**
     * 对外主入口：返回建议文本；失败返回降级文案（不抛传输类异常）。
     */
    public String requestAdvice(AiRequest req) {
        AiHealthResult r = send(req);
        return r.isSuccess() ? r.getAdviceText() : r.getMessage();
    }

    // ============ 内部主流程 ============

    private AiHealthResult send(AiRequest req) {
        // ③ 参数为空：入口校验，不重试
        if (req == null || req.getUserMetrics() == null || !isValidMetrics(req.getUserMetrics())) {
            return AiHealthResult.fail(AiHealthResult.INVALID_PARAM,
                    "当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入");
        }

        String apiKey;
        try {
            apiKey = loadApiKey(); // 缺失则抛 AiConfigException
        } catch (AiConfigException e) {
            return AiHealthResult.fail(AiHealthResult.CONFIG_ERROR, "AI 服务未配置，请联系管理员");
        }

        String jsonBody = buildOpenAiJson(req);
        int attempt = 0;
        while (attempt <= MAX_RETRY) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(readApiUrl());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();

                // ④ 服务器报错：5xx 可重试 1 次；4xx 直接降级，不重试
                if (status >= 500) {
                    if (attempt <= MAX_RETRY) {
                        continue;
                    }
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
                }
                if (status >= 400) {
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
                }

                String body = readBody(conn);
                AiHealthResult r = parseResponse(body);
                if (!r.isSuccess() || !r.hasThreeSections()) {
                    return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
                }
                return r;

            } catch (java.net.SocketTimeoutException e) {      // ② 接口超时：可重试 1 次
                if (attempt <= MAX_RETRY) {
                    continue;
                }
                return AiHealthResult.fail(AiHealthResult.TIMEOUT, "AI 建议请求超时，请稍后重试");

            } catch (ConnectException | UnknownHostException e) { // ① 断网：不重试
                return AiHealthResult.fail(AiHealthResult.NETWORK_ERROR, "暂时无法获取 AI 建议，请检查网络或稍后再试");

            } catch (IOException e) {                          // 其它 IO
                return AiHealthResult.fail(AiHealthResult.NETWORK_ERROR, "暂时无法获取 AI 建议，请检查网络或稍后再试");

            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
    }

    // ============ 响应解析（手工 JSON，无第三方库） ============

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
            String diet = extractSegment(content, "饮食", "运动");
            String exercise = extractSegment(content, "运动", "健康");
            String health = extractSegment(content, "健康", null);
            AiHealthResult r = AiHealthResult.ok(content, diet, exercise, health);
            r.setFinishReason(extractJsonString(body, "finish_reason"));
            return r;
        } catch (Exception e) {
            return AiHealthResult.fail(AiHealthResult.SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
        }
    }

    // ============ 请求体构造（OpenAI Chat Completions 兼容） ============

    private String buildOpenAiJson(AiRequest req) {
        AiRequest.UserMetrics um = req.getUserMetrics();
        AiRequest.HistoryTrend ht = req.getHistoryTrend();
        StringBuilder userSb = new StringBuilder();
        userSb.append(String.format(
                "最新指标：BMI=%.1f(%s)，体重=%.1fkg，体脂率=%.1f%%，身高=%.1fcm，年龄=%d，性别=%s，测量时间=%s。",
                um.getBmi(), um.getBmiGrade(), um.getWeight(), um.getBodyFat(),
                um.getHeight(), um.getAge(), um.getGender() == 1 ? "男" : "女", um.getMeasureTime()));
        if (ht != null && ht.getPoints() != null && !ht.getPoints().isEmpty()) {
            userSb.append(String.format(" 历史趋势：近%d次，整体%s。", ht.getCount(), ht.getDirection()));
        }
        userSb.append(" 请按【饮食】【运动】【健康】三段给出可执行的建议。");

        return "{"
                + "\"model\":\"" + escapeJson(req.getModelParams().getModel()) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escapeJson(req.getSystemPrompt()) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escapeJson(userSb.toString()) + "\"}"
                + "],"
                + "\"temperature\":" + req.getModelParams().getTemperature() + ","
                + "\"max_tokens\":" + req.getModelParams().getMaxTokens()
                + "}";
    }

    // ============ 密钥与配置（只读 ai-key.properties，不硬编码） ============

    private String loadApiKey() throws AiConfigException {
        Properties p = loadAiProps();
        String key = p.getProperty("api.key");
        if (key == null || key.trim().isEmpty()) {
            throw new AiConfigException("ai-key.properties 缺失 api.key");
        }
        return key.trim();
    }

    private String readApiUrl() {
        try {
            return loadAiProps().getProperty("api.url", "https://api.openai.com/v1/chat/completions");
        } catch (AiConfigException e) {
            return "https://api.openai.com/v1/chat/completions";
        }
    }

    private String readModelDefault() {
        try {
            return loadAiProps().getProperty("api.model", "gpt-4o-mini");
        } catch (AiConfigException e) {
            return "gpt-4o-mini";
        }
    }

    private Properties loadAiProps() throws AiConfigException {
        Properties p = new Properties();
        try (InputStream is = AiService.class.getClassLoader().getResourceAsStream("ai-key.properties")) {
            if (is != null) {
                p.load(is);
                return p;
            }
        } catch (IOException ignored) {
            // 退回文件系统
        }
        try (InputStream is = new java.io.FileInputStream("ai-key.properties")) {
            p.load(is);
            return p;
        } catch (IOException e) {
            throw new AiConfigException("未找到 ai-key.properties", e);
        }
    }

    // ============ 辅助 ============

    private boolean isValidMetrics(AiRequest.UserMetrics um) {
        return um.getBmi() > 0 && um.getBmi() <= 100
                && um.getWeight() >= 10 && um.getWeight() <= 300
                && um.getHeight() >= 50 && um.getHeight() <= 250
                && um.getAge() >= 1 && um.getAge() <= 120
                && (um.getGender() == 0 || um.getGender() == 1)
                && um.getMeasureTime() != null && !um.getMeasureTime().isEmpty();
    }

    private String computeDirection(List<BodyRecord> recent) {
        if (recent == null || recent.size() < 2) {
            return "无数据";
        }
        double first = recent.get(0).getBmi();
        double last = recent.get(recent.size() - 1).getBmi();
        if (last - first > 0.1) {
            return "上升";
        }
        if (first - last > 0.1) {
            return "下降";
        }
        return "平稳";
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String classify(double bmi) {
        if (bmi < 18.5) {
            return "偏瘦";
        }
        if (bmi < 24.0) {
            return "正常";
        }
        if (bmi < 28.0) {
            return "超重";
        }
        return "肥胖";
    }

    private String readBody(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // —— 极简手工 JSON 取值（仅满足本项目需要，非通用解析器）——
    private String extractJsonString(String json, String key) {
        String token = "\"" + key + "\":";
        int idx = json.indexOf(token);
        if (idx < 0) {
            return null;
        }
        int start = json.indexOf('"', idx + token.length());
        if (start < 0) {
            return null;
        }
        int end = json.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    private String extractSegment(String text, String startMarker, String endMarker) {
        int s = text.indexOf("【" + startMarker + "】");
        if (s < 0) {
            s = text.indexOf(startMarker);
        }
        if (s < 0) {
            return "";
        }
        int from = text.indexOf("】", s);
        from = (from > 0 ? from + 1 : s + startMarker.length());
        int to = text.length();
        if (endMarker != null) {
            int e = text.indexOf("【" + endMarker + "】");
            if (e > 0) {
                to = e;
            }
        }
        return text.substring(from, to).trim();
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String unescapeJson(String s) {
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
