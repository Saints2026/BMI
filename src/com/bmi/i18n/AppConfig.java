package com.bmi.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 全局应用配置单例（对齐 ui_design.md 一.1）。
 *
 * <p>本类是唯一权威源，统一管理：
 *  <ul>
 *    <li>当前语言 {@link Lang}（中文 ZH / 英文 EN）</li>
 *    <li>当前主题（fresh / eye / warm）</li>
 *    <li>记住登录的加密凭据（用户名 + SHA-256 密文，绝不存明文）</li>
 *  </ul>
 * 以上三项均持久化到本地 {@code app-config.properties}，程序重启后自动加载上次选择。
 *
 * <p>语言 / 主题变更时通过观察者模式广播给所有已注册的
 * {@link LangChangeListener} / {@link ThemeChangeListener}，各视图在回调内调用
 * I18nUtil.t() 重绑全部文案、样式，实现「全局即时刷新」。
 *
 * <p>关键修复（双语切换 bug）：
 *  <ol>
 *    <li>原实现从不把语言写回配置文件，导致「重启不保留 + 下拉单向覆盖」；
 *        现 {@link #setLang} 在变更后始终持久化 {@code ui.lang.default}。</li>
 *    <li>新增 {@link #loadConfig()} 启动时读取持久化语言与主题，缺省回退中文 / 清爽蓝。</li>
 *    <li>语言变更后广播，视图在 {@code onLangChange()} 内把下拉选中值与内存语言变量
 *        双向同步（见 LoginView.syncLangCombo），消除「选中文却变英文」的单向覆盖问题。</li>
 *  </ol>
 */
public final class AppConfig {

    private static final AppConfig INSTANCE = new AppConfig();

    /** 本地配置文件：仅保存语言、主题、加密登录信息（密钥独立，不混存）。 */
    private static final Path CONFIG = resolveConfigPath();

    /**
     * 解析配置文件路径，保证返回值为「工作目录可用时非 null」。
     * 优先取用户工作目录（user.dir）下的 app-config.properties；
     * 当工作目录获取失败或路径解析异常时，回退到 JVM 启动目录的相对路径，
     * 再异常则回退为 null —— 读写逻辑会判断 null 并加载默认配置、不阻断启动。
     */
    private static Path resolveConfigPath() {
        try {
            String dir = System.getProperty("user.dir");
            if (dir != null && !dir.trim().isEmpty()) {
                return Paths.get(dir, "app-config.properties").toAbsolutePath();
            }
        } catch (Exception ignored) {
            // 回退到下方默认解析
        }
        try {
            return Paths.get("app-config.properties").toAbsolutePath();
        } catch (Exception ignored) {
            return null; // 极端场景兜底，读写逻辑判断 null 后加载默认配置
        }
    }

    private Lang lang = Lang.ZH;
    private String theme = "fresh";
    private boolean mockDaoEnabled = false; // Mock 模式：开启后使用 MockUserDao 脱离后端自测
    private final List<LangChangeListener> listeners = new ArrayList<>();
    private final List<ThemeChangeListener> themeListeners = new ArrayList<>();

    private AppConfig() {
        loadConfig(); // 构造即加载持久化偏好（首次运行无文件则回退默认）
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    // ============ 配置加载 / 持久化 ============

    /** 从 app-config.properties 加载语言与主题（缺省 ZH / fresh）。重复调用安全。 */
    public void loadConfig() {
        Properties p = load();
        String lc = p.getProperty("ui.lang.default");
        this.lang = (lc != null && !lc.trim().isEmpty()) ? Lang.fromCode(lc) : Lang.ZH;
        String th = p.getProperty("ui.theme");
        this.theme = (th != null && !th.trim().isEmpty()) ? th.trim() : "fresh";
        // Mock 模式开关（缺省关闭，走原 InMemoryUserDao）
        this.mockDaoEnabled = Boolean.parseBoolean(p.getProperty("mock.dao.enabled", "false"));
    }

    // ============ 语言 ============

    public Lang getLang() {
        return lang;
    }

    /**
     * 设置当前语言并广播给所有监听者；无论是否发生变化都持久化选择，
     * 确保下拉「再次选中同一项」也会落盘（避免单向覆盖后重启丢失）。
     */
    public void setLang(Lang l) {
        if (l == null) {
            return;
        }
        if (l != lang) {
            this.lang = l;
            for (LangChangeListener listener : new ArrayList<>(listeners)) {
                listener.onLangChange();
            }
        }
        persistLang();
    }

    private void persistLang() {
        setProp("ui.lang.default", lang.getCode());
    }

    // ============ 主题 ============

    public String getTheme() {
        return theme;
    }

    public void setTheme(String t) {
        if (t == null) {
            return;
        }
        if (!t.equals(this.theme)) {
            this.theme = t;
            for (ThemeChangeListener l : new ArrayList<>(themeListeners)) {
                l.onThemeChange();
            }
        }
        persistTheme();
    }

    private void persistTheme() {
        setProp("ui.theme", theme);
    }

    // ============ 记住登录（加密持久化，不存明文） ============

    /** 勾选记住登录时调用：写用户名 + 密码 SHA-256 密文。 */
    public void setRemember(String username, String pwdHash) {
        setProp("ui.remember.user", username == null ? "" : username);
        setProp("ui.remember.pwdHash", pwdHash == null ? "" : pwdHash);
    }

    public String getRememberedUser() {
        return getProp("ui.remember.user", "");
    }

    public String getRememberedPwdHash() {
        return getProp("ui.remember.pwdHash", "");
    }

    public boolean hasRemembered() {
        return !getRememberedUser().isEmpty();
    }

    /** 未勾选记住登录时调用：清空本地加密凭据。 */
    public void clearRemember() {
        setProp("ui.remember.user", "");
        setProp("ui.remember.pwdHash", "");
    }

    // ============ Mock 模式开关（离线自测，不依赖后端） ============

    /** 是否开启 Mock 模式：开启后 BmiApplication 使用 MockUserDao（内存预置 test01/Test1234）。 */
    public boolean isMockDaoEnabled() {
        return mockDaoEnabled;
    }

    /** 设置 Mock 模式开关并持久化到 app-config.properties（mock.dao.enabled）。 */
    public void setMockDaoEnabled(boolean on) {
        this.mockDaoEnabled = on;
        setProp("mock.dao.enabled", Boolean.toString(on));
    }

    // ============ 监听器注册 ============

    public void addListener(LangChangeListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(LangChangeListener l) {
        listeners.remove(l);
    }

    public void addThemeListener(ThemeChangeListener l) {
        if (l != null && !themeListeners.contains(l)) {
            themeListeners.add(l);
        }
    }

    public void removeThemeListener(ThemeChangeListener l) {
        themeListeners.remove(l);
    }

    // ============ 属性文件读写辅助（容错，失败不阻断） ============

    private Properties load() {
        Properties p = new Properties();
        // ① 空值兜底：路径解析失败直接返回默认配置，绝不把 null 传入 Files.* 触发 NPE
        if (CONFIG == null) {
            return p;
        }
        // ② 安全兜底：isRegularFile 可能因权限/安全策略抛异常，按「文件不存在」处理
        boolean exists;
        try {
            exists = Files.isRegularFile(CONFIG);
        } catch (Exception ignored) {
            return p; // 文件不存在 / 无访问权限 -> 加载默认配置，不阻断启动
        }
        if (!exists) {
            return p; // 文件不存在同样加载默认配置，不抛异常
        }
        try (InputStream is = Files.newInputStream(CONFIG)) {
            p.load(is); // app-config.properties 仅存 ASCII 码值，无需 UTF-8
        } catch (IOException ignored) {
            // 读取失败返回空属性，不阻断启动
        }
        return p;
    }

    private void setProp(String key, String value) {
        Properties p = load();
        p.setProperty(key, value == null ? "" : value);
        // ③ 路径兜底：解析失败无法持久化，内存态仍生效，不阻断程序
        if (CONFIG == null) {
            return;
        }
        try {
            Path parent = CONFIG.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(CONFIG)) {
                p.store(os, "BMI local app config - do not commit (lang/theme/encrypted-login only)");
            }
        } catch (IOException ignored) {
            // 写入失败不阻断（如只读目录），内存态仍生效
        }
    }

    private String getProp(String key, String def) {
        String v = load().getProperty(key);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
