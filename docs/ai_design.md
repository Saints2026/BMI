# AI 健康建议接口 · 详细设计文档（SDD-AI）

> 本文件是 `docs/plan.md` 第 5/6 节「AI 接口传参规范」的下游细化设计，必须严格遵循 `CODEBUDDY.md`（项目宪章）。
> 全部技术限定于白名单：**Java 8+ · 原生 HttpURLConnection · 手工 JSON 解析 · 密钥读 `ai-key.properties`**；不引入任何 Web 框架 / Spring / 第三方 HTTP 库 / JSON 库。

---

## 1. 文档信息

| 项 | 内容 |
|----|------|
| 文档标题 | AI 健康建议接口 详细设计文档 |
| 版本 | v1.0 |
| 日期 | 2026-07-14 |
| 上游文档 | `docs/plan.md` 第 5 节「AI 接口传参规范」、第 6 节「响应解析与降级」、第 3/4 节 MVC 与 model.ai 接口清单 |
| 关联需求 | `docs/spec.md` 功能需求 **FR-07（AI 健康建议）**、量化验收 **AC-07** |
| 治理宪章 | `CODEBUDDY.md`（技术栈白名单、分层铁律、命名规则、密钥安全） |
| 编写角色 | 架构师 Agent |
| 适用范围 | spec.md FR-07：将用户最新指标 + 近期历史趋势发送给 AI 接口，返回「饮食/运动/健康」三段建议文本；超时与失败须降级且不阻断主流程 |

---

## 2. 接口定位与调用位置

- **封装归属**：`com.bmi.model.ai.AiHealthClient`（model.ai 层），对上层仅暴露「建议文本获取能力」，不暴露 HTTP 细节。
- **编排归属**：`com.bmi.controller.AiController.getAdvice(long userId)`，负责汇总最新指标 + 历史摘要、构造 `AiRequest`、调用 Client、把文本回传视图。
- **调用方向**（呼应宪章第 5 节分层铁律 `view → controller → model`，model 绝不反向依赖 view/controller）：
  ```
  AiAdvicePanel(view)
        │  点击"获取建议"
        ▼
  AiController.getAdvice(userId)            ← 控制层编排
        │  1) RecordController/RecordDao 取 latest + history
        │  2) AiHealthClient.buildRequest(user, latest, history)
        │  3) AiHealthClient.requestAdvice(req)  → 内部 send()
        ▼
  AiHealthClient (model.ai)  ──HttpURLConnection──▶  外部大模型接口
        │ 返回 AiHealthResult / 降级文本
        ▼
  AiController  →  AiAdvicePanel.showAdvice(text)
  ```
- **不阻断原则**（来自 plan.md §6.2）：AI 调用无论成败，录入、计算、图表主流程照常；失败时仅建议展示区替换为降级文案。

---

## 3. 请求入参规范（Request）

### 3.1 内部请求 DTO：`AiRequest` 结构

`AiRequest` 为 model.ai 层内部 DTO，对应 spec FR-07 输入；实际发起 HTTP 时由 `buildRequest()` 序列化为 JSON 请求体。

| 字段 | 类型 | 必填 | 取值约束 | 说明 |
|------|------|------|----------|------|
| `systemPrompt` | String | Y | 非空，≤ 500 字 | 角色设定（健康顾问），如「你是一位严谨的中文健康顾问，请按『饮食/运动/健康』三段给出建议。」 |
| `userMetrics` | Object | Y | 见 3.2 | 最新一次测量指标 |
| `historyTrend` | Object | Y | 见 3.3 | 近 N 次历史趋势摘要（N 默认 5，最多 10） |
| `modelParams` | Object | Y | 见 3.4 | 模型参数 |

### 3.2 `userMetrics` 字段（最新指标）

