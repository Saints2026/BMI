package com.bmi.model.db;

import com.bmi.model.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户 DAO 的内存实现（演示/联调用，不落库）。
 * 生产环境替换为 JdbcUserDao（按 db_design.md 的 user 表 + db-config.properties 实现）。
 */
public class InMemoryUserDao implements UserDao {

    private final Map<String, User> byUsername = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    @Override
    public User findByUsername(String username) {
        return byUsername.get(username);
    }

    @Override
    public boolean existsUsername(String username) {
        return byUsername.containsKey(username);
    }

    @Override
    public void insert(User user) {
        user.setId(idSeq.getAndIncrement());
        byUsername.put(user.getUsername(), user);
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
}
