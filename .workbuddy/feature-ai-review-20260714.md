# feature-ai 分支 · AI 模块全量静态审查报告

> 生成时间：2026-07-14 17:49
> 审查对象：`feature-ai` 分支工作区中的全部 AI 模块源码
> 比对基线：`docs/ai_design.md`（SDD-AI v1.0 详细版，main 侧 §3–§8）+ `docs/plan.md` §4.4 / §6
> 工具：`git` 状态核查 + 逐文件静态阅读（无运行/单测，因 `lib/` 缺 jar 且模块无法编译）

---

## 0. 关键前提（务必先读）

1. **`feature-ai` 分支已存在**（本地 + `origin/feature-ai` 均有），当前工作区即在该分支。
2. **`docs/ai_design.md` 处于未解决的 Git 合并冲突状态**（含 `<<<<<<< HEAD` / `=======` / `>>>>>>> main`）。本报告以**详细版（与代码类结构一致的完整 SDD-AI）为比对基线**；feature-ai 自身 HEAD 侧短桩（`getHealthAdvice(BodyRecord)`）与代码不符，不作为基线。
3. **AI 包存在两套互斥实现**（`AiService` + `AiHealthClient`），且 `AiService` 引用的 DTO API 当前并不存在 → **模块无法编译**（详见 P0-1）。
4. **feature-ai 的全部 AI 源码均为未提交状态（`??` untracked）**，`git diff --stat HEAD` 为空 → 该分支在 git 历史中不含 AI 实现，PR 实际为空（见 P2-7）。

### 审查文件清单
| 文件 | 角色 | 状态 |
|------|------|------|
| `model/ai/AiService.java` | 详细签名版实现 | ❌ 编译失败（引用不存在 API） |
| `model/ai/AiHealthClient.java` | 简化版实现（设计命名类） | ✓ 可编译，但与设计多处不符 |
| `model/ai/AiRequest.java` | 请求 DTO（简化版） | ⚠ 缺设计要求的嵌套结构 |
| `model/ai/AiHealthResult.java` | 结果 DTO（简化版） | ⚠ 缺 9 字段/分段/usage |
| `model/ai/AiCacheUtil.java` | 缓存工具 | ⚠ 无调用方（死代码） |
| `model/ai/TestAiService.java` | 手测片段 | ⚠ 非 JUnit，包名错 |
| `model/ai/AiException.java` / `AiConfigException.java` | 异常类 | ✓ 结构正确 |
| `controller/AiController.java` | 控制层编排 | ❌ 依赖 AiService → 编译失败 |
| `model/ai/BodyRecord.java` | **重复错误实体** | ❌ 应删除 |
| `model/BodyRecord.java` | 正确实体 | ✓ 含 bmi/bodyFat/measureTime |

---

## 1. 缺陷总览（按优先级）

| 编号 | 优先级 | 标题 | 位置 |
|------|--------|------|------|
| P0-1 | **P0 阻塞** | AiService 引用不存在的 DTO API，整模块无法编译 | AiService.java / AiController.java |
| P0-2 | **P0 阻塞** | ai_design.md 含未解决合并冲突标记 | docs/ai_design.md:1,17,416 |
| P0-3 | **P0 阻塞** | AI 包两套互斥实现，无单一事实源 | AiService.java + AiHealthClient.java |
| P1-1 | P1 | 历史趋势取最旧 N 条，违背"近 N 次" | AiService.java:62-63 |
| P1-2 | P1 | 请求体缺 bmi/bmiGrade/bodyFat/measureTime | AiHealthClient.java:199-218 |
| P1-3 | P1 | loadConfig 抛 RuntimeException 而非 AiConfigException | AiHealthClient.java:45-65 |
| P1-4 | P1 | 三段校验仅要求"任一存在" | AiHealthClient.java:263 |
| P1-5 | P1 | token 用量 usage 从未解析 | AiService/AiHealthClient parseResponse |
| P1-6 | P1 | BodyRecord 被错误复制到 model/ai 包 | model/ai/BodyRecord.java |
| P1-7 | P1 | 缺失独立 BmiCalculator/BodyFatCalculator | 全仓无此类 |
| P2-1 | P2 | AiCacheUtil 未被调用（死代码/设计漂移） | AiCacheUtil.java |
| P2-2 | P2 | TestAiService 非 JUnit、包名/命名错 | TestAiService.java |
| P2-3 | P2 | 配置读取策略不一致/缺兜底 | AiHealthClient.java:47 vs AiService.java:249 |
| P2-4 | P2 | 默认模型名两处不一致 | AiService:241 vs AiHealthClient:54 |
| P2-5 | P2 | 手工 JSON 转义不完备/两处不一致 | escapeJson 两实现 |
| P2-6 | P2 | plan/ai_design 用 Swing，宪章要求 JavaFX | plan.md:16,274 / CODEBUDDY.md §2 |
| P2-7 | P2 | feature-ai AI 源码全部未提交，PR 为空 | git status 全 `??` |

