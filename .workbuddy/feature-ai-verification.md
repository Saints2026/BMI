# feature-ai 全量校验报告

> **校验对象**：本地同步后的 AI 模块源码（工作区未提交，等同 feature-ai 实现）
> **对照文档**：`docs/ai_design.md`（v1.0）、`docs/plan.md`（v1.0）、`CODEBUDDY.md`（宪章）
> **校验日期**：2026-07-14
> **校验方式**：静态代码审查 + MD5 比对（确认与快照 SNAP-20260714-001 逐字节一致）

## 0. 证据基础（为何可信）
| 文件 | MD5 | 快照比对 |
|------|-----|----------|
| `src/com/bmi/model/ai/AiService.java` | `0b7c5fb781bad4f68fa58d66101884b9` | ✅ 一致 |
| `src/com/bmi/model/ai/AiRequest.java` | `54aacf514eae5d706fdccb0ea06f9c4a` | ✅ 一致 |
| `src/com/bmi/model/ai/AiHealthResult.java` | `7c786074d3f8da3f61aa585ead1bd6eb` | ✅ 一致 |
| `src/com/bmi/model/ai/AiException.java` | `fa03505733188f1bd6a2a6e980db2eb0` | ✅ 一致 |
| `src/com/bmi/model/ai/AiConfigException.java` | `6f1c068fd67cd4ca41c5fc3795a22015` | ✅ 一致 |
| `src/com/bmi/controller/AiController.java` | `1c528185ff488f7b419d02f05f3b8f03` | ✅ 一致 |

> 6 文件 MD5 全部与快照一致 → **AI 源码自快照后未被改动**，本轮为对相同代码的全量复核与扩展。

---

## 1. 全量需求追溯矩阵

### 1.1 ai_design.md §3 请求入参
| 需求 | 代码落点 | 状态 |
|------|----------|------|
| §3.1 `AiRequest` 四段结构(systemPrompt/userMetrics/historyTrend/modelParams) | `AiRequest.java` | ✅ PASS |
| §3.2 `userMetrics` 8 字段(bmi/bmiGrade/bodyFat/weight/height/age/gender/measureTime) | `AiRequest.UserMetrics` | ✅ PASS |
| §3.3 `historyTrend`(count/direction/points) 数量约束 ≤10 | 结构齐全；但 **取数方向错误** | ❌ FAIL → G1 |
| §3.4 `modelParams`(model 取自配置/temperature/maxTokens) | `AiService.buildRequest:78-82` | ✅ PASS |
| §3.5 `buildRequest(User, BodyRecord, List<BodyRecord>)` 签名 | `AiService.java:46` | ✅ PASS（参数 `u` 未用 → G7） |
| §3.6 OpenAI Chat Completions 报文映射 | `buildOpenAiJson:196-218` | ✅ PASS |

### 1.2 ai_design.md §4 返回格式
| 需求 | 代码落点 | 状态 |
|------|----------|------|
| §4.1 `AiHealthResult` 9 字段 + `usage` 子结构 | `AiHealthResult.java` | ✅ 结构 PASS；**`usage` 从未填充** → G2 |
| §4.2 `parseResponse` 解析 content + usage 映射 | `AiService.parseResponse:173-192` | ❌ FAIL → G2（缺 usage） |
| §4.3 成功响应 JSON → `choices[0].message.content` | `extractJsonString("content")` | ✅ PASS |
| §4.4 三段映射 UI / `hasThreeSections` 门禁 | `AiHealthResult.hasThreeSections` | ✅ PASS（视图接线见 G10） |

### 1.3 ai_design.md §5 四类异常处理
| 需求 | 代码落点 | 状态 |
|------|----------|------|
| §5.1① 断网(Connect/UnknownHost)→NETWORK_ERROR 不重试 | `AiService:156-157` | ✅ PASS |
| §5.1② 超时(SocketTimeout)→TIMEOUT 重试1次 | `AiService:150-154` | ✅ PASS |
| §5.1③ 参数空→INVALID_PARAM 不重试 | `AiService:97-101` | ✅ PASS |
| §5.1④ 5xx→SERVER_ERROR 重试1次；4xx 不重试 | `AiService:133-141` | ✅ PASS |
| §5.1(附) 密钥缺失→`AiConfigException`→CONFIG_ERROR 文案 | `loadApiKey:222 + catch:106` | ✅ PASS |
| §5.2 六错误码常量(AI_NET_001/AI_TIMEOUT_002/AI_PARAM_003/AI_SRV_004/AI_CFG_005/0) | `AiHealthResult.java:9-14` | ✅ PASS |
| §5.3 职责边界：AiService 捕获转换降级 / AiController 捕获配置异常 | `AiService` 已内置；`AiController` 未显式 catch | ⚠️ 部分 → G6 |

### 1.4 ai_design.md §6 超时/重试/密钥
| 需求 | 代码落点 | 状态 |
|------|----------|------|
| §6.1 POST + 双超时 10000ms + 请求头 + URL 来自配置 | `AiService:116-124,34-36` | ✅ PASS |
| §6.2 `MAX_RETRY=1`；仅 TIMEOUT/5xx 重试；4xx/其他不重试 | `AiService:35,112-169` | ✅ PASS |
| §6.3 密钥仅 `loadApiKey` 读 `ai-key.properties`、零硬编码、gitignore 覆盖 | `AiService:222-263` + `.gitignore:14` | ✅ PASS（**静默回退无日志** → G5） |

