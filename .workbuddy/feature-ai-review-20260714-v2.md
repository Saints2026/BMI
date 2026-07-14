# feature-ai 分支 · AI 模块全量静态审查（对照 ai_design.md v1.0 + plan.md）

> 审查时间：2026-07-14 19:15（Git Bash 时区）
> 审查对象：当前 `feature-ai` 分支工作区 AI 模块源码（快照还原后状态）
> 比对基线：`docs/ai_design.md`（SDD-AI v1.0，已还原为干净详细版）+ `docs/plan.md`（SDD v1.0）
> 结论：AI 模块**无法编译**，根因为两个 DTO 与权威设计不一致；`AiService` 本身符合设计，是 DTO 需重建。

---

## 0. 审查范围与文件清单

| 文件 | 状态 | 说明 |
|------|------|------|
| `src/com/bmi/model/ai/AiService.java` | 快照版（设计正确） | 引用丰富 DTO 结构，遵循 ai_design §3–§7 |
| `src/com/bmi/model/ai/AiRequest.java` | **简化桩版（不符设计）** | 仅 4 字段 final 构造器，缺 UserMetrics/HistoryTrend/ModelParams |
| `src/com/bmi/model/ai/AiHealthResult.java` | **简化桩版（不符设计）** | 仅 success/code/message/adviceText，缺三段/usage/finishReason |
| `src/com/bmi/model/ai/AiException.java` | 符合 | 基类 Exception，符合 §7.1 |
| `src/com/bmi/model/ai/AiConfigException.java` | 符合 | extends AiException，符合 §7.1 |
| `src/com/bmi/controller/AiController.java` | 符合（依赖 AiService） | 编排正确，编译随 AiService |

> 已隔离（不在 src/）：`AiHealthClient.java`/`AiCacheUtil.java`/`TestAiService.java`/`model/ai/BodyRecord.java`（上一轮已移出仓库，解决了架构分裂 P0-3）。
> `docs/ai_design.md` 合并冲突标记已清除（解决了上一轮 P0-2）。

---

## 1. 缺陷总览（按优先级）

| 编号 | 级别 | 标题 | 位置 | 是否阻断编译 |
|------|------|------|------|--------------|
| **P0-1** | 🔴 P0 | `AiRequest` 不符 ai_design §3.1–§3.4（缺内嵌子结构） | `AiRequest.java` 全文 | ✅ 阻断 |
| **P0-2** | 🔴 P0 | `AiHealthResult` 不符 ai_design §4.1（缺三段/usage/finishReason/ok） | `AiHealthResult.java` 全文 | ✅ 阻断 |
| P1-1 | 🟠 P1 | 历史趋势取「最旧 N 条」而非「近 N 次」 | `AiService.java:62-63` | 否 |
| P1-2 | 🟠 P1 | `usage`（token 用量）从未解析 | `AiService.java:173-192` | 否 |
| P1-3 | 🟠 P1 | `hasThreeSections()` 须严格满足 AC-07 三段齐全 | 重建 `AiHealthResult` 时落实 | 否 |
| P1-4 | 🟠 P1 | 缺独立 `BmiCalculator`/`BodyFatCalculator`；`classify` 内联 | `AiService.java:295` + plan §4.4 | 否 |
| P2-1 | 🟡 P2 | 手工 JSON 转义未覆盖 `\t`/嵌套引号 | `AiService.java:358-363` | 否 |
| P2-2 | 🟡 P2 | 设计文档残留 `AiHealthClient` 旧命名（实际类为 `AiService`） | `ai_design.md` §2/§5.3/§6.3 | 否（文档） |
| P2-3 | 🟡 P2 | `AiController` 未显式 catch `AiConfigException`（由 AiService 内部吞掉） | `AiController.java:32` | 否 |
| P2-4 | 🟡 P2 | 四类异常未按 §5.1 记日志（WARN/INFO） | `AiService.java` 全局 | 否 |
| P2-5 | 🟡 P2 | 全部 AI 源码仍为未提交（`??`）状态，PR 为空 | git status | 否（分支卫生） |
| ⚠ 仓库级 | 🔴 阻塞 | `feature-ai` 历史含泄露密钥 `cfe1efb`，且分支未合并清理 | git 历史 | 推送前须处理 |

---

## 2. P0 详解与整改动作（必须修复）

### P0-1 · `AiRequest.java` 重建（对齐 ai_design §3.1–§3.4）

**设计要求**（§3.1）：`AiRequest` 含 4 字段信封 `systemPrompt`(String) / `userMetrics`(UserMetrics) / `historyTrend`(HistoryTrend) / `modelParams`(ModelParams)；每子结构见 §3.2/§3.3/§3.4。

**当前**（`AiRequest.java:7-35`）：仅 `systemPrompt`(final String)、`record`(BodyRecord)、`maxTokens`、`temperature`，构造器 `AiRequest(String, BodyRecord, int, double)`，无子结构、无 setter。