| 字段 | 类型 | 必填 | 取值约束 | 说明 |
|------|------|------|----------|------|
| `bmi` | double | Y | `>0 且 ≤100` | 最新 BMI（由 `BmiCalculator.calcBmi` 得，保留 1 位） |
| `bmiGrade` | String | Y | 偏瘦/正常/超重/肥胖 | 中国标准分级（`BmiCalculator.classify`） |
| `bodyFat` | double | Y | `0~60` | 最新体脂率（`BodyFatCalculator.predictBodyFat`） |
| `weight` | double | Y | `[10,300]` kg | 体重 |
| `height` | double | Y | `[50,250]` cm | 身高 |
| `age` | int | Y | `[1,120]` | 年龄 |
| `gender` | int | Y | `1=男 / 0=女` | 性别 |
| `measureTime` | String | Y | ISO-8601 `yyyy-MM-dd'T'HH:mm:ss` | 最近测量时间 |

### 3.3 `historyTrend` 字段（历史趋势摘要）

| 字段 | 类型 | 必填 | 取值约束 | 说明 |
|------|------|------|----------|------|
| `count` | int | Y | `≥0` | 实际纳入的点数（近 N 次，N 默认 5） |
| `direction` | String | N | 上升/下降/平稳/无数据 | 整体变化方向（由首末点比较得出） |
| `points` | Array<Point> | N | 每项含 `measureTime,bmi,weight,bodyFat` | 历史采样点（时间升序）；不足 2 点时 `direction=无数据` |

### 3.4 `modelParams` 字段（模型参数）

| 字段 | 类型 | 必填 | 取值约束 | 说明 |
|------|------|------|----------|------|
| `model` | String | Y | 非空 | 模型名（默认读 `ai-key.properties` 的 `api.model`，如 `gpt-4o-mini`） |
| `temperature` | double | Y | `[0.0,1.0]` | 采样温度（默认 0.7） |
| `maxTokens` | int | Y | `(0,2000]` | 最大生成 token（默认 800） |

### 3.5 `buildRequest()` 方法签名

```java
package com.bmi.model.ai;

/**
 * 依据最新记录与历史记录构造 AI 请求 DTO。
 * 参数来源：User(user)、Record latest(最新指标)、List<Record> history(趋势摘要)。
 */
public AiRequest buildRequest(User u, Record latest, List<Record> history);
```

> 与 `plan.md` §4.4 完全一致：`buildRequest(User u, Record latest, List<Record> history)`。
> `history` 由 `RecordController.queryRecords(userId, null, null)` 取得，`AiHealthClient` 内部截取近 N 条并算出 `direction`。

### 3.6 完整请求 JSON 示例（内部信封）

```json
{
  "systemPrompt": "你是一位严谨的中文健康顾问，请按『饮食/运动/健康』三段给出建议。",
  "userMetrics": {
    "bmi": 22.9,
    "bmiGrade": "正常",
    "bodyFat": 18.3,
    "weight": 70.0,
    "height": 175.0,
    "age": 30,
    "gender": 1,
    "measureTime": "2026-07-14T09:30:00"
  },
  "historyTrend": {
    "count": 5,
    "direction": "下降",
    "points": [
      {"measureTime":"2026-06-10T08:00:00","bmi":24.1,"weight":73.5,"bodyFat":20.1},
      {"measureTime":"2026-06-20T08:00:00","bmi":23.8,"weight":72.8,"bodyFat":19.7},
      {"measureTime":"2026-06-30T08:00:00","bmi":23.3,"weight":71.9,"bodyFat":19.2},
      {"measureTime":"2026-07-07T08:00:00","bmi":23.0,"weight":70.8,"bodyFat":18.6},
      {"measureTime":"2026-07-14T09:30:00","bmi":22.9,"weight":70.0,"bodyFat":18.3}
    ]
  },
  "modelParams": {
    "model": "gpt-4o-mini",
    "temperature": 0.7,
    "maxTokens": 800
  }
}
```

> **线上报文映射（对齐 plan.md §6.1）**：实际 `HttpURLConnection` 发送的是 OpenAI Chat Completions 兼容报文。`send()` 在序列化时将上述信封转换为：
> - `model` ← `modelParams.model`
> - `messages[0].role=system` ← `systemPrompt`
> - `messages[1].role=user` ← 由 `userMetrics` + `historyTrend` 拼成自然语言（如「最新指标：BMI=22.9(正常)… 历史趋势：近5次BMI由24.1降至22.9，呈下降趋势。」）
> - `temperature` ← `modelParams.temperature`；`max_tokens` ← `modelParams.maxTokens`
>
> 这样既满足本设计对「system/user/historyTrend/params 四段结构」的强约束，又完全复用 plan.md §6.1 的 OpenAI 报文约定，不引入白名单外格式。

