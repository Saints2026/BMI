# BMI 系统 · 第三方依赖清单（ui_lib_record）

> 作用：项目运行 / 测试所需的全部第三方 jar 的**唯一权威记录**。
> 维护规则：新增或升级依赖时，**先改本文件再放 jar**，保持「文档=lib 实况」。
> 适用架构：Java 8+ · 自研 MVC · JavaFX 视图 · JDBC（SQLite 首选 / MySQL 兼容）· HttpURLConnection 调 AI（无额外 jar）· JUnit 测试。
> 依赖管理：手动 jar 放 `lib/`，**无 Maven/Gradle**（宪章第 2 节技术栈白名单）。

---

## 1. 依赖总览

| # | 依赖 | 类别 | 必需 | 用途 | 默认版本 | 关键 jar |
|---|------|------|------|------|----------|-----------|
| 1 | **OpenJFX（JavaFX SDK）** | UI 运行时 | ✅ 运行必选 | JavaFX 桌面 GUI（`LoginView`/`InputView`/`ChartView`/`MainView` + `LineChart` 折线图） | 21.0.11 (LTS) | `javafx-base` `javafx-controls` `javafx-graphics` `javafx-fxml`（+ 原生 dll） |
| 2 | **mysql-connector-j** | JDBC 驱动 | ⚠️ 用 MySQL 时必选 | `db.type=mysql` 连接 MySQL（示例配置默认走 mysql） | 8.4.0 (LTS) | `mysql-connector-j-8.4.0.jar` |
| 3 | **sqlite-jdbc** | JDBC 驱动 | ⚠️ 用 SQLite 时必选 | `db.type=sqlite` 连接 SQLite（**宪章首选、零服务**，默认推荐） | 3.47.1.0 | `sqlite-jdbc-3.47.1.0.jar` |
| 4 | **JUnit 5**（Jupiter） | 测试 | ⚪ 仅测试 | `src/test/` 单元测试运行 | 5.11.4 / Platform 1.11.4 | `junit-platform-console-standalone-1.11.4.jar` |

> 说明：
> - 第 2、3 项为**二选一**驱动——SQLite 是宪章首选（无需安装数据库服务），MySQL 用于服务端迁移场景。两者 jar 可同时放入 `lib/`，由 `db-config.properties` 的 `db.type` 切换。
> - 第 4 项仅测试需要，不参与打包运行；可放 `lib/` 也可放 `lib/test/`。
> - **AI 模块不引入任何 jar**：`AiService` 用原生 `HttpURLConnection` 调外部大模型（宪章白名单），无需 OkHttp/JSON 库。
> - **被排除的污染依赖**（来自已隔离的 feature-ai 分支，本项目**禁止**引入）：`json-20240303.jar`、`okhttp-4.12.0.jar`、`okio-3.6.0.jar` —— 它们是 `AiHealthClient` 的残留，与宪章「原生 HttpURLConnection」冲突，勿放入 `lib/`。

---

## 2. 逐项详情（版本 / 下载 / 文件名）

### 2.1 OpenJFX（JavaFX SDK）— 21.0.11 (LTS)
- **为何此版本**：JavaFX 自 11 起独立于 JDK 发布；21 为当前 LTS，需 **JDK 21**（本工程技术栈限定 Java 21 + JavaFX 21）。
- **下载（Gluon 官方 SDK，含 jar + 原生库）**：
  - 落地页：<https://openjfx.io/download.html>
  - Windows x64 直链：<https://download2.gluonhq.com/openjfx/21.0.11/openjfx-21.0.11_windows-x64_bin-sdk.zip>
  - 其它平台：将文件名中 `windows-x64` 换为 `linux-x64` / `osx-x64` / `osx-aarch64`。
- **解压后 `lib/` 内含**（需全部保留，含 `.dll` 原生库）：
  `javafx.base.jar` `javafx.controls.jar` `javafx.fxml.jar` `javafx.graphics.jar`
  `javafx.media.jar` `javafx.swing.jar` `javafx.web.jar` `javafx-swt.jar` + `*.dll`（prism / glass / javafx_font 等）