**AiService 实际引用（均缺失 → 编译失败）**：
- `new AiRequest.UserMetrics()` + `req.setUserMetrics(um)` — `AiService.java:50,59`
- `new AiRequest.HistoryTrend()` + `req.setHistoryTrend(ht)` — `:61,76`
- `new AiRequest.ModelParams()` + `req.setModelParams(mp)` — `:78,82`
- `new AiRequest.MetricPoint()` + 其 setter — `:68-73`
- `req.setSystemPrompt(...)` — `:48`（当前 systemPrompt 是 final，无 setter）
- `req.getModelParams().getModel/getTemperature/getMaxTokens()` — `:210,215,216`

**整改动作**：按以下结构重建 `AiRequest`（保留 `getSystemPrompt` 与 `getUserMetrics/getHistoryTrend/getModelParams` getter）：
```java
public class AiRequest {
    private String systemPrompt;
    private UserMetrics userMetrics;
    private HistoryTrend historyTrend;
    private ModelParams modelParams;
    // + 全参构造器 / getter / setter

    public static class UserMetrics {   // §3.2
        private double bmi; private String bmiGrade; private double bodyFat;
        private double weight; private double height;
        private int age; private int gender; private String measureTime;
        // + getter/setter
    }
    public static class MetricPoint {    // §3.3 points[]
        private String measureTime; private double bmi; private double weight; private double bodyFat;
        // + getter/setter
    }
    public static class HistoryTrend {   // §3.3
        private int count; private String direction; private List<MetricPoint> points;
        // + getter/setter
    }
    public static class ModelParams {    // §3.4
        private String model; private double temperature; private int maxTokens;
        // + getter/setter
    }
}
```
**验收**：`AiService.buildRequest` 全部引用可编译；`userMetrics` 校验区间符合 §3.2（bmi>0&&≤100 / weight[10,300] / height[50,250] / age[1,120] / gender 0|1）。

### P0-2 · `AiHealthResult.java` 重建（对齐 ai_design §4.1）

**设计要求**（§4.1）：字段 `success`/`code`/`message`/`adviceText`/`dietAdvice`/`exerciseAdvice`/`healthAdvice`/`finishReason`/`usage{promptTokens,completionTokens,totalTokens}`；§4.2 要求解析并填充三段与 usage；§5.2 错误码常量。

**当前**（`AiHealthResult.java:7-43`）：仅 `success/code/message/adviceText`，工厂 `success(String)`/`fail(String,String)`，缺子结构与 `ok()`/`hasThreeSections()`/`setFinishReason()`。

**AiService 实际引用（缺失 → 编译失败）**：
- `AiHealthResult.ok(content, diet, exercise, health)` — `:186`
- `r.setFinishReason(...)` — `:187`
- `r.hasThreeSections()` — `:145`

**整改动作**：重建 `AiHealthResult`，含：
- 字段 + 私有构造器；
- 成功工厂 `public static AiHealthResult ok(String content, String diet, String exercise, String health)`（同时存 adviceText=content 与三段）；
- `public boolean hasThreeSections()`：要求 dietAdvice/exerciseAdvice/healthAdvice 均非空（AC-07 严格门禁）；
- `public void setFinishReason(String)` + `finishReason` 字段；
- `usage` 子对象（`Usage` 内嵌类：promptTokens/completionTokens/totalTokens）+ setter；
- 保留 `fail(String,String)` 与 5 个错误码常量（当前常量已与 §5.2 一致：AI_NET_001 / AI_TIMEOUT_002 / AI_PARAM_003 / AI_SRV_004 / AI_CFG_005 ✔）。

**验收**：`AiService.parseResponse`/`send` 全部引用可编译；`hasThreeSections()` 严格校验三段齐全。

> 修复 P0-1 + P0-2 后，`AiService` 与 `AiController` 恢复编译（其余代码已符合设计）。

---

## 3. P1 详解与整改动作（发布前须修）

### P1-1 · 历史趋势方向错误（AiService.java:62-63）
`history.subList(0, Math.min(history.size(), 10))` 取**最旧** 10 条（`RecordDao.queryByUser` 升序 old→new，见 plan §4.3）。§3.3 要求「近 N 次」（最新）。
**修复**：改为取末尾 N 条 `history.subList(Math.max(0, history.size()-N), history.size())`，N 默认 5、上限 10（§3.3）。`computeDirection(recent)` 随之得到正确的近期方向。

### P1-2 · usage 未解析（AiService.java:173-192）
§4.1/§4.2 要求填充 `usage` 子对象。当前 `parseResponse` 仅 `setFinishReason`，从不解析 `usage.prompt_tokens/completion_tokens/total_tokens`。
**修复**：在 `parseResponse` 中用 `extractJsonString` 取 `prompt_tokens`/`completion_tokens`/`total_tokens`（嵌套在 `usage` 对象内，需先定位 `"usage":{...}` 再取值），构造 `Usage` 并 `r.setUsage(...)`。

