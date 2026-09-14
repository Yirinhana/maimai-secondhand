package com.maimai.identity.service;

import com.maimai.admin.service.GovernanceGuard;
import com.maimai.common.BizException;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.dto.AccountClosureDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.util.Objects;

@Service
public class AccountClosureService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final SimpleRateLimiter limiter;
    private final GovernanceGuard guard;
    public AccountClosureService(JdbcTemplate db,PasswordEncoder passwords,SimpleRateLimiter limiter,GovernanceGuard guard) {this.db=db;this.passwords=passwords;this.limiter=limiter;this.guard=guard;}
    @Transactional
    public ClosureResult request(ClosureRequest request) {
        long actor=SecurityUtils.currentUserId();limiter.require("account-closure:"+actor,5,3600,"注销尝试过于频繁，请稍后再试");
        if(request==null||request.reason()==null||request.reason().isBlank()||request.reason().length()>500||request.password()==null||request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw BizException.badRequest("CLOSURE_INPUT_INVALID","请填写当前密码和500字以内的注销原因");
        guard.accounts();
        var users=db.query("SELECT email,password_hash,status FROM users WHERE id=? FOR UPDATE",(rs,n)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3)},actor);
        if(users.isEmpty()||!"ACTIVE".equals(users.getFirst()[2])) throw BizException.unauthorized("账号不可用");
        if(!passwords.matches(request.password(),users.getFirst()[1])) throw BizException.badRequest("PASSWORD_INVALID","当前密码不正确");
        guard.protectLastAdmin(actor);
        // Recent completions retain access to the approved 15-day ordinary aftersales period.
        if(count("""
                SELECT COUNT(*) FROM orders WHERE (buyer_id=? OR seller_id=?) AND
                (fulfillment_status NOT IN ('COMPLETED','CLOSED') OR pay_status='PAYING'
                 OR (fulfillment_status='COMPLETED' AND (completed_at IS NULL OR completed_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 15 DAY))))
                """,actor,actor)>0) throw blocked();
        if(count("SELECT COUNT(*) FROM aftersales a JOIN orders o ON o.id=a.order_id WHERE (o.buyer_id=? OR o.seller_id=?) AND a.status NOT IN ('RESOLVED','CLOSED')",actor,actor)>0) throw blocked();
        if(count("SELECT COUNT(*) FROM refunds r JOIN orders o ON o.id=r.order_id WHERE (o.buyer_id=? OR o.seller_id=?) AND r.status IN ('REQUESTED','PROCESSING')",actor,actor)>0) throw blocked();
        var key=new GeneratedKeyHolder();
        db.update(c->{var p=c.prepareStatement("INSERT INTO account_closure_requests(user_id,status,reason) VALUES (?,'DISABLED',?)",Statement.RETURN_GENERATED_KEYS);p.setLong(1,actor);p.setString(2,request.reason().trim());return p;},key);
        db.update("UPDATE users SET status='DISABLED',updated_at=UTC_TIMESTAMP(6) WHERE id=?",actor);
        db.update("UPDATE products SET status='OFF_SHELF',updated_at=UTC_TIMESTAMP(6) WHERE seller_id=? AND status IN ('ON_SALE','PENDING_REVIEW','CHANGES_REVIEW')",actor);
        db.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?",users.getFirst()[0]);
        return new ClosureResult(Objects.requireNonNull(key.getKey()).longValue(),"DISABLED","注销申请已记录，账号已停用且在售商品已暂停；依法留存交易记录，不代表所有数据已删除。");
    }
    private long count(String sql,Object...args) {return db.queryForObject(sql,Long.class,args);}
    private BizException blocked() {return BizException.conflict("CLOSURE_TRADE_PENDING","尚有未完成交易、处理中款项、有效售后或收货未满15天的订单，请处理完毕后再申请注销");}
}