---

## 4. 返回格式规范（Response）

### 4.1 逻辑响应对象：`AiHealthResult` 结构

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 是否成功获取建议 |
| `code` | String | 错误码：`0`=成功；其余见第 5 节错误码表 |
| `message` | String | 成功为空；失败为降级提示文案（即展示给用户的中文） |
| `adviceText` | String | 完整建议文本（含【饮食】/【运动】/【健康】分段） |
| `dietAdvice` | String | 解析出的「饮食」段 |
| `exerciseAdvice` | String | 解析出的「运动」段 |
| `healthAdvice` | String | 解析出的「健康」段 |
| `finishReason` | String | 模型结束原因（`stop`/`length` 等） |
| `usage` | Object | token 用量（见下） |

`usage` 子字段：`promptTokens`(int)、`completionTokens`(int)、`totalTokens`(int)。

### 4.2 `parseResponse()` 返回对象

```java
package com.bmi.model.ai;

/**
 * 解析外部模型返回的 JSON，抽取建议文本与用量；
 * 成功时 success=true；content 缺失/解析失败返回 success=false 的降级结果。
 */
public AiHealthResult parseResponse(String jsonBody);
```

> 与 `plan.md` §6.2 解析路径一致：`root.choices[0].message.content` 取建议文本；`usage.prompt_tokens / completion_tokens / total_tokens` 映射为 `usage` 子对象。三段（【饮食】/【运动】/【健康】）按分隔符切片填入 `dietAdvice/exerciseAdvice/healthAdvice`，整体文本填入 `adviceText`。

### 4.3 成功响应 JSON 示例（OpenAI 线上报文，对应 plan.md §6.2）

```json
{
  "choices": [
    { "message": { "role": "assistant", "content": "【饮食】...【运动】...【健康】..." } }
  ],
  "finish_reason": "stop",
  "usage": { "prompt_tokens": 120, "completion_tokens": 300, "total_tokens": 420 }
}
```

### 4.4 建议文本如何映射到 UI（spec FR-07 / ChartPanel 体系）

- 视图层建议展示区（如 `AiAdvicePanel.showAdvice(String text)`）直接渲染 `AiHealthResult.adviceText` 全文本；
- 若需分栏精致展示，可分别用 `dietAdvice/exerciseAdvice/healthAdvice` 填充「饮食 / 运动 / 健康」三个子面板；
- AC-07 要求「返回文本非空且含饮食/运动/健康分段」，`parseResponse` 在解析后做非空与三段包含校验，不达标则按服务器报错降级处理。

---

## 5. 四类异常处理方案（核心）

### 5.1 四类场景明细表

