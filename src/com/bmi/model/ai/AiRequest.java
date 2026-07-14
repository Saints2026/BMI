package com.bmi.model.ai;

/**
 * AI 请求 DTO
 * 对应 docs/ai_design.md §3.1 AiRequest 结构
 */
public class AiRequest {
    private final String systemPrompt;
    private final BodyRecord record;
    private final int maxTokens;
    private final double temperature;

    public AiRequest(String systemPrompt, BodyRecord record, int maxTokens, double temperature) {
        this.systemPrompt = systemPrompt;
        this.record = record;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public String getSystemPrompt() { return systemPrompt; }
    public BodyRecord getRecord() { return record; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }

    /**
     * 校验入参是否完整（对应规范“参数为空”异常）
     */
    public boolean isValid() {
        return record != null
                && record.getHeight() > 0 && record.getHeight() <= 250
                && record.getWeight() > 0 && record.getWeight() <= 300
                && record.getAge() >= 1 && record.getAge() <= 120
                && (record.getGender() == 0 || record.getGender() == 1);
    }
}