package com.bmi.controller;

import com.bmi.model.BodyRecord;
import com.bmi.model.ai.AiConfigException;
import com.bmi.model.ai.AiHealthClient;
import com.bmi.model.ai.AiHealthResult;
import com.bmi.model.db.RecordDao;

import java.util.List;

/**
 * AI 健康建议控制层（对齐 ai_design.md §2 编排归属）。
 *
 * 职责：
 *  - 注入 RecordDao，按 userId 自动拉取最新指标与近 10 条历史（P1-F5）；
 *  - 提供 {@link #getAdvice(long)} 重载，内部完成取数 + 调 AiHealthClient，上层无需手动传历史（P1-F5）；
 *  - 捕获 {@link AiConfigException}（密钥缺失）转「AI 服务未配置，请联系管理员」；
 *  - 对 AiHealthClient 返回的 AiHealthResult：成功取 adviceText，失败取 message 降级文案。
 */
public class AiController {

    private final RecordDao recordDao;
    private final AiHealthClient client;

    /** 注入 RecordDao（DB 访问由构造方提供，符合分层铁律：controller → model.db）。 */
    public AiController(RecordDao recordDao) {
        this.recordDao = recordDao;
        this.client = new AiHealthClient();
    }

    /**
     * 对外主入口（P1-F5）：按 userId 自动取数并获取建议。
     *
     * @param userId 用户 ID
     * @return 建议全文（成功）或降级文案（失败 / 数据不完整 / 未配置）
     */
    public String getAdvice(long userId) {
        try {
            BodyRecord latest = fetchLatestRecord(userId);
            if (latest == null) {
                return "当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入";
            }
            List<BodyRecord> history = fetchHistory(userId);   // 近 10 条，时间升序（P1-F4）
            AiHealthResult result = client.getHealthAdvice(latest, history);
            if (result.isSuccess()) {
                return result.getAdviceText();
            }
            return result.getMessage();
        } catch (AiConfigException e) {
            // 密钥缺失：转管理员提示（ai_design.md §5.1）
            return "AI 服务未配置，请联系管理员";
        }
    }

    /** 内部取数：用户最新一条记录。 */
    private BodyRecord fetchLatestRecord(long userId) {
        return recordDao.findLatest(userId);
    }

    /** 内部取数：近 10 条历史（时间升序），避免请求体过大（P1-F4）。 */
    private List<BodyRecord> fetchHistory(long userId) {
        return recordDao.queryLatestN(userId, 10);
    }
}
