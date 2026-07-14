# BMI 体质评估与预测系统 · 任务拆分与排期

> 本文件是 `plan.md` 的下游交付文档，覆盖 `plan.md` 全部模块与 `spec.md` 全部功能需求 FR-01~FR-07。
> 周期：2026-07-14 ~ 2026-07-24（11 天），团队 3 人，遵循 `CODEBUDDY.md` 宪章。

## 1. 模块划分

| 模块 | 职责 | 对应包/类 | 覆盖 FR |
|------|------|-----------|---------|
| **UI 模块** | Swing 界面与交互：登录注册、录入、历史表格、折线图自绘、主窗口整合 | `com.bmi.view`：LoginView / MainFrame / RecordInputPanel / HistoryPanel / ChartPanel | FR-01, FR-02, FR-05, FR-06 |
| **数据模块** | JDBC 存取、实体、业务计算（BMI/体脂）、建库建表、配置加载 | `com.bmi.model.db`：UserDao / RecordDao / User / Record / DbUtil；`com.bmi.model.ai`：BmiCalculator / BodyFatCalculator | FR-01, FR-03, FR-04, FR-05 |
| **AI 模块** | HttpURLConnection 调用大模型、请求/响应构造、降级与错误码处理 | `com.bmi.model.ai`：AiHealthClient；`com.bmi.controller`：AiController | FR-07 |

> 控制器层（`com.bmi.controller`：UserController / RecordController / ChartController / AiController）为跨模块串联枢纽，由对应模块负责人在联调期补齐。

## 2. 三人分工

| 开发 | 负责模块 | 主要类 | 交付物 |
|------|----------|--------|--------|
| **开发A-UI** | UI 模块 | LoginView、MainFrame、RecordInputPanel、HistoryPanel、ChartPanel | 全部 Swing 界面、折线图自绘、就地校验提示 |
| **开发B-数据** | 数据模块 | UserDao、RecordDao、User、Record、DbUtil、BmiCalculator、BodyFatCalculator | SQLite 建表 DDL、DAO 实现、业务计算、db-config 模板 |
| **开发C-AI** | AI 模块 | AiHealthClient、AiController | AI 请求/响应/降级、ai-key 模板、建议串联 |

**跨模块协作点**：
- UI 仅依赖 controller 的公开方法（事件回调），不直接碰 db/ai。
- controller 依赖 model.db 的 Dao/实体与 model.ai 的 AiHealthClient。
- 联调期由 A 提供 view 接口、B 提供 Dao/计算方法、C 提供 AiHealthClient，共同在 controller 层串联。

## 3. 每日开发计划（2026-07-14 ~ 2026-07-24）

