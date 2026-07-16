package com.bmi.model.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.bmi.model.User;

/**
 * 用户数据访问对象，封装 user 表的查询与写入操作。
 * <p>
 * 注意：密码哈希与盐生成逻辑暂留于此，规划中应由 UserController 层调度，
 * 当前为简化实现直接内嵌。
 */
public class UserDAO {

    /**
     * 登录校验：按用户名查找，比对密码哈希。
     *
     * @param username 用户名
     * @param password 明文密码（方法内加盐哈希后比对）
     * @return 匹配成功后返回 User 对象（含完整字段），否则返回 null
     */
    public User login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        String sql = "SELECT id, username, password_hash, salt, created_at, updated_at, status "
                   + "FROM `user` WHERE username = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, username);
            rs = ps.executeQuery();
            if (rs.next()) {
                String salt = rs.getString("salt");
                String storedHash = rs.getString("password_hash");
                String inputHash = sha256(password, salt);
                System.out.println("[UserDAO] login: stored_hash=" + storedHash + ", input_hash=" + inputHash);
                if (storedHash.equals(inputHash)) {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setPasswordHash(storedHash);
                    user.setSalt(salt);
                    Timestamp ca = rs.getTimestamp("created_at");
                    if (ca != null) {
                        user.setCreatedAt(ca.toLocalDateTime());
                    }
                    Timestamp ua = rs.getTimestamp("updated_at");
                    if (ua != null) {
                        user.setUpdatedAt(ua.toLocalDateTime());
                    }
                    user.setStatus(rs.getInt("status"));
                    return user;
                } else {
                    System.out.println("[UserDAO] login: password mismatch for username=" + username);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("User login failed for username: " + username, e);
        } catch (RuntimeException e) {
            throw new DataAccessException("Unexpected error during user login for username: " + username, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }

    /**
     * 用户注册：生成盐、SHA-256 哈希后写入数据库。
     *
     * @param username 用户名（需唯一）
     * @param password 明文密码
     * @return true 注册成功
     */
    public boolean register(String username, String password) {
        if (username == null || password == null) {
            System.err.println("[UserDAO] register failed: username or password is null");
            return false;
        }
        if (existsUsername(username)) {
            System.err.println("[UserDAO] register failed: username '" + username + "' already exists");
            return false;
        }
        String sql = "INSERT INTO `user` (username, password_hash, salt, created_at, updated_at, status) "
                   + "VALUES (?, ?, ?, ?, ?, 1)";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            String salt = generateSalt();
            String passwordHash = sha256(password, salt);
            LocalDateTime now = LocalDateTime.now();
            conn = JdbcUtil.getConnection();
            System.out.println("[UserDAO] register: username=" + username + ", hash=" + passwordHash + ", salt=" + salt);
            ps = conn.prepareStatement(sql);
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setTimestamp(5, Timestamp.valueOf(now));
            int affected = ps.executeUpdate();
            System.out.println("[UserDAO] register success, affected rows: " + affected);
            return affected > 0;
        } catch (SQLException e) {
            throw new DataAccessException("User registration failed for username: " + username, e);
        } catch (RuntimeException e) {
            throw new DataAccessException("Unexpected error during user registration for username: " + username, e);
        } finally {
            JdbcUtil.close(conn, ps);
        }
    }

    /**
     * 按主键查询用户。
     *
     * @param id 用户 ID
     * @return User 对象，未找到返回 null
     */
    public User getUserById(long id) {
        String sql = "SELECT id, username, password_hash, salt, created_at, updated_at, status "
                   + "FROM `user` WHERE id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            rs = ps.executeQuery();
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getLong("id"));
                user.setUsername(rs.getString("username"));
                user.setPasswordHash(rs.getString("password_hash"));
                user.setSalt(rs.getString("salt"));
                Timestamp ca = rs.getTimestamp("created_at");
                if (ca != null) {
                    user.setCreatedAt(ca.toLocalDateTime());
                }
                Timestamp ua = rs.getTimestamp("updated_at");
                if (ua != null) {
                    user.setUpdatedAt(ua.toLocalDateTime());
                }
                user.setStatus(rs.getInt("status"));
                return user;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get user by id: " + id, e);
        } catch (RuntimeException e) {
            throw new DataAccessException("Unexpected error getting user by id: " + id, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }

    /**
     * 检查用户名是否已存在。
     *
     * @param username 待检查用户名
     * @return true 已存在
     */
    private boolean existsUsername(String username) {
        String sql = "SELECT 1 FROM `user` WHERE username = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, username);
            rs = ps.executeQuery();
            boolean exists = rs.next();
            System.out.println("[UserDAO] existsUsername(" + username + "): " + exists);
            return exists;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check username existence: " + username, e);
        } catch (RuntimeException e) {
            throw new DataAccessException("Unexpected error checking username existence: " + username, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
    }

    /**
     * 生成 16 字节随机盐的十六进制字符串。
     *
     * @return 32 位十六进制盐
     */
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

    /**
     * SHA-256 加盐哈希。
     *
     * @param password 明文密码
     * @param salt     盐值
     * @return 64 位十六进制哈希字符串
     */
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
