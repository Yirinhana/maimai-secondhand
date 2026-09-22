package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.community.repository.CommunityRepository;
import com.maimai.notification.NotificationService;
import com.maimai.trade.service.TransactionProvenance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly=true)
public class ReputationService {
    private final CommunityRepository repo;
    private final NotificationService notifications;
    public ReputationService(CommunityRepository repo,NotificationService notifications){this.repo=repo;this.notifications=notifications;}
    public record Scope(String source,String label,long completedTrades,long ratings,Double averageRating,Double positivePercent){}
    public record Profile(long userId,String level,List<Scope> scopes,long confirmedContentActions,String rules,Instant calculatedAt){}
    public Profile profile(long user){
        if(repo.count("SELECT COUNT(*) FROM users WHERE id=? AND status='ACTIVE'",user)!=1)throw BizException.notFound("用户不存在");
        List<Scope> scopes=List.of(scope(user,"LIVE"),scope(user,"SIMULATED"),scope(user,"HISTORICAL"));
        long ratings=scopes.get(0).ratings()+scopes.get(1).ratings();
        long violations=repo.count("SELECT COUNT(DISTINCT resource_type,resource_id) FROM community_reports WHERE target_owner_id=? AND status='RESOLVED' AND process_action='HIDE'",user);
        double sum=scopes.stream().filter(s->!"HISTORICAL".equals(s.source())&&s.averageRating()!=null).mapToDouble(s->s.averageRating()*s.ratings()).sum();
        String level=ratings==0?"暂无有效交易评价":ratings<3?"评价积累中":sum/ratings>=4?"交易反馈稳定":"交易反馈需关注";
        if(violations>0)level+=" · 有已核实的内容处理记录";
        return new Profile(user,level,scopes,violations,"仅统计参与者身份一致、已付款且完成交付的订单；全额退款、隐藏评价不计入评分。4—5分为好评。模拟支付与正式渠道分别统计，历史体验记录不参与信誉等级。举报本身及AI意见不会降低信誉，处理结果可通过客服工单申诉。",Instant.now());
    }
    private Scope scope(long user,String source){
        String provenance=TransactionProvenance.sql("o");
        long trades=repo.count("SELECT COUNT(*) FROM orders o WHERE (o.buyer_id=? OR o.seller_id=?) AND o.buyer_id<>o.seller_id AND o.fulfillment_status='COMPLETED' AND o.pay_status='PAID' AND o.refund_status<>'FULL' AND "+provenance+"=?",user,user,source);
        return repo.queryOne("""
            SELECT COUNT(*) total,AVG(r.rating) average_rating,AVG(CASE WHEN r.rating>=4 THEN 100.0 ELSE 0.0 END) positive_rate
            FROM community_order_ratings r JOIN orders o ON o.id=r.order_id
            WHERE r.ratee_id=? AND r.is_hidden=0 AND r.rater_id<>r.ratee_id
              AND ((r.rater_id=o.buyer_id AND r.ratee_id=o.seller_id) OR (r.rater_id=o.seller_id AND r.ratee_id=o.buyer_id))
              AND o.fulfillment_status='COMPLETED' AND o.pay_status='PAID' AND o.refund_status<>'FULL' AND
            """+provenance+"=?",(rs,n)->new Scope(source,TransactionProvenance.label(source),trades,rs.getLong("total"),rs.getObject("average_rating")==null?null:Math.round(rs.getDouble("average_rating")*100)/100.0,rs.getObject("positive_rate")==null?null:Math.round(rs.getDouble("positive_rate")*10)/10.0),user,source);
    }
    @Transactional
    public void remindCompleted(){
        var candidates=repo.query("""
            SELECT o.id,o.order_no,o.buyer_id,o.seller_id FROM orders o
            WHERE (o.experience_source IS NULL OR o.experience_source='maimai-experience-checkout-v1') AND o.buyer_id<>o.seller_id AND o.fulfillment_status='COMPLETED' AND o.pay_status='PAID' AND o.refund_status<>'FULL'
              AND o.completed_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 15 DAY)
              AND EXISTS(SELECT 1 FROM payment_requests p WHERE p.order_id=o.id AND p.status='PAID')
              AND (NOT EXISTS(SELECT 1 FROM reputation_reminders n WHERE n.order_id=o.id AND n.user_id=o.buyer_id)
                OR NOT EXISTS(SELECT 1 FROM reputation_reminders n WHERE n.order_id=o.id AND n.user_id=o.seller_id))
            ORDER BY o.id LIMIT 100 FOR UPDATE
            """,(rs,n)->new Object[]{rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getLong(4)});
        for(var order:candidates){
            for(int participant:List.of(2,3)){
                long user=(long)order[participant];
                if(repo.update("INSERT IGNORE INTO reputation_reminders(order_id,user_id) VALUES(?,?)",order[0],user)==0)continue;
                if(repo.count("SELECT COUNT(*) FROM community_order_ratings WHERE order_id=? AND rater_id=?",order[0],user)>0)continue;
                notifications.notify(user,"RATING_REMINDER","交易完成，请客观评价","订单 "+order[1]+" 已完成交付。你可以评价实际沟通与履约情况，评价会影响对方的站内信誉展示；模拟付款会单独标明，不等于真实资金交易。");
            }
        }
    }
}