| 场景 | 触发条件（具体异常/判定） | 检测方式 | 处理动作 | 降级提示文案（直接展示，真诚、不暴露技术栈） | 返回上层的值 | 对应验收 |
|------|---------------------------|----------|----------|----------------------------------------------|--------------|----------|
| **① 断网** | `java.net.ConnectException` / `java.net.UnknownHostException`（DNS/连接被拒） | try-catch 捕获 `ConnectException`/`UnknownHostException` | 不重试；记日志（WARN 级，含脱敏 host） | 「暂时无法获取 AI 建议，请检查网络或稍后再试」 | 返回 `AiHealthResult` 失败实例（`code=NETWORK_ERROR`，`message`=上述文案） | spec FR-07 网络错误降级；AC-07 失败降级文案 |
| **② 接口超时** | `java.net.SocketTimeoutException`（connect 或 read > 10s） | try-catch 捕获 `SocketTimeoutException` | **可重试 1 次**（仅该类）；重试仍超时则返回降级 | 「AI 建议请求超时，请稍后重试」 | 返回 `AiHealthResult` 失败实例（`code=TIMEOUT`，`message`=上述文案） | plan.md §6.2 超时；**AC-07 超时阈值 10s + 超时文案** |
| **③ 参数为空** | `AiRequest` 校验失败：`userMetrics` 为 null，或 `bmi/weight/age/gender/measureTime` 越界/缺省 | `send()` 入口做 DTO 非空与区间校验，断言不成立即判失败 | 不重试；记日志（INFO 级，记录缺失字段） | 「当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入」 | 返回 `AiHealthResult` 失败实例（`code=INVALID_PARAM`，`message`=上述文案） | 补全 plan.md 未覆盖的入参空值场景；不阻断主流程 |
| **④ 服务器报错** | HTTP 状态码 `5xx`；或响应 `finish_reason=error`；或 `code!=0`；或 content 缺失 | 读 `HttpURLConnection.getResponseCode()`；解析后校验 `content` 非空与三段完整性 | **5xx 可重试 1 次**；重试仍失败 / content 缺失则降级 | 「AI 服务暂时不可用，请稍后再试」 | 返回 `AiHealthResult` 失败实例（`code=SERVER_ERROR`，`message`=上述文案） | plan.md §6.2 HTTP 非 2xx / 响应空；AC-07 失败降级 |
| **（附）密钥缺失** | `ai-key.properties` 缺失或 `api.key` 为空 | `loadApiKey()` 读取后判空 | 抛 `AiConfigException`（自定义异常，向上传递） | 「AI 服务未配置，请联系管理员」 | 抛 `AiConfigException`（由 `AiController` 捕获并转成上述文案返回） | plan.md §6.2 密钥缺失；**AC-07 密钥缺失文案** |

> 所有降级文案均**不暴露技术栈**（不含 HttpURLConnection/JSON/HTTP/SDK 等字眼），符合宪章与 AC-07 精神。

### 5.2 统一异常分类与错误码表

| 错误码常量 | code 值 | 含义 | 是否可重试 | 默认降级文案 |
|------------|---------|------|------------|--------------|
| `CODE_SUCCESS` | `0` | 成功 | — | （空） |
| `NETWORK_ERROR` | `AI_NET_001` | 断网 / 网络不可达 | 否 | 暂时无法获取 AI 建议，请检查网络或稍后再试 |
| `TIMEOUT` | `AI_TIMEOUT_002` | 连接/读取超时（>10s） | 是（1 次） | AI 建议请求超时，请稍后重试 |
| `INVALID_PARAM` | `AI_PARAM_003` | 入参 DTO 为空/越界 | 否 | 当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入 |
| `SERVER_ERROR` | `AI_SRV_004` | 服务器 5xx / 响应异常 / content 缺失 | 是（仅 5xx，1 次） | AI 服务暂时不可用，请稍后再试 |
| `CONFIG_ERROR` | `AI_CFG_005` | 密钥缺失/未配置 | 否（抛异常） | AI 服务未配置，请联系管理员 |

### 5.3 `AiHealthClient` 与 `AiController` 异常职责边界

- **`AiHealthClient`（model.ai 层）—— 捕获 + 转换 + 降级**
  - 在 `send()` 内 try-catch 全部传输层异常，转换为 `AiHealthResult`（成功或降级），**不向 Controller 抛出传输类异常**；
  - 仅当 `ai-key.properties`/`api.key` 缺失时抛 `AiConfigException`（属配置错误，需上层提示管理员）；
  - 负责重试决策（仅 TIMEOUT / 5xx 重试 1 次）。
- **`AiController`（controller 层）—— 捕获配置异常 + 转文案 + 编排**
  - `getAdvice(userId)` 调用 Client 时 catch `AiConfigException`，返回其文案「AI 服务未配置，请联系管理员」；
  - 对 Client 返回的 `AiHealthResult`：成功则取 `adviceText` 交视图；失败则直接取 `result.message` 作为降级文案交视图；
  - **绝不**在 Controller 内 new 出技术栈相关异常或日志细节。
- **视图层**：只负责展示 `AiController` 给回的文本，不做任何异常判断。

---

## 6. 调用与超时配置

### 6.1 HttpURLConnection 配置（原生，无第三方库）

