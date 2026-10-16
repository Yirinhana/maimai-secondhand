package com.maimai.catalog.assistant;

import com.maimai.common.BizException;
import com.maimai.common.PersonalMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real isolated MySQL, private image storage, deterministic model; all fixtures roll back. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ListingAssistantServiceIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired ListingAssistantService service;
    @Autowired PersonalMediaService media;
    @Autowired ListingAssistantCatalog catalog;
    @MockitoBean ListingVisionModel model;
    long user,other,root,leaf;
    List<String> photos;
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    @BeforeEach void setup() throws Exception {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("maimai_test");
        user=user();other=user();
        root=insert("INSERT INTO categories(name,status) VALUES('识图测试大类','ACTIVE')");
        leaf=insert("INSERT INTO categories(name,parent_id,status) VALUES('识图测试小类',?,'ACTIVE')",root);
        photos=List.of(photo(user,"DRAFT"));
        db.update("UPDATE listing_ai_runs SET status='FAILED' WHERE status='PROCESSING'");
        db.update("INSERT INTO listing_ai_daily_usage(usage_date,scope_id,calls) VALUES(?,0,0) ON DUPLICATE KEY UPDATE calls=0",today);
        when(model.configured()).thenReturn(true);when(model.modelName()).thenReturn("controlled");
        when(model.recognize(any(),any())).thenReturn(new ListingVisionModel.Recognition("白色台灯",leaf,"照片可见白色灯罩和圆形底座",List.of("白色灯罩"),List.of("请核实功能")));
    }
    @Test void repeatedRequestAndIdenticalPhotosAreChargedOnceAndDoNotPublish(){
        String key=UUID.randomUUID().toString();var one=service.recognize(user,photos,key);
        assertThat(one.status()).isEqualTo("SUCCEEDED");assertThat(one.cached()).isFalse();
        assertThat(service.recognize(user,photos,key).id()).isEqualTo(one.id());
        assertThat(service.recognize(user,photos,UUID.randomUUID().toString()).cached()).isTrue();
        verify(model,times(1)).recognize(any(),any());assertThat(calls(0)).isEqualTo(1);assertThat(calls(user)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM products WHERE seller_id=?",user)).isZero();
        assertThat(count("SELECT COUNT(*) FROM listing_drafts WHERE user_id=?",user)).isZero();
    }
    @Test void reorderedPhotosReuseSuccessfulResultEvenAfterQuotaExhaustion() throws Exception {
        String second=media.upload(user,"DRAFT",new MockMultipartFile("file","other.jpg","image/jpeg",VisionImagePayloadTest.jpeg(5,5))).id();
        var first=service.recognize(user,List.of(photos.getFirst(),second),UUID.randomUUID().toString());
        db.update("UPDATE listing_ai_daily_usage SET calls=3 WHERE scope_id=? AND usage_date=?",user,today);
        var repeated=service.recognize(user,List.of(second,photos.getFirst()),UUID.randomUUID().toString());
        assertThat(repeated.id()).isEqualTo(first.id());assertThat(repeated.cached()).isTrue();
        verify(model,times(1)).recognize(any(),any());assertThat(calls(0)).isEqualTo(1);
    }
    @Test void photosAndSavedResultArePrivateAndReviewPhotosCannotBeUsed() throws Exception {
        String foreign=photo(other,"DRAFT"),review=photo(user,"REVIEW");
        assertThatThrownBy(()->service.recognize(user,List.of(foreign),UUID.randomUUID().toString())).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.recognize(user,List.of(review),UUID.randomUUID().toString())).isInstanceOf(BizException.class);
        assertThat(calls(0)).isZero();verify(model,never()).recognize(any(),any());
        long id=service.recognize(user,photos,UUID.randomUUID().toString()).id();
        assertThatThrownBy(()->service.result(other,id)).isInstanceOf(BizException.class).hasMessageContaining("不存在");
    }
    @Test void userAndGlobalCapsAreEnforcedBeforeAnyProviderCall(){
        db.update("INSERT INTO listing_ai_daily_usage(usage_date,scope_id,calls) VALUES(?,?,3)",today,user);
        assertThatThrownBy(()->service.recognize(user,photos,UUID.randomUUID().toString())).isInstanceOf(BizException.class).hasMessageContaining("用完");
        db.update("UPDATE listing_ai_daily_usage SET calls=0 WHERE scope_id=?",user);
        db.update("UPDATE listing_ai_daily_usage SET calls=10 WHERE scope_id=0 AND usage_date=?",today);
        assertThatThrownBy(()->service.recognize(user,photos,UUID.randomUUID().toString())).isInstanceOf(BizException.class).hasMessageContaining("用完");
        verify(model,never()).recognize(any(),any());assertThat(service.availability(user).remainingToday()).isZero();
    }
    @Test void failureCountsButDoesNotRetryTheSameRequestOrExposeProviderMessage(){
        when(model.recognize(any(),any())).thenThrow(new IllegalStateException("secret-provider-key raw output"));
        String key=UUID.randomUUID().toString();var failure=service.recognize(user,photos,key);
        assertThat(failure.status()).isEqualTo("FAILED");assertThat(failure.recognition()).isNull();
        assertThat(failure.message()).doesNotContain("secret","provider","raw");
        assertThat(service.recognize(user,photos,key).status()).isEqualTo("FAILED");
        verify(model,times(1)).recognize(any(),any());assertThat(calls(user)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT result_json FROM listing_ai_runs WHERE id=?",String.class,failure.id())).isNull();
    }
    @Test void processingAndInterruptedRequestsCannotTriggerDuplicateCharges(){
        when(model.recognize(any(),any())).thenThrow(new IllegalStateException());
        var first=service.recognize(user,photos,UUID.randomUUID().toString());
        db.update("UPDATE listing_ai_runs SET status='PROCESSING' WHERE id=?",first.id());
        assertThatThrownBy(()->service.recognize(user,photos,UUID.randomUUID().toString())).isInstanceOf(BizException.class).hasMessageContaining("另一组");
        db.update("UPDATE listing_ai_runs SET created_at=UTC_TIMESTAMP(6)-INTERVAL 3 MINUTE WHERE id=?",first.id());
        assertThat(service.result(user,first.id()).status()).isEqualTo("FAILED");
        assertThat(calls(0)).isEqualTo(1);verify(model,times(1)).recognize(any(),any());
    }
    @Test void categoryChangesInvalidateOldRequestButOldSuccessfulTextRemainsReadable(){
        String key=UUID.randomUUID().toString();var first=service.recognize(user,photos,key);
        db.update("UPDATE categories SET status='DISABLED' WHERE id=?",root);
        assertThat(service.result(user,first.id()).recognition().categoryId()).isNull();
        assertThat(service.result(user,first.id()).recognition().title()).isEqualTo("白色台灯");
        assertThat(catalog.choices()).noneMatch(c->c.id()==root||c.id()==leaf);
        assertThatThrownBy(()->service.recognize(user,photos,key)).isInstanceOf(BizException.class).hasMessageContaining("分类");
        verify(model,times(1)).recognize(any(),any());
    }
    @Test void disabledConfigBlocksFreshRecognitionButDoesNotSpendBudget(){
        when(model.configured()).thenReturn(false);
        assertThat(service.availability(user).enabled()).isFalse();
        assertThatThrownBy(()->service.recognize(user,photos,UUID.randomUUID().toString())).isInstanceOf(BizException.class).hasMessageContaining("尚未开启");
        assertThat(calls(0)).isZero();verify(model,never()).recognize(any(),any());
    }
    @Test void cacheOnlyMissNeverCallsProviderEvenIfDailyQuotaHasReset(){
        assertThatThrownBy(()->service.recognize(user,photos,UUID.randomUUID().toString(),true)).isInstanceOf(BizException.class).hasMessageContaining("没有可复用");
        verify(model,never()).recognize(any(),any());assertThat(calls(0)).isZero();
    }
    @Test void disabledAccountAndInvalidPhotoListsFailClearlyWithoutCalls(){
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",user);
        assertThatThrownBy(()->service.recognize(user,photos,UUID.randomUUID().toString())).isInstanceOf(BizException.class).hasMessageContaining("账号暂不可用");
        assertThatThrownBy(()->service.recognize(other,List.of("https://internal/secret"),UUID.randomUUID().toString())).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.recognize(other,List.of(photos.getFirst(),photos.getFirst()),UUID.randomUUID().toString())).isInstanceOf(BizException.class);
        assertThat(calls(0)).isZero();verify(model,never()).recognize(any(),any());
    }
    @Test void successfulCacheIsOwnerScopedAndExpires() throws Exception {
        var first=service.recognize(user,photos,UUID.randomUUID().toString());
        var others=List.of(photo(other,"DRAFT"));service.recognize(other,others,UUID.randomUUID().toString());
        db.update("UPDATE listing_ai_runs SET created_at=UTC_TIMESTAMP(6)-INTERVAL 25 HOUR WHERE id=?",first.id());
        var next=service.recognize(user,photos,UUID.randomUUID().toString());assertThat(next.cached()).isFalse();
        verify(model,times(3)).recognize(any(),any());assertThat(calls(user)).isEqualTo(2);
    }
    private long user(){return insert("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test','识图测试','ACTIVE')",UUID.randomUUID()+"@listing-private.invalid");}
    private String photo(long owner,String purpose)throws Exception{return media.upload(owner,purpose,new MockMultipartFile("file","test.jpg","image/jpeg",VisionImagePayloadTest.jpeg(4,4))).id();}
    private long calls(long scope){return count("SELECT COALESCE(SUM(calls),0) FROM listing_ai_daily_usage WHERE usage_date=? AND scope_id=?",today,scope);}
    private long count(String sql,Object...args){return Objects.requireNonNull(db.queryForObject(sql,Long.class,args));}
    private long insert(String sql,Object...args){var k=new GeneratedKeyHolder();db.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},k);return Objects.requireNonNull(k.getKey()).longValue();}
}
