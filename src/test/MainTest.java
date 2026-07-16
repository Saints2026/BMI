package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.model.db.RecordDao;
import com.bmi.model.db.JdbcUtil;
import com.bmi.model.db.UserDao;

/**
 * BMI 系统 CRUD 集成测试。
 * <p>
 * 覆盖流程：登录/注册 → 创建多条 BodyRecord → 查询（列表+单条+时间范围+分页） → 更新 → 删除 → 清理。
 * <p>
 * 使用 JUnit 5（Jupiter）注解，遵循 ui_lib_record.md 依赖白名单。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MainTest {

    private static final String USERNAME = "test_bmi_user";
    private static final String PASSWORD = "Test123456";

    private static UserDao userDao = new UserDao();
    private static RecordDao recordDao = new RecordDao();
    private static long uid;
    private static BodyRecord r1;
    private static BodyRecord r2;
    private static BodyRecord r3;

    @BeforeAll
    public static void init() {
        cleanupUser();
        cleanupRecords();
    }

    @Test
    @Order(1)
    public void test1LoginAndRegister() {
        User user = userDao.login(USERNAME, PASSWORD);
        if (user == null) {
            User newUser = new User();
            newUser.setUsername(USERNAME);
            newUser.setPasswordHash(PASSWORD);
            boolean ok = userDao.insert(newUser);
            assertTrue(ok, "User registration should succeed");
            user = userDao.login(USERNAME, PASSWORD);
        }
        assertNotNull(user, "User should be logged in");
        uid = user.getId();
    }

    @Test
    @Order(2)
    public void test2CreateRecord() {
        r1 = buildRecord(uid, 175.0, 70.0, LocalDateTime.of(2026, 7, 1, 10, 0));
        assertTrue(recordDao.insert(r1), "Add record 1");

        r2 = buildRecord(uid, 175.0, 72.0, LocalDateTime.of(2026, 7, 8, 10, 0));
        assertTrue(recordDao.insert(r2), "Add record 2");

        r3 = new BodyRecord();
        r3.setUserId(uid);
        r3.setMeasureTime(LocalDateTime.of(2026, 7, 14, 10, 0));
        r3.setHeight(175.0);
        r3.setWeight(71.5);
        double heightM = 175.0 / 100.0;
        r3.setBmi(71.5 / (heightM * heightM));
        r3.setBodyFat(18.5);
        r3.setCreatedAt(LocalDateTime.now());
        assertTrue(recordDao.insert(r3), "Add record 3");
    }

    @Test
    @Order(3)
    public void test3QueryRecords() {
        List<BodyRecord> all = recordDao.listAllRecords(uid);
        assertEquals(3, all.size(), "Should have 3 records");

        BodyRecord fetched = recordDao.findById(r1.getId(), uid);
        assertNotNull(fetched, "Record should be found by id");
        assertEquals(70.0, fetched.getWeight(), 0.01, "Weight should match");
    }

    @Test
    @Order(4)
    public void test4UpdateRecord() {
        r1.setWeight(68.5);
        r1.setBmi(68.5 / (1.75 * 1.75));
        r1.setBodyFat(17.2);
        r1.setWaistCircum(80.0);
        r1.setHeartRate(65);
        assertTrue(recordDao.update(r1), "Update should succeed");

        BodyRecord afterUpdate = recordDao.findById(r1.getId(), uid);
        assertNotNull(afterUpdate, "Record should exist after update");
        assertEquals(68.5, afterUpdate.getWeight(), 0.01, "Updated weight");
        assertEquals(68.5 / (1.75 * 1.75), afterUpdate.getBmi(), 0.1, "Updated BMI");
    }

    @Test
    @Order(5)
    public void test5DeleteRecord() {
        assertTrue(recordDao.deleteById(r3.getId()), "Delete should succeed");

        List<BodyRecord> afterDelete = recordDao.listAllRecords(uid);
        assertEquals(2, afterDelete.size(), "Should have 2 records after delete");
    }

    @Test
    @Order(6)
    public void test6QueryByTimeRange() {
        Timestamp start = Timestamp.valueOf(LocalDateTime.of(2026, 7, 1, 0, 0));
        Timestamp end = Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 0, 0));
        List<BodyRecord> result = recordDao.queryByUser(uid, start, end);
        assertEquals(2, result.size(), "Should find 2 records in range");
    }

    @Test
    @Order(7)
    public void test7QueryByPage() {
        List<BodyRecord> page = recordDao.queryByUserPage(uid, 1, 1);
        assertEquals(1, page.size(), "First page should have 1 record");
    }

    @Test
    @Order(8)
    public void test8FindLatest() {
        BodyRecord latest = recordDao.findLatest(uid);
        assertNotNull(latest, "Latest record should exist");
        assertEquals(r2.getId(), latest.getId(), "Latest should be r2 (r3 was deleted)");
    }

    @AfterAll
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
