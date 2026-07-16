package com.bmi.model.db;

import com.bmi.model.User;

/**
 * 用户数据访问接口（对应 db_design.md 的 user 表）。
 * 实现类按 db_design.md 的 SQLite/MySQL 建表 SQL 落地 JDBC；
 * 当前提供 InMemoryUserDao 供界面联调与演示。
 */
public interface UserDao {

    /**
     * 按用户名查询用户（登录/查重）。
     */
    User findByUsername(String username);

    /**
     * 用户名是否已存在（注册唯一性校验，FR-01）。
     */
    boolean existsUsername(String username);

    /**
     * 插入用户（注册），插入后回填自增主键。
     */
    void insert(User user);

    /**
     * 按主键查询用户。
     */
    User findById(long id);
}
