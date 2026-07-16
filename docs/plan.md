# BMI 体质评估与预测系统 · 技术设计文档（SDD）

> 本文件是 `spec.md` 的下游设计文档，必须严格遵循 `CODEBUDDY.md`（项目宪章）。
> 设计范围完整覆盖 `spec.md` 全部功能需求 FR-01~FR-07。

## 1. 文档信息

| 项 | 内容 |
|----|------|
| 文档标题 | BMI 体质评估与预测系统 技术设计文档 |
| 版本 | v1.0 |
| 日期 | 2026-07-14 |
| 上游文档 | `docs/spec.md`（产品需求规格，v1.0） |
| 治理宪章 | `CODEBUDDY.md`（技术栈白名单、目录约定、命名规则、分层铁律） |
| 编写角色 | 架构师 Agent |
| 技术栈边界 | Java 8+ · Swing · JDBC + SQLite · HttpURLConnection · JUnit（jar 入 `lib/`，无 Maven/Gradle） |

## 2. 设计概述

系统为**桌面端自研 MVC** 应用：用户通过 Swing 界面录入身高/体重/年龄/性别，经控制器调度业务计算与数据访问层，完成 BMI 计算（FR-03）、体脂预测（FR-04）、记录落库（FR-05）、折线图渲染（FR-06）与 AI 健康建议（FR-07），并实现账号注册登录（FR-01）与录入校验（FR-02）。

核心设计原则（源自宪章第 5 节分层铁律）：
- **单向依赖**：`view → controller → model`，model 层绝不反向依赖 view/controller。
- **职责隔离**：view 不直接访问 db/ai；db 层只做存取不含业务计算；BMI/体脂公式归属 model 业务类。
- **配置外置**：密钥读 `ai-key.properties`，数据库配置读 `db-config.properties`，源码零硬编码。

---

## 3. MVC 三层交互设计

### 3.1 分层依赖关系（单向）

```
                ┌─────────────────────────────┐
                │         view 层             │
                │  LoginView / MainFrame /    │
                │  RecordInputPanel /        │
                │  HistoryPanel / ChartPanel  │
                └──────────────┬──────────────┘
                               │ 仅调用 controller 公开方法（事件驱动）
                               ▼
                ┌─────────────────────────────┐
                │       controller 层         │
                │  UserController /           │
                │  RecordController /         │
                │  ChartController /          │
                │  AiController               │
                └──────────────┬──────────────┘
                               │ 调度 model 业务与存取
                ┌──────────────┴──────────────┐
                ▼                             ▼
   ┌────────────────────┐        ┌────────────────────┐
   │   model.db 层      │        │   model.ai 层      │
   │ UserDao/RecordDao  │        │ AiHealthClient     │
   │ User/Record 实体   │        │ CalcUtil           │
   │ JdbcUtil(JDBC)     │        │ HttpURLConnection  │
   └─────────┬──────────┘        └─────────┬──────────┘
             ▼                             ▼
        ┌─────────┐                  ┌──────────┐
        │ SQLite  │                  │ 外部大模型│
        └─────────┘                  └──────────┘
```

> 依赖方向永远是「上 → 下」。model 层不知道 view/controller 的存在；控制器通过方法返回值把结果交还视图，视图自行刷新。

### 3.2 端到端调用时序示例（录入身高体重 → 计算 BMI → 体脂 → 落库 → 刷新图表）

```
用户        RecordInputPanel   RecordController   CalcUtil   RecordDao      ChartController   ChartPanel
 │              │                    │                │                │                  │               │                │
 │─输入175cm/70kg/30岁/男─▶│            │                │                │                  │               │                │
 │              │─onSave(height,weight,age,gender)─▶│                │                │                  │               │                │
 │              │                    │─calcBmi(175,70)─▶│                │                  │               │                │
 │              │                    │◀── 22.9 / 正常 ──│                │                  │               │                │
 │              │                    │─predictBodyFat(22.9,30,1)─▶│                │                  │               │                │
 │              │                    │◀── 18.3% ──────│                │                  │               │                │
 │              │                    │─buildRecord(...)→组装 Record 实体│                │                  │               │                │
 │              │                    │─insert(record, userId)────────────────────────▶│                  │               │                │
 │              │                    │◀──── 落库成功(id) ─────────────│                  │               │                │
 │              │                    │─notify 视图刷新──────────────│                  │               │                │
 │              │◀── 显示 BMI/分级/体脂 ─│                │                │                  │               │                │
 │              │─刷新请求─▶│─getSeries(userId, metric)─▶│                  │─queryByUser─▶│               │                │
 │              │                    │                │                │                  │◀─记录集──────│                │
 │              │                    │                │                │                  │─repaint(metric)─▶│ 重绘折线图   │
```

