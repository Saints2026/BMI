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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    /** setNetworkTimeout 使用的单线程 Executor（复用，避免每次创建线程池） */
    private static final Executor NETWORK_TIMEOUT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jdbc-network-timeout");
        t.setDaemon(true);
        return t;
    });

    private static String URL;
    private static String USER;
    private static String PASSWORD;
    private static boolean isSqlite;
    /** 配置是否就绪（db-config.properties 存在且含 db.url）。缺失时置 false 而非抛致命异常。 */
    private static boolean configured = false;

    static {
        String configFile = "db-config.properties";
        try (FileInputStream fis = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(fis);
            URL = props.getProperty("db.url");
            USER = props.getProperty("db.user");
            PASSWORD = props.getProperty("db.password");
            String driver = props.getProperty("db.driver");
            if (driver != null && !driver.trim().isEmpty()) {
                Class.forName(driver);
            }
            isSqlite = (URL != null && URL.startsWith("jdbc:sqlite"));
            configured = (URL != null && !URL.trim().isEmpty());
            if (!configured) {
                System.err.println("[BMI][WARN] JdbcUtil: " + configFile
                        + " 缺少 db.url，按未配置处理（不抛致命异常）");
            }
        } catch (FileNotFoundException e) {
            configured = false;
            System.err.println("[BMI][WARN] JdbcUtil: 配置文件未找到 " + configFile
                    + " (cwd=" + System.getProperty("user.dir") + ")，按未配置处理（不抛致命异常）");
        } catch (IOException | ClassNotFoundException e) {
            configured = false;
            System.err.println("[BMI][WARN] JdbcUtil: 加载 " + configFile + " 失败: " + e.getMessage()
                    + "，按未配置处理（不抛致命异常）");
        }
    }

    /**
     * 数据库配置是否就绪：配置文件存在且含非空 db.url。
     * <p>缺失时返回 {@code false}（而非在类初始化阶段抛 {@link ExceptionInInitializerError}），
     * 调用方据此降级处理，避免 {@code NoClassDefFoundError} 致页面崩溃、跳转卡死。</p>
     */
    public static boolean isConfigured() {
        return configured;
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
        if (!configured) {
            // 配置缺失：抛可恢复的 SQLException（由上层 try-catch 降级为提示），而非致命类初始化错误
            throw new SQLException("Database not configured: db-config.properties missing or db.url empty");
        }
        // 应用连接超时（单位：秒）
        DriverManager.setLoginTimeout(CONNECT_TIMEOUT_MS / 1000);
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        // 应用读取超时（SQLite 不支持 setNetworkTimeout，跳过）
        if (!isSqlite) {
            try {
                conn.setNetworkTimeout(NETWORK_TIMEOUT_EXECUTOR, READ_TIMEOUT_MS);
            } catch (SQLException ignored) {
                // 驱动不支持时忽略，不影响主流程
            }
        }
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
