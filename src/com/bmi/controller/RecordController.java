package com.bmi.controller;

import com.bmi.model.BodyRecord;
import com.bmi.model.db.RecordDao;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 记录控制器（对齐 plan.md §3 controller 层 RecordController）。
 * 负责身高体重录入→BMI/体脂计算→落库，以及历史查询/删除。
 * BMI 与体脂率计算在 controller 编排层（model 业务由 BmiCalculator/BodyFatCalculator 承载，此处内联以保持聚焦）；
 * 计算仅产生结果，数据库只持久化结果（宪章分层铁律）。
 */
public class RecordController {

    private final RecordDao recordDao;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public RecordController(RecordDao recordDao) {
        this.recordDao = recordDao;
    }

    /**
     * 创建一条测量记录：计算 BMI 与体脂率后落库。
     *
     * @param userId       用户ID
     * @param heightCm     身高 cm（区间 50~250）
     * @param weightKg     体重 kg（区间 10~300）
     * @param age          年龄（1~120）
     * @param gender       1=男 0=女
     * @param measureTime  测量时间 yyyy-MM-dd HH:mm:ss（null 表示当前）
     * @return 已落库的记录（含计算字段）；参数非法返回 null
     */
    public BodyRecord createRecord(long userId, double heightCm, double weightKg,
                                    int age, int gender, String measureTime) {
        if (heightCm < 50 || heightCm > 250 || weightKg < 10 || weightKg > 300
                || age < 1 || age > 120 || (gender != 0 && gender != 1)) {
            return null;
        }
        double bmi = calcBmi(weightKg, heightCm);
        double bodyFat = predictBodyFat(bmi, age, gender);

        BodyRecord r = new BodyRecord();
        r.setUserId(userId);
        r.setHeight(heightCm);
        r.setWeight(weightKg);
        r.setBmi(bmi);
        r.setBodyFat(bodyFat);
        r.setAge(age);
        r.setGender(gender);
        try {
            r.setMeasureTime(measureTime == null || measureTime.isEmpty()
                    ? new Timestamp(System.currentTimeMillis())
                    : new Timestamp(SDF.parse(measureTime).getTime()));
        } catch (ParseException e) {
            r.setMeasureTime(new Timestamp(System.currentTimeMillis()));
        }
        recordDao.insert(r);
        return r;
    }

    public List<BodyRecord> queryRecords(long userId, Timestamp start, Timestamp end) {
        return recordDao.queryByUser(userId, start, end);
    }

    /**
     * 分页查询历史记录（按 id 倒序，最新在前），v1.1 支撑 HistoryView 分页表格。
     */
    public List<BodyRecord> queryRecordsPage(long userId, int page, int size) {
        return recordDao.queryByUserPage(userId, page, size);
    }

    /**
     * 带时间筛选的分页查询（按测量时间倒序），v1.1 支撑 HistoryView 时间筛选 + 分页。
     */
    public List<BodyRecord> queryRecordsPage(long userId, Timestamp start, Timestamp end, int page, int size) {
        return recordDao.queryByUserPage(userId, start, end, page, size);
    }

    public void deleteRecord(long id) {
        recordDao.deleteById(id);
    }

    /**
     * 修改一条已有记录（InputView「修改选中记录」按钮调用，v1.1）。
     * 重新校验必填项；若 height/weight/age/gender 任一变化，重算 bmi/bodyFat；
     * 扩展字段（围度/体征/疾病/照片）原样透传（含 null）。最后委派 recordDao.update（限定 user_id 防越权）。
     *
     * @return 更新后的记录；参数非法或越权返回 null
     */
    public BodyRecord updateRecord(BodyRecord record) {
        if (record == null || record.getId() <= 0) {
            return null;
        }
        double h = record.getHeight();
        double w = record.getWeight();
        int age = record.getAge();
        int gender = record.getGender();
        if (h < 50 || h > 250 || w < 10 || w > 300
                || age < 1 || age > 120 || (gender != 0 && gender != 1)) {
            return null; // 校验失败，视图就地红字提示（AC-02/03/04）
        }
        // 仅当录入了年龄/性别时重算体脂；若 age/gender 为 0 且非男(1)，跳过体脂重算以免误覆盖
        record.setBmi(calcBmi(w, h));
        if (gender == 0 || gender == 1) {
            record.setBodyFat(predictBodyFat(record.getBmi(), age, gender));
        }
        recordDao.update(record);
        return record;
    }

    // —— 计算逻辑（对齐 spec FR-03 / FR-04，源自 db_design.md 注释）——
    // BMI = 体重(kg) / 身高(m)^2，保留 1 位小数
    static double calcBmi(double weightKg, double heightCm) {
        double h = heightCm / 100.0;
        return Math.round((weightKg / (h * h)) * 10.0) / 10.0;
    }

    // Deurenberg 公式：1.2*BMI + 0.23*age - 10.8*gender(男1女0) - 5.4
    static double predictBodyFat(double bmi, int age, int gender) {
        double v = 1.2 * bmi + 0.23 * age - 10.8 * gender - 5.4;
        return Math.round(v * 10.0) / 10.0;
    }
}
