# feature-ai 分支 AI 模块全量静态审查报告（远端同步版）

> 审查对象：**`origin/feature-ai` 已提交树**（HEAD = `2fad333`，领先本地 `feature-ai`/`cfe1efb` 三个修复提交）
> 比对基线：`docs/ai_design.md`（SDD-AI v1.0，已提交干净版）、`docs/plan.md`（架构设计）
> 提取方式：直接从 git 树 `git show origin/feature-ai:<path>` 抽取 8 个 AI 源码 + 2 份设计文档，**排除工作区残留的未跟踪污染文件**
> 审查时间：2026-07-14 19:30

---

## 0. 重大变化（与上一轮 `feature-ai-review-20260714-v2` 对比）

| 上一轮状态 | 远端同步后状态 | 结论 |
|---|---|---|
| P0-1 `AiRequest` 缺内嵌类 → 编译失败 | `AiRequest` 已重建（UserMetrics/HistoryTrend/ModelParams/Point） | ✅ **已修复** |
| P0-2 `AiHealthResult` 缺三段/usage/ok() | `AiHealthResult` 已重建（三段+usage+finishReason+ok()+hasThreeSections()+parseUsage） | ✅ **已修复** |
| P0-3 架构分裂（AiService+AiHealthClient 并存） | 统一为 `AiHealthClient`（符合 ai_design.md §7 指定封装名） | ✅ **已修复** |
| — | **新增** `ai-key.properties` 已提交入远端历史（`cfe1efb`） | 🔴 **P0 安全事故** |
| — | **新增** 引入 `org.json` + 提交未使用的 `okhttp`/`okio`（违反白名单） | 🔴 **P0 治理违规** |
| P1-4 缺独立计算类 | 新增 `BmiCalculator`/`BodyFatCalculator` | ✅ **已修复** |
| P1-1 取最旧 N 条 | 改为取最新 10 条（`start=max(0,size-10)`） | ✅ **已修复** |
| P1-2 缺 bmi/bodyFat 字段 | 已补全 | ✅ **已修复** |
| P1-3 抛 RuntimeException | 改抛 `AiConfigException` | ✅ **已修复** |
| P1-5 usage 未解析 | 已解析 | ✅ **已修复** |

---

## 1. ✅ 符合项（保留，勿回退）

- 封装类名 `AiHealthClient`（model.ai 层，仅暴露「建议文本获取」）—— 对齐 ai_design.md §7。
- `AiRequest` 四段结构（systemPrompt / userMetrics / historyTrend / modelParams）与 §3 对齐。
- `AiHealthResult` 9 字段 + 6 错误码常量（SUCCESS/NETWORK_ERROR/TIMEOUT/INVALID_PARAM/SERVER_ERROR/CONFIG_ERROR）+ `ok()`/`hasThreeSections()` —— 对齐 §4。
- 四类异常 + 重试骨架（MAX_RETRY=1，仅 5xx 重试）、`escapeJson` 转义补全（`\ " \n \r \t`）—— 对齐 §5。
- 双 10s 超时（`CONNECT_TIMEOUT_MS`/`READ_TIMEOUT_MS`）、Bearer 鉴权配置外置（`api.key`/`api.url`/`api.model`）—— 对齐 §6.1。
- 五条降级文案逐字一致：网络/超时/参数/服务器/配置 —— 对齐 §5 表。
- 历史趋势取**最新 10 条**、`usage`+`finish_reason` 解析、`AiConfigException` 异常契约、独立计算类 —— 均达标。

---

## 2. 🔴 P0 — 合并/发布阻断（安全 + 治理）

### P0-1 · API Key 已泄露进远端 git 历史（安全事故）
- **位置**：`origin/feature-ai` 已提交树含 `ai-key.properties`（由提交 `cfe1efb "共享AI模块API Key，方便团队直接运行"` 新增，3 行）。
- **设计要求**：CODEBUDDY.md §4.4 / ai_design.md §6.4 明确 `ai-key.properties` **已 gitignore、禁止提交、禁止硬编码**。
- **现状**：密钥明文已落入远端分支历史。即便工作区已隔离，远程副本已公开。
- **整改**：
  1. **立即轮换/吊销**该 API Key（最高优先，阻断一切其他工作）；
  2. 用 `git filter-repo` 或 `bfg` 从 `feature-ai` **全历史清除** `ai-key.properties`；
  3. **切勿将 `feature-ai` 合并或 PR 到 `main`**，否则污染扩散。
- **验收**：远端历史不再含 `ai-key.properties`；旧 Key 在服务商后台已失效。

