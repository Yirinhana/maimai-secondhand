package com.maimai.official;

import com.maimai.admin.service.GovernanceGuard;
import com.maimai.admin.service.AdminAuditService;
import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.official.OfficialDtos.*;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Editorial text lives in MySQL; untrusted clients cannot set the publisher or bypass publication state. */
@Service
public class OfficialArticleService {
    private static final RowMapper<AdminArticle> ROW=(rs,n)->new AdminArticle(rs.getLong("id"),rs.getString("slug"),
            rs.getString("title"),rs.getString("summary"),rs.getString("body"),Category.valueOf(rs.getString("category")),
            Status.valueOf(rs.getString("status")),instant(rs.getTimestamp("published_at")),instant(rs.getTimestamp("updated_at")));
    private final JdbcTemplate db;
    private final GovernanceGuard guard;
    private final Validator validator;
    private final AdminAuditService audit;

    public OfficialArticleService(JdbcTemplate db,GovernanceGuard guard,Validator validator,AdminAuditService audit) {
        this.db=db;this.guard=guard;this.validator=validator;this.audit=audit;
    }

    @Transactional(readOnly=true)
    public Page<ArticleSummary> list(Category category,int page,int size) {
        return query(category,Status.PUBLISHED,page,size).map(AdminArticle::summaryView);
    }

    @Transactional(readOnly=true)
    public ArticleDetail detail(String slug) {
        var rows=db.query("SELECT * FROM official_articles WHERE slug=? AND status='PUBLISHED'",ROW,slug);
        if(rows.isEmpty()) throw BizException.notFound("官方内容不存在或尚未发布");
        return rows.getFirst().detailView();
    }

    @Transactional(readOnly=true)
    public Page<AdminArticle> adminList(Category category,Status status,int page,int size) {
        guard.requireRole("OPERATOR","SUPER_ADMIN");
        return query(category,status,page,size);
    }

    @Transactional
    public AdminArticle create(ArticleRequest request) {
        guard.accounts();guard.requireRole("OPERATOR","SUPER_ADMIN");validate(request);
        long actor=SecurityUtils.currentUserId();
        try {
            db.update("INSERT INTO official_articles(slug,title,summary,body,category,status,published_at,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    request.slug(),request.title(),request.summary(),request.body(),request.category().name(),request.status().name(),
                    request.status()==Status.PUBLISHED?Timestamp.from(Instant.now()):null,actor,actor);
        } catch(DuplicateKeyException duplicate) {throw BizException.conflict("OFFICIAL_SLUG_TAKEN","链接名称已被使用，请换一个");}
        var created=Objects.requireNonNull(db.queryForObject("SELECT * FROM official_articles WHERE slug=?",ROW,request.slug()));
        audit.record("OFFICIAL_CREATE","OFFICIAL_ARTICLE",created.id(),"创建官方内容",null,auditState(created));
        return created;
    }

    @Transactional
    public AdminArticle update(long id,ArticleRequest request) {
        guard.accounts();guard.requireRole("OPERATOR","SUPER_ADMIN");validate(request);
        var existing=db.query("SELECT * FROM official_articles WHERE id=? FOR UPDATE",ROW,id);
        if(existing.isEmpty()) throw BizException.notFound("官方内容不存在");
        Instant published=existing.getFirst().publishedAt();
        if(published==null && request.status()==Status.PUBLISHED) published=Instant.now();
        try {
            db.update("UPDATE official_articles SET slug=?,title=?,summary=?,body=?,category=?,status=?,published_at=?,updated_by=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                    request.slug(),request.title(),request.summary(),request.body(),request.category().name(),request.status().name(),
                    published==null?null:Timestamp.from(published),SecurityUtils.currentUserId(),id);
        } catch(DuplicateKeyException duplicate) {throw BizException.conflict("OFFICIAL_SLUG_TAKEN","链接名称已被使用，请换一个");}
        var updated=Objects.requireNonNull(db.queryForObject("SELECT * FROM official_articles WHERE id=?",ROW,id));
        audit.record("OFFICIAL_UPDATE","OFFICIAL_ARTICLE",id,"编辑官方内容",auditState(existing.getFirst()),auditState(updated));
        return updated;
    }

    private Page<AdminArticle> query(Category category,Status status,int page,int size) {
        if(page<0 || page>10000 || size<1 || size>50) throw BizException.badRequest("PAGE_INVALID","页码或每页数量不正确，每页最多50条");
        var args=new ArrayList<Object>();StringBuilder where=new StringBuilder(" WHERE 1=1");
        if(category!=null) {where.append(" AND category=?");args.add(category.name());}
        if(status!=null) {where.append(" AND status=?");args.add(status.name());}
        long total=Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM official_articles"+where,Long.class,args.toArray()));
        args.add(size);args.add(page*size);
        var rows=db.query("SELECT * FROM official_articles"+where+" ORDER BY published_at DESC,updated_at DESC,id DESC LIMIT ? OFFSET ?",ROW,args.toArray());
        return new PageImpl<>(rows,PageRequest.of(page,size),total);
    }

    private void validate(ArticleRequest request) {
        if(request==null || !validator.validate(request).isEmpty()) throw BizException.badRequest("OFFICIAL_CONTENT_INVALID","请填写有效链接、标题、简介、正文、分类和状态");
        // Control characters have no visible plain-text meaning; keep ordinary paragraphs and tabs.
        if(request.body().chars().anyMatch(c->Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))
            throw BizException.badRequest("OFFICIAL_CONTENT_INVALID","正文包含不支持的控制字符");
    }
    private static Instant instant(Timestamp value) {return value==null?null:value.toInstant();}
    private static String auditState(AdminArticle article) {
        // Audit readers may include support staff; draft title, summary and body stay within editorial access.
        return "status="+article.status()+";category="+article.category()+";slug="+article.slug();
    }
}
