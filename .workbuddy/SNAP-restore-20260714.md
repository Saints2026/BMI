# BMI 快照恢复报告 · SNAP-20260714-001 还原

> 生成时间：2026-07-14 18:30 (GMT+8)
> 目标：将工作区完整还原至快照 SNAP-20260714-001 记录的 42 文件基线（逐字节 MD5）。
> 结论：**40/42 文件已逐字节还原；2 个文件（AiRequest.java / AiHealthResult.java）的快照字节已永久丢失，不可还原。**

---

## 1. 还原前诊断（为何需要还原）

当前位于 `feature-ai` 分支，工作区已严重偏离快照：

| 偏离类型 | 文件 | 说明 |
|----------|------|------|
| 文档被删 | `CODEBUDDY.md`、`docs/spec.md`、`docs/tasks.md`、`docs/ui_design.md` | 磁盘已不存在 |
| 文档被改 | `docs/plan.md`、`docs/ai_design.md`（含合并冲突标记）、`docs/db_design.md` | 内容非快照版 |
| AI 文件被改 | `src/com/bmi/model/ai/AiRequest.java`、`AiHealthResult.java` | 被 feature-ai 版本覆盖 |
| 污染新增 | `AiHealthClient.java`、`AiCacheUtil.java`、`TestAiService.java`、`model/ai/BodyRecord.java`(重复)、`ai-key.properties`(**含真实密钥**)、`lib/*.jar`(okhttp/json/okio)、`docs/ai_config.md` | 不在快照内 |
| 分支提交 | `feature-ai` 历史含提交 `cfe1efb chore: 共享AI模块API Key` | **密钥已落入 git 历史** |

**关键事实**：快照清单仅存 MD5，**不含文件内容**。因此还原内容必须回溯 git。查证结果：
- 6 篇文档 + `db_design.md` 的内容在 `origin/main` / 悬空 blob 中可得。
- `AiRequest.java`(MD5 `54aacf51…`) 与 `AiHealthResult.java`(MD5 `7c786074…`) 的快照字节**在全部 commit、index、git 对象库中均不存在** → 从未被提交，且已被 feature-ai 覆盖，永久丢失。

---

## 2. 还原执行（已完成）

| 步骤 | 动作 | 结果 |
|------|------|------|
| ① 还原 6 篇文档 | `git show origin/main:<path> > <path>`（CODEBUDDY.md / spec / plan / tasks / ai_design / ui_design） | 6/6 逐字节匹配快照 MD5 |
| ② 还原 db_design.md | 从悬空 blob `9333e7d6…`（`git cat-file -p`）写出 → MD5 `ac8a4923…` | ✅ 匹配快照 |
| ③ 还原 .gitignore | 从 `origin/main` 还原基线版本 | ✅ |
| ④ 隔离污染文件 | 移动到仓库外 `D:\Users\Desktop\BMI\_quarantine_feature-ai_20260714\` | ✅ 见 §3 |
| ⑤ 隔离密钥 | `ai-key.properties` 移动到隔离区并**改名** `ai-key.properties.QUARANTINED_SECRET_DO_NOT_COMMIT` | ✅ 见 §4 |
| ⑥ 技能核对 | 只读确认 course-manager / agent-team-orchestration / github（用户级）在位 | ✅ 三项均在 |

---

## 3. 隔离区清单（仓库外，可回退）

路径：`D:\Users\Desktop\BMI\_quarantine_feature-ai_20260714\`

```
ai-key.properties.QUARANTINED_SECRET_DO_NOT_COMMIT   ← 含真实 API Key（务必轮换！）
docs/ai_config.md
lib/json-20240303.jar        ← 第三方 jar（okhttp 违反宪章「原生 HttpURLConnection」）
lib/okhttp-4.12.0.jar
lib/okio-3.6.0.jar
src/com/bmi/model/ai/AiHealthClient.java
src/com/bmi/model/ai/AiCacheUtil.java
src/com/bmi/model/ai/TestAiService.java
src/com/bmi/model/ai/BodyRecord.java.dup
```

> 采用"隔离(移动)"而非"删除"，便于你回退或采纳其中可用的 feature-ai 成果。
> 如需彻底清除：`rm -rf "D:\Users\Desktop\BMI\_quarantine_feature-ai_20260714"`（请先确认无需保留）。

---

## 4. ⚠️ 安全事件：API Key 已泄露

- `feature-ai` 分支提交 `cfe1efb` 将 **`ai-key.properties`（真实密钥）** 纳入了 git 历史。
- 工作区副本已移至隔离区并重命名，避免被再次提交。
- **但密钥已存在于 git 历史（含 `origin/feature-ai`，若已推送则已公开）。**
- **必须立即轮换/吊销该 API Key**，并视情况清理 git 历史（`git filter-repo` / `bfg`）——这超出本次还原范围，需你授权后处理。

---

## 5. 最终校验

| 指标 | 结果 |
|------|------|
| 快照文件缺失 | **0**（无缺失） |
| 仓库内残留污染文件 | **0**（仅剩基线 `.gitignore` / `README.md` / `docs/.keep` / `lib/.keep`，均非污染） |
| 逐字节匹配 | **40 / 42** |
| 技能配置 | 三项 Skill 全部在位、未改动 |

**唯一缺口**：`src/com/bmi/model/ai/AiRequest.java` 与 `AiHealthResult.java` 当前为 feature-ai 版本（非快照字节，且缺失 `UserMetrics`/`HistoryTrend`/`ModelParams`/`ok()`/`hasThreeSections()` 等快照 `AiService` 所引用成员）→ AI 模块当前**无法编译**。

---

## 6. 后续建议（待你确认）

1. **重建两个 DTO**（推荐）：依据 `ai_design.md` §3 重构 `AiRequest`/`AiHealthResult` 到与快照 `AiService.java`（已在位，MD5 `0b7c5fb7`）匹配的接口，使 AI 模块恢复编译。这是唯一能恢复"可用快照"的路径（原字节已丢失）。
2. **密钥轮换**：立即吊销 feature-ai 中泄露的 Key。
3. **分支整理**：`feature-ai` 历史含污染提交与密钥，建议重置/删除该分支或清理历史后再继续。
4. **依赖补齐**：`lib/` 仍缺 JavaFX SDK / mysql-connector-j / JUnit（与快照一致，运行/测试前需放入）。

> 下一快照编号建议：跨日已重置，应为 `SNAP-YYYYMMDD-001`；若今日再生成则为 `SNAP-20260714-002`。
