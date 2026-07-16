package com.bmi.model.db;

import com.bmi.model.BodyRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/**
 * JdbcRecordDao 完整链路测试（对齐用户要求：单条记录 insert → queryByUser → update 全扩展字段）。
 *
 * 运行前提（与 docs/db_design.md / docs/mysql_init.sql 一致）：
 *  1) MySQL 8.0+ 已执行 db/mysql_init.sql 建表（user / body_record，含 10 个扩展列）；
 *  2) db-config.properties 已配置 db.url / db.user / db.password（指向该库，源码零硬编码）；
 *  3) 运行 classpath 含 mysql-connector-j-*.jar 与 junit-jupiter（lib/ 下）；
 *  4) 数据库可达。
 *
 * 测试以「自有事务隔离」为原则：@BeforeAll 插入一条测试用户（供 body_record 外键引用），
 * @AfterAll 清理测试用户与测试记录，避免污染业务数据。
 *
 * 校验点：
 *  - 扩展字段（腰围/颈围/腕围/高压/低压/心率/内脏脂肪/既往疾病/照片路径）正常存入、读取、更新；
 *  - DbUtil / DbException / JdbcRecordDao 三者配合无报错（异常统一为 DbException，不向上抛 SQLException）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JdbcRecordDaoChainTest {

    private RecordDao dao = new JdbcRecordDao();
    private long testUserId;
    private long recordId;

    @BeforeAll
    void setUp() throws Exception {
        // 插入一条隔离用的测试用户，供 body_record 外键引用（user 为 MySQL 保留字，反引号包裹）
        String uname = "ut_chain_" + System.nanoTime();
        String sql = "INSERT INTO `user` (`username`, `password_hash`, `salt`, `status`) VALUES (?, ?, ?, 1)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uname);
            ps.setString(2, "testhash");
            ps.setString(3, "testsalt");
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                Assertions.assertTrue(k.next(), "测试用户插入后未返回自增主键");
                testUserId = k.getLong(1);
            }
        } catch (DbException e) {
            throw new AssertionError("DbUtil 建连/插入测试用户失败：" + e.getMessage(), e);
        }
        System.out.println("[setUp] 测试用户已创建 id=" + testUserId);
    }

    @AfterAll
    void tearDown() {
        try {
            if (recordId > 0) {
                dao.deleteById(recordId);
            }
            try (Connection conn = DbUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM `user` WHERE `id` = ?")) {
                ps.setLong(1, testUserId);
                ps.executeUpdate();
            }
            System.out.println("[tearDown] 测试记录与测试用户已清理");
        } catch (DbException | SQLException e) {
            System.err.println("[tearDown] 清理失败（可手动清理 test user id=" + testUserId + "）：" + e.getMessage());
        }
    }

    @Test
    @DisplayName("链路：插入全字段记录 → 按 userId 查询 → 修改全部扩展字段 → 再次查询校验")
    void fullChainInsertQueryUpdate() {
        // ===== 1. 构造一条带全部扩展字段的记录 =====
        BodyRecord r = new BodyRecord();
        r.setUserId(testUserId);
        r.setMeasureTime(new Timestamp(System.currentTimeMillis()));
        r.setHeight(175.5);
        r.setWeight(68.2);
        r.setBmi(22.2);
        r.setBodyFat(18.5);
        // 扩展字段（全部赋值，覆盖 10 个扩展列）
        r.setWaistCircum(82.0);
        r.setHipCircum(95.5);
        r.setNeckCircum(36.0);
        r.setWristCircum(16.5);
        r.setSystolicBp(118);
        r.setDiastolicBp(76);
        r.setHeartRate(68);
        r.setVisceralFat(8);
        r.setDiseases("高血压,糖尿病");
        r.setPhotoPath("C:/Users/test/bmi/photos/12_1718000000.jpg");

        // ===== 2. 插入（校验点：DbUtil + JdbcRecordDao 配合无报错，返回自增主键）=====
        dao.insert(r);
        Assertions.assertTrue(r.getId() > 0, "插入后未回填自增主键");
        recordId = r.getId();
        System.out.println("[test] 已插入记录 id=" + recordId);

        // ===== 3. 按 userId 查询（校验点：全部扩展字段正常读出）=====
        List<BodyRecord> list = dao.queryByUser(testUserId, null, null);
        Assertions.assertFalse(list.isEmpty(), "按 userId 查询应至少返回 1 条");
        BodyRecord q = list.get(0);
        assertExtensionFields(q, 82.0, 95.5, 36.0, 16.5, 118, 76, 68, 8, "高血压,糖尿病", "C:/Users/test/bmi/photos/12_1718000000.jpg");
        System.out.println("[test] 查询读出扩展字段一致 ✓");

        // ===== 4. 修改全部扩展字段（校验点：update 配合 DbUtil 无报错，字段正确更新）=====
        q.setWaistCircum(80.0);
        q.setHipCircum(93.0);
        q.setNeckCircum(35.0);
        q.setWristCircum(15.5);
        q.setSystolicBp(122);
        q.setDiastolicBp(80);
        q.setHeartRate(72);
        q.setVisceralFat(9);
        q.setDiseases("高血压");
        q.setPhotoPath("C:/Users/test/bmi/photos/12_1719000000.jpg");
        dao.update(q);

        // ===== 5. 再次查询校验更新结果 =====
        BodyRecord u = dao.findById(recordId);
        Assertions.assertNotNull(u, "findById 应返回更新后的记录");
        assertExtensionFields(u, 80.0, 93.0, 35.0, 15.5, 122, 80, 72, 9, "高血压", "C:/Users/test/bmi/photos/12_1719000000.jpg");
        System.out.println("[test] 更新后扩展字段一致 ✓");

        // ===== 6. 防越权：用错误 userId 更新应不影响原记录（WHERE 含 user_id）=====
        u.setUserId(testUserId + 9999); // 伪造越权 user_id
        u.setVisceralFat(50);
        dao.update(u); // 由于 WHERE id=? AND user_id=? 不匹配，实际不更新
        BodyRecord after = dao.findById(recordId);
        Assertions.assertEquals(9, after.getVisceralFat(), "越权 user_id 不应改到原记录（防越权校验）");
        System.out.println("[test] 越权更新被忽略 ✓");
    }

    /** 统一断言 10 个扩展字段取值（含 null 安全比较）。 */
    private void assertExtensionFields(BodyRecord r,
                                       Double waist, Double hip, Double neck, Double wrist,
                                       Integer sys, Integer dia, Integer hr, Integer visc,
                                       String diseases, String photo) {
        Assertions.assertTrue(Objects.equals(r.getWaistCircum(), waist), "waist_circum");
        Assertions.assertTrue(Objects.equals(r.getHipCircum(), hip), "hip_circum");
        Assertions.assertTrue(Objects.equals(r.getNeckCircum(), neck), "neck_circum");
        Assertions.assertTrue(Objects.equals(r.getWristCircum(), wrist), "wrist_circum");
        Assertions.assertTrue(Objects.equals(r.getSystolicBp(), sys), "systolic_bp");
        Assertions.assertTrue(Objects.equals(r.getDiastolicBp(), dia), "diastolic_bp");
        Assertions.assertTrue(Objects.equals(r.getHeartRate(), hr), "heart_rate");
        Assertions.assertTrue(Objects.equals(r.getVisceralFat(), visc), "visceral_fat");
        Assertions.assertEquals(diseases, r.getDiseases(), "diseases");
        Assertions.assertEquals(photo, r.getPhotoPath(), "photo_path");
    }
}
