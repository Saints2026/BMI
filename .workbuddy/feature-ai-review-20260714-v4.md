# feature-ai @ a67f8f9 全量静态审查报告（P0/P1/P2 分级整改）

> 审查对象：`origin/feature-ai` HEAD = `a67f8f9`（"fix: 修正TestAiService适配新结构，删除废弃引用"）
> 比对基线：`docs/ai_design.md` v1.0、`docs/plan.md` v1.0、`CODEBUDDY.md` v1.0
> 提取方式：从 `git show a67f8f9:<path>` 取已提交字节（排除工作区残留），8 个 AI 源文件 + 设计文档逐字核对；`javac 17 + -encoding UTF-8` 实测编译。
> 生成时间：2026-07-14

---

## 0. ⚠️ 重大前提（影响"阻断合并"判定）

- **`a67f8f9` 已经是 `origin/main` 的祖先**（`git merge-base --is-ancestor a67f8f9 origin/main` = YES）。即本次审查的 AI 模块**已经合并上线到 main**，并非待合并。
- 因此"阻断合并"实际**已发生**：
  - `origin/main` 树**已含** `ai-key.properties`（真实明文 Key）；
  - `origin/main` 的 `TestAiService.java` 同样硬编码第二把 Key；
  - 两把密钥已落到 main 历史。
- **结论**：本报告同时给出 (a) 若"重新合并 feature-ai 到 main"时的阻断标记；(b) **针对 main 已泄露的紧急整改清单**。此前我给出的"推送 main 安全（main 不含密钥）"判断**已不成立**，请以本报告为准。

---

## 1. P0 安全校验 ❌ 高危（密钥已泄露，且已落到 main）

### P0-S1 · `ai-key.properties` 被提交，含真实明文 Key
- **证据**：`a67f8f9` 树与 `origin/main` 树均含 `ai-key.properties`；内容为 `api.key=sk-9e243736c6294347bf46dcf7d17d2ec6`（真实 Key，已脱敏）。
- **为何入库**：`.gitignore` 第 19 行虽已列 `ai-key.properties`，但该文件被 `git add` 强制入库——**gitignore 不移除已跟踪文件**，故密钥进入历史。
- **全分支历史**：多提交含此密钥（`cfe1efb`、`a689194`、`a63e028`、`2fad333`、`2ca45bb`、`2362c20`、`a67f8f9` 等）。
- **危害**：Key 已公开于 git 历史；main 已含 → 任何人 clone 即得。

### P0-S2 · `TestAiService.java` 硬编码第二把明文 Key
- **证据**：`src/com/bmi/model/ai/TestAiService.java:19` → `String apiKey = "sk-ed48946785d249e3b8349812416ef30e"; // 替换成你的真实 Key`。`a67f8f9` 与 `origin/main` 均含。
- **危害**：源码硬编码密钥，且注释"替换成你的真实 Key"表明曾用于真实调用；属明文硬编码违例（设计 §6.3 要求零硬编码）。

**整改（必须立即，P0）**：
1. **吊销 / 轮换两把 Key**（平台侧作废 `sk-9e2437…` 与 `sk-ed4894…`）；
2. 用 `git filter-repo` / `bfg` **清理 main 与 feature-ai 全历史**中的 `ai-key.properties` 与 `TestAiService` 硬编码；
3. 从仓库**彻底移除** `ai-key.properties`（仅靠 `.gitignore` 不够，已跟踪需 filter-repo）；
4. `TestAiService` 改读 `ai-key.properties`（或测试专用占位），删除硬编码。

> 注：全历史 jar 扫描确认 `lib/` 在 a67f8f9 仅 `.keep`，无密钥文件外的其它敏感配置（`db-config.properties` 未提交，仅 `db-config.properties.example`）。

---

## 2. P0 依赖校验 ✅ 通过

- **三方 jar 残留**：a67f8f9 树 `lib/` 仅 `.keep`；早期 feature-ai 历史曾含 `json-20240303.jar` / `okhttp-4.12.0.jar` / `okio-3.6.0.jar`，但 **a67f8f9 已移除**（解决此前 v3 审查的 P0-2）。✓
- **导入扫描**：`AiHealthClient.java` 仅 import JDK：
  `java.io.*`、`java.net.HttpURLConnection`/`URI`/`URL`、`java.nio.charset.StandardCharsets`、`java.util.List`、`com.bmi.exception.*`、`com.bmi.model.ai.BodyRecord`。**无** `org.json` / `okhttp` / `okio` / `gson` / `fasterxml` 等三方导入。✓
- **实现方式**：`doPost()` 用 `HttpURLConnection`；`buildJsonBody()` 手工 `StringBuilder` 拼 JSON；`parseResponse()`/`extractSection()` 手工 `indexOf`/`substring` 解析 JSON。✓ 符合"原生 HttpURLConnection + 手工 JSON"白名单（ai_design §1/§6.1）。

---

## 3. P1 功能校验

