# feature-ai 校验 · 结构化整改清单

> **校验对象**：本地仓库 AI 模块（工作区未提交代码，等同 feature-ai 实现）
> **对照文档**：`docs/ai_design.md`（v1.0）+ `docs/plan.md`（v1.0）+ 宪章 `CODEBUDDY.md`
> **校验方式**：静态代码审查（逐项比对设计约束、错误码、降级文案、超时/重试策略）
> **校验范围**：`model.ai`（`AiService`/`AiRequest`/`AiHealthResult`/`AiException`/`AiConfigException`）+ `controller.AiController`
> **日期**：2026-07-14

## 0. 工具与边界说明（重要）
- `gh` CLI **未安装**、GitHub 连接器 **已断开** → 无法直连远程 `feature-ai` 分支/PR。
- 本地 `git branch -a` 仅见 `main` 与 `origin/main`，**无** `feature-ai` 远程或本地分支；当前 AI 模块代码处于未提交工作区（`?? src/com/`）。
- 故本次校验以**工作区 AI 模块源码**作为 feature-ai 内容进行审查。若需针对远程 PR 校验，请先安装 `gh` 并连接 GitHub，或提供 PR 链接/补丁。

## 1. 符合项（Pass，共 12 项）
| # | 设计约束 | 结论 | 证据 |
|---|----------|------|------|
| P-01 | 类/包命名 `com.bmi.model.ai` + `XxxService` 后缀 | ✅ | `AiService`/`AiRequest`/`AiHealthResult`/`AiException`/`AiConfigException` |
| P-02 | `AiRequest` 四段结构（systemPrompt/userMetrics/historyTrend/modelParams）齐全 | ✅ | `AiRequest.java` |
| P-03 | `userMetrics` 8 字段 + `historyTrend`(count/direction/points) + `modelParams` 齐全 | ✅ | `AiRequest.java:53-225` |
| P-04 | `AiHealthResult` 9 字段 + 6 个错误码常量与 §5.2 逐字一致 | ✅ | `AiHealthResult.java:9-14` |
| P-05 | 四类异常（断网/超时/参数空/服务器错）+ 重试（仅 TIMEOUT 与 5xx 重试 1 次，其余不重试） | ✅ | `AiService.java:96-169` |
| P-06 | 五条降级文案与 §5.1/§5.2 逐字一致 | ✅ | `AiService.java:99-160` |
| P-07 | HttpURLConnection：POST + 双超时 10000ms + Content-Type/Accept/Bearer + URL 来自配置 | ✅ | `AiService.java:116-124, 34-36` |
| P-08 | 密钥安全：仅 `ai-key.properties` 读取、零硬编码；`.gitignore` 已覆盖 | ✅ | `AiService.java:222-263` + `.gitignore:14` |
| P-09 | `requestAdvice` 返回 String（成功=adviceText / 失败=message），不向上抛传输异常 | ✅ | `AiService.java:89-92` |
| P-10 | 密钥缺失抛 `AiConfigException` → 内部捕获 → CONFIG_ERROR 文案 | ✅ | `AiService.java:104-108,222-229` |
| P-11 | `AiController` 编排：findLatest + queryByUser + buildRequest + requestAdvice | ✅ | `AiController.java:32-42` |
| P-12 | `hasThreeSections` 成功门禁（含饮食/运动/健康三段） | ✅ | `AiHealthResult.java:46-51` |

## 2. 整改清单（Action Items）

### 🔴 P0 — 功能正确性（必须修）
| ID | 文件:行 | 设计要求 | 现状 | 整改动作 | 验收 |
|----|---------|----------|------|----------|------|
| **G1** | `AiService.java:62-63` | ai_design §3.3「近 N 次历史趋势（默认5，最多10）」应为**最新** N 条 | `history.subList(0, min(size,10))` 取的是**最旧** N 条（`queryByUser` 默认升序旧→新）；`computeDirection` 方向也基于最旧区间，整体语义错误 | 改为取末尾 N 条：`int from = Math.max(0, history.size()-10); history.subList(from, history.size())` | 输入 10+ 条记录时，`historyTrend.points` 为最近 10 条、`direction` 由最近区间得出；与 §3.3「近 N 次」一致 |

