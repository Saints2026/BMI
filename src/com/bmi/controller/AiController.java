package com.bmi.controller;

<<<<<<< HEAD
import com.bmi.client.AiHealthClient;
import com.bmi.exception.AiConfigException;
import com.bmi.exception.AiException;
import com.bmi.model.ai.BodyRecord;
import java.util.List;

public class AiController {
    private AiHealthClient client;

    public AiController(String apiKey, String apiUrl) throws AiConfigException {
        this.client = new AiHealthClient(apiKey, apiUrl);
    }

    public String getAdvice(List<BodyRecord> history) {
        try {
            AiHealthClient.AiHealthResult result = client.getHealthAdvice(history);
            return "【饮食】" + result.getDietAdvice() +
                   "\n【运动】" + result.getExerciseAdvice() +
                   "\n【健康】" + result.getHealthAdvice();
        } catch (AiException e) {
            System.err.println("获取AI建议失败: " + e.getMessage());
            return "AI服务暂时不可用，请稍后重试。";
        }
    }
}
=======
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.model.ai.AiRequest;
import com.bmi.model.ai.AiService;
import com.bmi.model.db.RecordDao;
import com.bmi.model.db.UserDao;

import java.util.List;

/**
 * AI 控制器（对齐 plan.md §3 controller 层 AiController / spec FR-07）。
 * 编排：汇总最新指标 + 历史趋势 → AiService.buildRequest → requestAdvice，返回建议文本或降级文案。
 * 不阻断主流程：AI 失败时仅返回降级文案（由 AiService 内部统一处理四类异常）。
 */
public class AiController {

    private final RecordDao recordDao;
    private final UserDao userDao;
    private final AiService aiService;

    public AiController(RecordDao recordDao, UserDao userDao, AiService aiService) {
        this.recordDao = recordDao;
        this.userDao = userDao;
        this.aiService = aiService;
    }

    /**
     * 获取 AI 健康建议。失败/数据不足时返回降级文案（不抛异常，不阻断主流程）。
     */
    public String getAdvice(long userId) {
        BodyRecord latest = recordDao.findLatest(userId);
        if (latest == null) {
            return "当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入";
        }
        List<BodyRecord> history = recordDao.queryByUser(userId, null, null);
        User user = userDao.findById(userId);

        AiRequest req = aiService.buildRequest(user, latest, history);
        return aiService.requestAdvice(req);
    }
}
>>>>>>> a9701724c795d31090f8e85919d5f7c77bbeb17d