- **本项目最小必要集**：`javafx-base` + `javafx-controls`（含 LineChart）+ `javafx-graphics` + `javafx-fxml`。建议整包保留以免缺模块。
- **已落地 `lib/` 的逐 jar 五项字段清单（名称 / 版本 / 用途 / 下载来源 / 兼容 JDK·JavaFX）**：

  | jar 名称 | 版本 | 用途 | 下载来源 | 兼容 JDK · JavaFX |
  |----------|------|------|----------|-------------------|
  | javafx-base.jar | 21.0.11 | JavaFX 基础模块（属性绑定、集合、并发、事件总线） | Gluon 官方 OpenJFX 21.0.11 SDK | JDK 21 · JavaFX 21 |
  | javafx-controls.jar | 21.0.11 | UI 控件（Button/Label/TextField/ComboBox/密码框/图表控件） | Gluon 官方 OpenJFX 21.0.11 SDK | JDK 21 · JavaFX 21 |
  | javafx-graphics.jar | 21.0.11 | 图形与窗口（Stage/Scene/CSS、Prism 渲染） | Gluon 官方 OpenJFX 21.0.11 SDK | JDK 21 · JavaFX 21 |
  | javafx-fxml.jar | 21.0.11 | FXML 加载（本项目以代码构建为主，保留以兼容） | Gluon 官方 OpenJFX 21.0.11 SDK | JDK 21 · JavaFX 21 |
  | javafx-media.jar | 21.0.11 | 音视频媒体播放（预留能力） | Gluon 官方 OpenJFX 21.0.11 SDK | JDK 21 · JavaFX 21 |
  | javafx.swing.jar | 21.0.11 | Swing/JavaFX 互操作（预留能力） | Gluon 官方 OpenJFX 21.0.11 SDK | JDK 21 · JavaFX 21 |
  | javafx.web.jar | 21.0.11 | WebView 内嵌浏览器（预留能力） | Gluon 官方 OpenJFX 21.0.11 SDK | JDK 21 · JavaFX 21 |
  | javafx-swt.jar | 21.0.11 | SWT/JavaFX 桥接（预留能力） | Gluon 官方 OpenJFX 21.0.11 SDK | JDK 21 · JavaFX 21 |

  > 全部 8 个 jar 已复制至 `lib/`，目录内**无任何 okhttp / okio / json 等污染依赖**（AI 模块仅用原生 `HttpURLConnection`）。

### 2.2 mysql-connector-j — 8.4.0 (LTS)
- **为何此版本**：8.x 兼容 Java 8+，且 8.4 为 MySQL 官方 LTS；驱动类 `com.mysql.cj.jdbc.Driver`（8.x JDBC4 自动注册，无需 `Class.forName`）。
- **下载**：
  - 官方：<https://dev.mysql.com/downloads/connector/j/>（选 *Platform Independent*, *Architecture Independent*, ZIP）
  - Maven Central 单 jar 直链：<https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar>
- **文件名**：`mysql-connector-j-8.4.0.jar`（单文件，依赖已内嵌，无需额外 jar）。

### 2.3 sqlite-jdbc — 3.47.1.0
- **为何此版本**：Xerial 维护的 SQLite JDBC，单 jar 内嵌各平台原生库（含 Windows `.dll`），`db.type=sqlite` 零配置运行。
- **下载**：
  - GitHub Releases：<https://github.com/xerial/sqlite-jdbc/releases>
  - Maven Central 单 jar 直链：<https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.47.1.0/sqlite-jdbc-3.47.1.0.jar>
- **文件名**：`sqlite-jdbc-3.47.1.0.jar`（单文件，原生库已内置）。

### 2.4 JUnit 5（Jupiter）— 5.11.4 / Platform 1.11.4
- **为何此版本**：JUnit 5 当前稳定线；用 **console-standalone** 单 jar 即可运行（已聚合 api + engine + launcher + opentest4j + apiguardian），**无需构建工具**。
- **下载**：
  - 官网：<https://junit.org/junit5/>
  - Maven Central 单 jar 直链：<https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar>
- **文件名**：`junit-platform-console-standalone-1.11.4.jar`
- **备选（拆分多 jar，若不用 standalone）**：`junit-jupiter-api` `junit-jupiter-engine` `junit-jupiter-params` `junit-platform-commons` `junit-platform-engine` `junit-platform-launcher` `junit-platform-console` + `opentest4j` + `apiguardian-api`。首选 standalone，省心。

---

## 3. `lib/` 存放规范

