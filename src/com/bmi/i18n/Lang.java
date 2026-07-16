package com.bmi.i18n;

/**
 * 语言枚举（对齐 ui_design.md 一.1）。
 * ZH=中文，EN=英文；code 用于持久化到 app-config.properties（ui.lang.default）。
 */
public enum Lang {
    ZH("zh", "中文"),
    EN("en", "English");

    private final String code;
    private final String display;

    Lang(String code, String display) {
        this.code = code;
        this.display = display;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    /** 由持久化 code 解析枚举，缺省回退 ZH。 */
    public static Lang fromCode(String c) {
        if (c == null) {
            return ZH;
        }
        for (Lang l : values()) {
            if (l.code.equalsIgnoreCase(c.trim())) {
                return l;
            }
        }
        return ZH;
    }
}