### 1.5 ai_design.md §7 命名 & §8 FR/AC 追溯
| 需求 | 代码落点 | 状态 |
|------|----------|------|
| §7.1 类/异常命名(`com.bmi.model.ai`, `XxxService`) | 全部符合 | ✅ PASS |
| §7.2 `send()` 主流程骨架对齐 | `AiService.send:96-169` | ✅ PASS |
| §8 FR-07 / AC-07(10s、超时文案、降级文案、密钥缺失文案、三段非空) | 全部覆盖且文案逐字一致 | ✅ PASS |

### 1.6 plan.md AI 相关约束
| 需求 | 代码落点 | 状态 |
|------|----------|------|
| §4.4 `AiService.requestAdvice` / `buildRequest` | 已实现 | ✅ PASS |
| §4.4 `BmiCalculator.calcBmi`/`classify`、`BodyFatCalculator.predictBodyFat` 为独立纯业务类 | **类不存在**：calcBmi/predictBodyFat 在 `RecordController`(静态方法)，classify 内联 `AiService` | ❌ FAIL → G3 |
| §6.1 请求构造(OpenAI 格式/10s/key 来自 props) | 已实现 | ✅ PASS |
| §6.2 响应解析与降级(4 场景) | 已实现并扩展 5xx | ✅ PASS |

### 1.7 依赖契约核验（AI 模块调用方可编译性）
| 项 | 结论 |
|----|------|
| `RecordDao.findLatest/queryByUser/findById` 存在且 `queryByUser` 升序 | ✅ 确认（RecordDao.java:23,52,58；注释明确升序）→ 支撑 G1 判定 |
| `UserDao.findById` 存在 | ✅ 确认（UserDao.java:30） |
| `BodyRecord` 全部 getter（age/gender/measureTime/bmi/bodyFat/weight/height） | ✅ 确认（grep 命中） |

---

## 2. 整改清单（Action Items）

### 🔴 P0 必须修复
| ID | 文件:行 | 问题 | 整改 | 验收 |
|----|---------|------|------|------|
| **G1** | `AiService.java:62-63` | `history.subList(0, min(size,10))` 取**最旧** N 条（`queryByUser` 升序），违背 §3.3「近 N 次」 | 取末尾 N 条：`int from = Math.max(0, history.size()-10); history.subList(from, history.size())` | `historyTrend.points` 为最近 10 条、`direction` 由最近区间得出 |

### 🟠 P1 应修复
| ID | 文件:行 | 问题 | 整改 | 验收 |
|----|---------|------|------|------|
| **G2** | `AiService.java:173-192` | `usage`(prompt/completion/total tokens) 从未解析，恒 null（§4.1/§4.2 缺失） | `parseResponse` 解析 `usage.prompt_tokens/completion_tokens/total_tokens` 并 `setUsage` | 成功响应后 `getUsage()` 三字段等于返回用量 |
| **G3** | 全局（缺类） | `BmiCalculator`/`BodyFatCalculator` 独立类不存在（plan §4.4/§8）；计算散落 `RecordController`(静态) 与 `AiService`(classify 内联) | 抽独立纯业务类供 JUnit 单测；AI 仅消费已有值则文档标注 | 计算逻辑单点维护、可单测 |

### 🟡 P2 建议修复
| ID | 文件:行 | 问题 | 整改 | 验收 |
|----|---------|------|------|------|
| **G4** | `AiService.java:321-336,365-367` | 手工 JSON 不处理转义 `\"`，content 含转义引号会截断 | 抽取时跳过 `\"`；unescape 仅作用于 content | 含转义引号内容完整解析 |
| **G5** | `AiService.java:231-245` | `readApiUrl/readModelDefault` 配置缺失**静默回退**默认值且无日志 | 缺失记 WARN 日志，或要求三者齐全、缺失即 CONFIG_ERROR | 配置不全可观测 |

### 🟢 P3 低优先
| ID | 文件:行 | 问题 | 整改 |
|----|---------|------|------|
| **G6** | `AiController.java:32-42` | 未显式 catch `AiConfigException`（功能等价但 §5.3 描述不符） | 保持 `AiService` 内部降级；文档 §5.3 加注 |
| **G7** | `AiService.java:46` | `buildRequest(User u,...)` 的 `u` 未引用 | 需用户上下文则补，否则对齐文档 |
| **G8** | `AiService.java:205` | 「近 N 次」用 `ht.getCount()`(0~10)，与 §3.3 默认5 文案不符 | 明确 N 或注释 |
| **G9** | `AiController.java:37` | 依赖 `queryByUser` 升序契约未代码级断言 | `buildRequest` 内显式排序或接口固化 |

### 🔵 集成项（另轮核验 view 层）
| ID | 范围 | 问题 | 整改 |
|----|------|------|------|
| **G10** | `view` 层 | 未核验是否接线 `AiController.getAdvice`（FR-07 端到端） | 补视图触发+展示，覆盖五类降级文案，主流程不阻断 |

---

## 3. 结论
- **整体达标度**：命名、错误码、降级文案、四类异常、超时/重试、密钥安全、AC-07 全部 **PASS**；核心调用骨架正确。
- **必须修复**：仅 **G1**（历史趋势取错方向）1 项 P0。
- **应修复**：G2（usage 未解析）、G3（独立计算类缺失，项目级架构偏差）。
- **建议/低优先**：G4/G5（稳健性与可观测）、G6–G9（文档一致性）、G10（视图接线）。
- **无阻塞**：无编译/依赖阻塞；`lib/` 仍缺 JavaFX/JDBC/JUnit jar（见快照），运行与单测需先补齐。
- **与上一轮关系**：本报告指出项与 `feature-ai-review.md` 一致；因源码 MD5 未变，结论不变，本报告为**全量需求追溯 + 整改清单**的合并版。
