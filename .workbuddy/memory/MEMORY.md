# BMI 项目长期记忆

## 项目概况
- **项目名称**：BMI 体质评估与预测系统
- **技术栈**：Java 8+ · JavaFX · JDBC + SQLite（首选）/ MySQL（兼容）· HttpURLConnection · JUnit
- **架构**：自研 MVC 单向依赖（view → controller → model.db / model.ai）
- **包路径**：`com.bmi.{view, controller, model.db, model.ai, test}`
- **依赖管理**：手动 jar 入 `lib/`，无 Maven/Gradle
- **治理宪章**：`CODEBUDDY.md`（技术栈白名单、命名规则、分层铁律）

## 文档体系
- `docs/spec.md` — 产品需求规格（FR-01~FR-07）
- `docs/plan.md` — 技术设计文档 SDD v1.0
- `docs/db_design.md` — 数据库详细设计 DBDD v1.1（含扩展字段）
- `docs/ai_design.md` — AI 接口设计
- `docs/ui_design.md` — UI 设计
- `docs/tasks.md` — 任务清单

## 数据库
- 表名：`user`（无 t_ 前缀，MySQL 保留字需反引号）、`body_record`
- user 表 7 字段：id, username, password_hash, salt, created_at, updated_at, status
- body_record 表 17 字段：id, user_id, measure_time, height, weight, bmi, body_fat + 10 扩展字段 + created_at
- 索引：idx_user_username(唯一)、idx_record_user_time(联合)、idx_record_user_id(分页)

## DAO 命名规范（plan.md v1.0 基线 + db_design.md v1.1 扩展）
- **UserDao**：insert / findByUsername / existsUsername
- **RecordDao**：insert / queryByUser / queryByUserPage / deleteById / update / findLatest
- **命名规则**：小驼峰方法名、XxxDao 后缀、实体大驼峰无后缀、表名小写下划线无前缀
