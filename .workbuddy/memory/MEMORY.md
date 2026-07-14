# BMI 项目 · 长期记忆

## 项目概述
- **名称**：BMI 体质评估与预测系统
- **类型**：Java 应用（MVC 架构），当前为初始脚手架（多数目录仅含 `.keep` 占位）
- **仓库**：https://github.com/Saints2026/BMI.git （origin，主机 GitHub）

## 目录结构（约定）
```
BMI/
├── docs/                 # 项目文档
├── lib/                  # 第三方 jar 依赖（无 Maven/Gradle，手动管理）
├── src/
│   ├── controller/       # 控制层：请求/流程调度
│   ├── model/
│   │   ├── ai/           # AI 模型集成（预测/评估）
│   │   └── db/           # 数据库访问层
│   ├── view/             # 视图层
│   └── test/             # 测试
└── README.md
```

## 技术约定（CODEBUDDY.md v1.0）
- **UI**：**JavaFX**（非 Swing），视图类 LoginView / InputView / ChartView / MainView；折线图用 `javafx.scene.chart.LineChart`。
- **语言/构建**：Java 8+；编译输出为 `target/ out/ bin/ *.class`（均已 gitignore，无构建工具配置文件，依赖放 `lib/`）。
- **数据层**：JDBC + **SQLite** 首选（兼容 MySQL）；表名 **`user`** + **`body_record`**（无前缀，MySQL 下 `` `user` `` 反引号包裹）；实体 User / BodyRecord。
- **AI 层**：封装类 **AiService**（model.ai），四类异常内部降级，HttpURLConnection 调用。
- **敏感配置**（已 gitignore，勿提交/勿泄露）：`db-config.properties`、`ai-key.properties`、`*.log`。
- **架构分层**：严格 MVC —— view(JavaFX) → controller → model(ai/db)，单向依赖。

## 课程知识库绑定（course-manager）
- **数据库**：`C:\Users\ASUS\.workbuddy\skills\course-manager\data\courses.db`
- **绑定范围**：本项目引用以下本地课程作为「Agentic 数字团队」知识库，后续可直接按 ID 调用（`course show/outline/export --id N`）：

| ID | 课程 | 章节 | 知识点 |
|----|------|------|--------|
| 3 | 01 Vibe Coding开发技术基础 | 4 | 13 |
| 4 | 02 Vibe Coding核心工作流 | 5 | 18 |
| 5 | 03 Vibe Coding项目宪章 | 6 | 15 |
| 6 | 04 Vibe Coding进阶对话技巧 | 6 | 16 |
| 7 | 05 Vibe Coding技能使用 | 5 | 17 |
| 8 | 01 Agentic数字团队-产品经理 | 5 | 14 |
| 2 | Agentic数字团队（总纲） | 11 | 66 |

- **说明**：课程素材存于 course-manager 本地库，非 BMI 项目内文件；BMI 项目 `docs/`、`lib/` 目前仅占位。

## 已安装技能（Skills）
- **course-manager** — 课程内容数据管理（大纲/章节/知识点/知识图谱），SQLite 持久化，导出 HTML/JSON。
- **agent-team-orchestration** — 多智能体团队编排（角色、任务生命周期、交接与评审）。
- **github** — 通过 `gh` CLI 操作 issue / PR / CI / api。
- ⚠️ 注：`jdbc` 与 `javafx-view-generator` 并非已安装 Skill，而是本项目已实现的两大**代码能力模块**（前者=DbUtil/JdbcRecordDao，后者=LoginView/InputView/ChartView/MainView），请勿误当作 Skill 调用。

## 项目快照索引（持久记忆）
- **SNAP-20260714-001** — 2026-07-14 17:18 生成。覆盖：7 篇文档（CODEBUDDY.md + docs/ 6 篇）+ 31 个 `.java` + 2 个 `.properties` + `db/mysql_init.sql` + `db-config.properties.example`；含全部文件 MD5 校验值。详见 `.workbuddy/memory/SNAPSHOT-20260714-001.md`。
- 下一快照编号规则：同日递增 `SNAP-20260714-002`；跨日重置为 `SNAP-YYYYMMDD-001`。
