package com.bmi.view.util;

import com.bmi.model.BodyRecord;
import com.bmi.model.db.PageResult;
import com.bmi.model.db.RecordDao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Mock 记录 DAO（离线自测工具，位于 UI 层 Mock 工具区，allowed area）。
 *
 * <p>实现 {@link RecordDao} 接口，全程内存、不落库、不触碰 JdbcUtil / JdbcRecordDao，
 * 使 Mock 模式下「录入 → 保存 → 历史读取」全流程完全脱离 JDBC，
 * 规避 {@code db-config.properties} 缺失导致的 JdbcUtil 类初始化致命异常
 * （{@code ExceptionInInitializerError} / 后续 {@code NoClassDefFoundError}）。
 *
 * <p>启用开关见 {@code AppConfig.isMockDaoEnabled()}（对应 app-config.properties 的
 * {@code mock.dao.enabled}）；BmiApplication 在开关开启时用本类替换 JdbcRecordDao。
 * 本类仅新增 Mock 工具，不修改任何 model/db/ai 后端业务文件。
 */
public class MockRecordDao implements RecordDao {

    /** 进程级内存存储（单例共享，保证跨控制器读写同源）。 */
    private static final Map<Long, BodyRecord> STORE = new ConcurrentHashMap<>();
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private List<BodyRecord> byUser(long userId) {
        return STORE.values().stream()
                .filter(r -> r.getUserId() == userId)
                .sorted(Comparator.comparing(
                        (BodyRecord r) -> r.getMeasureTime() == null ? LocalDateTime.MIN : r.getMeasureTime(),
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean insert(BodyRecord record) {
        if (record == null) {
            return false;
        }
        if (record.getId() <= 0) {
            record.setId(ID_SEQ.getAndIncrement());
        }
        if (record.getMeasureTime() == null) {
            record.setMeasureTime(LocalDateTime.now());
        }
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(LocalDateTime.now());
        }
        STORE.put(record.getId(), record);
        return true;
    }

    @Override
    public List<BodyRecord> queryByUser(long userId, Timestamp start, Timestamp end) {
        List<BodyRecord> all = byUser(userId);
        if (start == null && end == null) {
            return all;
        }
        List<BodyRecord> out = new ArrayList<>();
        for (BodyRecord r : all) {
            LocalDateTime t = r.getMeasureTime();
            if (start != null && t != null && t.isBefore(start.toLocalDateTime())) {
                continue;
            }
            if (end != null && t != null && t.isAfter(end.toLocalDateTime())) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    @Override
    public PageResult<BodyRecord> queryByUserPage(long userId, int page, int size) {
        return paginate(byUser(userId), page, size);
    }

    @Override
    public PageResult<BodyRecord> queryByUserPage(long userId, Timestamp start, Timestamp end, int page, int size) {
        return paginate(queryByUser(userId, start, end), page, size);
    }

    private PageResult<BodyRecord> paginate(List<BodyRecord> all, int page, int size) {
        int p = Math.max(1, page);
        int sz = Math.max(1, size);
        int from = Math.min((p - 1) * sz, all.size());
        int to = Math.min(from + sz, all.size());
        return new PageResult<>(new ArrayList<>(all.subList(from, to)), all.size(), p, sz);
    }

    @Override
    public boolean deleteById(long id, long userId) {
        BodyRecord r = STORE.get(id);
        if (r != null && r.getUserId() == userId) {
            STORE.remove(id);
            return true;
        }
        return false;
    }

    @Override
    public boolean update(BodyRecord record) {
        if (record == null || record.getId() <= 0) {
            return false;
        }
        BodyRecord existing = STORE.get(record.getId());
        if (existing == null || existing.getUserId() != record.getUserId()) {
            return false;
        }
        STORE.put(record.getId(), record);
        return true;
    }

    @Override
    public BodyRecord findLatest(long userId) {
        List<BodyRecord> all = byUser(userId);
        return all.isEmpty() ? null : all.get(0);
    }

    @Override
    public List<BodyRecord> listAllRecords(long userId) {
        return byUser(userId);
    }

    @Override
    public BodyRecord findById(long recordId, long userId) {
        BodyRecord r = STORE.get(recordId);
        return (r != null && r.getUserId() == userId) ? r : null;
    }
}
