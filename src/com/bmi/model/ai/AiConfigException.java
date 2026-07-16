package com.bmi.model.ai;

/**
 * AI 配置异常（对齐 ai_design.md §5.1「密钥缺失」）：
 * 仅当 ai-key.properties 缺失或 api.key 为空时由 {@link AiHealthClient#getHealthAdvice} 抛出，
 * 向上传递给 AiController 转成「AI 服务未配置，请联系管理员」文案。
 */
public class AiConfigException extends AiException {

    public AiConfigException(String message) {
        super(message);
    }

    public AiConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
