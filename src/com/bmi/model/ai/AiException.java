package com.bmi.model.ai;

/**
 * AI 模块自定义异常基类（对齐 ai_design.md §7.1）。
 *
 * 传输层异常由 {@link AiHealthClient} 内部捕获并转换为 {@link AiHealthResult} 降级，不向上抛出；
 * 仅配置类错误（密钥缺失）由 {@link AiConfigException} 向上传递，供控制层转成管理提示文案。
 */
public class AiException extends Exception {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