| 日期 | 开发A-UI | 开发B-数据 | 开发C-AI | 里程碑/联调点 |
|------|----------|------------|----------|----------------|
| 07-14 | 工程骨架、`MainFrame` 窗口框架、`LoginView` 原型 | SQLite 建库建表 DDL、`DbUtil`、`User`/`Record` 实体、`UserDao` 骨架 | `ai-key.properties` 加载器、`AiRequest` 模型、`AiHealthClient` 接口骨架 | **M1**：库可连通 + 配置可加载 |
| 07-15 | `LoginView` 完善（注册/登录表单 + 校验提示） | `UserDao` 注册（加盐 SHA-256）/ 登录查询；`RecordDao` 骨架 | `AiHealthClient` 用 `HttpURLConnection` 发送请求 + 设 10s 超时 | **M2**：`UserController` 串联 LoginView↔UserDao，登录注册端到端跑通（FR-01） |
| 07-16 | `RecordInputPanel`（身高/体重/年龄/性别输入 + 区间校验） | `RecordDao` insert/queryByUser/deleteById；`BmiCalculator`/`BodyFatCalculator` | `AiHealthClient` 响应体解析（choices[0].message.content） | **M3**：`RecordController` 串录入→BMI→体脂→落库（FR-02~04） |
| 07-17 | `HistoryPanel`（表格展示 + 删除按钮） | `RecordDao` 时间区间查询与排序优化 | 历史摘要构造（historySummary） | **M4**：查询/删除回写 `HistoryPanel`（FR-05） |
| 07-18 | `ChartPanel` 自绘折线图（坐标轴/数据点/时间横轴） | `ChartController` 取历史集合 | — | 图表随记录刷新（FR-06 基础） |
| 07-19 | `ChartPanel` 指标切换（BMI/体重/体脂）+ 空/不足 2 点提示 | 索引 `idx_record_user_time` 验证（查询 <1s） | — | **M5**：指标切换 <500ms 重绘（FR-06 完成） |
| 07-20 | 建议区 UI 占位与展示样式 | `RecordController` 汇总最新指标供 AI | `AiController` 串联数据→`AiHealthClient`；降级与错误码 | **M6**：AI 建议全链路打通（FR-07） |
| 07-21 | `MainFrame` 整合所有 Panel + 布局/菜单 | 全链路数据贯通检查 | 降级路径联调（超时/缺密钥） | **M7**：全链路集成（录入→计算→落库→图→AI） |
| 07-22 | UI 回归与边界提示核对 | JUnit：`BmiCalculator`/`BodyFatCalculator`/`UserDao`/`RecordDao` 单测 | JUnit：`AiHealthClient` 降级单测（Mock 超时/异常） | 单元测试完成 |
| 07-23 | 集成场景走查（FR-01~07） | 缺陷修复 + 性能复核（<1s/<500ms） | 缺陷修复 + AI 降级复核 | 集成测试完成 |
| 07-24 | 演示与界面验收 | 数据验收（AC-01~05） | AI 验收（AC-07） | **验收日**：对照 AC-01~07 量化验收，核对 plan/tasks 一致性 |

## 4. 任务看板（Task 列表）

| 任务ID | 模块 | 负责人 | 依赖任务 | 关联FR | 验收对应AC | 状态 |
|--------|------|--------|----------|--------|------------|------|
| T-01 | UI | 开发A | — | FR-01 | AC-01 | 待办 |
| T-02 | 数据 | 开发B | — | FR-01 | AC-01 | 待办 |
| T-03 | 控制 | 开发A/B | T-01,T-02 | FR-01 | AC-01 | 待办 |
| T-04 | UI | 开发A | — | FR-02 | AC-02 | 待办 |
| T-05 | 数据 | 开发B | — | FR-03 | AC-03 | 待办 |
| T-06 | 数据 | 开发B | — | FR-04 | AC-04 | 待办 |
| T-07 | 数据 | 开发B | — | FR-05 | AC-05 | 待办 |
| T-08 | 控制 | 开发A/B | T-04,T-05,T-06,T-07 | FR-02,03,04,05 | AC-02,03,04,05 | 待办 |
| T-09 | UI | 开发A | T-08 | FR-05 | AC-05 | 待办 |
| T-10 | UI | 开发A | — | FR-06 | AC-06 | 待办 |
| T-11 | 控制 | 开发A/B | T-07,T-10 | FR-06 | AC-06 | 待办 |
| T-12 | AI | 开发C | — | FR-07 | AC-07 | 待办 |
| T-13 | 控制 | 开发C | T-08,T-12 | FR-07 | AC-07 | 待办 |
| T-14 | UI | 开发A | T-03,T-08,T-09,T-11,T-13 | FR-01~07 | AC-01~07 | 待办 |
| T-15 | 测试 | 三人 | T-05,T-06,T-07,T-12 | FR-01~07 | AC-01~07 | 待办 |
| T-16 | 测试 | 三人 | T-14,T-15 | FR-01~07 | AC-01~07 | 待办 |

> 看板覆盖 FR-01~FR-07 全部开发与测试任务，每个 FR 均映射至量化验收标准 AC-01~AC-07。T-14/T-15/T-16 为集成与验收任务，确保全链路闭合。
