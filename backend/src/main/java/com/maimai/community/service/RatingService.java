package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.repository.CommunityRepository;
import com.maimai.community.repository.CommunityRows;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import static com.maimai.community.service.CommunitySupport.*;

@Service
@Transactional(readOnly = true)
public class RatingService {
    private static final String JOIN = " FROM community_order_ratings r JOIN users u ON u.id = r.rater_id JOIN orders o ON o.id = r.order_id ";
    private static final String SELECT = "SELECT r.*, u.nickname, o.refund_status" + JOIN;
    private final CommunityRepository repo;
    private final CommunitySupport support;
    public RatingService(CommunityRepository repo, CommunitySupport support) { this.repo = repo; this.support = support; }

    @Transactional
    public RatingItem create(Long actor, Long orderId, RatingRequest request) {
        support.self(actor);
        if (request == null || request.rating() == null || request.rating() < 1 || request.rating() > 5) throw BizException.badRequest("RATING_RANGE", "评分须为1至5");
        var comment = text(request.comment(), "评价", 0, 500);
        Order order = order(orderId, true);
        order.participant(actor);
        if (!"COMPLETED".equals(order.status()) || order.completed() == null) throw BizException.conflict("ORDER_NOT_COMPLETED", "订单完成后才可评价");
        long ratee = actor == order.buyer() ? order.seller() : order.buyer();
        try {
            long id = repo.insertReturningId("INSERT INTO community_order_ratings(order_id, rater_id, ratee_id, rating, comment) VALUES (?, ?, ?, ?, ?)",
                    orderId, actor, ratee, request.rating(), comment);
            return repo.queryOne(SELECT + " WHERE r.id = ?", CommunityRows.RATING, id);
        } catch (DuplicateKeyException ex) {
            throw BizException.conflict("RATING_DUPLICATE", "该订单已评价，每方只能评价一次");
        }
    }

    public PageResult<RatingItem> orderRatings(Long orderId, Integer page, Integer size) {
        order(orderId, false).participant(SecurityUtils.currentUserId());
        return list("r.order_id = ? AND r.is_hidden = 0", orderId, page, size);
    }

    public PageResult<RatingItem> mine(Long actor, Integer page, Integer size) {
        support.self(actor);
        return list("r.rater_id = ? AND r.is_hidden = 0", actor, page, size);
    }

    /** Public user profiles expose reviews without private order identifiers. */
    public PageResult<PublicRatingItem> received(Long userId, Integer page, Integer size) {
        if (repo.count("SELECT COUNT(*) FROM users WHERE id = ? AND status = 'ACTIVE'", userId) == 0) throw BizException.notFound("用户不存在");
        var result = list("r.ratee_id = ? AND r.is_hidden = 0", userId, page, size);
        var rows = result.items().stream().map(r -> new PublicRatingItem(r.id(), r.reviewerId(), r.rateeId(),
                r.reviewerNickname(), r.rating(), r.comment(), r.refundStatus(), r.createdAt())).toList();
        return new PageResult<>(rows, result.total(), result.page(), result.size(), result.totalPages());
    }

    private PageResult<RatingItem> list(String filter, Long id, Integer page, Integer size) {
        var p = paging(page, size);
        var rows = repo.query(SELECT + "WHERE " + filter + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?", CommunityRows.RATING, id, p.size(), p.offset());
        return p.result(rows, repo.count("SELECT COUNT(*)" + JOIN + "WHERE " + filter, id));
    }
    private Order order(Long id, boolean lock) {
        var result = repo.queryOne("SELECT buyer_id, seller_id, fulfillment_status, completed_at FROM orders WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Order(rs.getLong("buyer_id"), rs.getLong("seller_id"), rs.getString("fulfillment_status"), CommunityRows.instant(rs, "completed_at")), id);
        if (result == null) throw BizException.notFound("订单不存在");
        return result;
    }
    private record Order(long buyer, long seller, String status, Instant completed) {
        void participant(Long actor) {
            if (actor == null || (actor != buyer && actor != seller)) throw BizException.forbidden("只有交易双方可查看或评价订单");
        }
    }
}