---

## 4. 包结构与各包对外调用方法（接口清单）

物理目录与包名映射（宪章第 3/4 节）：源码根 `src/`，包路径 `com/bmi/<layer>/`。

| 逻辑目录 | 物理路径 | 包名 |
|----------|----------|------|
| 视图 | `src/com/bmi/view/` | `com.bmi.view` |
| 控制 | `src/com/bmi/controller/` | `com.bmi.controller` |
| 数据 | `src/com/bmi/model/db/` | `com.bmi.model.db` |
| AI | `src/com/bmi/model/ai/` | `com.bmi.model.ai` |
| 测试 | `src/com/bmi/test/` | `com.bmi.test` |

### 4.1 view 层（`com.bmi.view`）

| 类名 | 方法签名 | 说明 |
|------|----------|------|
| `LoginView` | `void showLogin()` | 显示登录/注册窗口 |
| `LoginView` | `void onLoginSuccess(User user)` | 登录成功后回调，跳转主界面 |
| `LoginView` | `void showError(String msg)` | 就地显示错误提示（如「用户名或密码错误」） |
| `MainFrame` | `void buildLayout(User user)` | 构建主窗口，组合各 Panel |
| `MainFrame` | `void switchToRecordTab()` / `void switchToHistoryTab()` / `void switchToChartTab()` | 切换功能页签 |
| `RecordInputPanel` | `void onSave(double heightCm, double weightKg, int age, int gender)` | 录入提交，触发控制器计算 |
| `RecordInputPanel` | `void showValidationError(String field, String msg)` | 输入框旁校验错误（如「身高应在 50–250cm 之间」） |
| `RecordInputPanel` | `void showResult(double bmi, String grade, double bodyFat)` | 展示 BMI/分级/体脂结果 |
| `HistoryPanel` | `void renderTable(List<Record> records)` | 渲染历史记录表格 |
| `HistoryPanel` | `void onDelete(long recordId)` | 请求删除指定记录 |
| `HistoryPanel` | `void showEmptyHint()` | 无记录提示「暂无历史记录」 |
| `ChartPanel` | `void setMetric(int metric)` | 设置当前指标（0=BMI/1=体重/2=体脂） |
| `ChartPanel` | `void repaint(List<Record> series)` | 重绘折线图（Swing 自绘） |
| `ChartPanel` | `void showChartHint(String msg)` | 数据不足提示（如「至少需 2 条记录才能显示趋势」） |

### 4.2 controller 层（`com.bmi.controller`）

| 类名 | 方法签名 | 说明 |
|------|----------|------|
| `UserController` | `boolean register(String username, String password, String confirm)` | 注册：校验唯一性+密码规则+加盐 SHA-256 落库 |
| `UserController` | `User login(String username, String password)` | 登录：比对散列，成功返回会话 User 对象 |
| `RecordController` | `Record createRecord(long userId, double h, double w, int age, int gender)` | 录入→计算 BMI/体脂→组装 Record→落库，返回完整记录 |
| `RecordController` | `List<Record> queryRecords(long userId, Date start, Date end)` | 按用户+时间区间查询（默认升序） |
| `RecordController` | `boolean deleteRecord(long userId, long recordId)` | 删除本人记录（越权拒绝） |
| `ChartController` | `List<Record> getSeries(long userId, int metric)` | 取历史记录集合供图表渲染 |
| `AiController` | `String getAdvice(long userId)` | 汇总最新指标+历史摘要→调用 AiHealthClient→返回建议文本 |

### 4.3 model.db 层（`com.bmi.model.db`）

| 类名 | 方法签名 | 说明 |
|------|----------|------|
| `UserDao` | `boolean insert(User user)` | 写入用户（username/passwordHash/salt） |
| `UserDao` | `User findByUsername(String username)` | 按用户名查用户（含散列与盐） |
| `UserDao` | `boolean existsUsername(String username)` | 用户名唯一性校验 |
| `RecordDao` | `long insert(Record record)` | 插入测量记录，返回自增主键 |
| `RecordDao` | `List<Record> queryByUser(long userId, Date start, Date end)` | 按用户+时间区间升序查询 |
| `RecordDao` | `int deleteById(long userId, long recordId)` | 按主键删除（限定 user_id 防越权） |
| `JdbcUtil` | `static Connection getConnection()` | 读取 `db-config.properties` 建立 JDBC 连接 |
| `JdbcUtil` | `static void close(Connection/Statement/ResultSet)` | 资源释放 |

