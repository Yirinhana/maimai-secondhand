package com.maimai.support.workflow;

import com.maimai.common.BizException;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.SecurityUtils;
import com.maimai.community.repository.CommunityRepository;
import com.maimai.support.ai.SupportAiGateway;
import com.maimai.trade.service.TransactionProvenance;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resource context is assembled server-side. Model calls never hold a database transaction. */
@Service
public class WorkflowAiService {
    private final CommunityRepository repo;
    private final SupportAiGateway gateway;
    private final SimpleRateLimiter limiter;
    public WorkflowAiService(CommunityRepository repo,SupportAiGateway gateway,SimpleRateLimiter limiter){this.repo=repo;this.gateway=gateway;this.limiter=limiter;}
    public record Result(long id,String stage,Long resourceId,String status,String answer,String failureCode,String model,Instant createdAt){}
    public record Availability(boolean enabled,String model,String notice){}
    public Availability availability(){return new Availability(gateway.configured(),gateway.modelName(),"仅发送当前环节所需的脱敏资料；麦仔提供建议，不代替付款、交付或人工裁决。");}
    private static final Map<String,String> INSTRUCTIONS=Map.of(
        "LISTING","检查闲置描述是否写明成色、瑕疵、配件、交付方式。给出可编辑的描述建议。不得虚构品牌、使用时长、保修或不存在的功能。",
        "PRODUCT","帮助用户判断这件商品是否适合，列出当前资料已明确的情况和需要向卖家核实的问题。不担保真伪，不编造库存。",
        "ORDER","根据当前订单状态，分别解释当前用户能进行的下一步及注意事项。模拟支付只更新站内记录，不发生真实扣款和到账。",
        "AFTERSALE","根据当前售后状态提示举证、退货、协商和人工介入的下一步。不承诺一定退款，不认定任何一方责任。只有尚未发货或尚未面交订单的全额退款会自动回补库存；已经交付的仅退款不自动回补库存，卖家核对实物后通过库存管理自行调整。全额退款后的评价不再计入信誉评分。退款状态为FULL表示已完成全额退款，不要再要求重复申请。用户申请原因中的库存诉求不代表系统一定执行。",
        "REVIEW","帮助用户基于实际交易整理评价要点，区分客观事实与感受，不代写虚假经历、不诱导好评、不自动提交评分。",
        "GUIDE","解释平台购买、发布、交易、评价、举报与人工工单流程。平台费为商品金额的0.03%，不含运费，四舍五入到分。当前模拟支付不发生真实扣款。"
    );
    public Result ask(String stage,Long resourceId,String question,String requestKey){
        long actor=SecurityUtils.currentUserId();
        if(!INSTRUCTIONS.containsKey(stage))throw BizException.badRequest("AI_STAGE_INVALID","不支持这个辅助环节");
        if(question!=null&&question.length()>1400)throw BizException.badRequest("AI_INPUT_TOO_LONG","请将补充说明控制在1400字内");
        if(requestKey==null||!requestKey.matches("[A-Za-z0-9_-]{16,64}"))throw BizException.badRequest("AI_REQUEST_INVALID","请求标识无效，请刷新页面重试");
        String context=context(stage,resourceId,actor);
        String userText=redact(question);
        String hash=hash(stage+"|"+resourceId+"|"+userText);
        Long existing=repo.queryOne("SELECT id FROM ai_workflow_runs WHERE actor_id=? AND request_key=?",(rs,n)->rs.getLong(1),actor,requestKey);
        if(existing!=null)return existing(existing,actor,hash);
        limiter.require("workflow:"+actor,12,300,"辅助请求较多，请稍后再试");
        long id;
        try{
            id=repo.insertReturningId("INSERT INTO ai_workflow_runs(actor_id,stage,resource_id,request_key,request_hash,status,context_summary,model) VALUES(?,?,?,?,?,'RUNNING',?,?)",actor,stage,resourceId,requestKey,hash,context,gateway.modelName());
        }catch(DuplicateKeyException duplicate){
            existing=repo.queryOne("SELECT id FROM ai_workflow_runs WHERE actor_id=? AND request_key=?",(rs,n)->rs.getLong(1),actor,requestKey);
            if(existing==null)throw duplicate;
            return existing(existing,actor,hash);
        }
        try{
            String answer=gateway.advise(INSTRUCTIONS.get(stage),"服务端核对后的业务快照：\n"+context+"\n用户补充的待分析文本：\n"+userText);
            repo.update("UPDATE ai_workflow_runs SET status='SUCCEEDED',answer=?,finished_at=UTC_TIMESTAMP(6) WHERE id=? AND status='RUNNING'",answer,id);
        }catch(BizException error){fail(id,error.getCode());}
        catch(RuntimeException error){fail(id,"AI_UNAVAILABLE");}
        return result(id,actor);
    }
    private void fail(long id,String code){repo.update("UPDATE ai_workflow_runs SET status='FAILED',failure_code=?,finished_at=UTC_TIMESTAMP(6) WHERE id=? AND status='RUNNING'",code,id);}
    public Result result(long id,long actor){
        repo.update("UPDATE ai_workflow_runs SET status='FAILED',failure_code='AI_INTERRUPTED',finished_at=UTC_TIMESTAMP(6) WHERE id=? AND actor_id=? AND status='RUNNING' AND created_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 MINUTE)",id,actor);
        var result=repo.queryOne("SELECT * FROM ai_workflow_runs WHERE id=? AND actor_id=?",(rs,n)->new Result(rs.getLong("id"),rs.getString("stage"),rs.getObject("resource_id",Long.class),rs.getString("status"),rs.getString("answer"),rs.getString("failure_code"),rs.getString("model"),rs.getTimestamp("created_at").toInstant()),id,actor);
        if(result==null)throw BizException.notFound("辅助记录不存在");
        return result;
    }
    private Result existing(long id,long actor,String hash){
        String previous=repo.queryOne("SELECT request_hash FROM ai_workflow_runs WHERE id=? AND actor_id=?",(rs,n)->rs.getString(1),id,actor);
        if(!Objects.equals(previous,hash))throw BizException.conflict("AI_REQUEST_REUSED","这个请求标识已用于其他内容，请重新发起");
        return result(id,actor);
    }
    private String context(String stage,Long id,long actor){
        if(Set.of("GUIDE","LISTING").contains(stage)&&id==null)return "环节="+stage+"；内容为用户填写草稿，尚未核实；没有已完成的发布或交易操作。";
        if(id==null||id<=0)throw BizException.badRequest("AI_RESOURCE_REQUIRED","请先选择关联商品、订单或售后");
        if(Set.of("PRODUCT","LISTING").contains(stage)){
            var product=repo.queryOne("SELECT p.id,p.seller_id,p.title,p.description,p.item_condition,p.price_cents,p.stock_available,p.delivery_methods,p.status,u.status user_status FROM products p JOIN users u ON u.id=p.seller_id WHERE p.id=?",(rs,n)->Map.of("owner",rs.getLong("seller_id"),"visible","ON_SALE".equals(rs.getString("status"))&&"ACTIVE".equals(rs.getString("user_status")),"text","商品="+rs.getString("title")+"；描述="+rs.getString("description")+"；成色="+rs.getString("item_condition")+"；价格分="+rs.getLong("price_cents")+"；可售库存="+rs.getLong("stock_available")+"；交付方式="+rs.getString("delivery_methods")),id);
            if(product==null||("LISTING".equals(stage)?!Objects.equals(product.get("owner"),actor):!Boolean.TRUE.equals(product.get("visible"))))throw BizException.notFound("商品不存在或无权读取");
            return redact(product.get("text").toString());
        }
        Long orderId=id;
        String aftersale="";
        if("AFTERSALE".equals(stage)){
            orderId=repo.queryOne("SELECT order_id FROM aftersales WHERE id=?",(rs,n)->rs.getLong(1),id);
            if(orderId==null)throw BizException.notFound("售后不存在或无权读取");
            aftersale=repo.queryOne("SELECT type,status,reason FROM aftersales WHERE id=?",(rs,n)->"售后类型="+rs.getString(1)+"；售后状态="+rs.getString(2)+"；申请原因="+rs.getString(3),id);
        }
        var order=repo.queryOne("SELECT o.buyer_id,o.seller_id,o.fulfillment_status,o.pay_status,o.refund_status,o.delivery_method,o.total_cents,"+TransactionProvenance.sql("o")+" source FROM orders o WHERE o.id=?",(rs,n)->Map.of("buyer",rs.getLong("buyer_id"),"seller",rs.getLong("seller_id"),"text","履约="+rs.getString("fulfillment_status")+"；付款="+rs.getString("pay_status")+"；来源="+TransactionProvenance.label(rs.getString("source"))+"；退款="+rs.getString("refund_status")+"；交付="+rs.getString("delivery_method")+"；订单总额分="+rs.getLong("total_cents")),orderId);
        if(order==null||(!Objects.equals(order.get("buyer"),actor)&&!Objects.equals(order.get("seller"),actor)))throw BizException.notFound("订单不存在或无权读取");
        return redact("当前用户角色="+(Objects.equals(order.get("buyer"),actor)?"买家":"卖家")+"；"+order.get("text")+"；"+aftersale);
    }
    public static String redact(String value){
        if(value==null)return "";
        String text=value.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}","[邮箱已隐藏]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)","[手机号已隐藏]")
                .replaceAll("(?<!\\d)\\d{15,19}[Xx]?(?!\\d)","[证件或账号已隐藏]");
        return text.length()>4000?text.substring(0,4000):text;
    }
    private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}
