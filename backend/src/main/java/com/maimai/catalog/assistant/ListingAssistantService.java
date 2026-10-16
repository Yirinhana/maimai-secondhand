package com.maimai.catalog.assistant;

import com.maimai.common.BizException;
import com.maimai.common.PersonalMediaService;
import com.maimai.common.SimpleRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** User-triggered, private and budgeted photo assistance; publication remains a separate action. */
@Service
public class ListingAssistantService {
    private static final String PROMPT_VERSION="photo-draft-1";
    private final JdbcTemplate db;private final PersonalMediaService media;private final ListingVisionModel model;
    private final ListingAssistantCatalog catalog;private final SimpleRateLimiter limiter;private final JsonMapper json;
    private final TransactionTemplate tx;private final int dailyLimit,userLimit;
    public ListingAssistantService(JdbcTemplate db,PersonalMediaService media,ListingVisionModel model,ListingAssistantCatalog catalog,
            SimpleRateLimiter limiter,JsonMapper json,PlatformTransactionManager manager,
            @Value("${MAIMAI_LISTING_AI_DAILY_LIMIT:10}") int dailyLimit,@Value("${MAIMAI_LISTING_AI_USER_DAILY_LIMIT:3}") int userLimit){
        this.db=db;this.media=media;this.model=model;this.catalog=catalog;this.limiter=limiter;this.json=json;this.tx=new TransactionTemplate(manager);
        this.dailyLimit=Math.max(0,Math.min(100,dailyLimit));this.userLimit=Math.max(0,Math.min(10,userLimit));
    }
    public record Availability(boolean enabled,int remainingToday,int remainingUserToday,int maxImages){}
    public record Result(long id,String status,boolean cached,ListingVisionModel.Recognition recognition,String message){}
    private record Row(long id,String hash,String status,String result,String failure){}
    private record Start(long id,boolean call,boolean cached){}
    private LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    private int used(long scope){return Optional.ofNullable(db.queryForObject("SELECT COALESCE(SUM(calls),0) FROM listing_ai_daily_usage WHERE usage_date=? AND scope_id=?",Integer.class,today(),scope)).orElse(0);}
    public Availability availability(long user){return new Availability(model.configured(),Math.max(0,dailyLimit-used(0)),Math.max(0,userLimit-used(user)),3);}
    private void expire(){db.update("UPDATE listing_ai_runs SET status='FAILED',failure_code='LISTING_AI_INTERRUPTED',updated_at=UTC_TIMESTAMP(6) WHERE status='PROCESSING' AND created_at<UTC_TIMESTAMP(6)-INTERVAL 2 MINUTE");}
    private List<Row> rows(String where,Object...args){return db.query("SELECT id,image_hash,status,result_json,failure_code FROM listing_ai_runs WHERE "+where,(r,n)->new Row(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),args);}
    public Result result(long user,long id){
        db.update("UPDATE listing_ai_runs SET status='FAILED',failure_code='LISTING_AI_INTERRUPTED',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND user_id=? AND status='PROCESSING' AND created_at<UTC_TIMESTAMP(6)-INTERVAL 2 MINUTE",id,user);
        return result(user,id,false);
    }
    private Result result(long user,long id,boolean cached){
        var rows=rows("id=? AND user_id=?",id,user);if(rows.isEmpty())throw BizException.notFound("这次识图记录不存在");
        var row=rows.getFirst();var recognition=row.result()==null?null:json.readValue(row.result(),ListingVisionModel.Recognition.class);
        // Category editing can invalidate an earlier result without invalidating its text.
        if(recognition!=null&&recognition.categoryId()!=null){var found=recognition.categoryId();if(catalog.choices().stream().noneMatch(c->c.id()==found))recognition=new ListingVisionModel.Recognition(recognition.title(),null,recognition.description(),recognition.visibleFeatures(),recognition.checklist());}
        String message=switch(row.status()){
            case "SUCCEEDED" -> cached?"已复用这组照片的识别结果，本次不消耗模型额度。":"已整理商品草稿，请核对照片中无法确认的信息后再发布。";
            case "PROCESSING" -> "麦仔正在识别照片，请稍候，无需重复点击。";
            default -> "这次识图未完成，已填写的内容和照片仍保留。可继续手动填写，或稍后主动重试。";
        };
        return new Result(row.id(),row.status(),cached,recognition,message);
    }
    public Result recognize(long user,List<String> ids,String requestKey){
        return recognize(user,ids,requestKey,false);
    }
    public Result recognize(long user,List<String> ids,String requestKey,boolean cacheOnly){
        if(requestKey==null||!requestKey.matches("[A-Za-z0-9_-]{8,64}"))throw BizException.badRequest("LISTING_AI_REQUEST","请刷新页面后重新识图");
        if(ids==null||ids.isEmpty()||ids.size()>3||new HashSet<>(ids).size()!=ids.size()||ids.stream().anyMatch(id->id==null||!id.matches("[a-f0-9-]{36}")))throw BizException.badRequest("LISTING_AI_IMAGES","请选择一至三张不同的商品照片");
        limiter.require("listing-photo-attempt:"+user,12,300,"识图请求较多，请稍后继续");
        // Ownership is checked before lookup, cache access or spending a call.
        var images=ids.stream().map(id->media.owned(user,id,"DRAFT")).toList();
        var choices=catalog.choices();if(choices.isEmpty())throw BizException.badRequest("LISTING_AI_CATEGORIES","暂时没有可用分类，请稍后再试");
        String hash=fingerprint(images,choices);
        Start start=tx.execute(status->{
            if(db.query("SELECT id FROM users WHERE id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getLong(1),user).isEmpty())
                throw BizException.badRequest("ACCOUNT_UNAVAILABLE","当前账号暂不可用，请重新登录或联系客服");
            var existing=rows("user_id=? AND request_key=?",user,requestKey);
            if(!existing.isEmpty()){
                var row=existing.getFirst();if(!row.hash().equals(hash))throw BizException.conflict("LISTING_AI_REQUEST_CHANGED","照片或分类已变化，请重新发起识图");
                return new Start(row.id(),false,true);
            }
            var cached=rows("user_id=? AND image_hash=? AND status='SUCCEEDED' AND created_at>UTC_TIMESTAMP(6)-INTERVAL 1 DAY ORDER BY id DESC LIMIT 1",user,hash);
            if(!cached.isEmpty())return new Start(cached.getFirst().id(),false,true);
            if(cacheOnly)throw BizException.notFound("这组照片没有可复用的识图结果，本次未调用模型；可以继续手动填写");
            if(!model.configured())throw BizException.badRequest("LISTING_AI_UNAVAILABLE","图片识别尚未开启，可以先手动填写草稿和查看站内比价");
            var date=today();
            db.update("INSERT INTO listing_ai_daily_usage(usage_date,scope_id,calls) VALUES(?,0,0) ON DUPLICATE KEY UPDATE calls=calls",date);
            db.queryForObject("SELECT calls FROM listing_ai_daily_usage WHERE usage_date=? AND scope_id=0 FOR UPDATE",Integer.class,date);
            expire();
            if(used(0)>=dailyLimit||used(user)>=userLimit)throw BizException.tooMany("今日识图次数已用完，照片和草稿仍保留；站内比价可以继续使用");
            if(db.queryForObject("SELECT COUNT(*) FROM listing_ai_runs WHERE status='PROCESSING'",Long.class)>0)throw BizException.tooMany("麦仔正在识别另一组照片，请稍后再试");
            db.update("UPDATE listing_ai_daily_usage SET calls=calls+1 WHERE usage_date=? AND scope_id=0",date);
            db.update("INSERT INTO listing_ai_daily_usage(usage_date,scope_id,calls) VALUES(?,?,1) ON DUPLICATE KEY UPDATE calls=calls+1",date,user);
            var key=new GeneratedKeyHolder();
            db.update(connection->{var p=connection.prepareStatement("INSERT INTO listing_ai_runs(user_id,request_key,image_hash,image_ids_json,model,status) VALUES(?,?,?,?,?,'PROCESSING')",Statement.RETURN_GENERATED_KEYS);p.setLong(1,user);p.setString(2,requestKey);p.setString(3,hash);p.setString(4,json.writeValueAsString(ids));p.setString(5,model.modelName());return p;},key);
            return new Start(Objects.requireNonNull(key.getKey()).longValue(),true,false);
        });
        Objects.requireNonNull(start);
        if(start.call()){
            try{
                var answer=model.recognize(images,choices);
                db.update("UPDATE listing_ai_runs SET status='SUCCEEDED',result_json=?,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND user_id=? AND status='PROCESSING'",json.writeValueAsString(answer),start.id(),user);
            }catch(RuntimeException error){
                // Do not surface provider output, credentials or uncertain retry suggestions.
                db.update("UPDATE listing_ai_runs SET status='FAILED',failure_code='LISTING_AI_FAILED',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND user_id=? AND status='PROCESSING'",start.id(),user);
            }
        }
        return result(user,start.id(),start.cached());
    }
    private String fingerprint(List<byte[]> images,List<ListingVisionModel.CategoryOption> choices){
        try{
            var digest=MessageDigest.getInstance("SHA-256");digest.update((PROMPT_VERSION+"\n"+model.modelName()+"\n"+json.writeValueAsString(choices)).getBytes(StandardCharsets.UTF_8));
            // Selecting the same photos in a different order must not spend another model call.
            var photoHashes=new ArrayList<String>();
            for(var image:images)photoHashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image)));
            Collections.sort(photoHashes);
            for(var photoHash:photoHashes)digest.update(photoHash.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
