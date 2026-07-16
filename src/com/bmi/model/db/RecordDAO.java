package com.bmi.model.db;

import java.sql.Timestamp;
import java.util.List;

import com.bmi.model.BodyRecord;

/**
 * 测量记录数据访问接口，定义 body_record 表的 CRUD 操作契约。
 * <p>
 * 命名遵循 CODEBUDDY.md §4.1：DAO 后缀为 {@code Dao}。
 * 实现类：{@link JdbcRecordDao}（JDBC）。
 */
public interface RecordDao {

    /**
     * 插入一条测量记录，并将自增主键回写到 record 对象。
     *
     * @param record 测量记录
     * @return true 写入成功
     */
    boolean insert(BodyRecord record);

    /**
     * 按用户 ID 和时间范围查询测量记录。
     *
     * @param userId 用户 ID
     * @param start  起始时间（含），为 null 时不限
     * @param end    结束时间（含），为 null 时不限
     * @return 记录列表（按 measure_time DESC 排序）
     */
    List<BodyRecord> queryByUser(long userId, Timestamp start, Timestamp end);

    /**
     * 按用户 ID 分页查询（page, size 参数）。
     *
     * @param userId 用户 ID
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 记录列表
     */
    List<BodyRecord> queryByUserPage(long userId, int page, int size);

    /**
     * 按用户 ID 和时间范围分页查询。
     *
     * @param userId 用户 ID
     * @param start  起始时间（含），为 null 时不限
     * @param end    结束时间（含），为 null 时不限
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 记录列表
     */
    List<BodyRecord> queryByUserPage(long userId, Timestamp start, Timestamp end, int page, int size);

    /**
     * 按主键删除记录。
     *
     * @param id 记录 ID
     * @return true 删除成功
     */
    boolean deleteById(long id);

    /**
     * 更新一条记录的全部字段（含扩展字段），限定 user_id 防越权。
     *
     * @param record 待更新记录（必须含 id 和 userId）
     * @return true 更新成功
     */
    boolean update(BodyRecord record);

    /**
     * 查询某用户最新一条记录。
     *
     * @param userId 用户 ID
     * @return 最新记录，无记录时返回 null
     */
    BodyRecord findLatest(long userId);

    /**
     * 查询某用户全部测量记录。
     *
     * @param userId 用户 ID
     * @return 记录列表
     */
    List<BodyRecord> listAllRecords(long userId);

    /**
     * 按主键 + 用户 ID 查询单条记录（防越权）。
     *
     * @param recordId 记录 ID
     * @param userId   所属用户 ID
     * @return 记录对象，未找到返回 null
     */
    BodyRecord findById(long recordId, long userId);
}
