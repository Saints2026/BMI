package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.model.db.RecordDAO;
import com.bmi.model.db.JdbcUtil;
import com.bmi.model.db.UserDAO;

/**
 * BMI 系统 CRUD 集成测试。
 * <p>
 * 覆盖流程：登录/注册 → 创建多条 BodyRecord → 查询（列表+单条+时间范围+分页） → 更新 → 删除 → 清理。
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MainTest {

    private static final String USERNAME = "test_bmi_user";
    private static final String PASSWORD = "Test123456";

    private static UserDAO userDao = new UserDAO();
    private static RecordDAO recordDao = new RecordDAO();
    private static long uid;
    private static BodyRecord r1;
    private static BodyRecord r2;
    private static BodyRecord r3;

    @BeforeClass
    public static void init() {
        cleanupUser();
        cleanupRecords();
    }

    @Test
    public void test1LoginAndRegister() {
        User user = userDao.login(USERNAME, PASSWORD);
        if (user == null) {
            boolean ok = userDao.register(USERNAME, PASSWORD);
            assertTrue("User registration should succeed", ok);
            user = userDao.login(USERNAME, PASSWORD);
        }
        assertNotNull("User should be logged in", user);
        uid = user.getId();
    }

    @Test
    public void test2CreateRecord() {
        r1 = buildRecord(uid, 175.0, 70.0, LocalDateTime.of(2026, 7, 1, 10, 0));
        assertTrue("Add record 1", recordDao.addRecord(r1));

        r2 = buildRecord(uid, 175.0, 72.0, LocalDateTime.of(2026, 7, 8, 10, 0));
        assertTrue("Add record 2", recordDao.addRecord(r2));

        r3 = new BodyRecord();
        r3.setUserId(uid);
        r3.setMeasureTime(LocalDateTime.of(2026, 7, 14, 10, 0));
        r3.setHeight(175.0);
        r3.setWeight(71.5);
        double heightM = 175.0 / 100.0;
        r3.setBmi(71.5 / (heightM * heightM));
        r3.setBodyFat(18.5);
        r3.setCreatedAt(LocalDateTime.now());
        assertTrue("Add record 3", recordDao.addRecord(r3));
    }

    @Test
    public void test3QueryRecords() {
        List<BodyRecord> all = recordDao.listAllRecords(uid);
        assertEquals("Should have 3 records", 3, all.size());

        BodyRecord fetched = recordDao.getRecordById(r1.getId(), uid);
        assertNotNull("Record should be found by id", fetched);
        assertEquals("Weight should match", 70.0, fetched.getWeight(), 0.01);
    }

    @Test
    public void test4UpdateRecord() {
        r1.setWeight(68.5);
        r1.setBmi(68.5 / (1.75 * 1.75));
        r1.setBodyFat(17.2);
        r1.setWaistCircum(80.0);
        r1.setHeartRate(65);
        assertTrue("Update should succeed", recordDao.updateRecord(r1));

        BodyRecord afterUpdate = recordDao.getRecordById(r1.getId(), uid);
        assertNotNull("Record should exist after update", afterUpdate);
        assertEquals("Updated weight", 68.5, afterUpdate.getWeight(), 0.01);
        assertEquals("Updated BMI", 68.5 / (1.75 * 1.75), afterUpdate.getBmi(), 0.1);
    }

    @Test
    public void test5DeleteRecord() {
        assertTrue("Delete should succeed", recordDao.deleteRecord(r3.getId(), uid));

        List<BodyRecord> afterDelete = recordDao.listAllRecords(uid);
        assertEquals("Should have 2 records after delete", 2, afterDelete.size());
    }

    @Test
    public void test6QueryByTimeRange() {
        Timestamp start = Timestamp.valueOf(LocalDateTime.of(2026, 7, 1, 0, 0));
        Timestamp end = Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 0, 0));
        List<BodyRecord> result = recordDao.queryByUserAndTimeRange(uid, start, end);
        assertEquals("Should find 2 records in range", 2, result.size());
    }

    @Test
    public void test7QueryByPage() {
        List<BodyRecord> page = recordDao.queryByUserPage(uid, null, null, 0, 1);
        assertEquals("First page should have 1 record", 1, page.size());
    }

    @AfterClass
    public static void cleanup() {
        cleanupRecords();
        cleanupUser();
        shutdownJdbc();
    }

    private static BodyRecord buildRecord(long userId, double heightCm, double weight, LocalDateTime measureTime) {
        BodyRecord r = new BodyRecord();
        r.setUserId(userId);
        r.setMeasureTime(measureTime);
        r.setHeight(heightCm);
        r.setWeight(weight);
        double heightM = heightCm / 100.0;
        r.setBmi(weight / (heightM * heightM));
        r.setBodyFat(20.0);
        r.setWaistCircum(85.0);
        r.setHipCircum(95.0);
        r.setNeckCircum(38.0);
        r.setWristCircum(16.5);
        r.setSystolicBp(120);
        r.setDiastolicBp(80);
        r.setHeartRate(72);
        r.setVisceralFat(10);
        r.setDiseases("none");
        r.setPhotoPath("/photos/test.jpg");
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    private static void cleanupUser() {
        String sql = "DELETE FROM `user` WHERE username = ?";
        try (java.sql.Connection conn = JdbcUtil.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, USERNAME);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private static void cleanupRecords() {
        if (uid <= 0) return;
        String sql = "DELETE FROM body_record WHERE user_id = ?";
        try (java.sql.Connection conn = JdbcUtil.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private static void shutdownJdbc() {
        try {
            Class<?> clazz = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
            java.lang.reflect.Method method = clazz.getMethod("uncheckedShutdown");
            method.invoke(null);
        } catch (Exception ignored) {
        }
    }
}
