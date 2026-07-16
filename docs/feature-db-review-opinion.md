# feature-db 分支 Review 意见（致后端负责人 ZZD66800）

> 审核人：UI 负责人　审核时间：2026-07-16
> 审核方式：**纯静态只读**（`git fetch` + `git archive` 隔离抽取），未合并、未修改 `feature-db`、未改本地工作树。
> 审核依据：`CODEBUDDY.md` · `docs/db_design.md` · `docs/spec.md` · `ui_lib_record.md`
> 关联记录：详见 `docs/ui_verification_report.md` 之「后端分支PR校验」章节。

---

## 0. 一句话结论

**② 严重违规 —— 阻断合并 / 阻断整支 cherry-pick。**

DB 模块代码本身质量达标（独立编译 0 错误、零反向 UI 依赖、实体/异常规范），但本分支**误删了整个 UI / controller / ai / i18n 层**，且 **DAO 接口与 UI 控制器契约 0% 兼容**。直接合入 `main` 会抹除 UI 全量工作并导致 UI 层编译失败。

---

## 1. 审核方式说明（为何可信）

| 项 | 说明 |
|----|------|
| 分支状态 | `main` HEAD `4b89bd8`；`feature-db` HEAD `432fcf8`（`feat(db): 完成数据库模块开发`，ZZD66800，2026-07-16 09:17）；merge-base = `4b89bd8` ⇒ 单提交、可快进 |
| 安全抽取 | 本地工作树 dirty（含 UI 工作），未 `checkout`，用 `git archive origin/feature-db \| tar -x` 只读隔离抽取 |
| 变更规模 | 48 files, +3454 / −4295 |
| 未做操作 | 未 merge / 未 cherry-pick / 未 commit / 未 push / 未改 feature-db |

---

## 2. 严重问题（S1–S3，必须修复，否则拒绝合并）

### S1 · 分支删除了整个 UI / controller / ai / i18n 层（25 文件）

**证据**（diff `4b89bd8 → 432fcf8` 删除清单）：

| 层 | 被删文件 |
|----|----------|
| view | `BmiApplication.java` `ChartView.java` `InputView.java` `LoginView.java` `MainView.java` `ViewUtil.java` |
| i18n | `AppConfig.java` `I18n.java` `Lang.java` `LangChangeListener.java` `ui_en.properties` `ui_zh.properties` |
| controller/ai | `controller/AiController.java` `client/AiHealthClient.java` `model/ai/{BmiCalculator,BodyFatCalculator,BodyRecord,TestAiService}.java` |
| exception | `AiConfigException.java` `AiException.java` |
| 配置/文档 | `ai-key.properties` `db-config.properties.example` `docs/ai_config.md` **`ui_lib_record.md`** |

**后果**：feature-db 是 `main` HEAD 直接子提交，快进合并即把 `main` 工作树变成 feature-db 的样子 → **UI 负责人全部工作被抹除**。
**要求**：feature-db **只应新增 DB 模块**，不得删除任何 UI / controller / ai / i18n / exception / client / 配置 / 文档文件。

### S2 · DAO 接口与 UI 控制器 0% 兼容

**证据**（UI 控制器在 `main`，DB 实现在 feature-db）：

`UserController.java:4` → `import com.bmi.model.db.UserDao;`
`RecordController.java:4` → `import com.bmi.model.db.RecordDao;`

| UI 控制器期望（main） | feature-db 实际提供 | 匹配 |
|------------------------|----------------------|------|
| `UserDao` (类) | `UserDAO` (类, L22) | ❌ 大小写 |
| `userDao.insert(user)` | `register(user)` (L87) | ❌ 方法名 |
| `userDao.findByUsername(name)` | 无（仅 `login` L31） | ❌ 缺失 |
| `userDao.existsUsername(name)` | `existsUsername` 为 **private** (L174) | ❌ 可见性 |
| `RecordDao` (类) | `RecordDAO` (类, L21) | ❌ 大小写 |
| `recordDao.insert(r)` | `addRecord(r)` (L29) | ❌ 方法名 |
| `recordDao.queryByUser(uid,start,end)` | `queryByUserAndTimeRange(uid,start,end)` (L271) | ❌ 方法名 |
| `recordDao.queryByUserPage(uid,page,size)` | `queryByUserPage(uid,start,end,offset,limit)` (L300) | ❌ 参数形态 |
| `recordDao.deleteById(id)` | `deleteRecord(id,userId)` (L216) | ❌ 方法名+签名 |
| `recordDao.update(record)` | `updateRecord(record)` (L161) | ❌ 方法名 |