**实体 `User`**（字段：`long id; String username; String passwordHash; String salt; Date createdAt;`）
**实体 `Record`**（字段：`long id; long userId; Date measureTime; double height; double weight; double bmi; double bodyFat; Date createdAt;`）

### 4.4 model.ai 层（`com.bmi.model.ai`）

| 类名 | 方法签名 | 说明 |
|------|----------|------|
| `CalcUtil` | `double calcBmi(double weightKg, double heightCm)` | BMI=weight/(h/100)²，保留 1 位小数 |
| `CalcUtil` | `BmiCategory classifyBmi(double bmi)` | 中国标准分级，返回枚举（UNDERWEIGHT/NORMAL/OVERWEIGHT/OBESE） |
| `CalcUtil` | `double predictBodyFat(double bmi, int age, int gender)` | Deurenberg 公式，保留 1 位小数 |
| `BmiCategory` | 枚举（`com.bmi.model` 包） | BMI 分级枚举，view 层 i18n 据此查表获取中文文案 |
| `AiHealthClient` | `String requestAdvice(AiRequest req)` | 组装 JSON→HttpURLConnection 调用→解析→降级 |
| `AiHealthClient` | `AiRequest buildRequest(User u, Record latest, List<Record> history)` | 构造请求（system/userMetrics/historySummary/modelParams） |

> `CalcUtil` 为纯业务类（无 DB/AI 依赖），归属 `com.bmi.model.ai` 包，可由 JUnit 直接单测，满足「db 层不含业务计算、计算归属 model 业务方法」铁律。`BmiCategory` 枚举位于 `com.bmi.model` 包，不在业务层硬编码中文分级文案。

---

## 5. 数据库表结构

选型 **SQLite**（见第 7 节），以下为建表 DDL（字段命名全小写下划线，符合宪章）。

```sql
-- 用户表
CREATE TABLE t_user (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,
    password_hash TEXT    NOT NULL,
    salt          TEXT    NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 测量记录表
CREATE TABLE t_record (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL,
    measure_time DATETIME NOT NULL,
    height       NUMERIC(5,2) NOT NULL,
    weight       NUMERIC(5,2) NOT NULL,
    bmi          NUMERIC(4,1) NOT NULL,
    body_fat     NUMERIC(4,1) NOT NULL,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
);

-- 索引：按用户+时间查询加速（AC-05 <1s）
CREATE INDEX idx_record_user_time ON t_record(user_id, measure_time);
```

字段约束说明：`t_user.username` 唯一且非空；`t_record.user_id` 为外键关联 `t_user.id`，删除用户级联删记录；`measure_time/height/weight/bmi/body_fat` 均非空。

---

## 6. AI 接口传参规范

### 6.1 请求构造

`AiHealthClient.buildRequest` 内部 `AiRequest` 结构（对应 spec FR-07 输入）：

| 字段 | 含义 |
|------|------|
| `systemPrompt` | 系统提示词（健康顾问角色设定） |
| `userMetrics` | 最新指标：BMI、体脂率、体重、年龄、性别 |
| `historySummary` | 近 N 条历史趋势摘要（如「近 5 次 BMI 由 24.1 降至 22.9」） |
| `modelParams` | 模型参数：model 名、temperature、maxTokens |

**调用方式**：原生 `HttpURLConnection`，POST JSON；连接超时与读取超时均设为 **10000ms（10s）**；API Key **仅**从 `ai-key.properties` 读取（key=`api.key`，另含 `api.url`、`api.model`）。