### P1-3 · AC-07 三段门禁严格化
随 P0-2 重建 `hasThreeSections()` 时，必须校验 `dietAdvice/exerciseAdvice/healthAdvice` 全部非空（§4.4 + AC-07「文本非空且含三段」）。当前 `extractSegment` 以 `【饮食】`/`饮食` 为标记，需确认三段均能切出，否则降级为 SERVER_ERROR（AiService.java:145-147 已接该分支）。

### P1-4 · 抽取独立计算类（plan §4.4 / ai_design §3.2）
`AiService.classify(double)`（:295）内联了 BMI 分级；§3.2 规定 `bmiGrade` 由 `BmiCalculator.classify` 得出，且 plan §4.4 列出 `BmiCalculator`/`BodyFatCalculator` 独立类。
**修复**：新建 `BmiCalculator`（含 `calcBmi`/`classify`）与 `BodyFatCalculator`（`predictBodyFat`），`AiService` 改调 `BmiCalculator.classify(latest.getBmi())`；原内联 `classify` 删除。

---

## 4. P2 详解（健壮性 / 一致性 / 卫生）

- **P2-1 JSON 转义**：`escapeJson`（:358）未处理 `\t` 与控制字符；`extractJsonString`（:321）遇到值内 `\"` 会误断。建议补充 `\t` 转义并在提取值时跳过转义序列（或限死后端不返回特殊字符）。
- **P2-2 文档旧命名**：`ai_design.md` §2/§5.3/§6.3/§7.1 多处写 `AiHealthClient`，实际封装类为 `AiService`（用户已确命名，AiService.java:22 注释「命名以用户指定为准」）。建议统一文档为 `AiService`，消除歧义。
- **P2-3 异常职责**：§5.3 写 `AiController` catch `AiConfigException`；当前 `AiService.requestAdvice` 内部已吞掉并返回文案，`AiController` 未 catch（行为正确但契约字面未达）。可二选一：保持现状（行为达标）或在 `AiController.getAdvice` 外包裹 try/catch（不必要）。
- **P2-4 日志缺失**：§5.1 要求各场景记 WARN/INFO（脱敏 host）；`AiService` 无任何日志。建议用 `System.err` 或轻量工具类输出（不引入第三方日志库，守白名单）。
- **P2-5 分支卫生**：全部 AI 源码 `git status` 为 `??` 未提交；推送前须提交到 `main`（非 feature-ai），并清理 feature-ai 含密钥历史（见下方仓库级告警）。

---

## 5. 仓库级阻塞（推送前必须处理）

- 🔴 **泄露密钥**：`feature-ai` 提交 `cfe1efb` 将真实 `ai-key.properties` 落入 git 历史。工作区副本已隔离，但**历史中密钥若已推送则已公开**。须：
  1. 立即**轮换/吊销**该 API Key；
  2. 用 `git filter-repo` 或 `bfg` 清理 feature-ai 历史后强制更新；
  3. 仅将 AI 模块内容提交到 `main`（参考 `.workbuddy/git-push-snapshot.sh`），**勿合并 feature-ai**。

---

## 6. 推荐修复顺序

1. **P0-1 + P0-2**：按 §2 重建 `AiRequest` + `AiHealthResult`（<— 这一步让整个 AI 模块恢复编译，是其余整改前提）。
2. **P1-1 + P1-2 + P1-3**：修正趋势方向、解析 usage、严格三段门禁（均落在已重建的 DTO 与 `AiService` 内）。
3. **P1-4**：抽取 `BmiCalculator`/`BodyFatCalculator`。
4. **P2-1~P2-4**：健壮性打磨。
5. **仓库级**：轮换密钥 + 清理 feature-ai 历史 + 提交到 main。
6. 编译验证：`javac -encoding UTF-8 -cp lib/* src/com/bmi/model/ai/*.java src/com/bmi/controller/AiController.java`。

---

## 7. 符合项（已达标，保留）

- ✅ 四类异常（断网/超时/参数为空/服务器报错）+ 重试策略（仅 TIMEOUT/5xx 重试 1 次）与 §5.1/§6.2 一致；
- ✅ 双 10s 超时常量 `CONNECT_TIMEOUT_MS`/`READ_TIMEOUT_MS`=10000，对齐 AC-07；
- ✅ Bearer 鉴权、URL/key/model 全配置外置（`ai-key.properties`），源码零硬编码；
- ✅ 五条降级文案与 §5.1/§5.2 逐字一致且不暴露技术栈；
- ✅ 错误码常量值与 §5.2 完全一致（AI_NET_001/AI_TIMEOUT_002/AI_PARAM_003/AI_SRV_004/AI_CFG_005）；
- ✅ `AiException`/`AiConfigException` 继承关系符合 §7.1；
- ✅ `AiController.getAdvice` 编排（findLatest→queryByUser→buildRequest→requestAdvice）符合 plan §4.2/§4.4 与 FR-07；
- ✅ OpenAI Chat Completions 报文格式（model/messages/temperature/max_tokens）符合 plan §6.1；
- ✅ 上一轮 P0-2（文档冲突）、P0-3（架构分裂）已解决。
