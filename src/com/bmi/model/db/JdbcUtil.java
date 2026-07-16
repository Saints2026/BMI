package com.bmi.model.db;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * JDBC 连接工具类，负责数据库连接的建立与资源释放。
 * <p>
 * 从当前工作目录下的 db-config.properties 读取数据库配置（db.url / db.user / db.password / db.driver）。
 * 连接超时与读取超时依赖驱动默认值。
 * 规划中应更名为 DbUtil 以符合 plan.md 命名约定。
 */
public class JdbcUtil {

    private static String URL;
    private static String USER;
    private static String PASSWORD;

    static {
        String configFile = "db-config.properties";
        try (FileInputStream fis = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(fis);
            URL = props.getProperty("db.url");
            USER = props.getProperty("db.user");
            PASSWORD = props.getProperty("db.password");
            String driver = props.getProperty("db.driver");
            Class.forName(driver);
        } catch (FileNotFoundException e) {
            throw new ExceptionInInitializerError("Configuration file not found: " + configFile
                    + " (current working directory: " + System.getProperty("user.dir") + ")");
        } catch (IOException | ClassNotFoundException e) {
            throw new ExceptionInInitializerError("Failed to load " + configFile + ": " + e.getMessage());
        }
    }

    /**
     * 获取数据库连接。
     *
     * @return JDBC Connection
     * @throws SQLException 连接失败时抛出
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void close(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(PreparedStatement ps) {
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(Connection conn, PreparedStatement ps) {
        close(ps);
        close(conn);
    }

    public static void close(Connection conn, PreparedStatement ps, ResultSet rs) {
        close(rs);
        close(ps);
        close(conn);
    }
}
