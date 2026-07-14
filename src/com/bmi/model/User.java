package com.bmi.model;

import java.sql.Timestamp;

/**
 * 用户实体（对应 db_design.md 的 user 表）。
 * 仅承载身份与认证要素，不含业务计算。
 */
public class User {

    private long id;
    private String username;
    private String passwordHash; // SHA-256 不可逆散列，绝不存明文
    private String salt;         // 随机盐，注册时生成
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private int status = 1;      // 1=正常 0=禁用

    public User() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
