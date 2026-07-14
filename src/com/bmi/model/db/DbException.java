package com.bmi.model.db;

/**
 * 数据库层统一运行时异常（对齐 db_design.md §6.3 配置外置 + 异常捕获）。
 * 用于封装配置缺失、连接失败等可预期错误，避免向 view 层泄露技术栈细节，
 * 由 controller 捕获后转为 ui_design.md 一.4 的中文降级弹窗文案。
 */
public class DbException extends RuntimeException {

    public DbException(String message) {
        super(message);
    }

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}
