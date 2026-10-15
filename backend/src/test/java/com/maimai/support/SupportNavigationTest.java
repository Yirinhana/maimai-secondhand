package com.maimai.support;

import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.support.ai.SupportAiGateway;
import com.maimai.support.chat.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class SupportNavigationTest {
    @Autowired SupportNavigation navigation;
    @Autowired TinaChatStore store;
    @Autowired JdbcTemplate db;
    long buyer,seller,category;
    @BeforeEach void setup(){
        buyer=user();seller=user();approve(seller);approve(buyer);
        db.update("INSERT INTO categories(name,status) VALUES('客服检索测试','ACTIVE')");category=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        var principal=new AuthenticatedUser(buyer,"nav@example.invalid","导航验收",Set.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void productsRequireAvailableStockActiveSellerApprovalAndBudget(){
        var good=product(seller,"导航外套",18000,"ON_SALE",2);
        product(seller,"导航外套贵",30000,"ON_SALE",1);
        product(seller,"导航外套没货",10000,"ON_SALE",0);
        product(seller,"导航外套待审",10000,"PENDING_REVIEW",1);
        product(buyer,"导航外套自己的",10000,"ON_SALE",1);
        long disabled=user();approve(disabled);db.update("UPDATE users SET status='DISABLED' WHERE id=?",disabled);product(disabled,"导航外套停用",10000,"ON_SALE",1);
        long pending=user();approve(pending);db.update("UPDATE seller_applications SET status='PENDING' WHERE user_id=?",pending);product(pending,"导航外套待认证",10000,"ON_SALE",1);
        var reply=navigation.resolve("找“导航外套”，预算200元以内").orElseThrow();
        assertThat(reply.resources().stream().filter(x->x.kind().equals("PRODUCT"))).extracting(SupportNavigation.Resource::path).containsExactly("/products/"+good);
        assertThat(reply.resources().getFirst().priceCents()).isEqualTo(18000L);
        assertThat(reply.resources().getLast().path()).contains("maxPrice=200.00");
    }
    @Test void zeroResultsDoNotInventGoodsOrIgnoreQuotedKeyword(){
        var reply=navigation.resolve("找“没有这件物品_73%”，预算10元以内").orElseThrow();
        assertThat(reply.answer()).contains("暂时没有找到");
        assertThat(reply.resources()).noneMatch(x->x.kind().equals("PRODUCT"));
        assertThat(reply.resources()).extracting(SupportNavigation.Resource::path).contains("/community/demands");
    }
    @Test void wildcardAndSqlContentAreLiteralSearchData(){
        product(seller,"导航标题",100,"ON_SALE",1);
        var reply=navigation.resolve("找“%' OR 1=1 --”").orElseThrow();
        assertThat(reply.resources()).noneMatch(x->x.kind().equals("PRODUCT"));
    }
    @Test void siteAnswersPersistReplayAndNeedNoModel(){
        SupportAiGateway offline=topic->{throw new AssertionError("No model calls for navigation");};
        var service=new TinaChatService(store,offline,new SimpleRateLimiter(),navigation);
        String key=UUID.randomUUID().toString();
        var answer=service.send(key,"我想看用户协议和隐私条款");
        assertThat(answer.source()).isEqualTo("SITE");
        assertThat(answer.resources()).extracting(SupportNavigation.Resource::path).containsExactly("/policies");
        assertThat(service.send(key,"我想看用户协议和隐私条款").id()).isEqualTo(answer.id());
        assertThat(store.history()).hasSize(1);
        assertThat(store.history().getFirst().resources()).isEqualTo(answer.resources());
    }
    @Test void ordinaryConversationIsNotMistakenForShopping(){
        assertThat(navigation.resolve("我需要一点帮助，今天心情不好")).isEmpty();
        assertThat(navigation.resolve("不要推荐外套")).isEmpty();
    }
    @Test void refundReplyProvidesOwnedEntryAndDoesNotClaimExecution(){
        var reply=navigation.resolve("我要退款").orElseThrow();
        assertThat(reply.resources()).extracting(SupportNavigation.Resource::path).contains("/orders","/policies","/support");
        assertThat(reply.answer()).doesNotContain("已退款","已提交");
    }
    private long user(){String email=UUID.randomUUID()+"@example.invalid";db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test','导航验收','ACTIVE')",email);return db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);}
    private void approve(long user){db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'SELLER')",user);db.update("INSERT INTO seller_applications(user_id,status) VALUES(?,'APPROVED')",user);}
    private long product(long user,String title,long price,String status,int stock){
        db.update("INSERT INTO products(seller_id,category_id,title,item_condition,price_cents,stock_available,region,delivery_methods,status) VALUES(?, ?, ?, 'GOOD', ?, ?, '上海市', 'EXPRESS', ?)",user,category,title,price,stock,status);
        return db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
}
