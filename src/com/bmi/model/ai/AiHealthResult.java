package com.bmi.model.ai;

/**
 * AI 健康建议逻辑响应对象（对齐 ai_design.md §4.1）。
 *
 * 字段完全覆盖设计：
 *   success / code / message / adviceText / dietAdvice / exerciseAdvice / healthAdvice /
 *   finishReason / usage{promptTokens, completionTokens, totalTokens}。
 *
 * 工厂方法：
 *   ok(...)   成功结果（adviceText 为完整建议文本，分段填入 diet/exercise/health）；
 *   fail(...) 降级结果（含错误码与展示文案，文案不暴露技术栈）。
 */
public class AiHealthResult {

    // ============ 错误码常量（ai_design.md §5.2）============
    public static final String CODE_SUCCESS  = "0";
    public static final String NETWORK_ERROR = "AI_NET_001";
    public static final String TIMEOUT       = "AI_TIMEOUT_002";
    public static final String INVALID_PARAM = "AI_PARAM_003";
    public static final String SERVER_ERROR  = "AI_SRV_004";
    public static final String CONFIG_ERROR  = "AI_CFG_005";

    private boolean success;
    private String code;
    private String message;
    private String adviceText;
    private String dietAdvice;
    private String exerciseAdvice;
    private String healthAdvice;
    private String finishReason;
    private Usage usage;

    /** 成功结果。 */
    public static AiHealthResult ok(String adviceText, String diet, String exercise, String health) {
        AiHealthResult r = new AiHealthResult();
        r.success = true;
        r.code = CODE_SUCCESS;
        r.adviceText = adviceText;
        r.dietAdvice = diet;
        r.exerciseAdvice = exercise;
        r.healthAdvice = health;
        return r;
    }

    /** 降级结果（或配置错误）。 */
    public static AiHealthResult fail(String code, String message) {
        AiHealthResult r = new AiHealthResult();
        r.success = false;
        r.code = code;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    /** 校验建议文本非空且含【饮食】【运动】【健康】三段（AC-07）。 */
    public boolean hasThreeSections() {
        return adviceText != null
                && adviceText.contains("【饮食】")
                && adviceText.contains("【运动】")
                && adviceText.contains("【健康】");
    }

    // ============ getters / setters ============
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAdviceText() { return adviceText; }
    public void setAdviceText(String adviceText) { this.adviceText = adviceText; }

    public String getDietAdvice() { return dietAdvice; }
    public void setDietAdvice(String dietAdvice) { this.dietAdvice = dietAdvice; }

    public String getExerciseAdvice() { return exerciseAdvice; }
    public void setExerciseAdvice(String exerciseAdvice) { this.exerciseAdvice = exerciseAdvice; }

    public String getHealthAdvice() { return healthAdvice; }
    public void setHealthAdvice(String healthAdvice) { this.healthAdvice = healthAdvice; }

    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }

    public Usage getUsage() { return usage; }
    public void setUsage(Usage usage) { this.usage = usage; }

    /** token 用量子对象（ai_design.md §4.1 usage）。 */
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;

        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int v) { this.promptTokens = v; }

        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int v) { this.completionTokens = v; }

        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int v) { this.totalTokens = v; }
    }
}
