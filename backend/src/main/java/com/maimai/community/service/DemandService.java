package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.repository.CommunityRepository;
import com.maimai.community.repository.CommunityRows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import static com.maimai.community.service.CommunitySupport.*;

@Service
@Transactional(readOnly = true)
public class DemandService {
    private static final String DEMANDS = " FROM community_demand_posts d JOIN users u ON u.id = d.author_id LEFT JOIN user_avatars av ON av.user_id=u.id ";
    private static final String REPLIES = " FROM community_demand_replies r JOIN users u ON u.id = r.author_id LEFT JOIN user_avatars av ON av.user_id=u.id ";
    private static final String PUBLIC = "d.status = 'PUBLISHED' AND d.is_closed = 0 AND d.is_deleted = 0";
    private final CommunityRepository repo;
    private final CommunitySupport support;
    public DemandService(CommunityRepository repo, CommunitySupport support) { this.repo = repo; this.support = support; }

    @Transactional
    public DemandItem create(Long actor, DemandCreateRequest request) {
        support.self(actor);
        var title = text(request.title(), "标题", 1, 120);
        var description = text(request.description(), "描述", 1, 800);
        budget(request.budgetMinCents(), request.budgetMaxCents());
        support.category(request.categoryId());
        long id = repo.insertReturningId("""
                INSERT INTO community_demand_posts(author_id, title, description, budget_min_cents, budget_max_cents, category_id, region)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", actor, title, description, request.budgetMinCents(), request.budgetMaxCents(),
                request.categoryId(), text(request.region(), "地区", 0, 100));
        return demand(id, false);
    }

    @Transactional
    public DemandItem update(Long actor, Long id, DemandPatchRequest request) {
        support.self(actor);
        DemandItem current = demand(id, true);
        owner(actor, current.authorId());
        if (current.isClosed()) throw BizException.conflict("DEMAND_CLOSED", "求购已关闭");
        if (request.title() == null && request.description() == null && request.budgetMinCents() == null
                && request.budgetMaxCents() == null && request.categoryId() == null && request.region() == null) {
            throw BizException.badRequest("DEMAND_PATCH_EMPTY", "至少修改一项");
        }
        var title = text(request.title() == null ? current.title() : request.title(), "标题", 1, 120);
        var description = text(request.description() == null ? current.description() : request.description(), "描述", 1, 800);
        long min = request.budgetMinCents() == null ? current.budgetMinCents() : request.budgetMinCents();
        long max = request.budgetMaxCents() == null ? current.budgetMaxCents() : request.budgetMaxCents();
        budget(min, max);
        Long category = request.categoryId() == null ? current.categoryId() : request.categoryId();
        support.category(category);
        var region = text(request.region() == null ? current.region() : request.region(), "地区", 0, 100);
        repo.update("""
                UPDATE community_demand_posts SET title = ?, description = ?, budget_min_cents = ?, budget_max_cents = ?,
                category_id = ?, region = ?, status = 'PENDING', reviewed_by = NULL, reviewed_at = NULL,
                review_reason = NULL, updated_at = NOW(6) WHERE id = ?""", title, description, min, max, category, region, id);
        return demand(id, false);
    }

    @Transactional
    public void close(Long actor, Long id) {
        support.self(actor);
        DemandItem current = demand(id, true);
        owner(actor, current.authorId());
        repo.update("UPDATE community_demand_posts SET is_closed = 1, status = 'CLOSED', updated_at = NOW(6) WHERE id = ?", id);
    }

    public DemandItem publicDetail(Long id) {
        DemandItem value = demand(id, false);
        visible(value);
        return value;
    }

    public PageResult<DemandItem> publicList(Integer page, Integer size) { return demandList(PUBLIC, page, size); }
    public PageResult<DemandItem> myList(Long actor, Integer page, Integer size) {
        support.self(actor);
        return demandList("d.author_id = ? AND d.is_deleted = 0", page, size, actor);
    }
    public PageResult<DemandItem> adminList(Long actor, String status, Integer page, Integer size) {
        support.moderator(actor);
        return demandList("d.status = ? AND d.is_deleted = 0", page, size, moderationStatus(status));
    }

    @Transactional
    public DemandReplyItem createReply(Long actor, Long id, DemandReplyRequest request) {
        support.self(actor);
        visible(demand(id, true));
        long reply = repo.insertReturningId("INSERT INTO community_demand_replies(demand_id, author_id, content) VALUES (?, ?, ?)",
                id, actor, text(request.content(), "回复", 1, 800));
        return reply(reply, false);
    }

    @Transactional
    public DemandReplyItem updateReply(Long actor, Long id, DemandReplyRequest request) {
        support.self(actor);
        var preliminary = reply(id, false);
        owner(actor, preliminary.authorId());
        // All reply mutations lock the parent before the reply, the same order as moderation.
        visible(demand(preliminary.demandId(), true));
        var current = reply(id, true);
        owner(actor, current.authorId());
        repo.update("""
                UPDATE community_demand_replies SET content = ?, status = 'PENDING', reviewed_by = NULL,
                reviewed_at = NULL, review_reason = NULL, updated_at = NOW(6) WHERE id = ?""", text(request.content(), "回复", 1, 800), id);
        return reply(id, true);
    }

    @Transactional
    public void deleteReply(Long actor, Long id) {
        support.self(actor);
        var preliminary = reply(id, false);
        owner(actor, preliminary.authorId());
        demand(preliminary.demandId(), true);
        owner(actor, reply(id, true).authorId());
        repo.update("UPDATE community_demand_replies SET is_deleted = 1, updated_at = NOW(6) WHERE id = ?", id);
    }

    public PageResult<DemandReplyItem> publicReplies(Long id, Integer page, Integer size) {
        visible(demand(id, false));
        // Parent visibility remains in the SQL predicate, not just an earlier authorization check.
        return replyList("r.demand_id = ? AND r.status = 'PUBLISHED' AND r.is_deleted = 0 AND EXISTS (SELECT 1 FROM community_demand_posts d WHERE d.id = r.demand_id AND " + PUBLIC + ")", page, size, id);
    }
    public DemandReplyItem publicReply(Long demandId,Long id){
        visible(demand(demandId,false));
        var result=replyList("r.id=? AND r.demand_id=? AND r.status='PUBLISHED' AND r.is_deleted=0",0,1,id,demandId);
        if(result.items().isEmpty())throw BizException.notFound("回复已隐藏、删除或不属于这条求购");
        return result.items().getFirst();
    }

    public PageResult<DemandReplyItem> myReplies(Long actor, Integer page, Integer size) {
        support.self(actor);
        return replyList("r.author_id = ? AND r.is_deleted = 0", page, size, actor);
    }

    public PageResult<DemandReplyItem> adminReplies(Long actor, String status, Integer page, Integer size) {
        support.moderator(actor);
        return replyList("r.status = ? AND r.is_deleted = 0", page, size, moderationStatus(status));
    }

    @Transactional
    public void review(Long actor, Long id, boolean approve, String reason) {
        support.moderator(actor);
        var current = demand(id, true);
        if (current.isClosed() || !"PENDING".equals(current.status())) throw BizException.conflict("DEMAND_NOT_PENDING", "求购不在待审状态");
        var note = text(reason, "审核原因", approve ? 0 : 1, 500);
        var status = approve ? "PUBLISHED" : "REJECTED";
        repo.update("UPDATE community_demand_posts SET status = ?, reviewed_by = ?, reviewed_at = NOW(6), review_reason = ?, updated_at = NOW(6) WHERE id = ?", status, actor, note, id);
        support.audit(actor, "DEMAND_REVIEW", "DEMAND_POST", id, current.status(), status, note);
    }

    @Transactional
    public void reviewReply(Long actor, Long id, boolean approve, String reason) {
        support.moderator(actor);
        var preliminary = reply(id, false);
        var parent = demand(preliminary.demandId(), true);
        if (approve) visible(parent);
        var current = reply(id, true);
        if (!"PENDING".equals(current.status())) throw BizException.conflict("REPLY_NOT_PENDING", "回复不在待审状态");
        var note = text(reason, "审核原因", approve ? 0 : 1, 500);
        var status = approve ? "PUBLISHED" : "REJECTED";
        repo.update("UPDATE community_demand_replies SET status = ?, reviewed_by = ?, reviewed_at = NOW(6), review_reason = ?, updated_at = NOW(6) WHERE id = ?", status, actor, note, id);
        support.audit(actor, "REPLY_REVIEW", "DEMAND_REPLY", id, current.status(), status, note);
    }

    DemandItem demand(Long id, boolean lock) {
        var value = repo.queryOne("SELECT d.*, u.nickname, CONCAT('/api/v1/avatars/',av.filename) author_avatar_url" + DEMANDS + "WHERE d.id = ? AND d.is_deleted = 0" + (lock ? " FOR UPDATE" : ""), CommunityRows.DEMAND, id);
        if (value == null) throw BizException.notFound("求购不存在");
        return value;
    }
    DemandReplyItem reply(Long id, boolean lock) {
        var value = repo.queryOne("SELECT r.*, u.nickname, CONCAT('/api/v1/avatars/',av.filename) author_avatar_url" + REPLIES + "WHERE r.id = ? AND r.is_deleted = 0" + (lock ? " FOR UPDATE" : ""), CommunityRows.REPLY, id);
        if (value == null) throw BizException.notFound("回复不存在");
        return value;
    }
    static void visible(DemandItem demand) {
        if (!"PUBLISHED".equals(demand.status()) || demand.isClosed()) throw BizException.notFound("求购不存在或暂不可见");
    }
    private static void owner(Long actor, long owner) {
        if (actor == null || actor != owner) throw BizException.forbidden("只能修改自己的内容");
    }
    private static void budget(Long min, Long max) {
        if (min == null || max == null || min < 0 || max < min || max > 100_000_000L) throw BizException.badRequest("BUDGET_INVALID", "预算须为有效金额，最高100万元");
    }
    private static String moderationStatus(String status) {
        String normalized = status == null || status.isBlank() ? "PENDING" : status.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING", "PUBLISHED", "REJECTED", "CLOSED", "HIDDEN" -> normalized;
            default -> throw BizException.badRequest("MODERATION_STATUS_INVALID", "审核状态不支持");
        };
    }
    private PageResult<DemandItem> demandList(String filter, Integer page, Integer size, Object... args) {
        var p = paging(page, size);
        var bound = new ArrayList<>(Arrays.asList(args)); bound.add(p.size()); bound.add(p.offset());
        var rows = repo.query("SELECT d.*, u.nickname, CONCAT('/api/v1/avatars/',av.filename) author_avatar_url" + DEMANDS + "WHERE " + filter + " ORDER BY d.created_at DESC, d.id DESC LIMIT ? OFFSET ?", CommunityRows.DEMAND, bound.toArray());
        return p.result(rows, repo.count("SELECT COUNT(*)" + DEMANDS + "WHERE " + filter, args));
    }
    private PageResult<DemandReplyItem> replyList(String filter, Integer page, Integer size, Object... args) {
        var p = paging(page, size);
        var bound = new ArrayList<>(Arrays.asList(args)); bound.add(p.size()); bound.add(p.offset());
        var rows = repo.query("SELECT r.*, u.nickname, CONCAT('/api/v1/avatars/',av.filename) author_avatar_url" + REPLIES + "WHERE " + filter + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?", CommunityRows.REPLY, bound.toArray());
        return p.result(rows, repo.count("SELECT COUNT(*)" + REPLIES + "WHERE " + filter, args));
    }
}
