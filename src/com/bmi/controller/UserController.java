package com.bmi.controller;

import com.bmi.model.User;
import com.bmi.model.db.UserDao;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 用户控制器（对齐 plan.md §3 controller 层 UserController）。
 * 负责注册/登录编排，密码仅存 SHA-256(盐+明文) 散列，绝不存明文（AC-01 / spec 数据安全）。
 */
public class UserController {

    private final UserDao userDao;

    public UserController(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * 注册：校验用户名唯一后，生成随机盐并存储散列。返回是否成功。
     */
    public boolean register(String username, String password) {
        if (username == null || username.length() < 3 || username.length() > 20
                || password == null || password.isEmpty()) {
            return false;
        }
        if (userDao.existsUsername(username)) {
            return false;
        }
        String salt = generateSalt();
        User user = new User();
        user.setUsername(username);
        user.setSalt(salt);
        user.setPasswordHash(hash(password, salt));
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setStatus(1);
        userDao.insert(user);
        return true;
    }

    /**
     * 登录：校验用户名与密码散列。成功返回用户，失败返回 null。
     */
    public User login(String username, String password) {
        User user = userDao.findByUsername(username);
        if (user == null) {
            return null;
        }
        String expect = hash(password, user.getSalt());
        return expect.equals(user.getPasswordHash()) ? user : null;
    }

    // —— 密码学辅助 ——

    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((salt + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