**请求 JSON 示例**（OpenAI Chat Completions 兼容格式）：

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "system", "content": "你是一位严谨的中文健康顾问，请按『饮食/运动/健康』三段给出建议。"},
    {"role": "user", "content": "最新指标：BMI=22.9(正常)，体脂率=18.3%，体重=70kg，年龄=30，性别=男。历史趋势：近5次BMI由24.1降至22.9，呈下降趋势。"}
  ],
  "temperature": 0.7,
  "max_tokens": 800
}
```

### 6.2 响应解析与降级

**响应 JSON 示例**：

```json
{
  "choices": [
    { "message": { "role": "assistant", "content": "【饮食】...【运动】...【健康】..." } }
  ]
}
```

解析路径：`root.choices[0].message.content` 取建议文本。

**降级策略与错误码处理**：

| 场景 | 处理 |
|------|------|
| `ai-key.properties` 缺失/key 为空 | 抛出 `AiConfigException`，视图提示「AI 服务未配置，请联系管理员」 |
| 连接/读取超时（>10s） | 捕获 `SocketTimeoutException`，返回降级文案「AI 建议请求超时，请稍后重试」 |
| 网络异常 / HTTP 非 2xx | 捕获 `IOException` 或状态码异常，返回「暂时无法获取 AI 建议，请检查网络或稍后再试」 |
| 响应体为空 / content 缺失 | 视为失败，走同上降级文案 |

> AI 调用失败**绝不阻断**主流程（录入/图表照常），仅建议区显示降级提示。

---

## 7. 模块命名统一规则（落实宪章）

| 类别 | 规则 | 正例 | 反例 |
|------|------|------|------|
| 包名 | 全小写 `com.bmi.<layer>` | `com.bmi.model.db` | `com.bmi.Model.DB` |
| 控制器类 | 后缀 `XxxController` | `RecordController` | `RecordMgr` / `RecordService`（控制器语境） |
| 视图类 | 后缀 `XxxView`/`XxxPanel`/`XxxFrame` | `LoginView`、`ChartPanel`、`MainFrame` | `ChartUI` |
| DAO 类 | 后缀 `XxxDao` | `UserDao` | `UserRepository` |
| 实体类 | 大驼峰无后缀 | `User`、`Record` | `UserEntity` |
| AI 封装 | 后缀 `XxxClient`/`XxxService` | `AiHealthClient` | `AiHelper` |
| 方法名 | 小驼峰、动词开头 | `calcBmi`、`insert`、`getById` | `BmiCalculation`、`InsertData` |
| 常量 | 全大写下划线 | `DEFAULT_TIMEOUT_MS` | `defaultTimeout` |
| 表名 | 小写下划线 | `t_user`、`t_record` | `TUser`、`UserTable` |
| 配置键 | 小写下划线 | `api.key`、`db.url` | `API_KEY` |

---

## 8. 技术选型说明

| 维度 | 选型 | 理由 | 是否在白名单 |
|------|------|------|--------------|
| 图表 | **Swing 自绘**（`JPanel` + `Graphics2D`） | 零额外依赖、完全在白名单内、避免引入第三方 jar；数据点规模小（个人记录），自绘足够；满足 FR-06 切换/重绘 <500ms | ✅（宪章允许二选一） |
| 数据库 | **SQLite** | 文件型零配置、桌面端无需起服务、JDBC 驱动 jar 入 `lib/` 即可；满足 FR-05 查询 <1s；符合「JDBC + MySQL 或 SQLite」白名单 | ✅ |
| 业务计算 | 纯 Java 类 `CalcUtil`（`com.bmi.model.ai`） | 无外部库，公式确定性高，易单测 | ✅ |
| AI 调用 | 原生 `HttpURLConnection` | 宪章指定，无 OkHttp 等额外依赖 | ✅ |
| 测试 | JUnit（jar 入 `lib/`） | 宪章指定 | ✅ |
| 依赖管理 | 手动 jar 入 `lib/` | 无 Maven/Gradle | ✅ |

> 明确**禁止**：Web 前端、Spring/SpringBoot、MyBatis/Hibernate、TensorFlow、JSP、Android、任何构建工具改造。本设计未引入上述任一技术。

---

## 9. FR 覆盖追溯

| 功能 | 对应类/接口 | 对应表/接口 |
|------|-------------|-------------|
| FR-01 登录注册 | `LoginView`、`UserController`、`UserDao`、`User` | `t_user` |
| FR-02 身高体重录入 | `RecordInputPanel`、`RecordController` | — |
| FR-03 BMI 计算分级 | `CalcUtil`、`RecordController` | `t_record.bmi` |
| FR-04 体脂预测 | `CalcUtil`、`RecordController` | `t_record.body_fat` |
| FR-05 历史记录管理 | `HistoryPanel`、`RecordController`、`RecordDao`、`Record` | `t_record` |
| FR-06 动态折线图 | `ChartPanel`、`ChartController` | 查询 `t_record` |
| FR-07 AI 健康建议 | `AiController`、`AiHealthClient` | 外部大模型接口 |

> 7 个 FR 全部有对应设计与类/表/接口映射，无遗漏。