| 配置项 | 取值 | 说明 / 验收对应 |
|--------|------|----------------|
| `setRequestMethod("POST")` | — | 固定 POST |
| `setConnectTimeout` | `10000` ms | 连接超时（常量 `CONNECT_TIMEOUT_MS=10000`） |
| `setReadTimeout` | `10000` ms | 读取超时（常量 `READ_TIMEOUT_MS=10000`）；**对齐 AC-07 超时阈值 10s** |
| 请求头 `Content-Type` | `application/json; charset=utf-8` | — |
| 请求头 `Authorization` | `Bearer <api.key>` | key 取自 `ai-key.properties` 的 `api.key`，**绝不硬编码** |
| 请求头 `Accept` | `application/json` | — |
| URL | `api.url`（取自 `ai-key.properties`） | 外部大模型端点 |

> `ai-key.properties` 结构（已 gitignore，宪章第 4/7 节）：
> ```properties
> api.url=https://<your-provider>/v1/chat/completions
> api.key=<保密，禁止提交>
> api.model=gpt-4o-mini
> ```

### 6.2 重试策略

- **重试开关**：`MAX_RETRY = 1`（仅 1 次）。
- **可重试**：`TIMEOUT`（`SocketTimeoutException`）、`SERVER_ERROR` 中的 **HTTP 5xx**。
- **不重试**：`NETWORK_ERROR`（断网）、`INVALID_PARAM`（参数为空）、`CONFIG_ERROR`（密钥缺失）、HTTP 4xx（客户端错误，重试无意义）。
- 重试采用简单同步重发（间隔 0ms，因已超时/5xx，无需退避）；重试失败按原场景降级。

### 6.3 密钥安全（呼应宪章）

- 密钥**仅**在 `AiHealthClient.loadApiKey()` 中通过 `Properties` 读取 `ai-key.properties`；
- 源码中**零硬编码**密钥 / URL / model 默认值（均来自配置文件）；
- `ai-key.properties` 与 `*.log` 已 gitignore（宪章第 7 节），提交前不得包含。

---

## 7. 命名与代码落点

### 7.1 新增/涉及类与方法（命名遵循宪章第 4/7 节后缀与驼峰规则）

| 类 / 异常 | 包路径 | 角色 |
|-----------|--------|------|
| `AiHealthClient` | `com.bmi.model.ai` | AI 封装 `XxxClient`（宪章命名） |
| `AiRequest` | `com.bmi.model.ai` | 请求 DTO（实体，大驼峰无后缀） |
| `AiHealthResult` | `com.bmi.model.ai` | 响应/结果实体 |
| `AiException` | `com.bmi.model.ai` | 自定义异常基类 `extends Exception` |
| `AiConfigException extends AiException` | `com.bmi.model.ai` | 密钥缺失专用异常（呼应 plan.md §6.2） |
| `UserMetrics` / `HistoryTrend` / `MetricPoint` / `ModelParams` / `Usage` | `com.bmi.model.ai` | `AiRequest`/`AiHealthResult` 的内嵌子结构 |

`AiHealthClient` 关键方法签名：

```java
package com.bmi.model.ai;

public class AiHealthClient {

    static final int CONNECT_TIMEOUT_MS = 10000;
    static final int READ_TIMEOUT_MS    = 10000;
    static final int MAX_RETRY          = 1;

    // 构造请求 DTO（对齐 plan.md §4.4）
    public AiRequest buildRequest(User u, Record latest, List<Record> history);

    // 对外主入口（对齐 plan.md §4.4，返回建议文本或降级文案）
    public String requestAdvice(AiRequest req);

    // 内部主流程：HTTP 调用 + 四类异常处理（见 7.2 骨架）
    private AiHealthResult send(AiRequest req);

    // 解析响应 JSON（对齐 plan.md §6.2）
    private AiHealthResult parseResponse(String jsonBody);

    // 读取 ai-key.properties（密钥安全）
    private String loadApiKey() throws AiConfigException;
}
```

### 7.2 `send()` 主流程骨架（含四类异常处理，Java 风格伪代码）

