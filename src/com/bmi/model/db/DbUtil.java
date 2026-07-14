package com.bmi.model.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * 数据库连接工具类（对齐 db_design.md §6.3 / 附录）。
 *
 * 职责：
 *  - 从 {@code db-config.properties} 读取连接配置（classpath 优先，回退工作目录），源码零硬编码；
 *  - 兼容 MySQL（db.type=mysql，首选本任务实现）与 SQLite（db.type=sqlite，宪章首选）两种方言；
 *  - SQLite 获取连接后自动执行 {@code PRAGMA foreign_keys = ON}（SQLite 默认关闭外键）；
 *  - 提供 closeQuietly 资源关闭工具，杜绝连接泄漏。
 *
 * 用法示例：
 * <pre>
 *   try (Connection conn = DbUtil.getConnection()) {
 *       // ... 使用 conn ...
 *   } catch (DbException e) {
 *       // 转为中文降级提示（ui_design.md 一.4）
 *   }
 * </pre>
 *
 * 依赖：MySQL 需将 mysql-connector-j-*.jar 放入 lib/（Java 8+ 驱动实现 JDBC4 自动注册，无需 Class.forName）。
 */
public final class DbUtil {

    /** 配置文件名（已 gitignore，呼应宪章第 4/7 节）。 */
    static final String CONFIG_NAME = "db-config.properties";

    /** 配置缓存：仅在类加载时读取一次（属连接参数，运行期不变）。 */
    private static final Properties PROPS = loadProps();

    private DbUtil() {
        // 工具类不可实例化
    }

    /**
     * 加载 db-config.properties。classpath 优先，回退文件系统工作目录。
     */
    private static Properties loadProps() {
        Properties p = new Properties();
        // 1) classpath 资源（随 jar 打包）
        try (InputStream is = DbUtil.class.getClassLoader().getResourceAsStream(CONFIG_NAME)) {
            if (is != null) {
                p.load(is);
                return p;
            }
        } catch (IOException ignored) {
            // 继续尝试文件系统
        }
        // 2) 工作目录文件
        try (InputStream is = new java.io.FileInputStream(CONFIG_NAME)) {
            p.load(is);
            return p;
        } catch (IOException e) {
            throw new DbException("未找到 " + CONFIG_NAME + "（请放置于 classpath 或工作目录）", e);
        }
    }

    /**
     * 获取数据库连接。根据 db.type 选择方言；连接失败时抛出 {@link DbException}（不向上抛 SQLException）。
     *
     * @throws DbException 配置缺失、URL 非法或数据库不可达时
     */
    public static Connection getConnection() throws DbException {
        String type = PROPS.getProperty("db.type", "mysql").trim().toLowerCase();
        String url = PROPS.getProperty("db.url");
        String user = PROPS.getProperty("db.user", "");
        String password = PROPS.getProperty("db.password", "");

        if (url == null || url.trim().isEmpty()) {
            throw new DbException(CONFIG_NAME + " 缺少 db.url");
        }

        try {
            if ("sqlite".equals(type)) {
                // sqlite-jdbc 实现 JDBC4 自动注册；无需显式加载驱动
                Connection conn = DriverManager.getConnection(url);
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON"); // SQLite 必须每次连接开启
                }
                return conn;
            }
            // 默认 MySQL：mysql-connector-j 8+ 自动注册驱动；旧版可在此显式
            // Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DbException("获取数据库连接失败：" + e.getMessage(), e);
        }
    }

    /**
     * 静默关闭连接（null 安全，吞掉关闭异常，避免掩盖主流程异常）。
     */
    public static void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            if (!conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ignored) {
            // 关闭失败不影响主流程
        }
    }

    /**
     * 静默关闭语句。
     */
    public static void closeQuietly(Statement stmt) {
        if (stmt == null) {
            return;
        }
        try {
            stmt.close();
        } catch (SQLException ignored) {
            // 关闭失败不影响主流程
        }
    }

    /**
     * 静默关闭结果集（关闭前先关闭其依赖的语句更安全，此处仅释放 ResultSet）。
     */
    public static void closeQuietly(ResultSet rs) {
        if (rs == null) {
            return;
        }
        try {
            rs.close();
        } catch (SQLException ignored) {
            // 关闭失败不影响主流程
        }
    }

    /**
     * 同时关闭 ResultSet 与 Statement（常用组合）。
     */
    public static void closeQuietly(ResultSet rs, Statement stmt) {
        closeQuietly(rs);
        closeQuietly(stmt);
    }
}
