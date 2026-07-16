package com.bmi.model.db;

import com.bmi.model.User;

/**
 * 用户数据访问接口，定义 user 表的查询与写入操作契约。
 * <p>
 * 命名遵循 CODEBUDDY.md §4.1：DAO 后缀为 {@code Dao}。
 * 实现类：{@link JdbcUserDao}（JDBC）、{@link InMemoryUserDao}（内存测试）。
 */
public interface UserDao {

    /**
     * 注册新用户（内部生成盐 + SHA-256 哈希）。
     *
     * @param user 用户实体（需包含 username 和明文密码 passwordHash 字段）
     * @return true 注册成功
     */
    boolean insert(User user);

    /**
     * 按用户名查询用户。
     *
     * @param username 用户名
     * @return User 对象，未找到返回 null
     */
    User findByUsername(String username);

    /**
     * 检查用户名是否已存在。
     *
     * @param username 待检查用户名
     * @return true 已存在
     */
    boolean existsUsername(String username);

    /**
     * 登录校验：按用户名查找，比对密码哈希。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 匹配成功返回 User 对象，否则返回 null
     */
    User login(String username, String password);

    /**
     * 按主键查询用户。
     *
     * @param id 用户 ID
     * @return User 对象，未找到返回 null
     */
    User findById(long id);
}
