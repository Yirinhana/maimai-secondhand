package com.maimai.governance;

import com.maimai.common.BizException;
import com.maimai.community.repository.CommunityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;

/** One bounded model request outside transactions. Claims and daily budget are shared across instances. */
@Service
public class GovernanceWorker {
    private final CommunityRepository repo;
    private final GovernanceSnapshot sources;
    private final GovernanceModel model;
    private final TransactionTemplate tx;
    private final int dailyLimit;
    private final int gapSeconds;
    private final JsonMapper json=JsonMapper.builder().build();
    public GovernanceWorker(CommunityRepository repo,GovernanceSnapshot sources,GovernanceModel model,PlatformTransactionManager manager,
            @Value("${MAIMAI_GOVERNANCE_DAILY_LIMIT:20}") int dailyLimit,@Value("${MAIMAI_GOVERNANCE_GAP_SECONDS:30}") int gapSeconds) {
        this.repo=repo;this.sources=sources;this.model=model;this.tx=new TransactionTemplate(manager);
        this.dailyLimit=Math.max(1,Math.min(dailyLimit,1000));this.gapSeconds=Math.max(10,Math.min(gapSeconds,3600));
    }
    public int dailyLimit(){return dailyLimit;}

    @Configuration
    static class Scheduling {
        @Bean("governanceScheduler") ThreadPoolTaskScheduler governanceScheduler() {
            var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("governance-");scheduler.setDaemon(true);return scheduler;
        }
    }
    @Scheduled(fixedDelayString="${maimai.governance.poll-ms:10000}",initialDelayString="${maimai.governance.initial-delay-ms:20000}",scheduler="governanceScheduler")
    public void tick() {
        for(int i=0;i<20 && captureOne();i++) { /* bounded intake, no external calls */ }
        recoverExpired();
        processOne();
    }
    public boolean captureOne() {
        return Boolean.TRUE.equals(tx.execute(status->{
            var signal=repo.queryOne("SELECT id,source_type,source_id,revision FROM ai_governance_signals WHERE captured_revision<revision AND updated_at<=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 30 SECOND) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
                (r,n)->new Object[]{r.getLong(1),r.getString(2),r.getLong(3),r.getLong(4)});
            if(signal==null)return false;
            long signalId=(long)signal[0];String type=(String)signal[1];long id=(long)signal[2];
            var snapshot=sources.load(type,id);
            if(snapshot==null) {
                repo.update("UPDATE ai_governance_jobs SET status='SUPERSEDED',finished_at=UTC_TIMESTAMP(6) WHERE source_type=? AND source_id=? AND status='QUEUED'",type,id);
            } else {
                repo.update("UPDATE ai_governance_jobs SET status='SUPERSEDED',finished_at=UTC_TIMESTAMP(6) WHERE source_type=? AND source_id=? AND source_hash<>? AND status='QUEUED'",type,id,snapshot.hash());
                repo.update("""
                    INSERT INTO ai_governance_jobs(signal_id,source_type,source_id,source_hash,lane,group_key,source_url,source_title,actor_id,subject_id,source_text,facts)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id
                    """,signalId,type,id,snapshot.hash(),snapshot.lane(),snapshot.groupKey(),snapshot.url(),snapshot.title(),snapshot.actorId(),snapshot.subjectId(),snapshot.text(),snapshot.facts());
                // Restoring an earlier draft must revive an unprocessed snapshot, without rebilling completed work.
                repo.update("UPDATE ai_governance_jobs SET status='QUEUED',next_attempt_at=UTC_TIMESTAMP(6),finished_at=NULL WHERE source_type=? AND source_id=? AND source_hash=? AND status='SUPERSEDED'",type,id,snapshot.hash());
                if(!GovernanceTriage.candidate(snapshot))repo.update("UPDATE ai_governance_jobs SET status='FILTERED',finished_at=UTC_TIMESTAMP(6) WHERE source_type=? AND source_id=? AND source_hash=? AND status='QUEUED'",type,id,snapshot.hash());
            }
            repo.update("UPDATE ai_governance_signals SET captured_revision=? WHERE id=?",signal[3],signalId);
            return true;
        }));
    }
    record Work(long id,int attempt,GovernanceSnapshot.Snapshot snapshot) {}
    private Work claim() {
        if(!model.configured())return null;
        return tx.execute(status->{
            var control=repo.queryOne("SELECT paused,(next_call_at<=UTC_TIMESTAMP(6)) ready FROM ai_governance_control WHERE id=1 FOR UPDATE",(r,n)->new boolean[]{r.getBoolean(1),r.getBoolean(2)});
            if(control[0]||!control[1]||repo.count("SELECT COUNT(*) FROM ai_governance_jobs WHERE status='ANALYSING'")>0)return null;
            var work=repo.queryOne("SELECT * FROM ai_governance_jobs WHERE status='QUEUED' AND next_attempt_at<=UTC_TIMESTAMP(6) ORDER BY (source_type='REPORT') DESC,id LIMIT 1 FOR UPDATE SKIP LOCKED",
                (r,n)->new Work(r.getLong("id"),r.getInt("attempts")+1,new GovernanceSnapshot.Snapshot(r.getString("source_type"),r.getLong("source_id"),r.getString("lane"),r.getString("group_key"),
                    r.getString("source_url"),r.getString("source_title"),r.getObject("actor_id",Long.class),r.getObject("subject_id",Long.class),r.getString("source_text"),r.getString("facts"))));
            if(work==null)return null;
            // Stale/deleted source content must not be sent to the provider.
            var current=sources.load(work.snapshot().type(),work.snapshot().id());
            if(current==null||!current.hash().equals(work.snapshot().hash())) {
                repo.update("UPDATE ai_governance_jobs SET status='SUPERSEDED',finished_at=UTC_TIMESTAMP(6) WHERE id=?",work.id());return null;
            }
            repo.update("INSERT INTO ai_governance_daily_usage(usage_date) VALUES (UTC_DATE()) ON DUPLICATE KEY UPDATE calls=calls");
            if(repo.update("UPDATE ai_governance_daily_usage SET calls=calls+1 WHERE usage_date=UTC_DATE() AND calls<?",dailyLimit)!=1)return null;
            repo.update("UPDATE ai_governance_control SET next_call_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE id=1",gapSeconds);
            repo.update("UPDATE ai_governance_jobs SET status='ANALYSING',attempts=attempts+1,model=?,started_at=UTC_TIMESTAMP(6),failure_code=NULL WHERE id=?",model.model(),work.id());
            return work;
        });
    }
    public boolean processOne() {
        var work=claim();if(work==null)return false;
        try {
            var assessment=model.assess(work.snapshot());
            // Validate even a different implementation of the model adapter.
            assessment=MiniMaxGovernanceModel.parse(json.writeValueAsString(assessment),work.snapshot().text());
            finish(work,assessment,null);
        } catch(RuntimeException failure) {
            String code=failure instanceof BizException e?e.getCode():"GOV_RESPONSE_INVALID";
            finish(work,null,code.matches("[A-Z_]{1,64}")?code:"GOV_UNAVAILABLE");
        }
        return true;
    }
    private void finish(Work work,GovernanceModel.Assessment assessment,String failure) {
        tx.executeWithoutResult(status->{
            var lease=repo.queryOne("SELECT status,attempts FROM ai_governance_jobs WHERE id=? FOR UPDATE",(r,n)->new Object[]{r.getString(1),r.getInt(2)},work.id());
            if(lease==null || !"ANALYSING".equals(lease[0]) || work.attempt()!=(int)lease[1])return;
            var current=sources.load(work.snapshot().type(),work.snapshot().id());
            if(current==null||!current.hash().equals(work.snapshot().hash())) {
                repo.update("UPDATE ai_governance_jobs SET status='SUPERSEDED',finished_at=UTC_TIMESTAMP(6) WHERE id=?",work.id());return;
            }
            if(failure!=null) {
                repo.update("UPDATE ai_governance_jobs SET status='FAILED',failure_code=?,finished_at=UTC_TIMESTAMP(6) WHERE id=?",failure,work.id());
                openCase(work,"NEEDS_REVIEW","NORMAL");
                var recent=repo.query("SELECT status FROM ai_governance_jobs WHERE status IN ('FAILED','COMPLETED') ORDER BY finished_at DESC,id DESC LIMIT 3",(r,n)->r.getString(1));
                if(recent.size()==3&&recent.stream().allMatch("FAILED"::equals))repo.update("UPDATE ai_governance_control SET paused=1,pause_reason='连续三次分析失败，已暂停以避免继续消耗额度' WHERE id=1");
                return;
            }
            repo.update("UPDATE ai_governance_jobs SET status='COMPLETED',category=?,priority=?,result_json=?,finished_at=UTC_TIMESTAMP(6) WHERE id=?",assessment.category(),assessment.priority(),json.writeValueAsString(assessment),work.id());
            if(!"NONE".equals(assessment.category()))openCase(work,assessment.category(),assessment.priority());
            else if(List.of("REPORT","AFTERSALE","SUPPORT_TICKET").contains(work.snapshot().type()))openCase(work,"NEEDS_REVIEW","NORMAL");
        });
    }
    private void openCase(Work work,String category,String priority) {
        String key=work.snapshot().lane()+":"+work.snapshot().groupKey()+":"+category;
        repo.update("""
            INSERT INTO ai_governance_cases(case_key,lane,category,priority,latest_job_id) VALUES (?,?,?,?,?)
            ON DUPLICATE KEY UPDATE latest_job_id=VALUES(latest_job_id),
              priority=CASE WHEN priority='HIGH' OR VALUES(priority)='HIGH' THEN 'HIGH' WHEN priority='NORMAL' OR VALUES(priority)='NORMAL' THEN 'NORMAL' ELSE 'LOW' END,
              status=IF(status IN ('RESOLVED','DISMISSED'),'OPEN',status),updated_at=UTC_TIMESTAMP(6)
            """,key,work.snapshot().lane(),category,priority,work.id());
        long caseId=repo.queryOne("SELECT id FROM ai_governance_cases WHERE case_key=?",(r,n)->r.getLong(1),key);
        repo.update("INSERT INTO ai_governance_case_jobs(case_id,job_id) VALUES (?,?) ON DUPLICATE KEY UPDATE job_id=job_id",caseId,work.id());
    }
    public void recoverExpired() {
        tx.executeWithoutResult(status->{
            var expired=repo.query("SELECT id,attempts FROM ai_governance_jobs WHERE status='ANALYSING' AND started_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 3 MINUTE) FOR UPDATE SKIP LOCKED",(r,n)->new long[]{r.getLong(1),r.getLong(2)});
            for(var row:expired) {
                // Interrupted requests still consume budget. Reclaimed tasks never run concurrently with a live lease.
                repo.update("UPDATE ai_governance_jobs SET status='QUEUED',failure_code='GOV_INTERRUPTED',next_attempt_at=UTC_TIMESTAMP(6) WHERE id=?",row[0]);
                if(row[1]>=1) {
                    repo.update("UPDATE ai_governance_jobs SET status='FAILED',finished_at=UTC_TIMESTAMP(6) WHERE id=?",row[0]);
                    var work=repo.queryOne("SELECT * FROM ai_governance_jobs WHERE id=?",(r,n)->new Work(r.getLong("id"),r.getInt("attempts"),new GovernanceSnapshot.Snapshot(r.getString("source_type"),r.getLong("source_id"),r.getString("lane"),r.getString("group_key"),r.getString("source_url"),r.getString("source_title"),r.getObject("actor_id",Long.class),r.getObject("subject_id",Long.class),r.getString("source_text"),r.getString("facts"))),row[0]);
                    openCase(work,"NEEDS_REVIEW","NORMAL");
                }
            }
        });
    }
}
