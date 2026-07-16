package com.bmi.view.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 加密工具（UI 层专用，对齐专项约束「本地存储用户名 + 加密密码，不存明文」）。
 *
 * <p>密码一律以 SHA-256 十六进制摘要形式存储 / 比对，绝不保存明文。
 * 注册与记住登录凭据均经本类加密后写入 UI 层本地存储（{@code UserSession} 内存 / app-config.properties）。
 */
public final class Sha256Util {

    private Sha256Util() {
    }

    /** 返回输入字符串的 SHA-256 十六进制摘要（小写）。null 按空串处理。 */
    public static String hash(String input) {
        if (input == null) {
            input = "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