---

## 2. P0 详细整改（必须修复）

### P0-1 【编译阻断】AiService 引用不存在的 DTO API
- **设计**：ai_design §3.1 定义 `AiRequest` 含 `UserMetrics`/`HistoryTrend`/`ModelParams` 内嵌结构 + setter；§4.1 定义 `AiHealthResult` 含 9 字段 + `dietAdvice`/`exerciseAdvice`/`healthAdvice` + `hasThreeSections()` + `ok()`。
- **现状**：实际 `AiRequest.java` 仅 `AiRequest(String, BodyRecord, int, double)` + `getRecord/getMaxTokens/getTemperature/isValid`，**无内嵌类、无 setter**；实际 `AiHealthResult.java` 仅 `success(String)`/`fail(String,String)`，**无 `ok()`/`setFinishReason()`/`hasThreeSections()`/分段字段**。
- **代码证据**：AiService.java:50 `new AiRequest.UserMetrics()`、:59 `req.setUserMetrics(...)`、:61 `new AiRequest.HistoryTrend()`、:78 `new AiRequest.ModelParams()`、:98 `req.getUserMetrics()`、:145 `r.hasThreeSections()`、:187 `r.setFinishReason(...)`、:197-216 `req.getModelParams()...` —— 上述符号在现有 DTO 中均不存在 → **编译错误**。
- **波及**：AiController.java:40-41、BmiApplication.java:12,35 均依赖 AiService → **整应用无法 `javac`**。
- **整改**（结合 P0-3 决策）：补齐设计要求的 DTO 结构，使对外封装类可编译（见 P0-3）。
- **验收**：`javac -d out src/**/*.java` 全量通过；`AiController.getAdvice(userId)` 可运行。

### P0-2 【分支不可合并】ai_design.md 合并冲突未解决
- **位置**：docs/ai_design.md:1 `<<<<<<< HEAD`、:17 `=======`、:416 `>>>>>>> main`。
- **现状**：feature-ai 合并 main 时该文档冲突未解决，文件内同时保留 feature-ai 短桩版与 main 详细版。
- **整改**：以解决冲突方式保留**详细版（SDD-AI v1.0）**为唯一权威，删除全部冲突标记；若确需"缓存 10 分钟"，应作为新增小节写入详细版（当前设计未定义缓存）。
- **验收**：文件中无 `<<<<<<<`/`=======`/`>>>>>>>`；内容与代码一致。

### P0-3 【架构分裂】两套互斥 AI 实现，无单一事实源
- **设计**：plan §4.4 / ai_design §7.1 明确封装类命名为 `AiHealthClient`（XxxClient）。
- **现状**：
  - `AiHealthClient`：简化版（`buildRequest(BodyRecord, String)`、简单 `AiRequest`），可编译但仅被 `TestAiService` 调用（死代码）。
  - `AiService`：详细签名版（`buildRequest(User, Record, List)`、富 DTO）但 DTO 未实现 → 编译失败；却被 `AiController`/`BmiApplication` 引用。
- **整改（推荐）**：以 **`AiHealthClient`** 为唯一对外封装（符合设计命名），**删除 `AiService`**；按 P0-1 补齐 `AiRequest`/`AiHealthResult` 到设计结构，并让 `AiHealthClient` 使用之；更新 `AiController`/`BmiApplication` 引用 `AiHealthClient`。
- **验收**：AI 包仅一个对外封装类；所有调用点引用一致；全量编译通过。

