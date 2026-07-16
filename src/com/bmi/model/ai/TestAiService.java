package com.bmi.model.ai;

import com.bmi.model.BodyRecord;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 健康建议服务自测入口（model.ai 层，仅用于本地手工验证）。
 *
 * <p>本类与同包 {@link AiHealthClient} / {@link AiHealthResult} 对齐：
 * <ul>
 *   <li>{@link AiHealthClient} 使用无参构造器，密钥/URL/模型从 {@code ai-key.properties} 读取（源码零硬编码）；</li>
 *   <li>{@link AiHealthClient#getHealthAdvice(BodyRecord, List)} 入参为
 *       {@code com.bmi.model.BodyRecord}（最新记录 + 历史列表），返回顶层类 {@link AiHealthResult}；</li>
 *   <li>失败时 {@link AiHealthResult} 通过 {@code isSuccess()/getCode()/getMessage()} 返回降级信息。</li>
 * </ul>
 */
public class TestAiService {
    public static void main(String[] args) {
        try {
            // 1. 构造最新一次测量记录（需满足 isValidMetrics 校验：bmi/体重/身高/年龄/性别）
            BodyRecord latest = newRecord(22.8, 17.5, 60.0, 172.0, 25, 1, "2026-07-14 09:00:00");

            // 2. 构造历史趋势（时间升序）
            List<BodyRecord> history = new ArrayList<>();
            history.add(newRecord(22.5, 18.0, 59.0, 172.0, 25, 1, "2026-07-01 09:00:00"));
            history.add(newRecord(23.1, 18.5, 61.0, 172.0, 25, 1, "2026-07-07 09:00:00"));
            history.add(latest);

            // 3. 创建客户端（无参构造；密钥/URL/模型由 ai-key.properties 提供）
            AiHealthClient client = new AiHealthClient();

            // 4. 获取建议（入参：最新记录 + 历史列表）
            AiHealthResult result = client.getHealthAdvice(latest, history);

            // 5. 打印结果（区分成功 / 降级）
            if (result.isSuccess()) {
                System.out.println("【饮食】" + result.getDietAdvice());
                System.out.println("【运动】" + result.getExerciseAdvice());
                System.out.println("【健康】" + result.getHealthAdvice());
            } else {
                System.out.println("AI 服务降级：code=" + result.getCode()
                        + "，message=" + result.getMessage());
            }

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 构造一条满足客户端校验的 BodyRecord 测试数据。 */
    private static BodyRecord newRecord(double bmi, double bodyFat, double weight,
                                        double height, int age, int gender, String measureTime) {
        BodyRecord r = new BodyRecord();
        r.setBmi(bmi);
        r.setBodyFat(bodyFat);
        r.setWeight(weight);
        r.setHeight(height);
        r.setAge(age);
        r.setGender(gender);
        r.setMeasureTime(LocalDateTime.parse(measureTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return r;
    }
}
