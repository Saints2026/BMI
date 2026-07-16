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
 * JDBC 实现的测量记录数据访问对象。
 */
public class JdbcRecordDao implements RecordDao {

    private static final String SELECT_ALL =
            "SELECT id, user_id, measure_time, height, weight, bmi, body_fat, "
          + "waist_circum, hip_circum, neck_circum, wrist_circum, "
          + "systolic_bp, diastolic_bp, heart_rate, visceral_fat, "
          + "diseases, photo_path, created_at FROM body_record";

    @Override
    public boolean insert(BodyRecord record) {
        if (record == null) return false;
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
            if (record.getDiseases() != null) ps.setString(15, record.getDiseases());
            else ps.setNull(15, Types.VARCHAR);
            if (record.getPhotoPath() != null) ps.setString(16, record.getPhotoPath());
            else ps.setNull(16, Types.VARCHAR);
            ps.setTimestamp(17, record.getCreatedAt() != null
                    ? Timestamp.valueOf(record.getCreatedAt()) : Timestamp.valueOf(java.time.LocalDateTime.now()));
            int affected = ps.executeUpdate();
            if (affected > 0) {
                rs = ps.getGeneratedKeys();
                if (rs.next()) record.setId(rs.getLong(1));
                return true;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert body record for userId: " + record.getUserId(), e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return false;
    }

    @Override
    public List<BodyRecord> queryByUser(long userId, Timestamp start, Timestamp end) {
        StringBuilder sql = new StringBuilder(SELECT_ALL);
        buildWhereClause(sql, start, end);
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql.toString());
            ps.setLong(1, userId);
            int idx = setTimeParams(ps, 2, start, end);
            rs = ps.executeQuery();
            List<BodyRecord> list = new ArrayList<>();
            while (rs.next()) list.add(mapRecord(rs));
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query records for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
    }

    @Override
    public PageResult<BodyRecord> queryByUserPage(long userId, int page, int size) {
        return queryByUserPage(userId, null, null, page, size);
    }

