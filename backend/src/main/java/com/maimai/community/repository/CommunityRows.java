package com.maimai.community.repository;

import com.maimai.community.dto.CommunityDtos.*;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/** Explicit JDBC mappings. Missing columns and conversion failures must propagate. */
public final class CommunityRows {
    private CommunityRows() { }
    public static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
    public static final RowMapper<DemandItem> DEMAND = (rs, n) -> new DemandItem(
            rs.getLong("id"), rs.getLong("author_id"), rs.getString("nickname"), rs.getString("title"),
            rs.getString("description"), rs.getLong("budget_min_cents"), rs.getLong("budget_max_cents"),
            nullableLong(rs, "category_id"), rs.getString("region"), rs.getString("status"), rs.getBoolean("is_closed"),
            instant(rs, "created_at"), instant(rs, "updated_at"), nullableLong(rs, "reviewed_by"),
            instant(rs, "reviewed_at"), rs.getString("review_reason"), rs.getString("author_avatar_url"));
    public static final RowMapper<DemandReplyItem> REPLY = (rs, n) -> new DemandReplyItem(
            rs.getLong("id"), rs.getLong("demand_id"), rs.getLong("author_id"), rs.getString("nickname"),
            rs.getString("content"), rs.getString("status"), instant(rs, "created_at"), instant(rs, "updated_at"),
            nullableLong(rs, "reviewed_by"), instant(rs, "reviewed_at"), rs.getString("review_reason"), rs.getString("author_avatar_url"));
    public static final RowMapper<ReportItem> REPORT = (rs, n) -> new ReportItem(
            rs.getLong("id"), rs.getLong("reporter_id"), rs.getString("resource_type"), rs.getLong("resource_id"),
            rs.getString("reason"), rs.getString("status"), nullableLong(rs, "processed_by"),
            rs.getString("process_action"), rs.getString("process_note"), instant(rs, "created_at"), instant(rs, "resolved_at"));
    public static final RowMapper<RatingItem> RATING = (rs, n) -> new RatingItem(
            rs.getLong("id"), rs.getLong("order_id"), rs.getLong("rater_id"), rs.getLong("ratee_id"), rs.getString("nickname"),
            rs.getInt("rating"), rs.getString("comment"), rs.getString("refund_status"), instant(rs, "created_at"), rs.getBoolean("simulated"), rs.getString("reviewer_avatar_url"));
}
