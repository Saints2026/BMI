package com.bmi.model.db;

import com.bmi.model.BodyRecord;

import java.sql.Timestamp;
import java.util.List;

/**
 * 身体记录数据访问接口（对应 db_design.md v1.1 的 body_record 表，含 10 个扩展列）。
 * 实现类按 db_design.md 的建表 SQL 落地 JDBC（idx_record_user_time / idx_record_user_id 加速查询）；
 * 生产实现为 {@link JdbcRecordDao}（MySQL JDBC，已取代联调演示版 InMemoryRecordDao）。
 */
public interface RecordDao {

    /**
     * 插入一条测量记录（FR-05 保存）；插入后由实现回填自增主键到 record.id。
     */
    void insert(BodyRecord record);

    /**
     * 按用户 + 时间区间查询（start/end 为 null 表示不限定），按 measure_time 升序（FR-05/FR-06/FR-07）。
     */
    List<BodyRecord> queryByUser(long userId, Timestamp start, Timestamp end);

    /**
     * 按用户分页查询，按 id 倒序（最新在前），命中 idx_record_user_id（v1.1 分页）。
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     */
    List<BodyRecord> queryByUserPage(long userId, int page, int size);

    /**
     * 按用户 + 时间区间分页查询，按 measure_time 倒序，命中 idx_record_user_time（v1.1 分页 + 时间筛选）。
     */
    List<BodyRecord> queryByUserPage(long userId, Timestamp start, Timestamp end, int page, int size);

    /**
     * 按主键删除（FR-05 删除）。注：越权防护建议在 controller 层先校验归属再调用；
     * 如需库内强约束可改用 deleteById(long id, long userId)。
     */
    void deleteById(long id);

    /**
     * 按 id 更新全部列（含扩展字段），限定 user_id 防越权（v1.1，支撑修改历史旧记录）。
     * 扩展字段为 null 时写入 NULL。
     */
    void update(BodyRecord record);

    /**
     * 取某用户最新一条记录（AI 建议与图表起点）。
     */
    BodyRecord findLatest(long userId);

    /**
     * 按主键精确查询单条记录（v1.1，支撑 PhotoController 绑定/解绑前的归属校验与路径读取）。
     * 返回 null 表示不存在。
     */
    BodyRecord findById(long id);
}
