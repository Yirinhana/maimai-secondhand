package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.community.repository.CommunityRepository;
import com.maimai.support.ai.SupportAiGateway;
import com.maimai.support.workflow.WorkflowAiService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class ReportContextService {
    private final CommunityRepository repo;
    private final CommunitySupport support;
    private final SupportAiGateway ai;
    private final com.maimai.common.SimpleRateLimiter limiter;
    public ReportContextService(CommunityRepository repo,CommunitySupport support,SupportAiGateway ai,com.maimai.common.SimpleRateLimiter limiter){this.repo=repo;this.support=support;this.ai=ai;this.limiter=limiter;}
    public record Source(Long ownerId,String text,String url){}
    public record Assessment(long id,String status,String assessment,String failureCode,String model,Instant createdAt){}
    public record Context(long id,String reporterNickname,String targetNickname,Long targetOwnerId,String sourceText,String currentText,String targetUrl,boolean snapshotSaved,long sameTargetReports,long reporterDismissals,List<Assessment> assessments){}
    Source source(String type,long id){
        return switch(type){
            case "PRODUCT" -> repo.queryOne("SELECT seller_id,CONCAT(title,'\n',COALESCE(description,'')) body FROM products WHERE id=?",(rs,n)->new Source(rs.getLong(1),rs.getString(2),"/products/"+id),id);
            case "PRODUCT_COMMENT" -> repo.queryOne("SELECT author_id,content,product_id FROM product_comments WHERE id=?",(rs,n)->new Source(rs.getLong(1),rs.getString(2),"/products/"+rs.getLong(3)+"#discussion-"+id),id);
            case "DEMAND_POST" -> repo.queryOne("SELECT author_id,CONCAT(title,'\n',description) FROM community_demand_posts WHERE id=?",(rs,n)->new Source(rs.getLong(1),rs.getString(2),"/community/demands?demandId="+id),id);
            case "DEMAND_REPLY" -> repo.queryOne("SELECT author_id,content,demand_id FROM community_demand_replies WHERE id=?",(rs,n)->new Source(rs.getLong(1),rs.getString(2),"/community/demands?demandId="+rs.getLong(3)+"&replyId="+id),id);
            case "ORDER_REVIEW" -> repo.queryOne("SELECT rater_id,CONCAT(rating,'分\n',COALESCE(comment,'')),ratee_id FROM community_order_ratings WHERE id=?",(rs,n)->new Source(rs.getLong(1),rs.getString(2),"/sellers/"+rs.getLong(3)+"#rating-"+id),id);
            default -> null;
        };
    }
    public Context detail(long id){
        support.moderator(SecurityUtils.currentUserId());
        var report=repo.queryOne("SELECT r.*,u.nickname reporter_name FROM community_reports r JOIN users u ON u.id=r.reporter_id WHERE r.id=?",(rs,n)->new Object[]{rs.getString("resource_type"),rs.getLong("resource_id"),rs.getLong("reporter_id"),rs.getString("reporter_name"),rs.getObject("target_owner_id",Long.class),rs.getString("content_snapshot"),rs.getString("target_url")},id);
        if(report==null)throw BizException.notFound("举报不存在");
        Source current=source((String)report[0],(long)report[1]);
        Long owner=report[4]!=null?(Long)report[4]:current==null?null:current.ownerId();
        String nickname=owner==null?"来源账号不可用":repo.queryOne("SELECT nickname FROM users WHERE id=?",(rs,n)->rs.getString(1),owner);
        boolean snapshot=report[5]!=null;
        String text=snapshot?(String)report[5]:current==null?"原文不可用，不能仅凭举报描述作出处罚":current.text();
        String url=report[6]!=null?(String)report[6]:current==null?null:current.url();
        var assessments=repo.query("SELECT * FROM report_ai_assessments WHERE report_id=? ORDER BY id DESC LIMIT 5",(rs,n)->new Assessment(rs.getLong("id"),rs.getString("status"),rs.getString("assessment"),rs.getString("failure_code"),rs.getString("model"),rs.getTimestamp("created_at").toInstant()),id);
        return new Context(id,(String)report[3],nickname,owner,text,current==null?null:current.text(),url,snapshot,
            repo.count("SELECT COUNT(*) FROM community_reports WHERE resource_type=? AND resource_id=?",report[0],report[1]),
            repo.count("SELECT COUNT(*) FROM community_reports WHERE reporter_id=? AND status='DISMISSED'",report[2]),assessments);
    }
    public Context assess(long id){
        Context context=detail(id);
        String status=repo.queryOne("SELECT status FROM community_reports WHERE id=?",(rs,n)->rs.getString(1),id);
        if(!"PENDING".equals(status))throw BizException.conflict("REPORT_PROCESSED","举报已处理，无需重新分析");
        limiter.require("report-ai:"+SecurityUtils.currentUserId(),12,300,"审核辅助请求较多，请稍后重试");
        if(repo.count("SELECT COUNT(*) FROM report_ai_assessments WHERE report_id=? AND status='RUNNING' AND created_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 MINUTE)",id)>0)throw BizException.conflict("AI_RUNNING","本条举报正在分析，请稍后刷新");
        long attempt=repo.insertReturningId("INSERT INTO report_ai_assessments(report_id,requested_by,status,model) VALUES(?,?,'RUNNING',?)",id,SecurityUtils.currentUserId(),ai.modelName());
        String reason=repo.queryOne("SELECT reason FROM community_reports WHERE id=?",(rs,n)->rs.getString(1),id);
        try{
            String answer=ai.advise("按以下顺序输出纯文本审核建议：内容是否存在违规线索；举报是否存在伪造、断章取义或骚扰线索；还缺少哪些证据；建议管理员下一步。单次举报、重复举报或历史驳回都不能单独证明恶意。不得给出确定的人格判断、封号决定或信誉分。资料不足时明确无法判断，不能把举报不成立等同恶意。",
                "举报原因为待核查陈述："+WorkflowAiService.redact(reason)+"\n被举报内容："+WorkflowAiService.redact(context.sourceText())+"\n已保存提交时快照="+context.snapshotSaved()+"；同对象举报数="+context.sameTargetReports()+"；举报人历史驳回数="+context.reporterDismissals()+"。计数仅为核查线索，不是违规证明。");
            repo.update("UPDATE report_ai_assessments SET status='SUCCEEDED',assessment=?,finished_at=UTC_TIMESTAMP(6) WHERE id=?",answer,attempt);
        }catch(BizException error){repo.update("UPDATE report_ai_assessments SET status='FAILED',failure_code=?,finished_at=UTC_TIMESTAMP(6) WHERE id=?",error.getCode(),attempt);}
        catch(RuntimeException error){repo.update("UPDATE report_ai_assessments SET status='FAILED',failure_code='AI_UNAVAILABLE',finished_at=UTC_TIMESTAMP(6) WHERE id=?",attempt);}
        return detail(id);
    }
}