---

## 3. P1 详细整改（重要，发布前须修）

### P1-1 历史趋势取最旧 N 条
- **位置**：AiService.java:62-63 `history.subList(0, Math.min(history.size(), 10))`；`RecordDao.queryByUser` 默认升序。
- **设计**：ai_design §3.3 `historyTrend` 取"近 N 次"（最新）。
- **整改**：取末尾 N 条 `history.subList(Math.max(0, size - 10), size)`；或在查询后 reverse。
- **验收**：趋势点为最新 10 次且按时间升序；direction 由首末点正确得出。

### P1-2 请求体缺关键字段
- **位置**：AiHealthClient.java:199-218 `buildJsonBody`、:72-83 `buildRequest`。
- **设计**：ai_design §3.2 `userMetrics` 需 `bmi>0`、`bmiGrade`、`bodyFat 0~60`、`weight`、`height`、`age`、`gender`、`measureTime`。
- **现状**：仅发送 height/weight/age/gender，且未计算 BMI/体脂 → AI 拿不到 bmi/bodyFat，建议质量差。
- **整改**：调用 `BmiCalculator`/`BodyFatCalculator`（见 P1-7）计算后补全 8 字段。
- **验收**：请求 JSON 含全部 8 字段且 bmi/bodyFat 非空。

### P1-3 异常契约违反（配置异常）
- **位置**：AiHealthClient.java:45-65 `loadConfig()`。
- **设计**：ai_design §5.1/§7.3 密钥缺失抛 `AiConfigException`，由 `AiController` 捕获转文案。
- **现状**：抛 `RuntimeException` → `AiController` 的 `catch (AiConfigException)` 抓不到 → 异常上抛至视图，破坏"不阻断/不暴露技术栈"。
- **整改**：缺失/为空时抛 `AiConfigException`（或返回 `CONFIG_ERROR` 降级），与 AiService 一致。
- **验收**：无 `ai-key.properties` 时返回「AI 服务未配置，请联系管理员」且不崩溃。

### P1-4 三段校验弱化
- **位置**：AiHealthClient.java:263 `if (!contains("饮食") && !contains("运动") && !contains("健康"))`。
- **设计**：AC-07 要求文本非空且含【饮食】【运动】【健康】三段；ai_design §4.4 要求三段完整性校验，不达标降级。
- **现状**：只要含任一关键词即通过；缺失段落不降级。
- **整改**：要求三段均含（对齐 AiService 期望的 `hasThreeSections()`）。
- **验收**：缺任一段即降级为 `SERVER_ERROR` 文案。

### P1-5 缺失 usage 解析
- **位置**：AiService.parseResponse:173-192、AiHealthClient.parseResponse:251-267 均未读 `usage`。
- **设计**：ai_design §4.1/§4.3 `usage{promptTokens,completionTokens,totalTokens}`。
- **整改**：解析 `usage` 子对象并填充；`AiHealthResult` 增加 `Usage` 字段（或在设计中明确移除该字段）。
- **验收**：成功结果含 token 用量，或设计明确删除该字段且代码同步。

### P1-6 实体错位/重复
- **位置**：`model/ai/BodyRecord.java`（错误位置，仅 height/weight/age/gender）；`model/BodyRecord.java`（正确实体）。
- **设计**：实体在 `com.bmi.model`（宪章 §3/4、plan §4.3）；AI 包不放置实体。
- **现状**：AiHealthClient、TestAiService 使用 model/ai 的残缺 BodyRecord（无 bmi/bodyFat/measureTime），无法发完整指标。
- **整改**：删除 `model/ai/BodyRecord.java`；改 `import com.bmi.model.BodyRecord`。
- **验收**：全仓仅一个 BodyRecord（com.bmi.model）；AI 包无实体类。

### P1-7 缺失独立计算类
- **位置**：全仓无 `BmiCalculator`/`BodyFatCalculator`；AiService.classify 内联(:295-306)；AiHealthClient 不计算。
- **设计**：plan §4.4/§8、ai_design §3.2 明确要求独立纯 Java 计算类（可 JUnit 单测）。
- **整改**：新增 `BmiCalculator`(calcBmi/classify)、`BodyFatCalculator`(predictBodyFat)，AI 与 RecordController 共用。
- **验收**：两计算类存在且被 AI 模块调用；有 JUnit 单测。