**后果**：类名大小写 + 方法名 + 分页参数三重不匹配 → **合并后 UI 层编译失败**（编译期错误，非运行时）。
**要求**：按上表逐项对齐命名与签名。

### S3 · 缺失 `predictBodyFat`

**证据**：`RecordController.java:44 / :109 / :123` 调用并定义 `predictBodyFat(bmi, age, gender)`；feature-db `CalcUtil` **无此方法**。
**后果**：合并后 `RecordController` 编译失败 + 体脂预测能力缺口。
**要求**：在 model 业务类（或 controller 编排处）增补 `predictBodyFat(bmi,age,gender)`，与 `RecordController` 契约一致。

---

## 3. 风险问题（R1–R11，建议修复，不单独阻断）

| ID | 问题 | 位置 | 依据 |
|----|------|------|------|
| R1 | 分页无边界校验，参数形态 (offset,limit) ≠ (page,size) | `RecordDAO.java:300` vs `RecordController:73/80` | CODEBUDDY #4.2 |
| R2 | 无 PageResult / 分页 DTO | `RecordDAO.queryByUserPage` | db_design.md |
| R3 | 无批量删除 / JDBC 事务 | 全 DAO | 健壮性 |
| R4 | 业务计算侵入 DB 层（`CalcUtil` 放 model） | `CalcUtil.java:7` 自承应拆 | CODEBUDDY §5 |
| R5 | 引入非白名单 jar：`junit-4.13.2` + `hamcrest-core-1.3` | `lib/` | `ui_lib_record.md` §2.4（仅允 JUnit 5 standalone） |
| R6 | `classifyBmi` 硬编码中文「偏瘦/正常/超重/肥胖」 | `CalcUtil.java:55/57/59/61` | CODEBUDDY #4.1 |
| R7 | 调试 `System.out/err` 打印，且泄露 `stored_hash`/`passwordHash`/`salt` | `UserDAO:49/105`；`RecordDAO:72/77/113/200/226` | 安全/规范 |
| R8 | 删除 `db-config.properties.example` | diff `D db-config.properties.example` | 配置样例需保留 |
| R9 | `JdbcUtil` 无超时常量（注释「依赖驱动默认值」） | `JdbcUtil.java:17/27` | CODEBUDDY #4.2 |
| R10 | 删除依赖白名单权威文档 `ui_lib_record.md` | diff `D ui_lib_record.md` | 项目治理 |
| R11 | 文档整篇重写（±数千行）与宪章潜在冲突 | `docs/*` | CODEBUDDY §6 |

---

## 4. 整改分步指南（rebase + 改接口，实操）

> 目标：在不破坏 UI 工作的前提下，把 DB 模块干净地并入 `main`。

### Step 1 · 把 feature-db rebase 到最新 main（含 UI 工作）
```bash
git fetch origin
git checkout feature-db
git rebase origin/main        # 拿到 UI 负责人的最新工作，解决冲突后继续
```
⚠️ rebase 过程中**不要**把 main 的 UI 文件删掉；若 rebase 把删除带进来，用 `git checkout origin/main -- <被删文件>` 还原。

### Step 2 · 还原被误删的文件（从 main 取回）
```bash
git checkout origin/main -- \
  src/com/bmi/view \
  src/com/bmi/i18n \
  src/com/bmi/controller/AiController.java \
  src/com/bmi/client/AiHealthClient.java \
  src/com/bmi/exception \
  src/com/bmi/model/ai \
  ai-key.properties \
  db-config.properties.example \
  docs/ai_config.md \
  ui_lib_record.md
```
（执行后 `git status` 应显示这些文件为「恢复」，而非「删除」。）

