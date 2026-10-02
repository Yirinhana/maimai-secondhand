package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.community.dto.CommunityDtos.RatingRequest;
import com.maimai.notification.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class RatingDetailsService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    public RatingDetailsService(JdbcTemplate jdbc,NotificationService notifications){this.jdbc=jdbc;this.notifications=notifications;}
    public record Details(Integer description,Integer communication,Integer fulfillment,String followup,Instant followedUpAt,List<String> images){}
    public void attach(long actor,long rating,RatingRequest input){
        for(var score:Arrays.asList(input.descriptionRating(),input.communicationRating(),input.fulfillmentRating()))if(score!=null&&(score<1||score>5))throw BizException.badRequest("RATING_DIMENSION","分项评分须为1至5分");
        var ids=input.imageIds()==null?List.<String>of():input.imageIds();
        if(ids.size()>4||new HashSet<>(ids).size()!=ids.size())throw BizException.badRequest("RATING_IMAGES","评价最多附4张不同图片");
        jdbc.update("UPDATE community_order_ratings SET description_rating=?,communication_rating=?,fulfillment_rating=? WHERE id=?",input.descriptionRating(),input.communicationRating(),input.fulfillmentRating(),rating);
        int sort=0;
        for(String id:ids.stream().sorted().toList()){
            if(jdbc.query("SELECT id FROM personal_media WHERE id=? AND owner_id=? AND purpose='REVIEW' FOR UPDATE",(r,n)->r.getString(1),id,actor).isEmpty())throw BizException.notFound("评价图片不存在或不属于本人");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM rating_images WHERE media_id=?",Long.class,id)>0)throw BizException.conflict("RATING_IMAGE_USED","该图片已用于其他评价，请重新选择");
            jdbc.update("INSERT INTO rating_images(rating_id,media_id,sort) VALUES(?,?,?)",rating,id,sort++);
        }
    }
    @Transactional(readOnly=true)
    public Details detail(long id){
        var rows=jdbc.query("SELECT r.* FROM community_order_ratings r JOIN users u ON u.id=r.rater_id JOIN orders o ON o.id=r.order_id WHERE r.id=? AND r.is_hidden=0 AND u.status='ACTIVE' AND r.rater_id<>r.ratee_id AND ((r.rater_id=o.buyer_id AND r.ratee_id=o.seller_id) OR (r.rater_id=o.seller_id AND r.ratee_id=o.buyer_id))",(r,n)->new Details(r.getObject("description_rating",Integer.class),r.getObject("communication_rating",Integer.class),r.getObject("fulfillment_rating",Integer.class),r.getString("followup"),r.getTimestamp("followed_up_at")==null?null:r.getTimestamp("followed_up_at").toInstant(),List.of()),id);
        if(rows.isEmpty())throw BizException.notFound("评价已不可见");
        var d=rows.getFirst();
        var images=jdbc.query("SELECT media_id FROM rating_images WHERE rating_id=? ORDER BY sort",(r,n)->"/api/v1/community/ratings/"+id+"/images/"+r.getString(1),id);
        return new Details(d.description(),d.communication(),d.fulfillment(),d.followup(),d.followedUpAt(),images);
    }
    public Details followup(long actor,long id,String text){
        if(text==null||text.isBlank()||text.strip().length()>500)throw BizException.badRequest("FOLLOWUP_TEXT","请填写1至500字的追评");
        var orders=jdbc.query("SELECT o.order_no,r.ratee_id FROM community_order_ratings r JOIN orders o ON o.id=r.order_id WHERE r.id=? AND r.rater_id=? AND r.is_hidden=0 AND o.fulfillment_status='COMPLETED' AND o.pay_status='PAID' AND o.refund_status<>'FULL' AND (o.experience_source IS NULL OR o.experience_source=?) AND EXISTS(SELECT 1 FROM payment_requests p WHERE p.order_id=o.id AND p.status='PAID') AND ((r.rater_id=o.buyer_id AND r.ratee_id=o.seller_id) OR (r.rater_id=o.seller_id AND r.ratee_id=o.buyer_id)) FOR UPDATE",(r,n)->new Object[]{r.getString(1),r.getLong(2)},id,actor,com.maimai.trade.domain.Order.INTERACTIVE_EXPERIENCE);
        if(orders.isEmpty())throw BizException.notFound("评价不存在或当前订单不可追评");
        if(jdbc.update("UPDATE community_order_ratings SET followup=?,followed_up_at=UTC_TIMESTAMP(6) WHERE id=? AND followed_up_at IS NULL",text.strip(),id)!=1)throw BizException.conflict("FOLLOWUP_EXISTS","每条评价可追加一次，原有内容会保留");
        notifications.notify((Long)orders.getFirst()[1],"REPUTATION_UPDATED","收到一条追评","对方补充了本次交易的使用体验。","/orders/"+orders.getFirst()[0]);
        return detail(id);
    }
}