### P0-2 · 白名单违规依赖（治理边界）
- **位置**：`origin/feature-ai` 已提交 `lib/json-20240303.jar`（被 `AiHealthClient.parseResponse` 用 `org.json.JSONObject` 引用）+ `lib/okhttp-4.12.0.jar` + `lib/okio-3.6.0.jar`（**完全未被任何代码使用**）。
- **设计要求**：ai_design.md §1 白名单「Java 8+ · 原生 HttpURLConnection · **手工 JSON 解析** · 不引入任何 Web 框架 / Spring / 第三方 HTTP 库 / **JSON 库**」；plan.md §6.2「无 OkHttp 等额外依赖」。
- **现状**：代码**能编译**（因 jar 在 lib/），但违反不可逾越的宪章白名单。`okhttp`/`okio` 是死重依赖且被明文禁止。
- **整改**：
  1. 删除 `lib/okhttp-4.12.0.jar`、`lib/okio-3.6.0.jar`、并从 git 历史清理；
  2. 移除 `org.json` 依赖：请求 JSON 已手工拼接（`buildJsonBody`），仅需把 `parseResponse` 的 `new JSONObject(...)` 改为**手工解析** `usage`/`finish_reason`/`content`（当前 `extractContent` 已是手工字符串解析，可复用）；
  3. 删除 `lib/json-20240303.jar`。
- **验收**：`lib/` 不含任何第三方 HTTP/JSON jar；`AiHealthClient` 无 `import org.json.*`；`javac` 仍可编译。
- **备注**：若团队确需 JSON 库，须先在 CODEBUDDY.md 白名单**显式例外**并走变更流程，而非直接引入。

---

## 3. 🟠 P1 — 发布前须修

### P1-1 · 异常继承链断裂
- **位置**：`AiConfigException.java:3` 直接 `extends Exception`；`AiException.java` **不在**已提交树。
- **设计要求**：ai_design.md §7 规定 `AiException extends Exception`（基类）+ `AiConfigException extends AiException`。
- **现状**：基类缺失，子类未继承指定基类 —— 违反异常契约（虽可编译）。
- **整改**：补回 `src/com/bmi/model/ai/AiException.java`（`extends Exception`），并改 `AiConfigException extends AiException`。
- **验收**：`AiConfigException` 是 `AiException` 子类；grep 全仓仅有 `AiException` 一个基类。

### P1-2 · 三段分隔符不匹配（功能性 bug，导致 AI 建议恒降级）
- **位置**：`AiHealthClient.buildRequest` 设 `systemPrompt = "...请按『饮食』『运动』『健康』三段给出建议。"`（『』 引号）；`AiHealthResult.extractSection` 却查找 `"【饮食】"`（【】 全角方括号）。
- **设计要求**：ai_design.md §4.3 / AC-07 期望输出含 `【饮食】/【运动】/【健康】` 分隔；解析 `extractSection` 用 `【】`。
- **现状**：prompt 指令用 『』，解析期望 【】→ AI 几乎不会输出【】分隔 → `extractSection` 返回空 → `hasThreeSections()` 恒 false → `parseResponse` 一律返回 SERVER_ERROR 降级。**FR-07 实际不可用**。
- **整改**：统一分隔符。推荐把 `systemPrompt` 改为 `…请按【饮食】/【运动】/【健康】三段给出建议。`（与解析、AC-07 一致）；`buildJsonBody` 的 user 文案同理。
- **验收**：用含 `【饮食】…【运动】…【健康】…` 的样例响应跑 `hasThreeSections()` 返回 true；`extractSection` 三段均非空。

### P1-3 · 实体重复 + 分层违例 + buildRequest 签名偏离
- **位置**：`src/com/bmi/model/ai/BodyRecord.java`（7 参构造器：height/weight/age/gender/heartRate/systolicBP/diastolicBP，缺 `getBodyFat()`/`getMeasureTime()`/`getId()`）；`buildRequest(BodyRecord, List<BodyRecord>)`。
- **设计要求**：CODEBUDDY.md §3 实体 `User`/`BodyRecord` 独立存放于 `model/`（非 `model.ai`）；plan.md §6.1 `buildRequest(User u, Record latest, List<Record> history)`。
- **现状**：AI 模块自建了一份 `BodyRecord` 副本（分层违例）；真实 `model/BodyRecord.java` 不在已提交树（仅工作区残留）。`buildRequest` 签名与设计不符，且控制器若传真实实体会**类型不匹配**无法编译接线。
- **整改**：删除 `model.ai.BodyRecord`；改引用 `model.BodyRecord`；`buildRequest` 调整为 `buildRequest(User u, BodyRecord latest, List<BodyRecord> history)`（或保持 `(BodyRecord, List<BodyRecord>)` 但改用真实实体类型）。
- **验收**：`model.ai` 不含 `BodyRecord`；`buildRequest` 入参类型来自 `model` 层；控制器可传真实实体编译通过。