### Step 3 · DAO 类更名
- `UserDAO` → `UserDao`；`RecordDAO` → `RecordDao`（CODEBUDDY §4.1 后缀 `Dao`）。
- 同步更新 `UserController` / `RecordController` 的 import（已是 `UserDao`/`RecordDao`，无需改 import，只需改实现类名）。

### Step 4 · 方法名对齐
- `addRecord` → `insert`；`getRecordById` → `selectById`；`deleteRecord` → `deleteById`；`queryByUserAndTimeRange` → `queryByUser`。
- `UserDAO` 补 `findByUsername(name)`；将 `existsUsername` 由 private 提升为 public。
- `updateRecord` → `update`（保持签名一致）。

### Step 5 · 分页参数统一 + 边界校验
- 改 `queryByUserPage(userId, start, end, offset, limit)` → `queryByUserPage(userId, page, size)`。
- 加边界：`if (page < 1) page = 1; if (size <= 0) size = 10;` 内部换算 `offset = (page-1)*size`。
- 建议补 `PageResult<T>` DTO（含 `total`/`list`/`page`/`size`）。

### Step 6 · 补 `predictBodyFat`
- 在 `model` 业务类（如 `CalcUtil` 或独立 `BodyFatCalculator`）增补 `predictBodyFat(double bmi, int age, int gender)`，签名与 `RecordController:123` 一致。

### Step 7 · `classifyBmi` 去中文硬编码（R6）
- 返回枚举/状态码（如 `UNDERWEIGHT/NORMAL/OVERWEIGHT/OBESE`），中文文案移回 view 层 i18n。

### Step 8 · 删调试打印（R7，安全）
- 删除 `UserDAO`/`RecordDAO` 全部 `System.out/err`，**严禁打印 hash / salt / 明文密码**。

### Step 9 · 依赖对齐（R5/R8/R9/R10）
- 删除 `lib/junit-4.13.2.jar` + `lib/hamcrest-core-1.3.jar`，换白名单 `junit-platform-console-standalone-1.11.4.jar`；`MainTest` 改 JUnit 5 注解。
- 恢复 `ui_lib_record.md` 与 `db-config.properties.example`。
- `JdbcUtil` 补 `CONNECT_TIMEOUT_MS` / `READ_TIMEOUT_MS` 常量并应用到连接；类名按团队约定统一。

### Step 10 · 文档复核（R11）
- 文档重构与 `CODEBUDDY.md` 宪章复核一致（表名 / 命名 / 分层），由 DBA / 架构师 review。

### Step 11 · 重新提 PR（仅增量）
- 确认 `git diff --stat origin/main...HEAD` 仅含 **DB 8 文件 + 3 jar** 的增量，UI/controller/ai/i18n 层无删除。
- PR 描述附：独立编译结果 + 合并后 `bash build.sh` 0 错误截图。

---

## 5. 验收标准（量化，合并前必过）

| # | 验收项 | 标准 |
|---|--------|------|
| 1 | DB 模块独立编译 | 0 错误（JDK17 + lib） |
| 2 | 合并后全量编译 | `bash build.sh` 0 错误 |
| 3 | DAO ↔ UI 接口契合 | 11/11 匹配（见 §2 对照表） |
| 4 | `predictBodyFat` | 存在且签名与 `RecordController` 一致 |
| 5 | 安全 | 无 `System.out` 打印 hash/salt/密码 |
| 6 | 依赖白名单 | `lib/` 仅含白名单 jar（JUnit 5 standalone + 驱动） |
| 7 | 误删还原 | UI/controller/ai/i18n 层文件数 = main 现状，无删除 |
| 8 | 中文硬编码 | `classifyBmi` 等不出现中文文案 |

---

*本意见基于 `docs/ui_verification_report.md`「后端分支PR校验」章节的静态证据整理而成，全程只读、未改动任何分支。*
