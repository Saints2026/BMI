package com.bmi.view.util;

import com.bmi.model.User;
import com.bmi.model.db.UserDao;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock 用户 DAO（离线自测工具，位于 UI 层 Mock 工具区，allowed area）。
 *
 * <p>实现 {@link UserDao} 接口，全程内存、不落库；构造时预置测试账号
 * <b>test01 / Test1234</b>，使开启 Mock 模式后可完整跑通
 * 注册 → 登录 → 录入 → 图表 全流程，无需任何后端数据库。
 *
 * <p>启用开关见 {@code AppConfig.isMockDaoEnabled()}（对应 app-config.properties 的
 * {@code mock.dao.enabled}）；BmiApplication 在开关开启时使用本类替换 InMemoryUserDao。
 * 本类仅新增 Mock 工具，不修改任何 model/db/ai 后端业务文件。
 */
public class MockUserDao implements UserDao {

    /** 预置测试账号（与 UserController 的 SHA-256(salt+明文) 校验契约一致）。 */
    public static final String SEED_USERNAME = "test01";
    public static final String SEED_PASSWORD = "Test1234";

    private final Map<String, User> byUsername = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public MockUserDao() {
        seedTestAccount();
    }

    private void seedTestAccount() {
        String salt = "mock-seed-salt";
        User u = new User();
        u.setUsername(SEED_USERNAME);
        u.setSalt(salt);
        u.setPasswordHash(sha256hex(salt + SEED_PASSWORD));
        u.setCreatedAt(LocalDateTime.now());
        u.setStatus(1);
        u.setId(idSeq.getAndIncrement());
        byUsername.put(SEED_USERNAME, u);
    }

    @Override
    public User findByUsername(String username) {
        return byUsername.get(username);
    }

    @Override
    public boolean existsUsername(String username) {
        return byUsername.containsKey(username);
    }

    @Override
    public User login(String username, String password) {
        if (username == null || password == null) return null;
        if (!existsUsername(username)) return null;
        User user = findByUsername(username);
        if (user == null) return null;
        // SHA-256(salt + 明文) 比对，契约与 InMemoryUserDao / UserController 一致
        String inputHash = sha256hex(user.getSalt() + password);
        return user.getPasswordHash().equals(inputHash) ? user : null;
    }

    @Override
    public boolean insert(User user) {
        if (user.getId() == 0) {
            user.setId(idSeq.getAndIncrement());
        }
        byUsername.put(user.getUsername(), user);
        return true;
    }

    @Override
    public User findById(long id) {
        for (User u : byUsername.values()) {
            if (u.getId() == id) {
                return u;
            }
        }
        return null;
    }

    private static String sha256hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
