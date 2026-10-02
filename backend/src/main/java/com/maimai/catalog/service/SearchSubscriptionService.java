package com.maimai.catalog.service;

import com.maimai.common.BizException;
import com.maimai.notification.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class SearchSubscriptionService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final CategoryAvailability categories;
    public SearchSubscriptionService(JdbcTemplate jdbc,NotificationService notifications,CategoryAvailability categories){this.jdbc=jdbc;this.notifications=notifications;this.categories=categories;}
    public record Input(String name,String keyword,Long categoryId,Long minPriceCents,Long maxPriceCents,String region,String condition,String deliveryMethod){}
    public record Watch(long id,String name,String keyword,Long categoryId,Long minPriceCents,Long maxPriceCents,String region,String condition,String deliveryMethod,boolean enabled,Instant createdAt){}
    public List<Watch> mine(long user){return jdbc.query("SELECT * FROM search_subscriptions WHERE user_id=? ORDER BY id DESC",(r,n)->new Watch(r.getLong("id"),r.getString("name"),r.getString("keyword"),r.getObject("category_id",Long.class),r.getObject("min_price_cents",Long.class),r.getObject("max_price_cents",Long.class),r.getString("region"),r.getString("item_condition"),r.getString("delivery_method"),r.getBoolean("enabled"),r.getTimestamp("created_at").toInstant()),user);}
    public List<Watch> create(long user,Input input){
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user);
        if(mine(user).size()>=20)throw BizException.badRequest("WATCH_LIMIT","最多保留20个找货条件，请先移除不再需要的条件");
        String name=clean(input.name(),80),keyword=clean(input.keyword(),120),region=clean(input.region(),100),condition=clean(input.condition(),20),delivery=clean(input.deliveryMethod(),10);
        if(name.isEmpty())throw BizException.badRequest("WATCH_NAME","请给找货条件取个名字");
        if((input.minPriceCents()!=null&&input.minPriceCents()<0)||(input.maxPriceCents()!=null&&input.maxPriceCents()<0)||(input.minPriceCents()!=null&&input.maxPriceCents()!=null&&input.minPriceCents()>input.maxPriceCents()))throw BizException.badRequest("WATCH_PRICE","预算区间不正确");
        if(!condition.isEmpty())ProductQueryService.parseCondition(condition);
        if(!Set.of("","EXPRESS","MEETUP").contains(delivery))throw BizException.badRequest("WATCH_DELIVERY","交付方式不正确");
        if(input.categoryId()!=null)categories.requireActive(input.categoryId());
        jdbc.update("INSERT INTO search_subscriptions(user_id,name,keyword,category_id,min_price_cents,max_price_cents,region,item_condition,delivery_method) VALUES(?,?,?,?,?,?,?,?,?)",user,name,keyword,input.categoryId(),input.minPriceCents(),input.maxPriceCents(),region,condition,delivery);
        return mine(user);
    }
    public void enabled(long user,long id,boolean enabled){if(jdbc.update("UPDATE search_subscriptions SET enabled=? WHERE id=? AND user_id=?",enabled,id,user)!=1)throw BizException.notFound("找货条件不存在");}
    public void remove(long user,long id){if(jdbc.update("DELETE FROM search_subscriptions WHERE id=? AND user_id=?",id,user)!=1)throw BizException.notFound("找货条件不存在");}
    public List<Long> due(){return jdbc.query("SELECT s.id FROM search_subscriptions s JOIN users u ON u.id=s.user_id WHERE s.enabled=1 AND u.status='ACTIVE' ORDER BY s.checked_at,s.id LIMIT 100",(r,n)->r.getLong(1));}
    public void check(long id){
        var subs=jdbc.query("SELECT user_id,keyword,category_id,min_price_cents,max_price_cents,region,item_condition,delivery_method,created_at FROM search_subscriptions WHERE id=? AND enabled=1 FOR UPDATE",(r,n)->new Object[]{r.getLong(1),r.getString(2),r.getObject(3,Long.class),r.getObject(4,Long.class),r.getObject(5,Long.class),r.getString(6),r.getString(7),r.getString(8),r.getTimestamp(9)},id);
        if(subs.isEmpty())return;var s=subs.getFirst();long user=(Long)s[0];
        jdbc.update("UPDATE search_subscriptions SET checked_at=UTC_TIMESTAMP(6) WHERE id=?",id);
        // Maximum three in-site alerts per hour per user, including all their saved searches.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='SEARCH_MATCH' AND created_at>UTC_TIMESTAMP()-INTERVAL 1 HOUR",Long.class,user)>=3)return;
        var args=new ArrayList<Object>();args.add(s[2]);args.add(s[2]);args.add(id);args.add(user);args.add(s[8]);args.add(s[1]);args.add(s[1]);
        String filter=" AND (? IS NULL OR p.price_cents>=?) AND (? IS NULL OR p.price_cents<=?) AND (?='' OR p.region=?) AND (?='' OR p.item_condition=?) AND (?='' OR FIND_IN_SET(?,p.delivery_methods)>0)";
        for(int i:new int[]{3,4,5,6,7}){args.add(s[i]);args.add(s[i]);}
        var hits=jdbc.query("WITH RECURSIVE active AS (SELECT id,parent_id FROM categories WHERE parent_id IS NULL AND status='ACTIVE' UNION ALL SELECT c.id,c.parent_id FROM categories c JOIN active a ON c.parent_id=a.id WHERE c.status='ACTIVE'), chosen AS (SELECT id FROM active WHERE (? IS NULL AND parent_id IS NULL) OR id=? UNION ALL SELECT a.id FROM active a JOIN chosen ch ON a.parent_id=ch.id) SELECT p.id,p.title FROM products p JOIN users u ON u.id=p.seller_id WHERE p.category_id IN (SELECT id FROM chosen) AND p.status='ON_SALE' AND p.stock_available>0 AND u.status='ACTIVE' AND EXISTS(SELECT 1 FROM seller_applications sa WHERE sa.user_id=p.seller_id AND sa.status='APPROVED') AND NOT EXISTS(SELECT 1 FROM search_subscription_matches m WHERE m.subscription_id=? AND m.product_id=p.id) AND p.seller_id<>? AND p.updated_at>? AND (?='' OR LOCATE(LOWER(?),LOWER(p.title))>0)"+filter+" ORDER BY p.updated_at,p.id LIMIT 1",(r,n)->new Object[]{r.getLong(1),r.getString(2)},args.toArray());
        if(!hits.isEmpty()){
            var hit=hits.getFirst();jdbc.update("INSERT INTO search_subscription_matches(subscription_id,product_id) VALUES(?,?)",id,hit[0]);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='SEARCH_MATCH' AND target_path=?",Long.class,user,"/products/"+hit[0])==0)
                notifications.notify(user,"SEARCH_MATCH","找到符合条件的新闲置",(String)hit[1],"/products/"+hit[0]);
        }
    }
    private static String clean(String s,int max){String value=s==null?"":s.strip();if(value.length()>max||value.indexOf('\0')>=0)throw BizException.badRequest("WATCH_CONTENT","找货条件长度超出限制");return value;}
}
