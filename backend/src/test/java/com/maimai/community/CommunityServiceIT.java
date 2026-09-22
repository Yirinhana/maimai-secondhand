package com.maimai.community;

import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.service.CommunityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Real maimai_test integration tests. Fixtures use generated IDs; cleanup touches only their own rows. */
@SpringBootTest
@ActiveProfiles("test")
class CommunityServiceIT {
    @Autowired CommunityService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.maimai.community.service.ProductDiscussionService discussions;
    @Autowired com.maimai.community.service.RatingService ratings;
    long buyer, seller, operator, support, stranger, category, product, order;
    final List<Long> users = new ArrayList<>();

    @BeforeEach
    void setup() {
        buyer = user("买家", "USER"); seller = user("卖家", "SELLER");
        operator = user("运营", "OPERATOR"); support = user("客服", "SUPPORT"); stranger = user("其他", "USER");
        category = insert("INSERT INTO categories(name, sort, status) VALUES (?, 1, 'ACTIVE')", "社区测试-" + UUID.randomUUID());
        product = insert("""
                INSERT INTO products(seller_id, category_id, title, item_condition, price_cents, stock_available, stock_reserved,
                stock_sold, region, delivery_methods, freight_cents, status) VALUES (?, ?, '社区测试商品', 'GOOD', 1999, 10, 0, 0, '上海', 'MEET', 0, 'ON_SALE')""", seller, category);
        jdbc.update("INSERT INTO seller_applications(user_id, status, channel_status, intro) VALUES (?, 'APPROVED', 'QUALIFIED', '仅测试资格')", seller);
        order = insert("""
                INSERT INTO orders(order_no, batch_id, buyer_id, seller_id, delivery_method, goods_amount_cents, freight_cents, total_cents,
                fulfillment_status, pay_status, refund_status, expires_at, completed_at)
                VALUES (?, 1, ?, ?, 'MEET', 1999, 0, 1999, 'COMPLETED', 'PAID', 'NONE', DATE_ADD(NOW(6), INTERVAL 1 DAY), NOW(6))""",
                "CT" + UUID.randomUUID().toString().replace("-", "").substring(0, 28), buyer, seller);
        jdbc.update("INSERT INTO payment_requests(pay_no,order_id,amount_cents,channel,status,simulated) VALUES(?,?,1999,'MOCK','PAID',1)","CP"+UUID.randomUUID().toString().replace("-", "").substring(0,26),order);
        login(buyer, "USER");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (users.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(users.size(), "?"));
        Object[] args = users.toArray();
        jdbc.update("UPDATE product_comments SET reply_to_id=NULL WHERE product_id=?",product);
        jdbc.update("DELETE FROM product_comments WHERE product_id=?",product);
        jdbc.update("DELETE FROM order_items WHERE order_id=?",order);
        jdbc.update("DELETE FROM community_action_logs WHERE actor_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM community_reports WHERE reporter_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM community_order_ratings WHERE rater_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM community_demand_replies WHERE author_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM community_demand_posts WHERE author_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM community_product_footprints WHERE user_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM community_footprint_preferences WHERE user_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM community_seller_follows WHERE follower_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM community_favorites WHERE user_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM notifications WHERE user_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM payment_requests WHERE order_id=?",order);
        jdbc.update("DELETE FROM orders WHERE id = ?", order);
        jdbc.update("DELETE FROM products WHERE id = ?", product);
        jdbc.update("DELETE FROM categories WHERE id = ?", category);
        jdbc.update("DELETE FROM seller_applications WHERE user_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (" + placeholders + ")", args);
        jdbc.update("DELETE FROM users WHERE id IN (" + placeholders + ")", args);
        users.clear();
    }

