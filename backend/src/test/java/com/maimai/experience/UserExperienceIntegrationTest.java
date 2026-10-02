package com.maimai.experience;

import com.maimai.catalog.service.*;
import com.maimai.catalog.controller.SellerTasksController;
import com.maimai.catalog.dto.CatalogDtos.ProductUpdateRequest;
import com.maimai.common.*;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.community.dto.CommunityDtos.RatingRequest;
import com.maimai.community.service.*;
import com.maimai.notification.*;
import com.maimai.support.dto.SupportDtos.CreateTicket;
import com.maimai.support.service.SupportTicketService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Statement;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Exercises persisted owner isolation, conflict handling and links with real MySQL. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserExperienceIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired JsonMapper json;
    @Autowired ListingDraftService drafts;
    @Autowired ProductSpecifications specifications;
    @Autowired SellerProductService products;
    @Autowired ProductQueryService catalog;
    @Autowired ProductRevisionService revisions;
    @Autowired PersonalMediaService media;
    @Autowired DraftImageAttachmentService attachments;
    @Autowired RatingService ratings;
    @Autowired RatingDetailsService ratingDetails;
    @Autowired ReportService reports;
    @Autowired ReportContextService reportContexts;
    @Autowired SearchSubscriptionService searches;
    @Autowired SupportTicketService tickets;
    @Autowired NotificationService notifications;
    @Autowired InboxController inbox;
    @Autowired SellerTasksController tasks;
    @Autowired com.maimai.catalog.controller.ListingDraftController draftController;
    @Autowired PersonalMediaController mediaController;
    long buyer,seller,other,category,child,product,order;
    String orderNo;

    @BeforeEach void setup(){
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("maimai_test");
        buyer=user("体验买家","USER");seller=user("体验卖家","SELLER");other=user("其他买家","USER");
        category=insert("INSERT INTO categories(name,status) VALUES('生活家居','ACTIVE')");
        child=insert("INSERT INTO categories(name,parent_id,status) VALUES('书桌',?,'ACTIVE')",category);
        db.update("INSERT INTO seller_applications(user_id,status,channel_status,intro) VALUES(?,'APPROVED','QUALIFIED','测试履约资料')",seller);
        product=insert("INSERT INTO products(seller_id,category_id,title,description,item_condition,price_cents,stock_available,region,delivery_methods,freight_cents,status) VALUES(?,?,'木书桌','已说明边角磨损','GOOD',10000,2,'上海','EXPRESS',500,'ON_SALE')",seller,child);
        orderNo="UX"+UUID.randomUUID().toString().replace("-","").substring(0,28);
        order=insert("INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,goods_amount_cents,freight_cents,total_cents,platform_fee_cents,fulfillment_status,pay_status,refund_status,expires_at,completed_at) VALUES(?,1,?,?,'EXPRESS',10000,500,10500,3,'COMPLETED','PAID','NONE',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),UTC_TIMESTAMP(6))",orderNo,buyer,seller);
        db.update("INSERT INTO order_items(order_id,product_id,title,item_condition,price_cents,quantity) VALUES(?,?,'书桌','GOOD',10000,1)",order,product);
        db.update("INSERT INTO payment_requests(pay_no,order_id,amount_cents,channel,status,simulated) VALUES(?,?,10500,'MOCK','PAID',1)","UP"+UUID.randomUUID().toString().replace("-","").substring(0,28),order);
        login(buyer,"USER");
    }
    @AfterEach void logout(){SecurityContextHolder.clearContext();}
    @Test void incompleteDraftIsPrivateAndStaleDeviceCannotOverwriteOrResurrectClearedDraft(){
        var body=json.readTree("{\"schema\":1,\"form\":{\"title\":\"只填一半\",\"priceYuan\":\"\"},\"imageIds\":[]}");
        var first=drafts.save(buyer,"new",0,body);
        assertThat(first.version()).isEqualTo(1);assertThat(drafts.get(buyer,"new").payload().path("form").path("title").asText()).isEqualTo("只填一半");
        assertThat(drafts.get(other,"new").version()).isZero();
        code("DRAFT_CONFLICT",()->drafts.save(buyer,"new",0,body));
        drafts.save(buyer,"new",1,null);
        code("DRAFT_CONFLICT",()->drafts.save(buyer,"new",1,body));
        assertThat(drafts.get(buyer,"new").payload().isNull()).isTrue();
        code("NOT_FOUND",()->drafts.get(buyer,Long.toString(product)));
    }
    @Test void cloudDraftRejectsOtherUsersImagesAndForeignProductBinding() throws Exception {
        var image=media.upload(seller,"DRAFT",photo());
        code("NOT_FOUND",()->drafts.save(buyer,"new",0,json.readTree("{\"schema\":1,\"form\":{},\"imageIds\":[\""+image.id()+"\"]}")));
        code("NOT_FOUND",()->drafts.save(buyer,"new",0,json.readTree("{\"schema\":1,\"form\":{},\"productId\":"+product+"}")));
        code("NOT_FOUND",()->media.mine(buyer,image.id()));
    }
    @Test void attachingDraftPhotosIsOwnedAndIdempotentAndStartsReview() throws Exception {
        login(seller,"SELLER");var image=media.upload(seller,"DRAFT",photo());
        attachments.attach(seller,product,List.of(image.id()));attachments.attach(seller,product,List.of(image.id()));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id=?",Long.class,product)).isEqualTo(1);
        assertThat(catalog.detail(product).status()).isEqualTo("CHANGES_REVIEW");
    }
    @Test void categoryParametersSurviveDetailsAndAuditsAndRequireRereview(){
        login(seller,"SELLER");assertThat(specifications.fields(child)).extracting(ProductSpecifications.Field::key).contains("dimensions","transport");
        products.update(seller,product,new ProductUpdateRequest("木书桌",child,"已说明边角磨损","GOOD",null,10000L,null,"上海",List.of("EXPRESS"),500L,null,List.of(),null,null,Map.of("dimensions","120 × 60 × 75 cm","transport","有电梯，可协助搬运")));
        assertThat(catalog.detail(product).specifications()).containsEntry("dimensions","120 × 60 × 75 cm");
        assertThat(catalog.detail(product).status()).isEqualTo("CHANGES_REVIEW");
        assertThat(revisions.list(product,0,10,false).content().getFirst().content().specifications()).containsKey("transport");
        code("SPECIFICATION_FIELD",()->specifications.encode(child,Map.of("battery","90%")));
    }
    @Test void sellerTasksUseActualOrderAndAfterSaleDeadlines(){
        db.update("UPDATE orders SET fulfillment_status='PAID_PENDING_SHIP',ship_deadline=UTC_TIMESTAMP()+INTERVAL 2 HOUR WHERE id=?",order);
        insert("INSERT INTO bargain_offers(product_id,buyer_id,quantity,offer_price_cents,expires_at) VALUES(?,?,1,9000,UTC_TIMESTAMP()+INTERVAL 1 HOUR)",product,buyer);
        insert("INSERT INTO aftersales(aftersale_no,order_id,buyer_id,type,reason,status,return_inspection_deadline) VALUES(?,?,?,'RETURN_REFUND','测试退货','RETURN_SHIPPED',UTC_TIMESTAMP()+INTERVAL 3 HOUR)","UA"+UUID.randomUUID().toString().substring(0,20),order,buyer);
        login(seller,"SELLER");var result=tasks.mine();
        assertThat(result.counts()).containsEntry("SHIP",1L).containsEntry("BARGAIN",1L).containsEntry("AFTERSALE",1L).containsEntry("MESSAGE",0L);
        assertThat(result.items().getFirst().kind()).isEqualTo("BARGAIN");assertThat(result.items()).anyMatch(t->t.targetPath().equals("/orders/"+orderNo));
        login(other,"USER");assertThat(tasks.mine().items()).isEmpty();
    }
    @Test void richReviewIncludesOnlyOwnedImagesAndAppendOnlyFollowup() throws Exception {
        var image=media.upload(buyer,"REVIEW",photo());
        var rating=ratings.create(buyer,order,new RatingRequest(4,"边角与描述一致",4,5,4,List.of(image.id())));
        var detail=ratingDetails.detail(rating.id());assertThat(detail.communication()).isEqualTo(5);assertThat(detail.images()).hasSize(1);
        assertThat(media.review(rating.id(),image.id())).isNotEmpty();
        code("NOT_FOUND",()->ratingDetails.followup(other,rating.id(),"冒用他人评价"));
        assertThat(ratingDetails.followup(buyer,rating.id(),"使用两周，桌面稳定").followup()).contains("两周");
        code("FOLLOWUP_EXISTS",()->ratingDetails.followup(buyer,rating.id(),"重复追评"));
        assertThat(ratings.orderRatings(order,0,20).items().getFirst().comment()).isEqualTo("边角与描述一致");
        db.update("UPDATE community_order_ratings SET is_hidden=1 WHERE id=?",rating.id());
        code("NOT_FOUND",()->ratingDetails.detail(rating.id()));code("NOT_FOUND",()->media.review(rating.id(),image.id()));
    }
    @Test void reportedFollowupAndImageEvidenceReachModerationSnapshot() throws Exception {
        var image=media.upload(buyer,"REVIEW",photo());
        var rating=ratings.create(buyer,order,new RatingRequest(4,"收到的商品外观一致",4,5,4,List.of(image.id())));
        ratingDetails.followup(buyer,rating.id(),"使用后发现新的问题，请核查");
        login(other,"USER");
        var report=reports.create(other,new com.maimai.community.dto.CommunityDtos.ReportCreateRequest("ORDER_REVIEW",rating.id(),"核查追加评价中的争议信息"));
        long moderator=user("体验审核员","OPERATOR");login(moderator,"OPERATOR");
        var context=reportContexts.detail(report.id());
        assertThat(context.snapshotSaved()).isTrue();
        assertThat(context.sourceText()).contains("收到的商品外观一致","追加评价：使用后发现新的问题","附图1张","不识别图片");
        assertThat(context.targetUrl()).isEqualTo("/sellers/"+seller+"#rating-"+rating.id());
    }
    @Test void legacyReviewsDoNotGetInventedDimensions(){
        var rating=ratings.create(buyer,order,new RatingRequest(4,"正常交付"));
        assertThat(ratingDetails.detail(rating.id()).description()).isNull();
        assertThat(ratingDetails.detail(rating.id()).images()).isEmpty();
    }
    @Test void privateReviewImageCannotBeAttachedByAnotherAccount() throws Exception {
        var image=media.upload(other,"REVIEW",photo());
        code("NOT_FOUND",()->ratings.create(buyer,order,new RatingRequest(5,"评价",5,5,5,List.of(image.id()))));
    }
    @Test void savedSearchMatchesDescendantsDeduplicatesAndCanPause(){
        long watch=searches.create(buyer,new SearchSubscriptionService.Input("找书桌","书桌",category,9000L,12000L,"上海","GOOD","EXPRESS")).getFirst().id();
        searches.check(watch);assertThat(matchCount()).isZero(); // Existing unchanged stock is not a new listing.
        db.update("UPDATE products SET updated_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND WHERE id=?",product);
        searches.check(watch);searches.check(watch);
        assertThat(matchCount()).isEqualTo(1);
        assertThat(inbox.list("DISCOVERY",false,0).items().getFirst().targetPath()).isEqualTo("/products/"+product);
        searches.enabled(buyer,watch,false);assertThat(searches.due()).doesNotContain(watch);
        code("NOT_FOUND",()->searches.enabled(other,watch,true));code("NOT_FOUND",()->searches.remove(other,watch));
    }
    @Test void savedSearchDoesNotRecommendHiddenOrUnavailableStock(){
        long watch=searches.create(buyer,new SearchSubscriptionService.Input("书桌",null,category,null,null,null,null,null)).getFirst().id();
        db.update("UPDATE products SET updated_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND,stock_available=0 WHERE id=?",product);searches.check(watch);assertThat(matchCount()).isZero();
        db.update("UPDATE products SET stock_available=1,status='OFF_SHELF' WHERE id=?",product);searches.check(watch);assertThat(matchCount()).isZero();
        db.update("UPDATE products SET status='ON_SALE' WHERE id=?",product);db.update("UPDATE categories SET status='DISABLED' WHERE id=?",category);searches.check(watch);assertThat(matchCount()).isZero();
    }
    @Test void overlappingSearchesDoNotSendDuplicateNotifications(){
        var one=searches.create(buyer,new SearchSubscriptionService.Input("找书桌","书桌",category,null,null,null,null,null)).getFirst();
        var two=searches.create(buyer,new SearchSubscriptionService.Input("找家具",null,category,null,null,null,null,null)).getFirst();
        db.update("UPDATE products SET updated_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND WHERE id=?",product);
        searches.check(one.id());searches.check(two.id());
        assertThat(matchCount()).isEqualTo(1);
    }
    @Test void staleBrowserCannotSaveDraftOrUploadPhotoUnderAnotherAccount()throws Exception{
        code("DRAFT_ACCOUNT_CHANGED",()->draftController.save("new",new com.maimai.catalog.controller.ListingDraftController.Save(0,json.readTree("{\"schema\":1,\"form\":{}}"),seller)));
        code("MEDIA_ACCOUNT_CHANGED",()->mediaController.upload("DRAFT",seller,photo()));
        assertThat(drafts.get(buyer,"new").version()).isZero();
    }
    @Test void handoffCopiesOnlyExplicitlyChosenOwnedCompletedTurns(){
        String selected=turn(buyer,"希望确认售后时限"),unselected=turn(buyer,"不希望分享的内容"),foreign=turn(other,"别人的内容");
        var ticket=tickets.create(new CreateTicket("人工核对售后","请帮我核对这笔订单",orderNo,List.of(selected)));
        var messages=tickets.messages(ticket.id(),0,20).items();
        assertThat(messages).hasSize(3);assertThat(messages).anyMatch(m->m.body().contains("希望确认售后时限"));assertThat(messages).noneMatch(m->m.body().contains("不希望分享的内容"));
        code("NOT_FOUND",()->tickets.create(new CreateTicket("越权测试","不得分享他人问答",null,List.of(foreign))));
        assertThat(inbox.list("SERVICE",false,0).items()).anyMatch(n->n.targetPath().equals("/support/tickets/"+ticket.id()));
    }
    @Test void notificationsCannotExposeForeignOrdersOrExternalLinks(){
        notifications.notify(buyer,"ORDER","订单已发货","订单 "+orderNo+" 已发货");
        notifications.notify(buyer,"SYSTEM","测试","无跳转","https://example.test/phishing");
        notifications.notify(other,"ORDER","无关账号通知","订单 "+orderNo+" 不属于该账号");
        var entries=inbox.list("ALL",false,0).items();assertThat(entries).hasSize(2);assertThat(entries).anyMatch(e->("/orders/"+orderNo).equals(e.targetPath()));assertThat(entries).noneMatch(e->e.targetPath()!=null&&e.targetPath().startsWith("http"));
        login(other,"USER");assertThat(inbox.list("TRADE",false,0).items().getFirst().targetPath()).isNull();
    }
    private long matchCount(){return db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='SEARCH_MATCH'",Long.class,buyer);}
    private String turn(long user,String question){String id=UUID.randomUUID().toString();db.update("INSERT INTO support_ai_turns(owner_id,request_id,attempt_id,question,answer,status) VALUES(?,?,?,?,?,'COMPLETE')",user,id,UUID.randomUUID().toString(),question,"可以联系人工核实具体订单");return id;}
    private MockMultipartFile photo()throws Exception{var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);return new MockMultipartFile("file","photo.png","image/png",bytes.toByteArray());}
    private long user(String name,String role){long id=insert("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-hash',?,'ACTIVE')",UUID.randomUUID()+"@ux.test",name);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)",id,role);return id;}
    private long insert(String sql,Object...args){var key=new GeneratedKeyHolder();db.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    private void login(long id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id,"local-test","核查主体",Set.of(role)),null,List.of()));}
    private void code(String code,org.assertj.core.api.ThrowableAssert.ThrowingCallable action){assertThatThrownBy(action).isInstanceOf(BizException.class).extracting(e->((BizException)e).getCode()).isEqualTo(code);}
}
