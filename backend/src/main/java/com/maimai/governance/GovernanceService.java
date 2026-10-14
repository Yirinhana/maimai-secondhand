package com.maimai.governance;

import com.maimai.common.BizException;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.SecurityUtils;
import com.maimai.community.repository.CommunityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly=true)
public class GovernanceService {
    private final CommunityRepository repo;
    private final GovernanceModel model;
    private final GovernanceWorker worker;
    private final GovernanceSnapshot sources;
    private final GovernanceSignals signals;
    private final SimpleRateLimiter limiter;
    private final JsonMapper json=JsonMapper.builder().build();
    public GovernanceService(CommunityRepository repo,GovernanceModel model,GovernanceWorker worker,GovernanceSnapshot sources,GovernanceSignals signals,SimpleRateLimiter limiter) {
        this.repo=repo;this.model=model;this.worker=worker;this.sources=sources;this.signals=signals;this.limiter=limiter;
    }
    public record Overview(boolean configured,boolean paused,String pauseReason,String model,int dailyLimit,long callsToday,List<String> lanes,
                           Map<String,Long> jobs,Map<String,Long> cases,Instant generatedAt) {}
    public record CaseRow(long id,String lane,String category,String priority,String status,Long assignedTo,String assignedName,
                          String title,long evidenceCount,String summary,Instant updatedAt) {}
    public record Job(long id,String sourceType,long sourceId,String title,String sourceUrl,Long actorId,String actorName,Long subjectId,String subjectName,
                      String sourceText,String facts,String status,int attempts,String failureCode,String model,String category,
                      GovernanceModel.Assessment assessment,boolean sourceChanged,Instant createdAt,Instant startedAt,Instant finishedAt) {}
    public record Action(String actor,String action,String note,Instant createdAt) {}
    public record Detail(CaseRow item,List<Job> evidence,List<Action> actions) {}
    public record Page<T>(List<T> items,long total,int page,int totalPages) {}

