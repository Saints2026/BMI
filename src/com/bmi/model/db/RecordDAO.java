package com.bmi.model.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.bmi.model.BodyRecord;

/**
 * 测量记录数据访问对象，封装 body_record 表的 CRUD 操作。
 * <p>
 * 包含增删改查全部接口；扩展字段（围度/体征/疾病/照片）均支持 null 写入/读出，
 * 符合 db_design.md 选填字段约定。
 */
public class RecordDAO {

    /**
     * 插入一条测量记录，并将自增主键回写到 record 对象。
     *
     * @param record 测量记录（必填字段不可为空）
     * @return true 写入成功且已回填 id
     */
    public boolean addRecord(BodyRecord record) {
        if (record == null) {
            return false;
        }
        String sql = "INSERT INTO body_record (user_id, measure_time, height, weight, bmi, body_fat, "
                   + "waist_circum, hip_circum, neck_circum, wrist_circum, "
                   + "systolic_bp, diastolic_bp, heart_rate, visceral_fat, "
                   + "diseases, photo_path, created_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, record.getUserId());
            ps.setTimestamp(2, record.getMeasureTime() != null
                    ? Timestamp.valueOf(record.getMeasureTime()) : Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.setDouble(3, record.getHeight());
            ps.setDouble(4, record.getWeight());
            ps.setDouble(5, record.getBmi());
            ps.setDouble(6, record.getBodyFat());
            setDoubleOrNull(ps, 7, record.getWaistCircum());
            setDoubleOrNull(ps, 8, record.getHipCircum());
            setDoubleOrNull(ps, 9, record.getNeckCircum());
            setDoubleOrNull(ps, 10, record.getWristCircum());
            setIntOrNull(ps, 11, record.getSystolicBp());
            setIntOrNull(ps, 12, record.getDiastolicBp());
            setIntOrNull(ps, 13, record.getHeartRate());
            setIntOrNull(ps, 14, record.getVisceralFat());
            if (record.getDiseases() != null) {
                ps.setString(15, record.getDiseases());
            } else {
                ps.setNull(15, Types.VARCHAR);
            }
            if (record.getPhotoPath() != null) {
                ps.setString(16, record.getPhotoPath());
            } else {
                ps.setNull(16, Types.VARCHAR);
            }
            ps.setTimestamp(17, record.getCreatedAt() != null
                    ? Timestamp.valueOf(record.getCreatedAt()) : Timestamp.valueOf(java.time.LocalDateTime.now()));
            int affected = ps.executeUpdate();
            System.out.println("[RecordDAO] addRecord: userId=" + record.getUserId() + ", affected=" + affected);
            if (affected > 0) {
                rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    record.setId(rs.getLong(1));
                    System.out.println("[RecordDAO] addRecord: generated id=" + record.getId());
                }
                return true;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to add body record for userId: " + record.getUserId(), e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return false;
    }

    /**
     * 查询某用户全部测量记录，按测量时间降序排列。
     *
     * @param userId 用户 ID
     * @return 记录列表（可能为空）
     */
    public List<BodyRecord> listAllRecords(long userId) {
        List<BodyRecord> list = new ArrayList<>();
        String sql = "SELECT id, user_id, measure_time, height, weight, bmi, body_fat, "
                   + "waist_circum, hip_circum, neck_circum, wrist_circum, "
                   + "systolic_bp, diastolic_bp, heart_rate, visceral_fat, "
                   + "diseases, photo_path, created_at "
                   + "FROM body_record WHERE user_id = ? ORDER BY measure_time DESC";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, userId);
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRecord(rs));
            }
            System.out.println("[RecordDAO] listAllRecords: userId=" + userId + ", count=" + list.size());
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list records for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return list;
    }

    /**
     * 按主键 + 用户 ID 查询单条记录（限定 user_id 防越权）。
     *
     * @param recordId 记录 ID
     * @param userId   所属用户 ID
     * @return 记录对象，未找到返回 null
     */
    public BodyRecord getRecordById(long recordId, long userId) {
        String sql = "SELECT id, user_id, measure_time, height, weight, bmi, body_fat, "
                   + "waist_circum, hip_circum, neck_circum, wrist_circum, "
                   + "systolic_bp, diastolic_bp, heart_rate, visceral_fat, "
                   + "diseases, photo_path, created_at "
                   + "FROM body_record WHERE id = ? AND user_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, recordId);
            ps.setLong(2, userId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapRecord(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get record by id: " + recordId + " for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }

    /**
     * 更新一条记录的全部字段（含扩展字段），必须限定 user_id 防越权。
     *
     * @param record 待更新记录（必须含 id 和 userId）
     * @return true 更新成功
     */
    public boolean updateRecord(BodyRecord record) {
        if (record == null) {
            return false;
        }
        String sql = "UPDATE body_record SET height=?, weight=?, bmi=?, body_fat=?, "
                   + "waist_circum=?, hip_circum=?, neck_circum=?, wrist_circum=?, "
                   + "systolic_bp=?, diastolic_bp=?, heart_rate=?, visceral_fat=?, "
                   + "diseases=?, photo_path=? "
                   + "WHERE id=? AND user_id=?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setDouble(1, record.getHeight());
            ps.setDouble(2, record.getWeight());
            ps.setDouble(3, record.getBmi());
            ps.setDouble(4, record.getBodyFat());
            setDoubleOrNull(ps, 5, record.getWaistCircum());
            setDoubleOrNull(ps, 6, record.getHipCircum());
            setDoubleOrNull(ps, 7, record.getNeckCircum());
            setDoubleOrNull(ps, 8, record.getWristCircum());
            setIntOrNull(ps, 9, record.getSystolicBp());
            setIntOrNull(ps, 10, record.getDiastolicBp());
            setIntOrNull(ps, 11, record.getHeartRate());
            setIntOrNull(ps, 12, record.getVisceralFat());
            if (record.getDiseases() != null) {
                ps.setString(13, record.getDiseases());
            } else {
                ps.setNull(13, Types.VARCHAR);
            }
            if (record.getPhotoPath() != null) {
                ps.setString(14, record.getPhotoPath());
            } else {
                ps.setNull(14, Types.VARCHAR);
            }
            ps.setLong(15, record.getId());
            ps.setLong(16, record.getUserId());
            int affected = ps.executeUpdate();
            System.out.println("[RecordDAO] updateRecord: id=" + record.getId() + ", affected=" + affected);
            return affected > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update record id: " + record.getId(), e);
        } finally {
            JdbcUtil.close(conn, ps);
        }
    }

    /**
     * 按主键 + 用户 ID 删除记录（限定 user_id 防越权）。
     *
     * @param recordId 记录 ID
     * @param userId   所属用户 ID
     * @return true 删除成功
     */
    public boolean deleteRecord(long recordId, long userId) {
        String sql = "DELETE FROM body_record WHERE id = ? AND user_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, recordId);
            ps.setLong(2, userId);
            int affected = ps.executeUpdate();
            System.out.println("[RecordDAO] deleteRecord: id=" + recordId + ", affected=" + affected);
            return affected > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete record id: " + recordId + " for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps);
        }
    }

    private static final String SELECT_ALL =
            "SELECT id, user_id, measure_time, height, weight, bmi, body_fat, "
          + "waist_circum, hip_circum, neck_circum, wrist_circum, "
          + "systolic_bp, diastolic_bp, heart_rate, visceral_fat, "
          + "diseases, photo_path, created_at FROM body_record";

    private String buildWhereClause(StringBuilder sql, Timestamp start, Timestamp end) {
        sql.append(" WHERE user_id = ?");
        if (start != null) {
            sql.append(" AND measure_time >= ?");
        }
        if (end != null) {
            sql.append(" AND measure_time <= ?");
        }
        sql.append(" ORDER BY measure_time DESC");
        return sql.toString();
    }

    private int setTimeParams(PreparedStatement ps, int index, Timestamp start, Timestamp end) throws SQLException {
        if (start != null) {
            ps.setTimestamp(index++, start);
        }
        if (end != null) {
            ps.setTimestamp(index++, end);
        }
        return index;
    }

    /**
     * 按用户 ID 和时间范围查询测量记录，支持 start/end 可选筛选。
     *
     * @param userId 用户 ID
     * @param start  起始时间（含），为 null 时不限
     * @param end    结束时间（含），为 null 时不限
     * @return 记录列表（按 measure_time DESC 排序）
     */
    public List<BodyRecord> queryByUserAndTimeRange(long userId, Timestamp start, Timestamp end) {
        StringBuilder sql = new StringBuilder(SELECT_ALL);
        buildWhereClause(sql, start, end);
        try (Connection conn = JdbcUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, userId);
            setTimeParams(ps, 2, start, end);
            try (ResultSet rs = ps.executeQuery()) {
                List<BodyRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRecord(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query records by time range for userId: " + userId, e);
        }
    }

    /**
     * 按用户 ID 和时间范围分页查询测量记录。
     *
     * @param userId 用户 ID
     * @param start  起始时间（含），为 null 时不限
     * @param end    结束时间（含），为 null 时不限
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 记录列表（按 measure_time DESC 排序）
     */
    public List<BodyRecord> queryByUserPage(long userId, Timestamp start, Timestamp end, int offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_ALL);
        buildWhereClause(sql, start, end);
        sql.append(" LIMIT ? OFFSET ?");
        try (Connection conn = JdbcUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, userId);
            int idx = setTimeParams(ps, 2, start, end);
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<BodyRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRecord(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to page query records for userId: " + userId, e);
        }
    }

    private BodyRecord mapRecord(ResultSet rs) throws SQLException {
        BodyRecord r = new BodyRecord();
        r.setId(rs.getLong("id"));
        r.setUserId(rs.getLong("user_id"));
        Timestamp mt = rs.getTimestamp("measure_time");
        if (mt != null) {
            r.setMeasureTime(mt.toLocalDateTime());
        }
        r.setHeight(rs.getDouble("height"));
        r.setWeight(rs.getDouble("weight"));
        r.setBmi(rs.getDouble("bmi"));
        r.setBodyFat(rs.getDouble("body_fat"));

        double wc = rs.getDouble("waist_circum");
        if (!rs.wasNull()) { r.setWaistCircum(wc); }
        double hc = rs.getDouble("hip_circum");
        if (!rs.wasNull()) { r.setHipCircum(hc); }
        double nc = rs.getDouble("neck_circum");
        if (!rs.wasNull()) { r.setNeckCircum(nc); }
        double wrc = rs.getDouble("wrist_circum");
        if (!rs.wasNull()) { r.setWristCircum(wrc); }
        int sbp = rs.getInt("systolic_bp");
        if (!rs.wasNull()) { r.setSystolicBp(sbp); }
        int dbp = rs.getInt("diastolic_bp");
        if (!rs.wasNull()) { r.setDiastolicBp(dbp); }
        int hr = rs.getInt("heart_rate");
        if (!rs.wasNull()) { r.setHeartRate(hr); }
        int vf = rs.getInt("visceral_fat");
        if (!rs.wasNull()) { r.setVisceralFat(vf); }
        r.setDiseases(rs.getString("diseases"));
        r.setPhotoPath(rs.getString("photo_path"));

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) {
            r.setCreatedAt(ca.toLocalDateTime());
        }
        return r;
    }

    private void setDoubleOrNull(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value != null) {
            ps.setDouble(index, value);
        } else {
            ps.setNull(index, Types.DOUBLE);
        }
    }

    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }
}