| 项 | 结果 | 说明 |
|----|------|------|
| **P1-F1 分隔符统一 `【】`** | ✅ PASS | `extractSection` 用 `【饮食】/【运动】/【健康】`，`parseResponse` 与 `AiController` 拼接均用 `【】`，与 ai_design §4.3 一致。（修复了此前 v3 的 P1-2 分隔符不符） |
| **P1-F2 统一顶层异常 `AiException`** | ✅ PASS | `AiException extends Exception` 为顶层；`AiConfigException extends AiException`；所有抛出均为二者。 |
| **P1-F3 无重复 BodyRecord 实体** | 🟡 修正 | 见下；a67f8f9 仅一份 `com.bmi.model.ai.BodyRecord`，合并不会新增副本，但属"迷你重造实体"偏差。 |
| **P1-F4 AI 请求携带历史 10 条** | ❌ FAIL | 见下；无"近 N 次（默认5，最多10）"截取语义。 |
| **P1-F5 AiController 可注入 DAO** | ❌ FAIL | 见下；无任何 DAO 依赖，不自行编排取数。 |
| **P1-F6 请求报文合规（可用性）** | ❌ FAIL（高） | 见下；缺 `model`/`messages`/`userMetrics`，API 调用必败。 |
| **P1-F7 四类异常+错误码+重试** | ❌ FAIL | 见下；仅笼统抛 `AiException`，结构不符设计 §4.1/§5。 |
| **P1-F8 降级文案** | 🟡 偏差 | 三段缺失返回硬编码通用建议，非设计标准降级文案。 |

### P1-F3 详情（修正之前"重复"判定）
- a67f8f9 仅有 `com.bmi.model.ai.BodyRecord`（迷你版：仅 `bmi`/`bodyFat`/`measureDate`）。
- `origin/main` 同路径同内容（4 个 model/ai 文件 MD5 与 a67f8f9 完全一致）→ **合并不会产生第二份副本**，故非"两份重复"。降为 P2 治理建议。
- 但偏差仍在：(a) 实体位于 `model.ai` 而非设计规定的 `com.bmi.model`；(b) 它是"重造的迷你实体"，缺设计 `buildRequest` 所需的 `weight`/`height`/`age`/`gender`/`measureTime` 等字段；(c) 全仓（含 main）无 `com.bmi.model.Record`/`User` 实体，AI 模块自造实体。建议统一到项目实体。

### P1-F4 详情
- `buildJsonBody(List<BodyRecord> historyTrend)` 透传调用方传入的全部记录，**无 `subList` 截取、无"近 N 次（默认5，最多10）"语义**（违反 ai_design §3.3）。调用方传 >10 条则全部发送；不保证"最新 10 条"。

### P1-F5 详情
- `AiController` 仅持有 `AiHealthClient`；构造器收 `(apiKey, apiUrl)`；`getAdvice(List<BodyRecord> history)` 由**调用方**传 history。**无任何 `RecordDao`/`UserDao` 依赖**，不自行取最新指标与历史（违反 ai_design §2 / plan §4.2 编排职责）。FR-07 端到端无法由 Controller 独立完成。

### P1-F6 详情（最影响可用性）
- `buildJsonBody` 仅输出 `{"historyTrend":[...]}`，**缺失**：`model`、`messages[system/user]`、`temperature`、`max_tokens`，且**未携带 `userMetrics`**（当前 BMI/体重/年龄/性别等）。
- OpenAI / DeepSeek Chat Completions **必填 `model`+`messages`** → 实际调用必返回 400（缺少 'model'）。违背 plan §6.1 / ai_design §3.6 信封约定。FR-07 实际不可用。

### P1-F7 详情
- 设计 §5 要求 `NETWORK_ERROR/TIMEOUT/INVALID_PARAM/SERVER_ERROR/CONFIG_ERROR` 四类 + 错误码常量（`AI_NET_001`…`AI_CFG_005`）+ 重试 1 次。代码仅笼统 `throw new AiException("AI接口调用失败")`，**无分类、无错误码、无重试、无特定降级文案**。
- `AiHealthResult` 仅为 3 字段内部类（`diet/exercise/health`），缺设计 §4.1 的 `success`/`code`/`message`/`adviceText`/`finishReason`/`usage`。结构不符。

---

## 4. 代码规范校验

- **包分层（按您给定口径 `com.bmi.client / com.bmi.exception / com.bmi.model.ai`）**：代码完全符合。✅
  - ⚠️ **但与设计文档冲突**：`ai_design.md §7.1` / `CODEBUDDY.md` 目录约定规定 `AiHealthClient`、`AiException`、`AiConfigException` 均在 `com.bmi.model.ai`。若以设计文档为准，当前分层属**偏差**（建议统一到 `com.bmi.model.ai`，或反向修订设计文档）。本次以您给定分层为验收口径 → 达标；以宪章为准 → 偏差。
