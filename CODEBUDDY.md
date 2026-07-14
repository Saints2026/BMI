# CODEBUDDY.md — BMI 体质评估与预测系统 · 项目宪章

> 本文件是项目的最高治理规范。`spec.md` / `plan.md` / `tasks.md` 及所有代码必须严格遵循本文件。
> 任何"无关技术"（未在本文件技术栈内列出的框架、库、语言）一律禁止引入。

## 1. 项目定位
- **名称**：BMI 体质评估与预测系统
- **目标**：录入身高体重 → 计算 BMI → 预测体脂 → 记录与趋势可视化 → 输出 AI 健康建议
- **仓库**：https://github.com/Saints2026/BMI.git

## 2. 技术栈边界（白名单，不得超出）
| 层 | 允许技术 | 禁止 |
|----|----------|------|
| 语言 | Java（JDK 8+） | 其它语言 |
| 架构 | 自研 MVC（controller / model / view） | Spring/SpringBoot 等重型框架 |
| 视图 | Java Swing（桌面 GUI，含折线图表绘制） | Web 前端、JSP、Android |
| 数据 | JDBC + MySQL（或 SQLite） | ORM 框架（MyBatis/Hibernate） |
| AI | HTTP 调用外部大模型接口（原生 HttpURLConnection / 轻量 HTTP 客户端） | 本地训练框架、TensorFlow 等 |
| 依赖管理 | 手动 jar 放 `lib/`（无 Maven/Gradle） | 引入构建工具改动结构 |
| 测试 | JUnit（jar 置于 `lib/`） | 其它测试框架 |

> 图表：折线图使用 Swing 自绘（`JPanel` + `Graphics2D`）或 JFreeChart（jar 入 `lib/`），二选一，不得引入 Web 图表库。

## 3. 目录约定（不得随意新增顶层目录）
```
src/
├── controller/     # 控制层：接收视图事件，调度 model，回写 view
├── model/
│   ├── ai/         # AI 健康建议 / 体脂预测接口封装
│   └── db/         # 数据访问（DAO）+ 实体
├── view/           # Swing 界面
└── test/           # JUnit 测试
docs/               # spec.md / plan.md / tasks.md
lib/                # 第三方 jar
```

## 4. 命名统一规则
- **包名**：全小写，`com.bmi.<layer>`，如 `com.bmi.controller`、`com.bmi.model.db`、`com.bmi.model.ai`、`com.bmi.view`。
- **类名**：大驼峰。分层后缀强制：
  - 控制器 `XxxController`；视图 `XxxView` / `XxxPanel` / `XxxFrame`；
  - 数据访问 `XxxDao`；实体 `Xxx`（如 `User`、`Record`）；
  - AI 封装 `XxxClient` / `XxxService`。
- **方法名**：小驼峰，动词开头（`getById`、`insert`、`calcBmi`、`predictBodyFat`）。
- **常量**：全大写下划线。**数据库表**：小写下划线（`t_user`、`t_record`）。
- **AI 密钥/DB 配置**：只读取 `ai-key.properties` / `db-config.properties`，**禁止硬编码密钥**（已 gitignore）。

## 5. 分层交互铁律
- 单向依赖：`view → controller → model`；model 不得反向依赖 view/controller。
- view 不直接访问 db 或 ai；一切经 controller 调度。
- db 层只做数据存取，不含业务计算；BMI/体脂计算归属 model 业务方法。

## 6. 文档规范（SDD）
- 三份文档存于 `docs/`：`spec.md`（做什么）、`plan.md`（怎么设计）、`tasks.md`（谁在何时做）。
- 验收标准必须**量化**（数值、边界、错误提示）。
- 文档之间保持一致：`plan.md` 覆盖 `spec.md` 全部功能；`tasks.md` 覆盖 `plan.md` 全部模块。

## 7. 交付与协作
- 团队 3 人：UI 模块、数据模块、AI 模块各一人（详见 tasks.md）。
- 开发周期：2026-07-14 ~ 2026-07-24。
- 提交前不得包含 `db-config.properties` / `ai-key.properties` / `*.log`（已 gitignore）。