---

## 4. P2 详细整改（一般 / 健壮性 / 文档）

### P2-1 死代码 AiCacheUtil
- 仅定义、无调用方。feature-ai 短桩提"缓存 10 分钟"，但基线详细设计未定义缓存。
- 整改：需缓存则按设计补接入点（请求前查、成功后写）；否则删除。

### P2-2 测试不规范
- `TestAiService.java`：`package com.bmi.model.ai`（应在 `com.bmi.test`）、用 `main` 而非 `@Test`、名为 TestAiService 却测 AiHealthClient、运行会真实打外部 API。
- 整改：迁 `com.bmi.test`，改 JUnit 用例，离线 mock 四类异常与降级。

### P2-3 配置读取策略不一致
- AiHealthClient.java:47 仅 `getResourceAsStream`（缺失即 RuntimeException）；AiService.java:249-263 有 classpath→文件 双兜底。
- 整改：统一双兜底，缺文件统一返回 CONFIG_ERROR 降级，不抛裸 RuntimeException。

### P2-4 默认模型不一致
- AiService:241 `gpt-4o-mini` vs AiHealthClient:54 `deepseek-chat`。
- 整改：统一默认值（建议来自 `api.model` 配置，不硬编码差异）。

### P2-5 JSON 转义不完备
- AiService.escapeJson:358-363 丢弃 `\r`；AiHealthClient.escapeJson:223-230 处理 `\r`；两处均未覆盖 `\b \f \/`。
- 整改：统一一个 `escapeJson`（覆盖 `\" \\ \/ \b \f \n \r \t`）复用。

### P2-6 文档与宪章视图技术栈冲突
- plan.md:16,274 与 ai_design §2 用 Swing（ChartPanel/AiAdvicePanel）；CODEBUDDY.md §2 强制 JavaFX。
- 整改：以宪章为准统一 JavaFX；更新文档控件命名（ChartView/AiAdviceView）。

### P2-7 分支卫生
- feature-ai 的 AI 源码全部 `??` 未提交，`git diff --stat HEAD` 为空 → PR 为空、不可评审/合入。
- 整改：修复编译与冲突后，将 AI 模块及配套 model/db 调整提交到 feature-ai 并推送。

---

## 5. 符合项（PASS，保留）

- 四类异常分级与重试策略骨架正确（超时/5xx 重试 1 次；断网/参数/配置不重试）—— AiService/AiHealthClient 的 `send()` 均体现。
- 双 10s 超时 `CONNECT_TIMEOUT_MS=READ_TIMEOUT_MS=10000` 与 AC-07 一致。
- 鉴权头 `Authorization: Bearer <api.key>`、`Content-Type`、`Accept` 正确；key/url/model 来自配置、源码零硬编码。
- 五条降级文案与 ai_design §5.2 **逐字一致**（网络/超时/参数/服务器/配置）。
- `AiException`/`AiConfigException` 继承结构正确，符合 §7.1。
- `AiController.getAdvice(userId)` 编排（取 latest+history → buildRequest → requestAdvice → 返回 String，不阻断主流程）符合 plan §4.2 / FR-07。
- OpenAI Chat Completions 报文格式（model/messages/temperature/max_tokens）符合 plan §6.1。

---

## 6. 整改建议顺序

1. **P0-2**：先解决 ai_design.md 冲突，确立权威设计基线。
2. **P0-3 + P0-1**：决策唯一封装类为 `AiHealthClient`，删除 `AiService`，补齐 `AiRequest`/`AiHealthResult` 到设计结构 → 恢复编译。
3. **P1-6 + P1-7**：删除错误 BodyRecord、新增 BmiCalculator/BodyFatCalculator。
4. **P1-2 / P1-3 / P1-4 / P1-5**：补全请求字段、修正异常契约、强化三段校验、解析 usage。
5. **P2-x**：清理死代码/测试规范/配置兜底/转义/文档冲突，最后提交到 feature-ai（P2-7）。