- **类命名大驼峰**：`AiHealthClient`/`AiController`/`AiException`/`AiConfigException`/`BmiCalculator`/`BodyFatCalculator`/`BodyRecord`/`TestAiService` 均合规。✅
- **冗余类**：`TestAiService` 为本地测试（您说明不影响主干），但含硬编码密钥（见 P0-S2），须清理；`BodyRecord`(model.ai) 为迷你重造实体（见 P1-F3）。
- **编译验证**：`javac 17 -encoding UTF-8` 编译 4 文件（`AiException`/`AiConfigException`/`BodyRecord`/`AiHealthClient`）**0 错误**。注意：重复 `import java.net.URI;`（第8、12行）在 Java 中**合法**（非错误）；唯一构建注意——源码含中文，**必须 `-encoding UTF-8`**，否则 Windows GBK 默认报"编码 GBK 的不可映射字符"。

---

## 5. 缺陷清单（汇总 + 阻断合并标记）

| ID | 级 | 类别 | 缺陷 | 位置 | 阻断合并 |
|----|----|------|------|------|----------|
| **P0-S1** | 🔴 P0 | 安全 | `ai-key.properties` 提交含真实明文 Key（已落 main） | `ai-key.properties`（全历史） | 🔴 **阻断 / 已泄露** |
| **P0-S2** | 🔴 P0 | 安全 | `TestAiService` 硬编码第二把明文 Key（已落 main） | `TestAiService.java:19` | 🔴 **阻断 / 已泄露** |
| P0-D1 | ✅ | 依赖 | a67f8f9 无 json/okhttp/okio jar；仅用原生 HttpURLConnection+手工 JSON | `lib/` + `AiHealthClient.java` | ✅ 通过 |
| P1-F6 | 🟠 P1 | 功能 | 请求缺 `model`/`messages`/`userMetrics`，API 调用必 400 失败 | `AiHealthClient.java:36-54` | 🟠 须修（可用性） |
| P1-F5 | 🟠 P1 | 功能 | `AiController` 无 DAO 注入，不编排取数 | `AiController.java:9-26` | 🟠 须修 |
| P1-F7 | 🟠 P1 | 功能 | 四类异常/错误码/重试缺失；`AiHealthResult` 结构不符 §4.1 | `AiHealthClient.java:56-145` | 🟠 须修 |
| P1-F4 | 🟠 P1 | 功能 | 历史未截取"近 10 条"上限/语义 | `AiHealthClient.java:36-54` | 🟠 须修 |
| P1-F8 | 🟡 P1 | 功能 | 三段缺失降级文案偏差（返回硬编码通用建议） | `AiHealthClient.java:101-114` | 🟡 建议 |
| P1-F3 | 🟡 P2 | 规范 | `BodyRecord` 位于 model.ai 且字段不全（非重复副本） | `BodyRecord.java` | 🟡 建议 |
| P2-G1 | 🟡 P2 | 规范 | 包分层偏离设计文档（应 `com.bmi.model.ai`） | 多文件 | 🟡 建议 |
| P2-G2 | 🟡 P2 | 构建 | 须 `-encoding UTF-8` 编译（源码中文） | 全 AI 模块 | 🟡 建议 |
| P2-G3 | 🟡 P2 | 规范 | `Content-Type "application/json; utf-8"` 应为 `; charset=utf-8` | `AiHealthClient.java:62` | 🟡 建议 |

**P0 高危汇总**：2 项均为密钥明文泄露，且**已同时存在于 `origin/main` 与 `origin/feature-ai` 历史**，属已发生安全事故，优先级高于一切功能项。

---

## 6. 整改结论与顺序

1. **🔴 立即（P0，安全）**：
   - 吊销并轮换两把 Key（`sk-9e2437…`、`sk-ed4894…`）；
   - `git filter-repo`/`bfg` 清理 main + feature-ai 全历史中的 `ai-key.properties` 与 `TestAiService` 硬编码；
   - 从仓库移除 `ai-key.properties`，仅靠 `.gitignore` 不够（已跟踪需 filter-repo）；
   - 全仓 grep 确认无其它 `sk-` 明文残留。
2. **🟠 发布前（P1，可用性）**：按 plan §6.1 / ai_design §3.6 补全 OpenAI 信封（`model`/`messages`/`userMetrics`/`temperature`/`max_tokens`）；`AiController` 注入 `RecordDao`/`UserDao` 自行编排取数；落实四类异常+错误码+重试 1 次；历史截取"近 10 条"。
3. **🟡 规范（P2）**：统一包分层到设计文档口径；`BodyRecord` 统一到项目实体；`-encoding UTF-8` 写入构建脚本；修正 `Content-Type` 与降级文案。

> 说明：编译本身无阻断（javac UTF-8 实测 0 错误），功能项 P1-F6 是"合但并不工作"而非"合并不了"；真正阻断合并的是 P0-S1/S2（密钥），且因 a67f8f9 已是 main 祖先，密钥已随合并进入 main，须直接整改 main。
