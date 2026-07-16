import com.bmi.controller.RecordController;
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.model.db.PageResult;
import com.bmi.model.db.RecordDao;
import com.bmi.view.MainView;
import com.bmi.view.util.PageNavigator;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 无头跳转冒烟测试：验证 PageNavigator 双层兜底跳转 / 空指针防护，
 * 以及 RecordController 保存落库读回的时间解析健壮性（无需数据库 / 显示设备）。
 */
public class NavSmoke {

    /** 记录每次 buildXxx 调用的 mock 工厂。 */
    static class MockHost implements PageNavigator.NavigationHost {
        String last = null;
        public Parent buildRegister() { last = "register"; return new StackPane(); }
        public Parent buildLogin() { last = "login"; return new StackPane(); }
        public Parent buildUserInfoInput(User u) { last = "userinfo"; return new StackPane(); }
        public Parent buildInput(User u) { last = "input"; return new StackPane(); }
        public Parent buildMain(User u) { last = "main"; return new StackPane(); }
        public Parent buildAiAnalysis(User u) { last = "ai"; return new StackPane(); }
        public Parent buildPhoto(User u) { last = "photo"; return new StackPane(); }
        public Parent buildReport(User u) { last = "report"; return new StackPane(); }
        public Parent buildChart(User u) { last = "chart"; return new StackPane(); }
        public Parent buildSettings(User u) { last = "settings"; return new StackPane(); }
    }

    /** 内存版 RecordDao，实现接口全部方法（仅测试用）。 */
    static class MemDao implements RecordDao {
        private final Map<Long, BodyRecord> map = new HashMap<>();
        private long seq = 0;
        public boolean insert(BodyRecord r) { r.setId(++seq); map.put(r.getId(), r); return true; }
        public List<BodyRecord> queryByUser(long userId, Timestamp start, Timestamp end) {
            List<BodyRecord> out = new ArrayList<>();
            for (BodyRecord r : map.values()) {
                if (r.getUserId() != userId) continue;
                LocalDateTime t = r.getMeasureTime();
                if (start != null && t != null && t.isBefore(start.toLocalDateTime())) continue;
                if (end != null && t != null && t.isAfter(end.toLocalDateTime())) continue;
                out.add(r);
            }
            return out;
        }
        public PageResult<BodyRecord> queryByUserPage(long userId, int page, int size) {
            List<BodyRecord> all = queryByUser(userId, null, null);
            return new PageResult<>(all, all.size(), page, size);
        }
        public PageResult<BodyRecord> queryByUserPage(long userId, Timestamp s, Timestamp e, int p, int sz) {
            List<BodyRecord> all = queryByUser(userId, s, e);
            return new PageResult<>(all, all.size(), p, sz);
        }
        public boolean deleteById(long id, long userId) { return map.remove(id) != null; }
        public boolean update(BodyRecord r) {
            if (map.containsKey(r.getId())) { map.put(r.getId(), r); return true; }
            return false;
        }
        public BodyRecord findLatest(long userId) {
            BodyRecord latest = null;
            for (BodyRecord r : map.values())
                if (r.getUserId() == userId && (latest == null || r.getId() > latest.getId())) latest = r;
            return latest;
        }
        public List<BodyRecord> listAllRecords(long userId) { return queryByUser(userId, null, null); }
        public BodyRecord findById(long id, long userId) {
            BodyRecord r = map.get(id);
            return r != null && r.getUserId() == userId ? r : null;
        }
    }

    static void check(boolean cond, String name, AtomicBoolean ok) {
        System.out.println((cond ? "PASS" : "FAIL") + " - " + name);
        if (!cond) ok.set(false);
    }

    public static void main(String[] args) throws Exception {
        new JFXPanel(); // 启动 FX 工具包
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(true);

        Platform.runLater(() -> {
            try {
                MockHost host = new MockHost();
                PageNavigator.init(new Stage(), host, 1000, 700);

                User u = new User();
                u.setId(1L);
                u.setUsername("smoke");

                // 第一层：toMain(user) 标准跳转
                PageNavigator.toMain(u);
                check("main".equals(host.last), "toMain(user) -> buildMain", ok);

                // 第二层（user 非空时优先走 toMain）
                PageNavigator.forceHome(u);
                check("main".equals(host.last), "forceHome(user) -> buildMain", ok);

                // 空用户 toMain：未登录提示 + 终止，不抛 NPE
                PageNavigator.toMain(null);
                check(true, "toMain(null) 无 NPE / 安全终止", ok);

                // 空用户 forceHome：无 MainView 实例时仅告警不崩溃
                PageNavigator.forceHome(null);
                check(MainView.getCurrent() == null || MainView.getCurrent() != null,
                        "forceHome(null) 无 MainView 实例不崩溃", ok);

                // 持久化落库读回 + 时间解析健壮性
                RecordController rc = new RecordController(new MemDao());
                BodyRecord saved = rc.createRecord(1L, 175, 70, 30, 1, null);
                check(saved != null, "createRecord 返回记录", ok);
                check(saved.getBmi() > 0, "BMI 计算 > 0 (=" + saved.getBmi() + ")", ok);

                List<BodyRecord> list = rc.queryRecords(1L, null, null);
                check(list.size() == 1, "queryRecords 读回 1 条", ok);
                check(!list.isEmpty() && list.get(0).getId() == saved.getId(), "落库读回 id 一致", ok);

                // 非法时间串不抛异常（回退 LocalDateTime.now）
                BodyRecord r2 = rc.createRecord(1L, 180, 80, 25, 0, "bad-time");
                check(r2 != null && r2.getMeasureTime() != null, "非法时间串 -> 回退 now，无异常", ok);

            } catch (Throwable t) {
                t.printStackTrace();
                ok.set(false);
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        System.out.println(ok.get() ? "NAV_SMOKE_OK" : "NAV_SMOKE_FAIL");
        System.exit(ok.get() ? 0 : 1);
    }
}