### 3.1 目录结构（推荐）
```
lib/
├── .keep
├── javafx/                                   # JavaFX SDK 解压后的 lib 内容（jars + 原生 dll）
│   ├── javafx.base.jar
│   ├── javafx.controls.jar
│   ├── javafx.fxml.jar
│   ├── javafx.graphics.jar
│   ├── ...（其余按需）
│   └── *.dll                                 # Windows 原生库（必须随 jar 一起）
├── mysql-connector-j-8.4.0.jar               # MySQL 驱动（db.type=mysql）
├── sqlite-jdbc-3.47.1.0.jar                  # SQLite 驱动（db.type=sqlite，宪章首选）
├── junit-platform-console-standalone-1.11.4.jar  # 测试（仅测试用，可放 lib/test/）
└── ui_lib_record.md                         # ← 本清单
```
> 要点：
> 1. **JavaFX 单独建 `lib/javafx/` 子目录**：JavaFX 21 必须以 **module-path** 方式加载（classpath 方式在 11+ 失效），原生 `.dll` 必须和 jar 同目录。
> 2. **JDBC 驱动平铺**在 `lib/` 根：JDBC4 自动注册，`-cp "lib/*"` 即可被 `DriverManager` 发现。
> 3. **禁止**放入 `json-*` / `okhttp-*` / `okio-*` 等污染 jar（见 §1 末）。
> 4. 所有 jar **版本号写进文件名**（如 `-8.4.0`），便于和本清单核对、避免「同名不同版」混乱。

### 3.2 版本与 JDK 兼容矩阵
| 组合 | JDK | JavaFX | mysql-connector-j | 说明 |
|------|-----|--------|-------------------|------|
| ✅ 推荐 | **21 (LTS)** | 21.0.2 | 8.4.0 | 现代长期支持，最省心 |
| ✅ 兼容 | 17 (LTS) | 17.0.x | 8.4.0 | 同代 LTS |
| ⚠️ 仅限必须 JDK 8 | **8** | **8**（OpenJFX 8） | **8.0.33** | JavaFX 8 随 Oracle JDK 8 捆绑；mysql 驱动改用 8.0.x |

> 若团队统一用 JDK 8，则第 1 项改用 **JavaFX 8** SDK、第 2 项改用 `mysql-connector-j-8.0.33.jar`，其余不变。

### 3.3 `.gitignore` 建议（可选）
当前 `.gitignore` **未忽略** `lib/*.jar`——意味着 jar 会随仓库提交。两种策略二选一：
- **A（课设交付推荐）**：保持现状，jar 入库，克隆即可运行；本清单（`ui_lib_record.md`）记录版本便于复现。
- **B（纯源码仓库）**：在 `.gitignore` 追加 `lib/*.jar`（保留 `lib/.keep`），仅提交本清单，他人按 §2 链接自行下载。

---

## 4. 编译 / 运行 / 测试 命令示例

> 假设：JDK 21 + JavaFX 21 已就位，`db.type=sqlite`（宪章首选，免装数据库）。

```bash
# ① 编译（源码含中文注释，必须 -encoding UTF-8）
javac -encoding UTF-8 -cp "lib/*" -d out ^
      src/com/bmi/**/*.java

# ② 运行（JavaFX 走 module-path，业务代码走 classpath）
java --module-path lib/javafx ^
     --add-modules javafx.controls,javafx.fxml ^
     -cp "out;lib/*" ^
     com.bmi.BmiApplication

# ③ 切换 MySQL（改 db-config.properties：db.type=mysql + 填 url/user/password）
#    jar 已在 lib/，命令同上，无需改代码。

# ④ 运行测试（JUnit standalone，扫描 out 下 *Test 类）
java -jar lib/junit-platform-console-standalone-1.11.4.jar ^
     --class-path "out;lib/*" ^
     --scan-class-path --include-classname ".*Test"
```

> 注：`DbUtil.getConnection()` 默认 `db.type=mysql`；若想零服务直接跑，先把 `db-config.properties` 设为
> `db.type=sqlite` + `db.url=jdbc:sqlite:bmi.db`（`bmi.db` 首次连接自动创建，`PRAGMA foreign_keys=ON` 由 `DbUtil` 自动执行）。

---

## 5. 依赖核对清单（每次提交前自检）
- [ ] `lib/javafx/` 含 `javafx-base/controls/graphics/fxml` 四个 jar **且**含 `*.dll`
- [ ] `lib/mysql-connector-j-8.4.0.jar` 存在（用 MySQL 时）
- [ ] `lib/sqlite-jdbc-3.47.1.0.jar` 存在（用 SQLite 时，宪章首选，建议常备）
- [ ] `lib/junit-platform-console-standalone-1.11.4.jar` 存在（测试时）
- [ ] **无** `json-*` / `okhttp-*` / `okio-*` 污染 jar
- [ ] 本文件版本号与实际 jar 文件名一致
- [ ] 中文编译已加 `-encoding UTF-8`
