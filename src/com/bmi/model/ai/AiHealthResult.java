package com.bmi.model.ai;

/**
 * AI 返回结果 DTO
 * 对应 docs/ai_design.md §4.1 AiHealthResult 结构
 */
public class AiHealthResult {
    private final boolean success;
    private final String code;
    private final String message;
    private final String adviceText;

    private AiHealthResult(boolean success, String code, String message, String adviceText) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.adviceText = adviceText;
    }

    // ---- 成功工厂 ----
    public static AiHealthResult success(String adviceText) {
        return new AiHealthResult(true, "0", null, adviceText);
    }

    // ---- 失败工厂（各错误码） ----
    public static AiHealthResult fail(String code, String message) {
        return new AiHealthResult(false, code, message, null);
    }

    // ---- 预定义错误码（对应 docs/ai_design.md §5.2） ----
    public static final String CODE_SUCCESS = "0";
    public static final String NETWORK_ERROR = "AI_NET_001";
    public static final String TIMEOUT = "AI_TIMEOUT_002";
    public static final String INVALID_PARAM = "AI_PARAM_003";
    public static final String SERVER_ERROR = "AI_SRV_004";
    public static final String CONFIG_ERROR = "AI_CFG_005";

    // ---- Getter ----
    public boolean isSuccess() { return success; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getAdviceText() { return adviceText; }
}