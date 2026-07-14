# CODEBUDDY.md — BMI 体质评估与预测系统 · 项目宪章（v1.0）

> 本文件是项目的最高治理规范。`spec.md` / `plan.md` / `ai_design.md` / `db_design.md` 及所有代码必须严格遵循本文件。
> 任何"无关技术"（未在本文件技术栈内列出的框架、库、语言）一律禁止引入。

---

## 1. 项目定位
- **名称**：BMI 体质评估与预测系统
- **目标**：录入身高体重 → 计算 BMI → 预测体脂 → 记录与趋势可视化 → 输出 AI 健康建议
- **仓库**：https://github.com/Saints2026/BMI.git

## 2. 技术栈边界（白名单，不得超出）

| 层 | 允许技术 | 禁止 |
|----|----------|------|
| 语言 | Java（JDK 8+） | 其它语言 |
| 架构 | 自研 MVC（controller / model / view） | Spring/SpringBoot 等重型框架 |
| 视图 | **JavaFX**（桌面 GUI，含 `javafx.scene.chart.LineChart` 绘制折线图） | Web 前端、JSP、Android、Swing |
| 数据 | JDBC + SQLite（首选）/ MySQL | ORM 框架（MyBatis/Hibernate） |
| AI | HTTP 调用外部大模型接口（原生 HttpURLConnection） | 本地训练框架、TensorFlow 等 |
| 依赖管理 | 手动 jar 放 `lib/`（无 Maven/Gradle） | 引入构建工具改动结构 |
| 测试 | JUnit（jar 置于 `lib/`） | 其它测试框架 |

> 图表：折线图使用 **JavaFX LineChart**（`javafx.scene.chart.*`），零额外依赖；不得引入 JFreeChart 或 Web 图表库。

## 3. 目录约定（不得随意新增顶层目录）

```
src/
├── com/bmi/
│   ├── controller/     # 控制层：接收视图事件，调度 model，回写 view
│   ├── model/
│   │   ├── ai/         # AI 封装（AiService + AiRequest/AiHealthResult/AiException）
│   │   ├── db/         # 数据访问（DAO 接口 + 实现 + 实体）
│   │   └── User.java / BodyRecord.java  # 实体（独立存放）
│   └── view/           # JavaFX 界面（LoginView / InputView / ChartView）
└── test/               # JUnit 测试
docs/                   # spec.md / plan.md / tasks.md / ai_design.md / db_design.md
lib/                    # 第三方 jar（含 JavaFX SDK / JDBC 驱动 / JUnit）
```

## 4. 命名统一规则

### 4.1 包名与类名

| 类别 | 规则 | 正例 | 反例 |
|------|------|------|------|
| 包名 | 全小写 `com.bmi.<layer>` | `com.bmi.controller` | `com.bmi.Controller` |
| 控制器类 | 后缀 `XxxController` | `UserController` | `UserCtrl` |
| 视图类 | 后缀 `XxxView` | `LoginView`、`InputView`、`ChartView` | `LoginUI` |
| DAO 类 | 后缀 `XxxDao` | `UserDao`、`RecordDao` | `UserRepository` |
| 实体类 | 大驼峰无后缀 | `User`、`BodyRecord` | `UserEntity` |
| AI 封装 | 后缀 `XxxService` / `XxxClient` | `AiService` | `AiHelper` |
| 异常类 | 后缀 `Exception` | `AiConfigException` extends AiException | — |

### 4.2 方法名与常量

- **方法名**：小驼峰，动词开头（`getById`、`insert`、`calcBmi`、`predictBodyFat`、`requestAdvice`）。
- **常量**：全大写下划线（`CONNECT_TIMEOUT_MS`、`READ_TIMEOUT_MS`、`MAX_RETRY`）。

### 4.3 数据库表名

| 规则 | 正例 | 反例 |
|------|------|------|
| 小写下划线（**无前缀**） | `user`、`body_record` | `TUser`、`UserTable`、`t_user` |
| 外键引用 | `body_record.user_id → user.id`（ON DELETE CASCADE） | — |

> ⚠️ **历史说明**：早期设计文档曾采用 `t_` 前缀命名（`t_user`/`t_record`）。经 v1.0 统一，正式表名定为 `user` 与 `body_record`（见 `docs/db_design.md`）。所有新建 SQL、DAO 引用均以此为准。MySQL 下 `user` 为保留字，建表与查询一律用反引号 `` `user` `` 包裹。

### 4.4 配置键

小写下划线（`api.key`、`api.url`、`api.model`、`db.url`）；密钥仅读 `ai-key.properties` / `db-config.properties`，**禁止硬编码**（两文件已 gitignore）。

## 5. 分层交互铁律

- 单向依赖：`view → controller → model`；model 不得反向依赖 view/controller。
- view 不直接访问 db 或 ai；一切经 controller 调度。
- db 层只做数据存取，不含业务计算；BMI/体脂计算归属 controller 编排或 model 业务方法。
- AI 层（model.ai）封装全部 HTTP 细节，对 Controller 仅暴露「文本获取」能力；四类异常（断网/超时/参数为空/服务器报错）在 AI 层内部捕获并转换为降级结果，不向上抛传输异常（详见 `ai_design.md §5`）。

## 6. 文档规范（SDD）

文档存于 `docs/`：

| 文档 | 角色 | 内容 |
|------|------|------|
| `spec.md` | 产品经理 Agent | 产品需求规格（US/FR/AC）——做什么 |
| `plan.md` | 架构师 Agent | 技术设计（MVC/包结构/表/AI接口/选型）——怎么设计 |
| `tasks.md` | 架构师 Agent | 任务拆分与排期（模块分工/每日计划）——谁何时做 |
| `ai_design.md` | 架构师 Agent | AI 接口详细设计（入参/返回/四类异常/send骨架） |
| `db_design.md` | DBA Agent | 数据库详细设计（E-R图/双方言SQL/字段注释） |

验收标准必须**量化**（数值、边界、错误提示文案）。

文档之间保持一致：
- `plan.md` 覆盖 `spec.md` 全部 FR；
- `ai_design.md` 是 `plan.md` §5/§6 的下游细化，覆盖 FR-07 与 AC-07；
- `db_design.md` 是 `plan.md` §4 的落地版本，覆盖 FR-01/FR-05 的建表 DDL；
- `tasks.md` 覆盖 `plan.md` 全部模块。

## 7. 交付与协作

- 团队 3 人：UI 模块（JavaFX）、数据模块（JDBC+SQLite）、AI 模块（HttpURLConnection）各一人（详见 `tasks.md`）。
- 开发周期：2026-07-14 ~ 2026-07-24。
- 提交前不得包含 `db-config.properties` / `ai-key.properties` / `*.log`（已 gitignore）。
