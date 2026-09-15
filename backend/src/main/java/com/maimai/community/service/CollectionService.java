package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.repository.CommunityRepository;
import com.maimai.community.repository.CommunityRows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import static com.maimai.community.service.CommunitySupport.paging;

/** Private favorites, seller follows and opt-out browsing history. */
@Service
@Transactional(readOnly = true)
public class CollectionService {
    private final CommunityRepository repo;
    private final CommunitySupport support;
    public CollectionService(CommunityRepository repo, CommunitySupport support) { this.repo = repo; this.support = support; }

    @Transactional
    public void addFavorite(Long user, Long product) {
        support.self(user);
        support.publicProduct(product);
        repo.update("INSERT INTO community_favorites(user_id, product_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)", user, product);
    }

    @Transactional
    public void removeFavorite(Long user, Long product) {
        support.self(user);
        repo.update("DELETE FROM community_favorites WHERE user_id = ? AND product_id = ?", user, product);
    }

    public PageResult<FavoriteItem> favorites(Long user, Integer page, Integer size) {
        support.self(user);
        var p = paging(page, size);
        var items = repo.query("""
                SELECT f.id, f.product_id, p.title, p.price_cents, p.status, f.created_at
                FROM community_favorites f JOIN products p ON p.id = f.product_id
                WHERE f.user_id = ? ORDER BY f.created_at DESC, f.id DESC LIMIT ? OFFSET ?""",
                (rs, n) -> {
                    boolean visible = "ON_SALE".equals(rs.getString("status"));
                    return new FavoriteItem(rs.getLong("id"), rs.getLong("product_id"),
                            visible ? rs.getString("title") : "该商品暂不可见", visible ? rs.getLong("price_cents") : 0,
                            rs.getString("status"), visible, CommunityRows.instant(rs, "created_at"));
                }, user, p.size(), p.offset());
        return p.result(items, repo.count("SELECT COUNT(*) FROM community_favorites WHERE user_id = ?", user));
    }

    @Transactional
    public void follow(Long user, Long seller) {
        support.self(user);
        if (Objects.equals(user, seller)) throw BizException.badRequest("FOLLOW_SELF", "不能关注自己");
        if (repo.count("SELECT COUNT(*) FROM users WHERE id = ? AND status = 'ACTIVE'", seller) == 0) throw BizException.notFound("目标用户不存在");
        // Match SellerApplication enums and only the latest application; a former approval cannot bypass suspension.
        Boolean qualified = repo.queryOne("""
                SELECT status = 'APPROVED'
                FROM seller_applications WHERE user_id = ? ORDER BY created_at DESC,id DESC LIMIT 1""", (rs, n) -> rs.getBoolean(1), seller);
        if (!Boolean.TRUE.equals(qualified)) throw BizException.forbidden("该用户尚未通过卖家审核或卖家权限已暂停，暂不能关注为卖家");
        repo.update("INSERT INTO community_seller_follows(follower_id, seller_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE follower_id = VALUES(follower_id)", user, seller);
    }

    @Transactional
    public void unfollow(Long user, Long seller) {
        support.self(user);
        repo.update("DELETE FROM community_seller_follows WHERE follower_id = ? AND seller_id = ?", user, seller);
    }

    public PageResult<FollowItem> follows(Long user, Integer page, Integer size) {
        support.self(user);
        var p = paging(page, size);
        var rows = repo.query("""
                SELECT f.id, f.seller_id, u.nickname, f.created_at FROM community_seller_follows f
                JOIN users u ON u.id = f.seller_id WHERE f.follower_id = ?
                ORDER BY f.created_at DESC, f.id DESC LIMIT ? OFFSET ?""",
                (rs, n) -> new FollowItem(rs.getLong("id"), rs.getLong("seller_id"), rs.getString("nickname"), CommunityRows.instant(rs, "created_at")),
                user, p.size(), p.offset());
        return p.result(rows, repo.count("SELECT COUNT(*) FROM community_seller_follows WHERE follower_id = ?", user));
    }

    public boolean enabled(Long user) {
        support.self(user);
        Boolean value = repo.queryOne("SELECT enabled FROM community_footprint_preferences WHERE user_id = ?", (rs, n) -> rs.getBoolean(1), user);
        return value == null || value;
    }

    @Transactional
    public void setEnabled(Long user, Boolean enabled) {
        if (enabled == null) throw BizException.badRequest("FOOTPRINT_SETTING_INVALID", "enabled不能为空");
        support.lockUser(user);
        repo.update("""
                INSERT INTO community_footprint_preferences(user_id, enabled) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_at = NOW(6)""", user, enabled);
    }

    @Transactional
    public void record(Long user, Long product) {
        // Lock before any consistent read: the preference must reflect a previously committed opt-out.
        support.lockUser(user);
        if (!enabled(user)) return;
        support.publicProduct(product);
        repo.update("""
                INSERT INTO community_product_footprints(user_id, product_id, viewed_at) VALUES (?, ?, NOW(6))
                ON DUPLICATE KEY UPDATE viewed_at = NOW(6)""", user, product);
    }

    @Transactional
    public void clear(Long user, Long product) {
        support.lockUser(user);
        if (product == null) repo.update("DELETE FROM community_product_footprints WHERE user_id = ?", user);
        else repo.update("DELETE FROM community_product_footprints WHERE user_id = ? AND product_id = ?", user, product);
    }

    public PageResult<FootprintItem> footprints(Long user, Integer page, Integer size) {
        support.self(user);
        var p = paging(page, size);
        var items = repo.query("""
                SELECT f.id, f.product_id, p.title, p.price_cents, p.status, f.viewed_at
                FROM community_product_footprints f JOIN products p ON p.id = f.product_id
                WHERE f.user_id = ? AND f.viewed_at >= DATE_SUB(NOW(6), INTERVAL 30 DAY)
                ORDER BY f.viewed_at DESC, f.id DESC LIMIT ? OFFSET ?""", (rs, n) -> {
                    boolean visible = "ON_SALE".equals(rs.getString("status"));
                    return new FootprintItem(rs.getLong("id"), rs.getLong("product_id"),
                            visible ? rs.getString("title") : "该商品暂不可见", visible ? rs.getLong("price_cents") : 0,
                            rs.getString("status"), CommunityRows.instant(rs, "viewed_at"));
                }, user, p.size(), p.offset());
        return p.result(items, repo.count("SELECT COUNT(*) FROM community_product_footprints WHERE user_id = ? AND viewed_at >= DATE_SUB(NOW(6), INTERVAL 30 DAY)", user));
    }

    @Transactional
    public int cleanupExpiredFootprints() {
        // Bounded retention work runs separately, never from a read-only list request.
        return repo.update("DELETE FROM community_product_footprints WHERE viewed_at < DATE_SUB(NOW(6), INTERVAL 30 DAY) ORDER BY viewed_at LIMIT 1000");
    }
}
