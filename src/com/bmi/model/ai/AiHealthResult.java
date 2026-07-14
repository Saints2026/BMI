package com.bmi.model.ai;

public class AiHealthResult {
    // 错误码常量
    public static final String SUCCESS = "0";
    public static final String NETWORK_ERROR = "AI_NET_001";
    public static final String TIMEOUT = "AI_TIMEOUT_002";
    public static final String INVALID_PARAM = "AI_PARAM_003";
    public static final String SERVER_ERROR = "AI_SRV_004";
    public static final String CONFIG_ERROR = "AI_CFG_005";

    private boolean success;
    private String code;
    private String message;
    private String adviceText;
    private String dietAdvice;
    private String exerciseAdvice;
    private String healthAdvice;
    private String finishReason;
    private Usage usage;

    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    }

    public AiHealthResult() {}

    public static AiHealthResult success(String adviceText) {
        AiHealthResult r = new AiHealthResult();
        r.success = true;
        r.code = SUCCESS;
        r.adviceText = adviceText;
        r.dietAdvice = extractSection(adviceText, "饮食");
        r.exerciseAdvice = extractSection(adviceText, "运动");
        r.healthAdvice = extractSection(adviceText, "健康");
        return r;
    }

    public static AiHealthResult fail(String code, String message) {
        AiHealthResult r = new AiHealthResult();
        r.success = false;
        r.code = code;
        r.message = message;
        return r;
    }

    private static String extractSection(String text, String section) {
        if (text == null) return "";
        String pattern = "【" + section + "】";
        int start = text.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = text.length();
        for (String other : new String[]{"【饮食】", "【运动】", "【健康】"}) {
            if (other.equals(pattern)) continue;
            int pos = text.indexOf(other, start);
            if (pos != -1 && pos < end) {
                end = pos;
            }
        }
        return text.substring(start, end).trim();
    }

    // P1-4：三段必须齐全
    public boolean hasThreeSections() {
        return dietAdvice != null && !dietAdvice.isEmpty()
                && exerciseAdvice != null && !exerciseAdvice.isEmpty()
                && healthAdvice != null && !healthAdvice.isEmpty();
    }

    public boolean ok() {
        return success && code.equals(SUCCESS) && hasThreeSections();
    }

    // P1-5：解析 usage
    public void parseUsage(org.json.JSONObject usageJson) {
        if (usageJson == null) return;
        this.usage = new Usage();
        this.usage.setPromptTokens(usageJson.optInt("prompt_tokens", 0));
        this.usage.setCompletionTokens(usageJson.optInt("completion_tokens", 0));
        this.usage.setTotalTokens(usageJson.optInt("total_tokens", 0));
    }

    // getters/setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
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
}
