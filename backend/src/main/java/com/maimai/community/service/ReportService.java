package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.repository.CommunityRepository;
import com.maimai.community.repository.CommunityRows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import static com.maimai.community.service.CommunitySupport.*;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private final CommunityRepository repo;
    private final CommunitySupport support;
    private final DemandService demands;
    public ReportService(CommunityRepository repo, CommunitySupport support, DemandService demands) {
        this.repo = repo; this.support = support; this.demands = demands;
    }

    @Transactional
    public ReportItem create(Long actor, ReportCreateRequest request) {
        support.self(actor);
        ResourceType type;
        try { type = ResourceType.valueOf(request.resourceType().strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException | NullPointerException ex) { throw BizException.badRequest("RESOURCE_TYPE_INVALID", "举报类型不支持"); }
        Long target = request.resourceId();
        if (target == null || target < 1) throw BizException.badRequest("REPORT_RESOURCE_INVALID", "举报对象ID无效");
        switch (type) {
            case PRODUCT -> support.publicProduct(target);
            case PRODUCT_COMMENT -> {
                Long product=repo.queryOne("SELECT product_id FROM product_comments WHERE id=? AND status='PUBLISHED'",(rs,n)->rs.getLong(1),target);
                if(product==null)throw BizException.notFound("留言不存在或暂不可见");
                support.publicProduct(product);
            }
            case DEMAND_POST -> DemandService.visible(demands.demand(target, false));
            case DEMAND_REPLY -> {
                var reply = demands.reply(target, false);
                if (!"PUBLISHED".equals(reply.status())) throw BizException.notFound("回复暂不可见");
                DemandService.visible(demands.demand(reply.demandId(), false));
            }
            case ORDER_REVIEW -> {
                if (repo.count("SELECT COUNT(*) FROM community_order_ratings WHERE id = ? AND is_hidden = 0", target) == 0) throw BizException.notFound("评价不存在或暂不可见");
            }
        }
        long id = repo.insertReturningId("INSERT INTO community_reports(reporter_id, resource_type, resource_id, reason) VALUES (?, ?, ?, ?)",
                actor, type.name(), target, text(request.reason(), "举报原因", 6, 500));
        return report(id, false);
    }

    public PageResult<ReportItem> mine(Long actor, Integer page, Integer size) {
        support.self(actor);
        return list("reporter_id = ?", actor, page, size);
    }

    public PageResult<ReportItem> admin(String status, Integer page, Integer size) {
        support.moderator(SecurityUtils.currentUserId());
        String filter = status == null || status.isBlank() ? "PENDING" : status.strip().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("PENDING", "RESOLVED", "DISMISSED", "FORWARDED").contains(filter)) throw BizException.badRequest("REPORT_STATUS_INVALID", "举报状态不支持");
        return list("status = ?", filter, page, size);
    }

    @Transactional
    public void process(Long actor, Long id, ReportAction action, String note) {
        support.moderator(actor);
        var report = report(id, true);
        if (!"PENDING".equals(report.status())) throw BizException.conflict("REPORT_PROCESSED", "举报已处理");
        String reason = text(note, "处理说明", 6, 500);
        if (action == null || (action == ReportAction.FORWARD_TO_PRODUCT && !"PRODUCT".equals(report.resourceType()))
                || (action == ReportAction.HIDE && "PRODUCT".equals(report.resourceType()))) {
            throw BizException.badRequest("REPORT_ACTION_MISMATCH", "处理动作与举报资源类型不匹配");
        }
        if (action == ReportAction.HIDE) hide(actor, report, reason);
        String status = switch (action) { case KEEP, HIDE -> "RESOLVED"; case REJECT -> "DISMISSED"; case FORWARD_TO_PRODUCT -> "FORWARDED"; };
        repo.update("""
                UPDATE community_reports SET status = ?, processed_by = ?, process_action = ?, process_note = ?, resolved_at = NOW(6)
                WHERE id = ? AND status = 'PENDING'""", status, actor, action.name(), reason, id);
        // Report ID makes concurrent processing and each moderation decision separately traceable.
        support.audit(actor, "REPORT_PROCESS", "REPORT", id, report.status(), status, reason);
    }

    private void hide(Long actor, ReportItem report, String reason) {
        Long id = report.resourceId();
        String before;
        switch (report.resourceType()) {
            case "PRODUCT_COMMENT" -> {
                before=repo.queryOne("SELECT status FROM product_comments WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1),id);
                if(before==null)throw BizException.notFound("留言不存在");
                repo.update("UPDATE product_comments SET status='HIDDEN',updated_at=UTC_TIMESTAMP(6) WHERE id=?",id);
            }
            case "DEMAND_POST" -> {
                var demand = demands.demand(id, true);
                before = demand.status();
                repo.update("UPDATE community_demand_posts SET status = 'HIDDEN', updated_at = NOW(6) WHERE id = ?", id);
            }
            case "DEMAND_REPLY" -> {
                var preliminary = demands.reply(id, false);
                demands.demand(preliminary.demandId(), true);
                before = demands.reply(id, true).status();
                repo.update("UPDATE community_demand_replies SET status = 'HIDDEN', updated_at = NOW(6) WHERE id = ?", id);
            }
            case "ORDER_REVIEW" -> {
                Boolean hidden = repo.queryOne("SELECT is_hidden FROM community_order_ratings WHERE id = ? FOR UPDATE", (rs, n) -> rs.getBoolean(1), id);
                if (hidden == null) throw BizException.notFound("评价不存在");
                before = hidden ? "HIDDEN" : "VISIBLE";
                repo.update("UPDATE community_order_ratings SET is_hidden = 1 WHERE id = ?", id);
            }
            default -> throw BizException.badRequest("REPORT_ACTION_MISMATCH", "该资源类型不支持隐藏");
        }
        support.audit(actor, "CONTENT_HIDE", report.resourceType(), id, before, "HIDDEN", reason);
    }

    private ReportItem report(Long id, boolean lock) {
        var item = repo.queryOne("SELECT * FROM community_reports WHERE id = ?" + (lock ? " FOR UPDATE" : ""), CommunityRows.REPORT, id);
        if (item == null) throw BizException.notFound("举报不存在");
        return item;
    }
    private PageResult<ReportItem> list(String filter, Object value, Integer page, Integer size) {
        var p = paging(page, size);
        return p.result(repo.query("SELECT * FROM community_reports WHERE " + filter + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", CommunityRows.REPORT,
                value, p.size(), p.offset()), repo.count("SELECT COUNT(*) FROM community_reports WHERE " + filter, value));
    }
}