    @Test
    void favoritesAreIdempotentPrivateAndMaskUnreviewedChanges() {
        service.addFavorite(buyer, product); service.addFavorite(buyer, product);
        assertThat(service.listMyFavorites(buyer, 0, 20).total()).isEqualTo(1);
        forbidden(() -> service.listMyFavorites(stranger, 0, 20));
        jdbc.update("UPDATE products SET status = 'PENDING', title = '未审核秘密' WHERE id = ?", product);
        var result = service.listMyFavorites(buyer, 0, 20).items().getFirst();
        assertThat(result.productTitle()).isEqualTo("该商品暂不可见");
        assertThat(result.productPriceCents()).isZero(); assertThat(result.isPublicVisible()).isFalse();
        code("NOT_FOUND", () -> service.addFavorite(buyer, product));
        service.removeFavorite(buyer, product); service.removeFavorite(buyer, product);
        assertThat(service.listMyFavorites(buyer, 0, 20).items()).isEmpty();
    }

    @Test void productDiscussionPersistsRepliesAndGuardsOwnershipAndModeration() {
        var question=discussions.create(buyer,product,new com.maimai.community.service.ProductDiscussionService.CreateComment("配件还在吗？",null));
        login(seller,"SELLER");
        var answer=discussions.create(seller,product,new com.maimai.community.service.ProductDiscussionService.CreateComment("可在私信中确认配件清单",question.id()));
        assertThat(answer.seller()).isTrue();
        assertThat(answer.replyPreview()).isEqualTo("配件还在吗？");
        forbidden(()->discussions.delete(seller,product,question.id()));
        code("NOT_FOUND",()->discussions.create(seller,product,new com.maimai.community.service.ProductDiscussionService.CreateComment("无效引用",Long.MAX_VALUE)));
        login(buyer,"USER");
        var report=service.createReport(buyer,new ReportCreateRequest("PRODUCT_COMMENT",answer.id(),"需要核实的留言内容"),"买家");
        login(operator,"OPERATOR");service.processReport(operator,report.id(),ReportAction.HIDE,"运营核实后隐藏");
        assertThat(discussions.list(product,0,10).items()).extracting(x->x.id()).containsExactly(question.id());
        login(buyer,"USER");discussions.delete(buyer,product,question.id());
        assertThat(discussions.list(product,0,10).total()).isZero();
    }

    @Test void discussionMasksDeletedParentAndRejectsInvisibleProductsAndExcessPosts() {
        var parent=discussions.create(buyer,product,new com.maimai.community.service.ProductDiscussionService.CreateComment("原问题",null));
        login(seller,"SELLER");discussions.create(seller,product,new com.maimai.community.service.ProductDiscussionService.CreateComment("回复",parent.id()));
        login(buyer,"USER");discussions.delete(buyer,product,parent.id());
        assertThat(discussions.list(product,0,10).items().getFirst().replyPreview()).isNull();
        for(int i=0;i<4;i++)discussions.create(buyer,product,new com.maimai.community.service.ProductDiscussionService.CreateComment("留言"+i,null));
        assertThatThrownBy(()->discussions.create(buyer,product,new com.maimai.community.service.ProductDiscussionService.CreateComment("超频",null))).isInstanceOf(BizException.class);
        jdbc.update("UPDATE products SET status='PENDING' WHERE id=?",product);
        code("NOT_FOUND",()->discussions.list(product,0,10));
        login(stranger,"USER");code("NOT_FOUND",()->discussions.create(stranger,product,new com.maimai.community.service.ProductDiscussionService.CreateComment("不能查看",null)));
    }

    @Test void productRatingsOnlyIncludePurchasersCompletedMatchingOrders() {
        service.createOrderRating(buyer,order,new RatingRequest(5,"购买评价"),"买家");
        assertThat(ratings.product(product,0,10).total()).isZero();
        jdbc.update("INSERT INTO order_items(order_id,product_id,title,item_condition,price_cents,quantity) VALUES(?,?,'测试快照','GOOD',1999,1)",order,product);
        assertThat(ratings.product(product,0,10).total()).isEqualTo(1);
        login(seller,"SELLER");service.createOrderRating(seller,order,new RatingRequest(5,"卖家评价买家"),"卖家");
        assertThat(ratings.product(product,0,10).total()).isEqualTo(1);
        jdbc.update("UPDATE orders SET fulfillment_status='SHIPPED' WHERE id=?",order);
        assertThat(ratings.product(product,0,10).total()).isZero();
        jdbc.update("UPDATE orders SET fulfillment_status='COMPLETED' WHERE id=?",order);
        jdbc.update("UPDATE community_order_ratings SET is_hidden=1 WHERE rater_id=?",buyer);
        assertThat(ratings.product(product,0,10).total()).isZero();
    }

