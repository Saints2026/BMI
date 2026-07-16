package com.bmi.model;

import java.time.LocalDateTime;

/**
 * 用户身份实体，对应 user 表。
 * <p>
 * 仅存储身份与认证要素（用户名、密码 SHA-256 哈希、盐、注册时间、状态），不含业务指标。
 */
public class User {

    private long id;
    private String username;
    private String passwordHash;
    private String salt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int status;

    public User() {
    }

    public User(long id, String username, String passwordHash, String salt,
                LocalDateTime createdAt, LocalDateTime updatedAt, int status) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
    }

    public long getId() { return id; }

    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }

    public void setSalt(String salt) { this.salt = salt; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getStatus() { return status; }

    public void setStatus(int status) { this.status = status; }
}