    @Override
    public PageResult<BodyRecord> queryByUserPage(long userId, Timestamp start, Timestamp end, int page, int size) {
        if (page < 1) page = 1;
        if (size <= 0) size = 10;
        int offset = (page - 1) * size;
        long total = countByUser(userId, start, end);
        StringBuilder sql = new StringBuilder(SELECT_ALL);
        buildWhereClause(sql, start, end);
        sql.append(" LIMIT ? OFFSET ?");
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql.toString());
            ps.setLong(1, userId);
            int idx = setTimeParams(ps, 2, start, end);
            ps.setInt(idx++, size);
            ps.setInt(idx, offset);
            rs = ps.executeQuery();
            List<BodyRecord> list = new ArrayList<>();
            while (rs.next()) list.add(mapRecord(rs));
            return new PageResult<>(list, total, page, size);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to page query records for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
    }

    @Override
    public boolean deleteById(long id, long userId) {
        String sql = "DELETE FROM body_record WHERE id = ? AND user_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete record id: " + id + " for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps);
        }
    }

    @Override
    public boolean update(BodyRecord record) {
        if (record == null) return false;
        String sql = "UPDATE body_record SET height=?, weight=?, bmi=?, body_fat=?, "
                   + "waist_circum=?, hip_circum=?, neck_circum=?, wrist_circum=?, "
                   + "systolic_bp=?, diastolic_bp=?, heart_rate=?, visceral_fat=?, "
                   + "diseases=?, photo_path=? WHERE id=? AND user_id=?";
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
            if (record.getDiseases() != null) ps.setString(13, record.getDiseases());
            else ps.setNull(13, Types.VARCHAR);
            if (record.getPhotoPath() != null) ps.setString(14, record.getPhotoPath());
            else ps.setNull(14, Types.VARCHAR);
            ps.setLong(15, record.getId());
            ps.setLong(16, record.getUserId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update record id: " + record.getId(), e);
        } finally {
            JdbcUtil.close(conn, ps);
        }
    }

    @Override
    public BodyRecord findLatest(long userId) {
        String sql = SELECT_ALL + " WHERE user_id = ? ORDER BY measure_time DESC LIMIT 1";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, userId);
            rs = ps.executeQuery();
            if (rs.next()) return mapRecord(rs);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find latest record for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }

    @Override
    public List<BodyRecord> listAllRecords(long userId) {
        return queryByUser(userId, null, null);
    }

    @Override
    public List<BodyRecord> queryLatestN(long userId, int limit) {
        String sql = SELECT_ALL + " WHERE user_id = ? ORDER BY measure_time DESC LIMIT ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, userId);
            ps.setInt(2, limit > 0 ? limit : 10);
            rs = ps.executeQuery();
            List<BodyRecord> list = new ArrayList<>();
            while (rs.next()) list.add(mapRecord(rs));
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query latest records for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
    }

    @Override
    public BodyRecord findById(long recordId, long userId) {
        String sql = SELECT_ALL + " WHERE id = ? AND user_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, recordId);
            ps.setLong(2, userId);
            rs = ps.executeQuery();
            if (rs.next()) return mapRecord(rs);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get record by id: " + recordId, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }

    private long countByUser(long userId, Timestamp start, Timestamp end) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM body_record WHERE user_id = ?");
        if (start != null) sql.append(" AND measure_time >= ?");
        if (end != null) sql.append(" AND measure_time <= ?");
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = JdbcUtil.getConnection();
            ps = conn.prepareStatement(sql.toString());
            ps.setLong(1, userId);
            int idx = 2;
            if (start != null) ps.setTimestamp(idx++, start);
            if (end != null) ps.setTimestamp(idx++, end);
            rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count records for userId: " + userId, e);
        } finally {
            JdbcUtil.close(conn, ps, rs);
        }
        return 0;
    }

    private String buildWhereClause(StringBuilder sql, Timestamp start, Timestamp end) {
        sql.append(" WHERE user_id = ?");
        if (start != null) sql.append(" AND measure_time >= ?");
        if (end != null) sql.append(" AND measure_time <= ?");
        sql.append(" ORDER BY measure_time DESC");
        return sql.toString();
    }

    private int setTimeParams(PreparedStatement ps, int index, Timestamp start, Timestamp end) throws SQLException {
        if (start != null) ps.setTimestamp(index++, start);
        if (end != null) ps.setTimestamp(index++, end);
        return index;
    }

    private BodyRecord mapRecord(ResultSet rs) throws SQLException {
        BodyRecord r = new BodyRecord();
        r.setId(rs.getLong("id"));
        r.setUserId(rs.getLong("user_id"));
        Timestamp mt = rs.getTimestamp("measure_time");
        if (mt != null) r.setMeasureTime(mt.toLocalDateTime());
        r.setHeight(rs.getDouble("height"));
        r.setWeight(rs.getDouble("weight"));
        r.setBmi(rs.getDouble("bmi"));
        r.setBodyFat(rs.getDouble("body_fat"));
        double wc = rs.getDouble("waist_circum");
        if (!rs.wasNull()) r.setWaistCircum(wc);
        double hc = rs.getDouble("hip_circum");
        if (!rs.wasNull()) r.setHipCircum(hc);
        double nc = rs.getDouble("neck_circum");
        if (!rs.wasNull()) r.setNeckCircum(nc);
        double wrc = rs.getDouble("wrist_circum");
        if (!rs.wasNull()) r.setWristCircum(wrc);
        int sbp = rs.getInt("systolic_bp");
        if (!rs.wasNull()) r.setSystolicBp(sbp);
        int dbp = rs.getInt("diastolic_bp");
        if (!rs.wasNull()) r.setDiastolicBp(dbp);
        int hr = rs.getInt("heart_rate");
        if (!rs.wasNull()) r.setHeartRate(hr);
        int vf = rs.getInt("visceral_fat");
        if (!rs.wasNull()) r.setVisceralFat(vf);
        r.setDiseases(rs.getString("diseases"));
        r.setPhotoPath(rs.getString("photo_path"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) r.setCreatedAt(ca.toLocalDateTime());
        return r;
    }

    private void setDoubleOrNull(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value != null) ps.setDouble(index, value);
        else ps.setNull(index, Types.DOUBLE);
    }

    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) ps.setInt(index, value);
        else ps.setNull(index, Types.INTEGER);
    }
}