```java
private AiHealthResult send(AiRequest req) {
    // —— ③ 参数为空：入口校验，不重试 ——
    if (req == null || req.getUserMetrics() == null
            || !isValidMetrics(req.getUserMetrics())) {
        log.info("AI 请求参数不完整");
        return AiHealthResult.fail(INVALID_PARAM,
            "当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入");
    }

    String apiKey;
    try {
        apiKey = loadApiKey();          // 缺失则抛 AiConfigException
    } catch (AiConfigException e) {
        return AiHealthResult.fail(CONFIG_ERROR, "AI 服务未配置，请联系管理员");
    }

    int attempt = 0;
    while (attempt <= MAX_RETRY) {
        attempt++;
        HttpURLConnection conn = null;
        try {
            conn = openConnection(req, apiKey);   // 设 10s 双超时 + 请求头
            int status = conn.getResponseCode();

            // —— ④ 服务器报错：5xx 可重试 1 次；4xx 不重试 ——
            if (status >= 500) {
                if (attempt <= MAX_RETRY) continue;     // 重试
                return AiHealthResult.fail(SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
            }
            if (status >= 400) {                        // 4xx 直接降级，不重试
                return AiHealthResult.fail(SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
            }

            String body = readBody(conn);
            AiHealthResult r = parseResponse(body);
            if (!r.isSuccess() || !r.hasThreeSections()) {  // content 缺失/无三段
                return AiHealthResult.fail(SERVER_ERROR,
                    "AI 服务暂时不可用，请稍后再试");
            }
            return r;

        } catch (SocketTimeoutException e) {        // —— ② 接口超时：可重试 1 次 ——
            if (attempt <= MAX_RETRY) continue;
            log.warn("AI 请求超时", e);
            return AiHealthResult.fail(TIMEOUT, "AI 建议请求超时，请稍后重试");

        } catch (ConnectException | UnknownHostException e) {  // —— ① 断网：不重试 ——
            log.warn("AI 网络不可达", e);
            return AiHealthResult.fail(NETWORK_ERROR,
                "暂时无法获取 AI 建议，请检查网络或稍后再试");

        } catch (IOException e) {                  // 其它 IO（含读响应失败）
            log.warn("AI 请求 IO 异常", e);
            return AiHealthResult.fail(NETWORK_ERROR,
                "暂时无法获取 AI 建议，请检查网络或稍后再试");

        } finally {
            if (conn != null) conn.disconnect();
        }
    }
    return AiHealthResult.fail(SERVER_ERROR, "AI 服务暂时不可用，请稍后再试");
}
```

> 说明：`requestAdvice(req)` 对外返回 `String`——成功取 `AiHealthResult.adviceText`，失败取 `AiHealthResult.message`；`AiController.getAdvice` 直接把该字符串交给视图，对配置异常额外 catch `AiConfigException` 兜底，与 plan.md §4.2 返回「建议文本」的契约一致。

---

## 8. FR / AC 追溯

| 需求 | 本设计落点 | 对齐说明 |
|------|------------|----------|
| FR-07 AI 健康建议 | `AiController` + `AiHealthClient` + `AiRequest`/`AiHealthResult` | 完全覆盖 spec FR-07 输入/处理/输出 |
| AC-07 超时阈值 10s | `READ_TIMEOUT_MS=10000`、`CONNECT_TIMEOUT_MS=10000` | 与 AC-07 数值一致 |
| AC-07 超时文案 | `TIMEOUT` → 「AI 建议请求超时，请稍后重试」 | 文案逐字一致 |
| AC-07 失败降级文案 | `NETWORK_ERROR`/`SERVER_ERROR` → 「暂时无法获取 AI 建议，请检查网络或稍后再试」 | 文案逐字一致 |
| AC-07 密钥缺失文案 | `CONFIG_ERROR` → 「AI 服务未配置，请联系管理员」 | 文案逐字一致 |
| AC-07 文本非空且含三段 | `parseResponse` 校验 `content` 非空且含【饮食】/【运动】/【健康】 | 达标才视为成功 |
| 宪章白名单 | 仅 Java + HttpURLConnection + 手工 JSON；jar 入 `lib/`；密钥读 `ai-key.properties` | 未引入任何白名单外技术 |