    @Test
    void followUsesLatestQualificationAndCannotFollowSelfOrImpersonate() {
        jdbc.update("UPDATE seller_applications SET channel_status='PENDING' WHERE user_id=?",seller);
        service.followSeller(buyer, seller); service.followSeller(buyer, seller);
        assertThat(service.listMyFollows(buyer, 0, 20).total()).isEqualTo(1);
        code("FOLLOW_SELF", () -> service.followSeller(buyer, buyer));
        forbidden(() -> service.followSeller(stranger, seller));
        service.unfollowSeller(buyer, seller);
        jdbc.update("INSERT INTO seller_applications(user_id, status, channel_status,created_at) SELECT user_id,'SUSPENDED','QUALIFIED',created_at FROM seller_applications WHERE user_id=? ORDER BY id DESC LIMIT 1", seller);
        forbidden(() -> service.followSeller(buyer, seller));
        assertThat(service.listMyFollows(buyer, 0, 20).total()).isZero();
    }

    @Test
    void footprintsReadOnlyListFiltersExpiredRowsAndProtectsOwner() {
        service.recordFootprint(buyer, product);
        jdbc.update("UPDATE community_product_footprints SET viewed_at = DATE_SUB(NOW(6), INTERVAL 31 DAY) WHERE user_id = ?", buyer);
        assertThat(service.listMyFootprints(buyer, 0, 20).items()).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM community_product_footprints WHERE user_id = ?", buyer)).isEqualTo(1);
        forbidden(() -> service.clearMyFootprints(stranger));
        service.setFootprintEnabled(buyer, false);
        service.recordFootprint(buyer, product);
        assertThat(service.listMyFootprints(buyer, 0, 20).items()).isEmpty();
        service.clearMyFootprints(buyer);
        assertThat(count("SELECT COUNT(*) FROM community_product_footprints WHERE user_id = ?", buyer)).isZero();
    }