### 🟠 P1 — 设计覆盖缺失（应修）
| ID | 文件:行 | 设计要求 | 现状 | 整改动作 | 验收 |
|----|---------|----------|------|----------|------|
| **G2** | `AiService.java:173-192` | ai_design §4.1/§4.2：`usage`(promptTokens/completionTokens/totalTokens) 由解析 `usage.*` 映射填充 | `parseResponse` 仅解析 `content` 与 `finish_reason`，**从未 setUsage**，usage 恒为 null | 解析 `usage.prompt_tokens/completion_tokens/total_tokens` 并 `r.setUsage(...)` | 成功响应后 `AiHealthResult.getUsage()` 三字段非零且等于返回用量 |
| **G3** | 全局（缺失类） | plan §4.4/§8：`BmiCalculator.calcBmi/classify`、`BodyFatCalculator.predictBodyFat` 为**独立纯业务类**，可由 JUnit 单测 | 源码**无** `BmiCalculator.java`/`BodyFatCalculator.java`；`classify` 以 `AiService` 私有方法内联（`:295-306`），bmi/bodyFat 由 `RecordController` 计算后入库 | 建议补 `BmiCalculator`/`BodyFatCalculator` 独立类（或至少抽出共享 `classify`），并补 JUnit；AI 模块仅消费已有值则保持但需在文档标注 | 存在可单测的独立计算类；`classify` 逻辑单点维护，无重复实现 |

### 🟡 P2 — 稳健性 / 配置（建议修）
| ID | 文件:行 | 设计要求 | 现状 | 整改动作 | 验收 |
|----|---------|----------|------|----------|------|
| **G4** | `AiService.java:321-336,365-367` | ai_design §4 允许手工 JSON，但应正确解析转义 | `extractJsonString` 用首个/末个 `"` 截取，content 内含转义 `\"` 会提前截断；`unescapeJson` 可能二次反转义 | 抽取时跳过 `\"`（转义引号），并确认 unescape 仅作用于 content 文本 | 含 `\"` 的返回 content 完整解析，不误降级 |
| **G5** | `AiService.java:231-245` | ai_design §6.3 配置缺失应明确 | `readApiUrl`/`readModelDefault` 在 `AiConfigException` 时**静默回退** openai 默认值，且**无日志**；若 `api.key` 在但 `api.url` 缺，会用错端点且难排查 | url/model 缺失时记 WARN 日志；或要求三者齐全、缺失即抛 `AiConfigException` 走 CONFIG_ERROR | 配置不全时有可观测日志，不再静默错误端点 |

### 🟢 P3 — 文档/职责一致性（低优先）
| ID | 文件:行 | 设计要求 | 现状 | 整改动作 | 验收 |
|----|---------|----------|------|----------|------|
| **G6** | `AiController.java:32-42` | ai_design §5.3：AiController 应 catch `AiConfigException` 转文案 | 当前 `AiService.requestAdvice` 已内部吞掉该异常返 CONFIG_ERROR 文案，控制器无需处理，功能等价；但与文档「控制器捕获」描述不一致 | 保持 `AiService` 内部降级一致性即可；文档 §5.3 加注「requestAdvice 已内置捕获，控制器透传字符串」 | 文档与实现一致 |
| **G7** | `AiService.java:46` | ai_design §3.5「参数来源 User(user)」 | `buildRequest(User u, ...)` 中 `u` 未被引用 | 如需用户上下文入请求则补充；否则保留参数作扩展 | 消除歧义（文档/实现二选一对齐） |
| **G8** | `AiService.java:205` | ai_design §3.3「默认5，最多10」 | `buildOpenAiJson` 用 `ht.getCount()`（实际 0~10）拼接「近 N 次」 | 明确 N 取值或注释说明 | 文案与取值一致 |
| **G9** | `AiController.java:37` | 依赖 `queryByUser` 升序契约 | 排序契约仅在注释/计划约定，未在代码或 DAO 接口断言 | `buildRequest` 内显式排序，或在 `RecordDao` 接口固化排序契约 | 排序契约有代码级保证 |

### 🔵 集成项（FR-07 端到端，需另轮核验 view 层）
| ID | 范围 | 设计要求 | 现状 | 整改动作 | 验收 |
|----|------|----------|------|----------|------|
| **G10** | `view` 层 | ai_design §2：`AiAdvicePanel`/`MainView.showAdvice(String)` 触发"获取建议"并展示 adviceText/三段；降级文案（含 CONFIG_ERROR）需展示 | 本轮聚焦 AI 模块，未核验 view 是否接线 `AiController.getAdvice` | 补充视图触发与展示逻辑，覆盖五类降级文案 | 点击"获取建议"→ 成功显示三段 / 失败显示对应降级文案，主流程不阻断 |

## 3. 结论
- **整体达标度**：命名、错误码、降级文案、四类异常处理、超时/重试、密钥安全 **全部符合** 设计；核心逻辑骨架正确。
- **必须修复**：仅 **G1（历史趋势取错方向）** 1 项 P0 功能缺陷。
- **建议修复**：G2（usage 未解析）、G3（BmiCalculator/BodyFatCalculator 缺失，项目级）、G4/G5（稳健性与配置可观测）。
- **阻塞项**：无编译/依赖阻塞；`lib/` 仍缺 JavaFX/JDBC/JUnit jar（见快照 SNAP-20260714-001），运行与单测需先补齐。

---
*本清单为静态审查产物，配合 `SNAP-20260714-001` MD5 校验（42/42 通过）共同构成 feature-ai 验收基线。*
