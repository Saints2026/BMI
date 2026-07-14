package com.bmi.model.ai;

/**
 * AI 模块自定义异常基类（对齐 ai_design.md §7）。
 * 传输层异常由 AiService 内部捕获并转换为 AiHealthResult 降级，不向上抛出；
 * 仅配置类错误（密钥缺失）抛出 AiConfigException 交由上层处理。
 */
public class AiException extends Exception {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