### P1-4 · 请求体未序列化 historyTrend（FR-07 输入不完整）
- **位置**：`AiHealthClient.buildRequest` 计算了 `historyTrend`（方向/点数/points），`req.setHistoryTrend(trend)`；但 `buildJsonBody` 仅拼 system + 当前 metrics 的 user 文案，**完全丢弃 `req.getHistoryTrend()`**。
- **设计要求**：ai_design.md §3.4/§3.6 请求信封含 `historyTrend`；FR-07「将用户最新指标 + 近期历史趋势发送」。
- **现状**：AI 只收到当前快照，收不到历史趋势 → 建议质量下降，违背 FR-07 输入完整性。
- **整改**：在 `buildJsonBody` 中序列化 `historyTrend`（方向/近 N 条 bmi 趋势，可简写进 user 文案或按信封补字段）。
- **验收**：发出的请求 JSON 含历史趋势信息；可对照 §3.6 示例核对。

### P1-5 · AiController 缺失（FR-07 控制层未纳入远端）
- **位置**：已提交树 `src/com/bmi/controller/` **为空**；`AiController.java` 仅存在于工作区未跟踪残留（且为旧 `AiService` 版本，已过时）。
- **设计要求**：plan.md §4.2/§6.1 `AiController.getAdvice(long userId)` 汇总指标+历史→调 `AiHealthClient`→返回文本；ai_design.md §5.3 由 `AiController` catch `AiConfigException` 返回配置文案。
- **现状**：FR-07 的控制器层在远端版本中缺失，端到端不可运行。
- **整改**：补 `AiController.getAdvice(long userId)`，内部 `new AiHealthClient()` + `buildRequest` + `requestAdvice`，并对 `AiConfigException` 兜底返回「AI 服务未配置，请联系管理员」。
- **验收**：`AiController` 编译通过且能被视图层调用；覆盖 FR-07。

### P1-6 · parseResponse 混用解析方式（随 P0-2 一并整改）
- **位置**：`AiHealthClient.parseResponse` 用 `org.json` 解析 usage/finish_reason，但 content 用 `extractContent` 手工解析。
- **整改**：移除 `org.json` 后，用同一套手工解析补齐 `usage.prompt_tokens/completion_tokens/total_tokens` 与 `finish_reason`（当前已实现的逻辑需保留，仅换解析手段）。
- **验收**：手工解析下 `usage` 三字段、`finishReason` 仍能正确填充。

---

## 4. 🟡 P2 — 健壮性 / 一致性 / 分支卫生

| 编号 | 项 | 现状 | 整改 |
|---|---|---|---|
| P2-1 | `TestAiService` 非 JUnit | 仅有 `main`，且调用 `model.ai.BodyRecord(175,70,25,1,72,120,80)`（7 参，依赖重复实体） | 改为 JUnit 测试或至少修正构造调用真实实体；命名/包符合测试规范 |
| P2-2 | 构造期抛异常 | 密钥缺失时 `new AiHealthClient()` 在构造函数 `loadConfig()` 抛 `AiConfigException`，调用方易漏 catch | 延迟到首次调用，或文档化「构造须 try-catch」 |
| P2-3 | 文档不一致 | plan.md §1 技术栈写 **Swing**，宪章/ai_design 指定 **JavaFX**；ai_design.md §54 示例用 『』、§156/397 用【】 | 统一为 JavaFX + 【】分隔符 |
| P2-4 | 无日志 | 四类异常/配置失败均无日志，排查困难 | 补 `java.util.logging` 记录异常与降级原因 |
| P2-5 | 分支卫生 | `feature-ai` 含 `cfe1efb` 等污染提交；`main` 上 AiRequest/AiHealthResult 仍为空桩（未提交正确版） | 修复后在 `main` 提交干净版本，用 filter-repo 清理 `feature-ai` 历史后删除该分支 |

---

## 5. 修复顺序建议

1. **P0-1 轮换 Key + 清理 feature-ai 历史**（安全，不可逆，最先做）
2. **P0-2 移除违规 jar + 改手工 JSON 解析**
3. **P1-1 补 `AiException` 基类**
4. **P1-2 统一三段分隔符**（否则 FR-07 恒降级）
5. **P1-3/P1-4 修实体重复 + 补 historyTrend 序列化**
6. **P1-5 补 `AiController` 接线**
7. **P2 打磨 + 在 main 提交干净版本、清理 feature-ai 分支**

---

## 6. 编译状态结论

- **当前 `origin/feature-ai` 可编译**（依赖已提交的 `json-20240303.jar`）。
- 但编译正确性建立在**违规依赖**之上；按 P0-2 移除 jar 后，需同步完成 P1-6 手工解析改造才能保持可编译。
- 完成 P0/P1 全部项后，AI 模块将**既符合白名单、又能编译、且 FR-07 端到端可用**。
