package com.bmi.exception;

public class AiConfigException extends AiException {
    public AiConfigException(String message) {
        super(message);
    }

    public AiConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}