    @Test
    void disablingFootprintsWinsAgainstConcurrentRecording() throws Exception {
        service.setFootprintEnabled(buyer, true);
        List<Callable<String>> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) jobs.add(() -> as(buyer, "USER", () -> { service.recordFootprint(buyer, product); return "RECORDED"; }));
        jobs.add(() -> as(buyer, "USER", () -> { service.setFootprintEnabled(buyer, false); return "DISABLED"; }));
        assertThat(parallel(jobs)).contains("DISABLED");
        assertThat(service.isFootprintEnabled(buyer)).isFalse();
        assertThat(count("""
                SELECT COUNT(*) FROM community_product_footprints f JOIN community_footprint_preferences p ON p.user_id = f.user_id
                WHERE f.user_id = ? AND f.viewed_at > p.updated_at""", buyer)).isZero();
        service.clearMyFootprints(buyer);
        parallel(java.util.Collections.nCopies(10, () -> as(buyer, "USER", () -> { service.recordFootprint(buyer, product); return "OK"; })));
        assertThat(count("SELECT COUNT(*) FROM community_product_footprints WHERE user_id = ?", buyer)).isZero();
    }

    @Test
    void paginationRejectsOverflowAndBlankEdits() {
        code("PAGE_INVALID", () -> service.listMyFavorites(buyer, Integer.MAX_VALUE, 50));
        code("PAGE_INVALID", () -> service.listPublicDemands(-1, 20));
        var d = createDemand();
        code("INVALID_CONTENT", () -> service.updateDemand(buyer, d.id(), new DemandPatchRequest(" ", null, null, null, null, null), "伪造昵称"));
        code("DEMAND_PATCH_EMPTY", () -> service.updateDemand(buyer, d.id(), new DemandPatchRequest(null, null, null, null, null, null), "买家"));
        assertThat(service.listMyDemands(buyer, 0, 999).size()).isEqualTo(50);
    }

    @Test
    void ordinaryUserAndSupportCannotReviewOrReadModerationQueues() {
        var d = createDemand();
        forbidden(() -> service.reviewDemand(buyer, d.id(), true, "通过"));
        forbidden(() -> service.listAdminDemands(buyer, "PENDING", 0, 20));
        login(support, "SUPPORT");
        forbidden(() -> service.reviewDemand(support, d.id(), true, "通过"));
        forbidden(() -> service.listAdminDemandReplies(support, null, 0, 20));
        forbidden(() -> service.listPendingReports(null, 0, 20));
        login(operator, "OPERATOR");
        assertThat(service.listAdminDemands(operator, null, 0, 50).items()).anyMatch(x -> x.id() == d.id());
        service.reviewDemand(operator, d.id(), true, "通过");
        code("DEMAND_NOT_PENDING", () -> service.reviewDemand(operator, d.id(), true, "重复"));
    }

    @Test
    void demandCriticalEditReturnsToReviewAndCannotModifyAnotherOwner() {
        var d = publishedDemand();
        login(stranger, "USER");
        forbidden(() -> service.updateDemand(stranger, d.id(), new DemandPatchRequest("越权", null, null, null, null, null), "其他"));
        login(buyer, "USER");
        var edited = service.updateDemand(buyer, d.id(), new DemandPatchRequest("重新审核标题", null, null, null, null, null), "伪造昵称");
        assertThat(edited.status()).isEqualTo("PENDING"); assertThat(edited.authorNickname()).isEqualTo("买家");
        code("NOT_FOUND", () -> service.publicDemand(d.id()));
        service.closeDemand(buyer, d.id());
        code("DEMAND_CLOSED", () -> service.updateDemand(buyer, d.id(), new DemandPatchRequest("不能重开", null, null, null, null, null), "买家"));
    }

    @Test
    void repliesRequireReviewAndStopBeingPublicWhenParentHiddenOrClosed() {
        var d = publishedDemand();
        login(seller, "SELLER");
        var r = service.createDemandReply(seller, d.id(), new DemandReplyRequest("我有适合你的商品"));
        assertThat(service.listDemandReplies(d.id(), 0, 20).items()).isEmpty();
        assertThat(service.listMyDemandReplies(seller, 0, 20).items()).anyMatch(x -> x.id() == r.id());
        login(operator, "OPERATOR");
        assertThat(service.listAdminDemandReplies(operator, null, 0, 50).items()).anyMatch(x -> x.id() == r.id());
        service.reviewDemandReply(operator, r.id(), true, "符合要求");
        assertThat(service.listDemandReplies(d.id(), 0, 20).total()).isEqualTo(1);
        login(buyer, "USER");
        var report = service.createReport(buyer, new ReportCreateRequest("DEMAND_POST", d.id(), "该帖子包含违规信息"), "买家");
        login(operator, "OPERATOR");
        service.processReport(operator, report.id(), ReportAction.HIDE, "已核实违规并隐藏");
        code("NOT_FOUND", () -> service.listDemandReplies(d.id(), 0, 20));
        login(buyer, "USER"); service.closeDemand(buyer, d.id());
        code("NOT_FOUND", () -> service.listDemandReplies(d.id(), 0, 20));
    }

    @Test
    void ratingOnlyForRealCompletedOrdersAndParticipants() {
        login(stranger, "USER");
        forbidden(() -> service.createOrderRating(stranger, order, new RatingRequest(5, "非参与者"), "其他"));
        forbidden(() -> service.listOrderRatings(order, 0, 20));
        login(buyer, "USER");
        jdbc.update("UPDATE orders SET fulfillment_status = 'SHIPPED' WHERE id = ?", order);
        code("ORDER_NOT_COMPLETED", () -> service.createOrderRating(buyer, order, new RatingRequest(5, "尚未完成"), "买家"));
        jdbc.update("UPDATE orders SET fulfillment_status = 'COMPLETED', completed_at = NULL WHERE id = ?", order);
        code("ORDER_NOT_COMPLETED", () -> service.createOrderRating(buyer, order, new RatingRequest(5, "伪完成状态"), "买家"));
    }

    @Test
    void directReplyHideIsAuditedAndOtherUsersCannotEditOrDelete() {
        var d = publishedDemand();
        login(seller, "SELLER");
        var reply = service.createDemandReply(seller, d.id(), new DemandReplyRequest("待运营核查的回复"));
        login(stranger, "USER");
        forbidden(() -> service.updateDemandReply(stranger, reply.id(), new DemandReplyRequest("越权改写内容")));
        forbidden(() -> service.deleteDemandReply(stranger, reply.id()));
        login(operator, "OPERATOR");
        service.reviewDemandReply(operator, reply.id(), true, "先审核发布");
        login(buyer, "USER");
        var report = service.createReport(buyer, new ReportCreateRequest("DEMAND_REPLY", reply.id(), "该回复存在不恰当信息"), "买家");
        login(operator, "OPERATOR");
        service.processReport(operator, report.id(), ReportAction.HIDE, "复核后确认应隐藏回复");
        assertThat(service.listDemandReplies(d.id(), 0, 20).items()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM community_demand_posts WHERE id = ?", String.class, d.id())).isEqualTo("PUBLISHED");
        assertThat(count("SELECT COUNT(*) FROM community_action_logs WHERE target_type = 'DEMAND_REPLY' AND target_id = ? AND action = 'CONTENT_HIDE'", reply.id())).isEqualTo(1);
    }

    @Test
    void bilateralRatingsUseServerDeterminedRateeAndLiveRefundStatus() {
        var buyerRating = service.createOrderRating(buyer, order, new RatingRequest(5, "描述准确"), "伪造昵称");
        assertThat(buyerRating.rateeId()).isEqualTo(seller); assertThat(buyerRating.reviewerNickname()).isEqualTo("买家");
        login(seller, "SELLER");
        var sellerRating = service.createOrderRating(seller, order, new RatingRequest(4, "沟通顺畅"), "卖家");
        assertThat(sellerRating.rateeId()).isEqualTo(buyer);
        assertThat(service.listOrderRatings(order, 0, 20).total()).isEqualTo(2);
        jdbc.update("UPDATE orders SET refund_status = 'PARTIAL' WHERE id = ?", order);
        SecurityContextHolder.clearContext();
        var publicReview = service.listReceivedRatings(seller, 0, 20).items().getFirst();
        assertThat(publicReview.refundStatus()).isEqualTo("PARTIAL");
        assertThat(publicReview.simulated()).isTrue();
        assertThat(publicReview.paymentSource()).isEqualTo("SIMULATED");
        jdbc.update("UPDATE orders SET experience_source='experience-test' WHERE id=?",order);
        assertThat(service.listReceivedRatings(seller,0,20).items().getFirst().simulated()).isTrue();
        assertThat(java.util.Arrays.stream(PublicRatingItem.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)).doesNotContain("orderId", "phone", "address");
    }

    @Test
    void concurrentDuplicateRatingsReturnOneSuccessAndExplicitConflicts() throws Exception {
        var results = parallel(java.util.Collections.nCopies(12, () -> as(buyer, "USER", () -> {
            try { service.createOrderRating(buyer, order, new RatingRequest(5, "并发评价"), "买家"); return "CREATED"; }
            catch (BizException e) { return e.getCode(); }
        })));
        assertThat(results.stream().filter("CREATED"::equals).count()).isEqualTo(1);
        assertThat(results.stream().filter("RATING_DUPLICATE"::equals).count()).isEqualTo(11);
        assertThat(count("SELECT COUNT(*) FROM community_order_ratings WHERE order_id = ?", order)).isEqualTo(1);
    }

    @Test
    void hiddenRatingsLeavePublicProfilesAndReportActionsMustMatchResource() {
        var rating = service.createOrderRating(buyer, order, new RatingRequest(1, "待核实内容"), "买家");
        var report = service.createReport(buyer, new ReportCreateRequest("ORDER_REVIEW", rating.id(), "评价中包含不恰当信息"), "买家");
        var productReport = service.createReport(buyer, new ReportCreateRequest("PRODUCT", product, "商品描述存在不实信息"), "买家");
        login(operator, "OPERATOR");
        code("REPORT_ACTION_MISMATCH", () -> service.processReport(operator, report.id(), ReportAction.FORWARD_TO_PRODUCT, "不能转交该资源类型"));
        code("REPORT_ACTION_MISMATCH", () -> service.processReport(operator, productReport.id(), ReportAction.HIDE, "商品须由商品模块处理"));
        service.processReport(operator, report.id(), ReportAction.HIDE, "举报成立隐藏评价内容");
        service.processReport(operator, productReport.id(), ReportAction.FORWARD_TO_PRODUCT, "转商品运营复核处理");
        assertThat(service.listReceivedRatings(seller, 0, 20).items()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product)).isEqualTo("ON_SALE");
        assertThat(count("SELECT COUNT(*) FROM community_action_logs WHERE actor_id = ? AND action = 'CONTENT_HIDE'", operator)).isEqualTo(1);
    }

    @Test
    void concurrentReportProcessingIsAuditedExactlyOnce() throws Exception {
        var report = service.createReport(buyer, new ReportCreateRequest("PRODUCT", product, "商品描述需要运营核实"), "买家");
        var results = parallel(java.util.Collections.nCopies(8, () -> as(operator, "OPERATOR", () -> {
            try { service.processReport(operator, report.id(), ReportAction.FORWARD_TO_PRODUCT, "转商品模块核实处理"); return "PROCESSED"; }
            catch (BizException e) { return e.getCode(); }
        })));
        assertThat(results.stream().filter("PROCESSED"::equals).count()).isEqualTo(1);
        assertThat(results.stream().filter("REPORT_PROCESSED"::equals).count()).isEqualTo(7);
        assertThat(count("SELECT COUNT(*) FROM community_action_logs WHERE target_type = 'REPORT' AND target_id = ? AND action = 'REPORT_PROCESS'", report.id())).isEqualTo(1);
        assertThat(service.listMyReports(buyer, 0, 20).items().getFirst().status()).isEqualTo("FORWARDED");
    }

    @Test
    void reportingPrivateDemandDoesNotRevealOrCreateNewReport() {
        var d = createDemand();
        login(stranger, "USER");
        code("NOT_FOUND", () -> service.createReport(stranger, new ReportCreateRequest("DEMAND_POST", d.id(), "尝试举报他人私密草稿"), "其他"));
        assertThat(service.listMyReports(stranger, 0, 20).items()).isEmpty();
        forbidden(() -> service.listMyReports(buyer, 0, 20));
    }

    private DemandItem createDemand() {
        login(buyer, "USER");
        return service.createDemand(buyer, new DemandCreateRequest("二手滑板求购", "轻量适合新手", 1000L, 3000L, category, "上海"), "买家");
    }
    private DemandItem publishedDemand() {
        var d = createDemand(); login(operator, "OPERATOR"); service.reviewDemand(operator, d.id(), true, "通过审核");
        return d;
    }
    private long user(String name, String role) {
        long id = insert("INSERT INTO users(email, password_hash, nickname, status) VALUES (?, 'test-hash', ?, 'ACTIVE')", "community-" + UUID.randomUUID() + "@local.test", name);
        users.add(id); jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, ?)", id, role); return id;
    }
    private long insert(String sql, Object... args) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, holder);
        return holder.getKey().longValue();
    }
    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void login(long id, String role) {
        var principal = new AuthenticatedUser(id, "local-test", "测试主体", Set.of(role));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    private String as(long actor, String role, Callable<String> action) throws Exception {
        login(actor, role); try { return action.call(); } finally { SecurityContextHolder.clearContext(); }
    }
    private List<String> parallel(List<Callable<String>> jobs) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(Math.min(8, jobs.size()))) {
            List<Future<String>> futures = new ArrayList<>();
            for (var job : jobs) futures.add(pool.submit(() -> { start.await(); return job.call(); }));
            start.countDown();
            List<String> results = new ArrayList<>();
            for (var f : futures) results.add(f.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
    private void forbidden(Runnable action) { code("FORBIDDEN", action); }
    private void code(String expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(expected));
    }
}
