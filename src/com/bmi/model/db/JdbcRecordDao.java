package com.bmi.model.db;

import com.bmi.model.BodyRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * 身体记录 DAO 的 MySQL JDBC 实现（对账 db_design.md v1.1 的 body_record 表，含 10 个扩展列）。
 *
 * 本类为生产实现，取代原 InMemoryRecordDao（联调演示版）。
 * 所有 SQL 标识符使用反引号包裹，对 MySQL 保留字（如 user 表）安全，且在 SQLite 下同样兼容。
 *
 * 设计要点：
 *  - 连接经 {@link DbUtil#getConnection()} 获取；每次调用独立获取并在 finally 关闭（无连接池，符合桌面端轻量定位）；
 *  - 扩展字段（腰围/臀围/颈围/腕围/血压/心率/内脏脂肪/疾病/照片）使用包装类型，null 时写入 SQL NULL（区分「未录入」与「0 值」）；
 *  - update / delete 均限定 user_id 防越权（FR-05 / AC-05）；
 *  - 异常统一抛 {@link DbException}（由 controller 捕获并转为 ui_design.md 一.4 中文弹窗）。
 */
public class JdbcRecordDao implements RecordDao {

    /** SELECT 列清单（与 ResultSet 映射顺序一致）。 */
    private static final String COLS =
            "`id`, `user_id`, `measure_time`, `height`, `weight`, `bmi`, `body_fat`,"
            + " `waist_circum`, `hip_circum`, `neck_circum`, `wrist_circum`,"
            + " `systolic_bp`, `diastolic_bp`, `heart_rate`, `visceral_fat`,"
            + " `diseases`, `photo_path`, `created_at`";

    /** INSERT 列清单（不含自增 id 与默认 created_at）。 */
    private static final String INSERT_COLS =
            "(`user_id`, `measure_time`, `height`, `weight`, `bmi`, `body_fat`,"
            + " `waist_circum`, `hip_circum`, `neck_circum`, `wrist_circum`,"
            + " `systolic_bp`, `diastolic_bp`, `heart_rate`, `visceral_fat`,"
            + " `diseases`, `photo_path`)";

    @Override
    public void insert(BodyRecord record) {
        final String sql = "INSERT INTO `body_record` " + INSERT_COLS
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet keys = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            int i = 1;
            ps.setLong(i++, record.getUserId());
            ps.setTimestamp(i++, record.getMeasureTime() != null
                    ? record.getMeasureTime() : new Timestamp(System.currentTimeMillis()));
            ps.setDouble(i++, record.getHeight());
            ps.setDouble(i++, record.getWeight());
            ps.setDouble(i++, record.getBmi());
            ps.setDouble(i++, record.getBodyFat());
            setNullableDouble(ps, i++, record.getWaistCircum());
            setNullableDouble(ps, i++, record.getHipCircum());
            setNullableDouble(ps, i++, record.getNeckCircum());
            setNullableDouble(ps, i++, record.getWristCircum());
            setNullableInt(ps, i++, record.getSystolicBp());
            setNullableInt(ps, i++, record.getDiastolicBp());
            setNullableInt(ps, i++, record.getHeartRate());
            setNullableInt(ps, i++, record.getVisceralFat());
            setNullableString(ps, i++, record.getDiseases());
            setNullableString(ps, i++, record.getPhotoPath());

            ps.executeUpdate();
            keys = ps.getGeneratedKeys();
            if (keys.next()) {
                record.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DbException("插入身体记录失败：" + e.getMessage(), e);
        } finally {
            DbUtil.closeQuietly(keys, ps);
            DbUtil.closeQuietly(conn);
        }
    }

    @Override
    public List<BodyRecord> queryByUser(long userId, Timestamp start, Timestamp end) {
        // 升序，供折线图 / AI 趋势（FR-05 / FR-06 / FR-07）
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS)
                .append(" FROM `body_record` WHERE `user_id` = ?");
        if (start != null) {
            sql.append(" AND `measure_time` >= ?");
        }
        if (end != null) {
            sql.append(" AND `measure_time` <= ?");
        }
        sql.append(" ORDER BY `measure_time` ASC");

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(sql.toString());
            int i = 1;
            ps.setLong(i++, userId);
            if (start != null) {
                ps.setTimestamp(i++, start);
            }
            if (end != null) {
                ps.setTimestamp(i++, end);
            }
            rs = ps.executeQuery();
            return mapList(rs);
        } catch (SQLException e) {
            throw new DbException("查询身体记录失败：" + e.getMessage(), e);
        } finally {
            DbUtil.closeQuietly(rs, ps);
            DbUtil.closeQuietly(conn);
        }
    }

    @Override
    public List<BodyRecord> queryByUserPage(long userId, int page, int size) {
        // 按 id 倒序（最新在前），命中索引 idx_record_user_id（v1.1 分页）
        final String sql = "SELECT " + COLS + " FROM `body_record`"
                + " WHERE `user_id` = ? ORDER BY `id` DESC LIMIT ? OFFSET ?";
        return pageQuery(sql, userId, null, null, page, size);
    }

    @Override
    public List<BodyRecord> queryByUserPage(long userId, Timestamp start, Timestamp end, int page, int size) {
        // 带时间筛选，按测量时间倒序，命中索引 idx_record_user_time（v1.1 分页 + 时间筛选）
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS)
                .append(" FROM `body_record` WHERE `user_id` = ?");
        if (start != null) {
            sql.append(" AND `measure_time` >= ?");
        }
        if (end != null) {
            sql.append(" AND `measure_time` <= ?");
        }
        sql.append(" ORDER BY `measure_time` DESC LIMIT ? OFFSET ?");

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(sql.toString());
            int i = 1;
            ps.setLong(i++, userId);
            if (start != null) {
                ps.setTimestamp(i++, start);
            }
            if (end != null) {
                ps.setTimestamp(i++, end);
            }
            ps.setInt(i++, Math.max(1, size));
            ps.setInt(i++, (Math.max(1, page) - 1) * Math.max(1, size));
            rs = ps.executeQuery();
            return mapList(rs);
        } catch (SQLException e) {
            throw new DbException("分页查询身体记录失败：" + e.getMessage(), e);
        } finally {
            DbUtil.closeQuietly(rs, ps);
            DbUtil.closeQuietly(conn);
        }
    }

    /** 分页查询通用封装（无时间筛选）。 */
    private List<BodyRecord> pageQuery(String sql, long userId, Timestamp start, Timestamp end, int page, int size) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(sql);
            int i = 1;
            ps.setLong(i++, userId);
            if (start != null) {
                ps.setTimestamp(i++, start);
            }
            if (end != null) {
                ps.setTimestamp(i++, end);
            }
            ps.setInt(i++, Math.max(1, size));
            ps.setInt(i++, (Math.max(1, page) - 1) * Math.max(1, size));
            rs = ps.executeQuery();
            return mapList(rs);
        } catch (SQLException e) {
            throw new DbException("分页查询身体记录失败：" + e.getMessage(), e);
        } finally {
            DbUtil.closeQuietly(rs, ps);
            DbUtil.closeQuietly(conn);
        }
    }

    @Override
    public void deleteById(long id) {
        final String sql = "DELETE FROM `body_record` WHERE `id` = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("删除身体记录失败：" + e.getMessage(), e);
        } finally {
            DbUtil.closeQuietly(ps);
            DbUtil.closeQuietly(conn);
        }
    }

    @Override
    public void update(BodyRecord record) {
        // 限定 user_id 防越权（FR-05）；扩展字段为 null 时写入 SQL NULL
        final String sql = "UPDATE `body_record` SET"
                + " `measure_time`=?, `height`=?, `weight`=?, `bmi`=?, `body_fat`=?,"
                + " `waist_circum`=?, `hip_circum`=?, `neck_circum`=?, `wrist_circum`=?,"
                + " `systolic_bp`=?, `diastolic_bp`=?, `heart_rate`=?, `visceral_fat`=?,"
                + " `diseases`=?, `photo_path`=?"
                + " WHERE `id` = ? AND `user_id` = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(sql);
            int i = 1;
            ps.setTimestamp(i++, record.getMeasureTime() != null
                    ? record.getMeasureTime() : new Timestamp(System.currentTimeMillis()));
            ps.setDouble(i++, record.getHeight());
            ps.setDouble(i++, record.getWeight());
            ps.setDouble(i++, record.getBmi());
            ps.setDouble(i++, record.getBodyFat());
            setNullableDouble(ps, i++, record.getWaistCircum());
            setNullableDouble(ps, i++, record.getHipCircum());
            setNullableDouble(ps, i++, record.getNeckCircum());
            setNullableDouble(ps, i++, record.getWristCircum());
            setNullableInt(ps, i++, record.getSystolicBp());
            setNullableInt(ps, i++, record.getDiastolicBp());
            setNullableInt(ps, i++, record.getHeartRate());
            setNullableInt(ps, i++, record.getVisceralFat());
            setNullableString(ps, i++, record.getDiseases());
            setNullableString(ps, i++, record.getPhotoPath());
            ps.setLong(i++, record.getId());
            ps.setLong(i++, record.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("更新身体记录失败：" + e.getMessage(), e);
        } finally {
            DbUtil.closeQuietly(ps);
            DbUtil.closeQuietly(conn);
        }
    }

    @Override
    public BodyRecord findLatest(long userId) {
        final String sql = "SELECT " + COLS + " FROM `body_record`"
                + " WHERE `user_id` = ? ORDER BY `measure_time` DESC LIMIT 1";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, userId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapOne(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new DbException("查询最新记录失败：" + e.getMessage(), e);
        } finally {
            DbUtil.closeQuietly(rs, ps);
            DbUtil.closeQuietly(conn);
        }
    }

    @Override
    public BodyRecord findById(long id) {
        final String sql = "SELECT " + COLS + " FROM `body_record` WHERE `id` = ? LIMIT 1";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapOne(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new DbException("按主键查询记录失败：" + e.getMessage(), e);
        } finally {
            DbUtil.closeQuietly(rs, ps);
            DbUtil.closeQuietly(conn);
        }
    }

    // ============ 映射与辅助 ============

    private List<BodyRecord> mapList(ResultSet rs) throws SQLException {
        List<BodyRecord> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapOne(rs));
        }
        return list;
    }

    private BodyRecord mapOne(ResultSet rs) throws SQLException {
        BodyRecord r = new BodyRecord();
        r.setId(rs.getLong("id"));
        r.setUserId(rs.getLong("user_id"));
        r.setMeasureTime(rs.getTimestamp("measure_time"));
        r.setHeight(rs.getDouble("height"));
        r.setWeight(rs.getDouble("weight"));
        r.setBmi(rs.getDouble("bmi"));
        r.setBodyFat(rs.getDouble("body_fat"));
        r.setWaistCircum(getDoubleOrNull(rs, "waist_circum"));
        r.setHipCircum(getDoubleOrNull(rs, "hip_circum"));
        r.setNeckCircum(getDoubleOrNull(rs, "neck_circum"));
        r.setWristCircum(getDoubleOrNull(rs, "wrist_circum"));
        r.setSystolicBp(getIntOrNull(rs, "systolic_bp"));
        r.setDiastolicBp(getIntOrNull(rs, "diastolic_bp"));
        r.setHeartRate(getIntOrNull(rs, "heart_rate"));
        r.setVisceralFat(getIntOrNull(rs, "visceral_fat"));
        r.setDiseases(rs.getString("diseases"));
        r.setPhotoPath(rs.getString("photo_path"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        return r;
    }

    /** 包装类型 Double：SQL NULL → null；否则返回实际值。 */
    private Double getDoubleOrNull(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    /** 包装类型 Integer：SQL NULL → null；否则返回实际值。 */
    private Integer getIntOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private void setNullableDouble(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, Types.DOUBLE);
        } else {
            ps.setDouble(idx, v);
        }
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, v);
        }
    }

    private void setNullableString(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, v);
        }
    }
}
