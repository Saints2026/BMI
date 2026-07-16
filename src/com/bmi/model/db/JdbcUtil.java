package com.bmi.model.db;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * JDBC 连接工具类，负责数据库连接的建立与资源释放。
 * <p>
 * 从当前工作目录下的 db-config.properties 读取数据库配置（db.url / db.user / db.password / db.driver）。
 * <p>
 * 超时常量 {@link #CONNECT_TIMEOUT_MS} / {@link #READ_TIMEOUT_MS} 遵循 CODEBUDDY.md \u00a74.2 常量命名规范。
 * SQLite 连接自动开启外键约束（PRAGMA foreign_keys = ON）。
 */
public class JdbcUtil {

    /** 连接超时（毫秒），遵循 CODEBUDDY.md \u00a74.2 全大写下划线常量命名 */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    /** 读取超时（毫秒），遵循 CODEBUDDY.md \u00a74.2 全大写下划线常量命名 */
    private static final int READ_TIMEOUT_MS = 10000;

    private static String URL;
    private static String USER;
    private static String PASSWORD;
    private static boolean isSqlite;

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
            isSqlite = (URL != null && URL.startsWith("jdbc:sqlite"));
        } catch (FileNotFoundException e) {
            throw new ExceptionInInitializerError("Configuration file not found: " + configFile
                    + " (current working directory: " + System.getProperty("user.dir") + ")");
        } catch (IOException | ClassNotFoundException e) {
            throw new ExceptionInInitializerError("Failed to load " + configFile + ": " + e.getMessage());
        }
    }

    /**
     * 获取数据库连接。
     * <p>
     * SQLite 连接自动执行 {@code PRAGMA foreign_keys = ON;} 开启外键约束。
     *
     * @return JDBC Connection
     * @throws SQLException 连接失败时抛出
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        if (isSqlite) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            } catch (SQLException ignored) {
                // SQLite 外键开启失败不影响主流程
            }
        }
        return conn;
    }

    public static void close(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public static void close(PreparedStatement ps) {
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException ignored) {
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
