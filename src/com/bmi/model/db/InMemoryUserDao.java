package com.bmi.model.db;

import com.bmi.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于内存 HashMap 的 UserDao 实现，主要用于单元测试与无数据库环境下的演示。
 * <p>
 * 密码哈希策略与 {@link JdbcUserDao} 一致：内部生成随机盐 + SHA-256。
 * insert 时 {@code user.getPasswordHash()} 字段传入的是明文密码，由 DAO 加盐哈希后存储。
 */
public class InMemoryUserDao implements UserDao {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final Map<String, User> usernameIndex = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    @Override
    public boolean insert(User user) {
        if (user == null || user.getUsername() == null || user.getPasswordHash() == null) {
            return false;
        }
        if (existsUsername(user.getUsername())) {
            return false;
        }
        String salt = generateSalt();
        String hash = sha256(user.getPasswordHash(), salt);
        long id = idSeq.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        User stored = new User(id, user.getUsername(), hash, salt, now, now, 1);
        store.put(id, stored);
        usernameIndex.put(user.getUsername(), stored);
        // 回写主键与哈希，便于调用方获取
        user.setId(id);
        user.setPasswordHash(hash);
        user.setSalt(salt);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setStatus(1);
        return true;
    }

    @Override
    public User findByUsername(String username) {
        if (username == null) {
            return null;
        }
        return usernameIndex.get(username);
    }

    @Override
    public boolean existsUsername(String username) {
        if (username == null) {
            return false;
        }
        return usernameIndex.containsKey(username);
    }

    @Override
    public User login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        User user = findByUsername(username);
        if (user == null || user.getSalt() == null || user.getPasswordHash() == null) {
            return null;
        }
        String inputHash = sha256(password, user.getSalt());
        if (user.getPasswordHash().equals(inputHash)) {
            return user;
        }
        return null;
    }

    @Override
    public User findById(long id) {
        return store.get(id);
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : saltBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String sha256(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
