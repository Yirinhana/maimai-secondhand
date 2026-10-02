package com.maimai.catalog.service;

import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Set;

/** Incomplete forms are separate from actual products; compare-and-set prevents cross-device loss. */
@Service
@Transactional
public class ListingDraftService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public ListingDraftService(JdbcTemplate jdbc,JsonMapper json){this.jdbc=jdbc;this.json=json;}
    public record Draft(long version,JsonNode payload,Instant updatedAt) {}
    private void authorize(long user,String key){
        if(!key.matches("new|[1-9][0-9]{0,17}"))throw BizException.badRequest("DRAFT_KEY","草稿编号无效");
        if(!key.equals("new")&&jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=? AND seller_id=?",Long.class,Long.parseLong(key),user)!=1)throw BizException.notFound("商品不存在");
    }
    public Draft get(long user,String key){
        authorize(user,key);
        return jdbc.query("SELECT * FROM listing_drafts WHERE user_id=? AND draft_key=?",(r,n)->new Draft(r.getLong("version"),json.readTree(r.getString("payload")),r.getTimestamp("updated_at").toInstant()),user,key).stream().findFirst().orElse(new Draft(0,null,null));
    }
    public Draft save(long user,String key,long version,JsonNode payload){
        authorize(user,key);
        // Serialize creation and updates for this account, including the initially missing draft row.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user);
        if(version<0||version!=get(user,key).version())throw BizException.conflict("DRAFT_CONFLICT","另一台设备更新了草稿，请选择保留哪份内容");
        String value=json.writeValueAsString(payload);
        if(value.length()>50000)throw BizException.badRequest("DRAFT_SIZE","草稿文字过多，请适当缩短");
        if(payload!=null&&!payload.isNull()){
            if(!payload.isObject()||!payload.path("form").isObject())throw BizException.badRequest("DRAFT_INVALID","草稿格式不正确");
            var allowed=Set.of("schema","form","limitShipping","imageIds","productId");
            for(String field:payload.propertyNames())if(!allowed.contains(field))throw BizException.badRequest("DRAFT_INVALID","草稿包含不支持的字段");
            var images=payload.path("imageIds");
            if(!images.isMissingNode()&&(!images.isArray()||images.size()>9))throw BizException.badRequest("DRAFT_IMAGES","草稿图片最多9张");
            for(var id:images)if(!id.isTextual()||jdbc.queryForObject("SELECT COUNT(*) FROM personal_media WHERE id=? AND owner_id=? AND purpose='DRAFT'",Long.class,id.asText(),user)!=1)throw BizException.notFound("草稿图片不存在或不属于当前账号");
            var product=payload.path("productId");
            if(!product.isMissingNode()&&!product.isNull()&&(!product.canConvertToLong()||jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=? AND seller_id=?",Long.class,product.asLong(),user)!=1))throw BizException.notFound("关联的商品草稿不属于当前账号");
        }
        jdbc.update("INSERT INTO listing_drafts(user_id,draft_key,payload,version,updated_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE payload=VALUES(payload),version=VALUES(version),updated_at=UTC_TIMESTAMP(6)",user,key,value,version+1);
        return get(user,key);
    }
}
