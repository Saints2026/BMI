package com.bmi.model.ai;

import com.bmi.model.BodyRecord;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 模块本地联调入口（开发期使用，非生产路径）。
 *
 * 安全（P1 #7）：API Key 仅从本地未跟踪的 ai-key.properties 读取，源码零硬编码。
 * 若配置文件缺失，捕获 {@link AiConfigException} 并打印提示，绝不抛明文密钥。
 */
public class TestAiService {
    public static void main(String[] args) {
        try {
            AiHealthClient client = new AiHealthClient();

            // 构造模拟数据（使用完整 BodyRecord 实体，含瞬时字段）
            BodyRecord latest = new BodyRecord();
            latest.setBmi(22.8);
            latest.setBodyFat(17.5);
            latest.setWeight(70.0);
            latest.setHeight(175.0);
            latest.setAge(30);
            latest.setGender(1);
            latest.setMeasureTime(Timestamp.valueOf("2026-07-14T09:30:00"));

            List<BodyRecord> history = new ArrayList<>();
            BodyRecord r1 = new BodyRecord();
            r1.setBmi(24.1); r1.setBodyFat(20.1); r1.setWeight(73.5);
            r1.setMeasureTime(Timestamp.valueOf("2026-06-10T08:00:00"));
            BodyRecord r2 = new BodyRecord();
            r2.setBmi(23.3); r2.setBodyFat(19.2); r2.setWeight(71.9);
            r2.setMeasureTime(Timestamp.valueOf("2026-06-30T08:00:00"));
            history.add(r1);
            history.add(r2);
            history.add(latest);

            AiHealthResult result = client.getHealthAdvice(latest, history);
            if (result.isSuccess()) {
                System.out.println(result.getAdviceText());
            } else {
                System.out.println("[降级] code=" + result.getCode() + ", message=" + result.getMessage());
            }
        } catch (AiConfigException e) {
            System.err.println("AI 配置缺失：" + e.getMessage() + "（请配置本地 ai-key.properties，勿提交密钥）");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
        }
    }
}
