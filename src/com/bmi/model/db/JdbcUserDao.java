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
 * JDBC 实现的用户数据访问对象。
 */
public class JdbcUserDao implements UserDao {

    @Override
    public boolean insert(User user) {
        if (user == null || user.getUsername() == null || user.getPasswordHash() == null) {
            return false;
        }
        if (existsUsername(user.getUsername())) {
            return false;
        }
        String sql = "INSERT INTO `user` (username, password_hash, salt, created_at, updated_at, status) "
                   + "VALUES (?, ?, ?, ?, ?, 1)";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            String salt = generateSalt();
            String passwordHash = sha256(user.getPasswordHash(), salt);
            LocalDateTime now = LocalDateTime.now();
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, user.getUsername());
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setTimestamp(5, Timestamp.valueOf(now));
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            throw new DataAccessException("User registration failed for username: " + user.getUsername(), e);
        } finally {
            JdbcUtil.close(conn, ps);
        }
    }

    @Override
    public User findByUsername(String username) {
        if (username == null) return null;
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
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by username: " + username, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }

    @Override
    public boolean existsUsername(String username) {
        if (username == null) return false;
        String sql = "SELECT 1 FROM `user` WHERE username = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, username);
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check username existence: " + username, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
    }

    @Override
    public User login(String username, String password) {
        if (username == null || password == null) return null;
        User user = findByUsername(username);
        if (user == null || user.getSalt() == null || user.getPasswordHash() == null) return null;
        String inputHash = sha256(password, user.getSalt());
        if (user.getPasswordHash().equals(inputHash)) return user;
        return null;
    }

    @Override
    public User findById(long id) {
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
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get user by id: " + id, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setSalt(rs.getString("salt"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) user.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) user.setUpdatedAt(ua.toLocalDateTime());
        user.setStatus(rs.getInt("status"));
        return user;
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : saltBytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String sha256(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
