package com.maimai.governance;

import com.maimai.community.repository.CommunityRepository;
import com.maimai.support.workflow.WorkflowAiService;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Explicit source allowlist. Never reads private chat, delivery addresses, images or credentials. */
@Component
public class GovernanceSnapshot {
    public record Snapshot(String type, long id, String lane, String groupKey, String url, String title,
                           Long actorId, Long subjectId, String text, String facts) {
        public String hash() { return digest(type+"|"+id+"|"+text+"|"+facts); }
    }
    private final CommunityRepository repo;
    public GovernanceSnapshot(CommunityRepository repo) { this.repo=repo; }

    public Snapshot load(String type,long id) {
        return switch(type) {
            case "PRODUCT" -> repo.queryOne("""
                SELECT * FROM products WHERE id=? AND status IN ('PENDING_REVIEW','CHANGES_REVIEW','ON_SALE')
                """, (r,n)->snapshot(type,id,"PUBLIC","PRODUCT:"+id,"/admin/products?productId="+id,r.getString("title"),
                    r.getLong("seller_id"),r.getLong("seller_id"),r.getString("title")+"\n"+r.getString("description")+"\n已披露缺陷："+value(r.getString("defects")),
                    "成色="+r.getString("item_condition")+"。未分析图片，不验证商品真伪。"),id);
            case "PRODUCT_COMMENT" -> repo.queryOne("""
                SELECT c.*,p.title,p.seller_id FROM product_comments c JOIN products p ON p.id=c.product_id
                WHERE c.id=? AND c.status='PUBLISHED'
                """,(r,n)->snapshot(type,id,"PUBLIC","PRODUCT:"+r.getLong("product_id"),"/products/"+r.getLong("product_id")+"#discussion-"+id,
                    r.getString("title")+" · 讨论",r.getLong("author_id"),r.getLong("seller_id"),r.getString("content"),
                    "来源为商品讨论；没有交易证据，不把留言中的指控视为已证实事实。"),id);
            case "ORDER_REVIEW" -> repo.queryOne("""
                SELECT r.*,o.fulfillment_status,o.refund_status,o.pay_status FROM community_order_ratings r
                JOIN orders o ON o.id=r.order_id WHERE r.id=? AND r.is_hidden=0 AND
                ((r.rater_id=o.buyer_id AND r.ratee_id=o.seller_id) OR (r.rater_id=o.seller_id AND r.ratee_id=o.buyer_id))
                """,(r,n)->snapshot(type,id,"PUBLIC","ORDER_REVIEW:"+r.getLong("order_id"),"/sellers/"+r.getLong("ratee_id")+"#rating-"+id,
                    "交易评价及追评",r.getLong("rater_id"),r.getLong("ratee_id"),value(r.getString("comment"))+"\n追评："+value(r.getString("followup")),
                    "评分="+r.getInt("rating")+"；履约="+r.getString("fulfillment_status")+"；退款="+r.getString("refund_status")+"。差评本身不是违规；图片未分析。"),id);
            case "DEMAND_POST" -> repo.queryOne("SELECT * FROM community_demand_posts WHERE id=? AND is_deleted=0 AND status IN ('PENDING','PUBLISHED')",
                (r,n)->snapshot(type,id,"PUBLIC","DEMAND:"+id,"/community/demands?demandId="+id,r.getString("title"),r.getLong("author_id"),null,
                    r.getString("title")+"\n"+r.getString("description"),"求购内容；待审核内容请在社区审核页核查。"),id);
            case "DEMAND_REPLY" -> repo.queryOne("""
                SELECT r.*,d.title FROM community_demand_replies r JOIN community_demand_posts d ON d.id=r.demand_id
                WHERE r.id=? AND r.is_deleted=0 AND d.is_deleted=0 AND r.status IN ('PENDING','PUBLISHED')
                """,(r,n)->snapshot(type,id,"PUBLIC","DEMAND:"+r.getLong("demand_id"),"/community/demands?demandId="+r.getLong("demand_id")+"&replyId="+id,
                    r.getString("title")+" · 回复",r.getLong("author_id"),null,r.getString("content"),"求购回复；未提供交易证据。"),id);
            case "REPORT" -> report(id);
            case "AFTERSALE" -> repo.queryOne("""
                SELECT a.*,o.seller_id,o.fulfillment_status,o.pay_status FROM aftersales a JOIN orders o ON o.id=a.order_id
                WHERE a.id=? AND a.status IN ('PENDING_SELLER','PENDING_RETURN','RETURN_SHIPPED','PENDING_MANUAL')
                """,(r,n)->snapshot(type,id,"SERVICE","ORDER:"+r.getLong("order_id"),"/aftersales/"+id,"售后诉求",r.getLong("buyer_id"),r.getLong("seller_id"),
                    "申请原因："+r.getString("reason")+"\n卖家答复："+value(r.getString("seller_reply")),
                    "售后="+r.getString("status")+"；类型="+r.getString("type")+"；履约="+r.getString("fulfillment_status")+"。未发送地址、证据图片、物流单号或付款账号。"),id);
            case "SUPPORT_TICKET" -> ticket(id);
            default -> null;
        };
    }
    private Snapshot report(long id) {
        var row=repo.queryOne("SELECT * FROM community_reports WHERE id=? AND status='PENDING'",(r,n)->new Object[]{
                r.getString("resource_type"),r.getLong("resource_id"),r.getLong("reporter_id"),r.getObject("target_owner_id",Long.class),r.getString("reason"),r.getString("content_snapshot")},id);
        if(row==null)return null;
        Snapshot target=List.of("PRODUCT","PRODUCT_COMMENT","ORDER_REVIEW","DEMAND_POST","DEMAND_REPLY").contains(row[0])?load((String)row[0],(long)row[1]):null;
        String group=target==null?row[0]+":"+row[1]:target.groupKey();
        return snapshot("REPORT",id,"PUBLIC",group,"/admin/community?tab=reports&reportId="+id,"用户主动举报",(Long)row[2],(Long)row[3],
                "举报理由（待核实）："+row[4]+"\n提交时原文："+value((String)row[5]),
                "用户已主动举报；AI 不能驳回或关闭举报。无原文快照时必须人工核查，举报不成立不等于恶意举报。");
    }
    private Snapshot ticket(long id) {
        var row=repo.queryOne("SELECT * FROM support_tickets WHERE id=? AND status<>'CLOSED'",(r,n)->new Object[]{r.getLong("owner_id"),r.getObject("order_id",Long.class),r.getString("title"),r.getString("status")},id);
        if(row==null)return null;
        List<String> messages=repo.query("SELECT body FROM support_messages WHERE ticket_id=? AND author_kind='USER' ORDER BY id DESC LIMIT 3",(r,n)->r.getString(1),id);
        return snapshot("SUPPORT_TICKET",id,"SERVICE",row[1]==null?"TICKET:"+id:"ORDER:"+row[1],"/support/tickets/"+id,(String)row[2],(Long)row[0],null,
                row[2]+"\n"+String.join("\n",messages),"客服工单状态="+row[3]+"；仅整理用户主动提交的最近三条诉求，未读取私人聊天。");
    }
    private static Snapshot snapshot(String type,long id,String lane,String group,String url,String title,Long actor,Long subject,String text,String facts) {
        return new Snapshot(type,id,lane,group,url,limit(redact(title),180),actor,subject,limit(redact(text),2800),limit(redact(facts),700));
    }
    public static String redact(String text) {
        return WorkflowAiService.redact(value(text))
            .replaceAll("(?i)(?:https?://|www\\.)[^\\s，。；]+","[外部链接已隐藏]")
            .replaceAll("(?i)(?:sk[-_]|g2a_)[A-Za-z0-9_-]{10,}","[密钥已隐藏]")
            .replaceAll("(?i)(?:密码|口令|授权码|API.?KEY|TOKEN)\\s*[:：=]\\s*[^\\s，。；]+","[敏感凭据已隐藏]")
            .replaceAll("(?:微信|QQ|住址|收货地址|联系地址|详细地址)\\s*[:：=]\\s*[^\\n，。；]+","[联系资料已隐藏]");
    }
    private static String value(String text){return text==null?"":text;}
    private static String limit(String text,int max){return text.length()>max?text.substring(0,max):text;}
    public static String digest(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    public Map<String,String> tables() { return Map.of("PRODUCT","products","PRODUCT_COMMENT","product_comments","ORDER_REVIEW","community_order_ratings",
            "DEMAND_POST","community_demand_posts","DEMAND_REPLY","community_demand_replies","REPORT","community_reports","AFTERSALE","aftersales","SUPPORT_TICKET","support_tickets"); }
}
