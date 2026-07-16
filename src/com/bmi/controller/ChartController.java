package com.bmi.controller;

import com.bmi.i18n.I18n;
import com.bmi.model.BodyRecord;
import com.bmi.model.db.RecordDao;

import java.util.List;

/**
 * 图表控制器（对齐 plan.md §3 controller 层 ChartController）。
 * 为 ChartView 提供按时间升序的趋势序列（FR-06 折线图数据源）。
 */
public class ChartController {

    private final RecordDao recordDao;

    public ChartController(RecordDao recordDao) {
        this.recordDao = recordDao;
    }

    /**
     * 获取某用户的测量趋势（按 measure_time 升序）。
     */
    public List<BodyRecord> getTrend(long userId) {
        return recordDao.queryByUser(userId, null, null);
    }

    /**
     * 按指标名抽取数值序列：bmi / weight / bodyFat。
     */
    public double valueOf(BodyRecord r, String metric) {
        if (metric.equals(I18n.t("ai.chart.weight"))) {
            return r.getWeight();
        } else if (metric.equals(I18n.t("ai.chart.bodyfat"))) {
            return r.getBodyFat();
        } else {
            return r.getBmi();
        }
    }
}