    private List<String> lanes() {
        var user=SecurityUtils.current();
        if(user.hasRole("SUPER_ADMIN"))return List.of("PUBLIC","SERVICE");
        var lanes=new ArrayList<String>();
        if(user.hasRole("OPERATOR"))lanes.add("PUBLIC");
        if(user.hasRole("SUPPORT"))lanes.add("SERVICE");
        if(lanes.isEmpty())throw BizException.forbidden("仅平台工作人员可查看预审信息");
        return lanes;
    }
    private String laneSql(String alias) { return alias+".lane IN ("+String.join(",",lanes().stream().map(s->"'"+s+"'").toList())+")"; }
    private void superAdmin() { if(!SecurityUtils.current().hasRole("SUPER_ADMIN"))throw BizException.forbidden("仅超级管理员可调整后台预审"); }
    public Overview overview() {
        var allowed=lanes();
        boolean paused=Boolean.TRUE.equals(repo.queryOne("SELECT paused FROM ai_governance_control WHERE id=1",(r,n)->r.getBoolean(1)));
        var jobs=new java.util.LinkedHashMap<String,Long>();
        for(String state:List.of("QUEUED","ANALYSING","FAILED","COMPLETED","SUPERSEDED","FILTERED"))jobs.put(state,repo.count("SELECT COUNT(*) FROM ai_governance_jobs j WHERE "+laneSql("j")+" AND j.status=?",state));
        var cases=new java.util.LinkedHashMap<String,Long>();
        for(String state:List.of("OPEN","IN_PROGRESS","RESOLVED","DISMISSED"))cases.put(state,repo.count("SELECT COUNT(*) FROM ai_governance_cases c WHERE "+laneSql("c")+" AND c.status=?",state));
        cases.put("HIGH",repo.count("SELECT COUNT(*) FROM ai_governance_cases c WHERE "+laneSql("c")+" AND c.priority='HIGH' AND c.status IN ('OPEN','IN_PROGRESS')"));
        long calls=repo.count("SELECT COALESCE(SUM(calls),0) FROM ai_governance_daily_usage WHERE usage_date=UTC_DATE()");
        String pauseReason=repo.queryOne("SELECT pause_reason FROM ai_governance_control WHERE id=1",(r,n)->r.getString(1));
        return new Overview(model.configured(),paused,pauseReason,model.model(),worker.dailyLimit(),calls,allowed,jobs,cases,Instant.now());
    }
    private static final String CASE_SELECT="""
        SELECT c.*,u.nickname assigned_name,j.source_title,j.result_json,
        (SELECT COUNT(*) FROM ai_governance_case_jobs e WHERE e.case_id=c.id) evidence_count
        FROM ai_governance_cases c JOIN ai_governance_jobs j ON j.id=c.latest_job_id LEFT JOIN users u ON u.id=c.assigned_to
        """;
    private CaseRow mapCase(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        var result=assessment(r.getString("result_json"));
        return new CaseRow(r.getLong("id"),r.getString("lane"),r.getString("category"),r.getString("priority"),r.getString("status"),r.getObject("assigned_to",Long.class),r.getString("assigned_name"),r.getString("source_title"),r.getLong("evidence_count"),result==null?"模型分析未完成，请按原文人工核查。":result.summary(),instant(r,"updated_at"));
    }
    private CaseRow item(long id,boolean lock) {
        // Row lock is acquired separately; correlated count queries are read-only.
        if(lock)repo.queryOne("SELECT id FROM ai_governance_cases c WHERE c.id=? AND "+laneSql("c")+" FOR UPDATE",(r,n)->r.getLong(1),id);
        var item=repo.queryOne(CASE_SELECT+" WHERE c.id=? AND "+laneSql("c"),this::mapCase,id);
        if(item==null)throw BizException.notFound("预审线索不存在或无权查看");
        return item;
    }
    public Page<CaseRow> list(String state,String lane,int page) {
        if(!Set.of("OPEN","IN_PROGRESS","RESOLVED","DISMISSED","ALL").contains(state))throw BizException.badRequest("GOV_STATE","线索状态无效");
        if(page<0||page>10000)throw BizException.badRequest("GOV_PAGE","页码无效");
        String filter=laneSql("c");var args=new ArrayList<Object>();
        if(!state.equals("ALL")){filter+=" AND c.status=?";args.add(state);}
        if(lane!=null&&!lane.isBlank()) {if(!lanes().contains(lane))throw BizException.forbidden("无权查看该业务区域");filter+=" AND c.lane=?";args.add(lane);}
        long total=repo.count("SELECT COUNT(*) FROM ai_governance_cases c WHERE "+filter,args.toArray());
        args.add(page*20);
        var items=repo.query(CASE_SELECT+" WHERE "+filter+" ORDER BY FIELD(c.priority,'HIGH','NORMAL','LOW'),c.updated_at DESC,c.id DESC LIMIT 20 OFFSET ?",this::mapCase,args.toArray());
        return new Page<>(items,total,page,(int)((total+19)/20));
    }
    private static final String JOB_SELECT="""
        SELECT j.*,u.nickname actor_name,s.nickname subject_name FROM ai_governance_jobs j
        LEFT JOIN users u ON u.id=j.actor_id LEFT JOIN users s ON s.id=j.subject_id
        """;
    private Job mapJob(java.sql.ResultSet r,int n) throws java.sql.SQLException {
        var current=sources.load(r.getString("source_type"),r.getLong("source_id"));
        return new Job(r.getLong("id"),r.getString("source_type"),r.getLong("source_id"),r.getString("source_title"),r.getString("source_url"),r.getObject("actor_id",Long.class),r.getString("actor_name"),r.getObject("subject_id",Long.class),r.getString("subject_name"),r.getString("source_text"),r.getString("facts"),r.getString("status"),r.getInt("attempts"),r.getString("failure_code"),r.getString("model"),r.getString("category"),assessment(r.getString("result_json")),current==null||!current.hash().equals(r.getString("source_hash")),instant(r,"created_at"),instant(r,"started_at"),instant(r,"finished_at"));
    }
    public Detail detail(long id) {
        var item=item(id,false);
        var evidence=repo.query(JOB_SELECT+" JOIN ai_governance_case_jobs e ON e.job_id=j.id WHERE e.case_id=? AND "+laneSql("j")+" ORDER BY j.id DESC LIMIT 50",this::mapJob,id);
        var actions=repo.query("SELECT a.*,u.nickname FROM ai_governance_actions a JOIN users u ON u.id=a.actor_id WHERE a.case_id=? ORDER BY a.id DESC LIMIT 50",(r,n)->new Action(r.getString("nickname"),r.getString("action"),r.getString("note"),instant(r,"created_at")),id);
        return new Detail(item,evidence,actions);
    }
    public Page<Job> jobs(String state,int page) {
        if(!Set.of("QUEUED","ANALYSING","COMPLETED","FAILED","SUPERSEDED","FILTERED").contains(state)||page<0||page>10000)throw BizException.badRequest("GOV_FILTER","任务筛选无效");
        long total=repo.count("SELECT COUNT(*) FROM ai_governance_jobs j WHERE "+laneSql("j")+" AND j.status=?",state);
        var items=repo.query(JOB_SELECT+" WHERE "+laneSql("j")+" AND j.status=? ORDER BY j.id DESC LIMIT 20 OFFSET ?",this::mapJob,state,page*20);
        return new Page<>(items,total,page,(int)((total+19)/20));
    }
    @Transactional
    public Detail action(long id,String action,String note) {
        var item=item(id,true);long actor=SecurityUtils.currentUserId();
        if(note==null||note.strip().length()<6||note.length()>1000)throw BizException.badRequest("GOV_NOTE","请填写6至1000字的核查说明");
        String state=switch(action){case "CLAIM"->"IN_PROGRESS";case "RESOLVE"->"RESOLVED";case "DISMISS"->"DISMISSED";case "REOPEN"->"OPEN";default->throw BizException.badRequest("GOV_ACTION","处理动作无效");};
        if(item.assignedTo()!=null&&item.assignedTo()!=actor&&!SecurityUtils.current().hasRole("SUPER_ADMIN"))throw BizException.conflict("GOV_ASSIGNED","已由其他工作人员跟进");
        if(!action.equals("REOPEN")&&!Set.of("OPEN","IN_PROGRESS").contains(item.status()))throw BizException.conflict("GOV_CLOSED","线索已结束，可先重新开启");
        repo.update("UPDATE ai_governance_cases SET status=?,assigned_to=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",state,action.equals("REOPEN")?null:actor,id);
        audit(id,action,GovernanceSnapshot.redact(note));
        return detail(id);
    }
    @Transactional
    public void pause(boolean paused) {superAdmin();repo.update("UPDATE ai_governance_control SET paused=?,pause_reason=NULL WHERE id=1",paused);audit(null,paused?"PAUSE":"RESUME",paused?"暂停新的模型请求；已发出的请求仍会收尾":"恢复队列，仍受服务端配置与每日额度上限约束");}
    @Transactional
    public int scanRecent() {
        superAdmin();limiter.require("governance:backfill",1,60,"请稍后再补查历史内容");
        int count=0;
        for(var entry:sources.tables().entrySet())for(long id:repo.query("SELECT id FROM "+entry.getValue()+" ORDER BY id DESC LIMIT 20",(r,n)->r.getLong(1))) {
            if(sources.load(entry.getKey(),id)!=null){signals.touch(entry.getKey(),id);count++;}
        }
        audit(null,"SCAN_RECENT","对每类最近20条记录进行补查，共登记 "+count+" 条；相同内容会去重，模型请求仍受每日上限限制。");return count;
    }
    @Transactional
    public void retry(long jobId) {
        var job=repo.queryOne("SELECT status FROM ai_governance_jobs j WHERE j.id=? AND "+laneSql("j")+" FOR UPDATE",(r,n)->r.getString(1),jobId);
        if(job==null)throw BizException.notFound("任务不存在或无权查看");
        if(!"FAILED".equals(job))throw BizException.conflict("GOV_RETRY","仅失败任务可重新提交");
        limiter.require("governance:retry:"+SecurityUtils.currentUserId(),10,300,"重新分析过于频繁");
        repo.update("UPDATE ai_governance_jobs SET status='QUEUED',next_attempt_at=UTC_TIMESTAMP(6) WHERE id=?",jobId);
        audit(null,"RETRY","人工请求重新分析任务 "+jobId+"；仍受每日调用上限限制。");
    }
    public String export(long id) {
        var d=detail(id);StringBuilder out=new StringBuilder("麦麦二手 · AI预审核查材料\n线索编号："+id+"\n生成时间："+Instant.now()+"\n");
        out.append("AI建议是待核实线索，不是用户举报或处罚决定。原文快照经过脱敏。最多包含最近50条依据。\n\n");
        for(var e:d.evidence()) {
            out.append("来源：").append(e.sourceType()).append(" #").append(e.sourceId()).append("\n标题：").append(e.title())
                .append("\n作者：").append(e.actorName()).append("\n关联对象：").append(e.subjectName())
                .append("\n站内路径：").append(e.sourceUrl()).append("\n任务状态：").append(e.status()).append("\n模型：").append(e.model())
                .append("\n快照时间：").append(e.createdAt()).append("\n原文已变化或不可用：").append(e.sourceChanged()).append("\n脱敏原文：\n").append(e.sourceText()).append("\n");
            if(e.assessment()!=null)out.append("待核实摘要：").append(e.assessment().summary()).append("\n依据摘录：").append(e.assessment().evidence()).append("\n待补证据：").append(e.assessment().missing()).append("\n人工建议：").append(e.assessment().suggestion()).append("\n");
            else out.append("尚无有效模型结论，请人工核查。\n");
            out.append("\n");
        }
        out.append("人工跟进记录（不代替原业务处理凭据）：\n");
        for(var a:d.actions())out.append(a.createdAt()).append(" · ").append(a.actor()).append(" · ").append(a.action()).append("\n").append(a.note()).append("\n");
        return out.toString();
    }
    private void audit(Long id,String action,String note) {repo.update("INSERT INTO ai_governance_actions(case_id,actor_id,action,note) VALUES (?,?,?,?)",id,SecurityUtils.currentUserId(),action,note);}
    private GovernanceModel.Assessment assessment(String value) {return value==null?null:json.readValue(value,GovernanceModel.Assessment.class);}
    private static Instant instant(java.sql.ResultSet r,String column) throws java.sql.SQLException {var t=r.getTimestamp(column);return t==null?null:t.toInstant();}
}
