package com.maimai.catalog;

import com.maimai.admin.dto.AdminDtos.ReviewApplicationRequest;
import com.maimai.admin.service.AdminSellerApplicationService;
import com.maimai.catalog.admin.AdminProductService;
import com.maimai.catalog.domain.ExperienceProductSource;
import com.maimai.catalog.dto.CatalogDtos.ProductCreateRequest;
import com.maimai.catalog.service.DraftImageAttachmentService;
import com.maimai.catalog.service.SellerProductService;
import com.maimai.common.BizException;
import com.maimai.common.PersonalMediaService;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.config.MaimaiProperties;
import com.maimai.identity.dto.MeDtos.SellerApplicationRequest;
import com.maimai.identity.service.MeService;
import com.maimai.payment.dto.ExperiencePaymentDtos.Result;
import com.maimai.payment.service.ExperiencePaymentService;
import com.maimai.payment.service.PaymentService;
import com.maimai.trade.domain.Order;
import com.maimai.trade.dto.TradeDtos.CheckoutItem;
import com.maimai.trade.dto.TradeDtos.CheckoutRequest;
import com.maimai.trade.service.CheckoutService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.*;

/** Real MySQL coverage for application approval, photo attachment, listing review and no-money checkout. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SellerPublishingIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired EntityManager entities;
    @Autowired MaimaiProperties properties;
    @Autowired MeService me;
    @Autowired AdminSellerApplicationService applications;
    @Autowired SellerProductService products;
    @Autowired AdminProductService reviews;
    @Autowired PersonalMediaService media;
    @Autowired DraftImageAttachmentService attachments;
    @Autowired CheckoutService checkout;
    @Autowired ExperiencePaymentService experiencePayments;
    @Autowired PaymentService payments;
    long seller, buyer, admin, category, application;
    String previousChannel;
    boolean previousExperience;

    @BeforeEach void prepare() {
        assertThat(db.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("maimai_test");
        previousChannel = properties.getPayment().getChannel();
        previousExperience = properties.isExperienceListingEnabled();
        // Mirrors the public environment: wechat remains configured, while the explicit simulation switch is on.
        properties.getPayment().setChannel("wechat");
        properties.setExperienceListingEnabled(true);
        seller = user("USER"); buyer = user("USER"); admin = user("SUPER_ADMIN");
        category = insert("INSERT INTO categories(name,status) VALUES('发布资格测试分类','ACTIVE')");
        login(seller, "USER");
        application = me.applySeller(seller, new SellerApplicationRequest("本人闲置，按实际情况说明与交付")).id();
        login(admin, "SUPER_ADMIN");
        applications.review(application, new ReviewApplicationRequest("APPROVE", "平台资料核对通过，未开通支付渠道", false));
        flush();
        login(seller, "USER", "SELLER");
    }

    @AfterEach void restore() {
        properties.getPayment().setChannel(previousChannel);
        properties.setExperienceListingEnabled(previousExperience);
        SecurityContextHolder.clearContext();
    }

    @Test void normallyApprovedSellerCanUploadPublishReviewAndCompleteExperiencePayment() throws Exception {
        var capability = me.latestApplication(seller);
        assertThat(capability.status()).isEqualTo("APPROVED");
        assertThat(capability.channelStatus()).isEqualTo("PENDING");
        assertThat(capability.publishingMode()).isEqualTo("SIMULATED");
        assertThat(capability.canPublish()).isTrue();
        assertThat(capability.publishingMessage()).contains("不发生真实扣款", "无需开通真实支付渠道");

        long product = draftWithPhoto();
        assertThat(source(product)).isEqualTo(ExperienceProductSource.SELLER_SIMULATION);
        products.submit(seller, product); flush();
        assertThat(status(product)).isEqualTo("PENDING_REVIEW");
        login(admin, "SUPER_ADMIN"); reviews.review(admin, product, true, "商品信息和照片审核通过"); flush();
        assertThat(status(product)).isEqualTo("ON_SALE");
        login(buyer, "USER");
        var order = checkout.checkoutExperience(buyer, request(product)).orders().getFirst();
        assertThat(order.experienceSource()).isEqualTo(Order.INTERACTIVE_EXPERIENCE);
        var session = experiencePayments.create(order.orderNo());
        assertThat(session.amountCents()).isEqualTo(2900);
        assertThat(experiencePayments.qr(session.token())).isNotEmpty();
        experiencePayments.finish(session.token(), Result.SUCCESS); flush();
        assertThat(db.queryForObject("SELECT status FROM payment_requests WHERE pay_no=?",String.class,session.payNo())).isEqualTo("PAID");
        assertThat(db.queryForObject("SELECT channel FROM payment_requests WHERE pay_no=?",String.class,session.payNo())).isEqualTo("EXPERIENCE_QR");
        assertThat(count("SELECT COUNT(*) FROM payment_requests WHERE order_id=? AND simulated=0",order.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE order_id=?",order.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM finance_allocation_expectations WHERE order_id=?",order.id())).isZero();
        assertThat(db.queryForObject("SELECT channel_status FROM seller_applications WHERE id=?",String.class,application)).isEqualTo("PENDING");
        assertThatThrownBy(() -> payments.pay(order.orderNo())).isInstanceOf(BizException.class);
    }

    @Test void localMockAllowsApprovedSellerWithoutPretendingChannelQualification() throws Exception {
        properties.setExperienceListingEnabled(false); properties.getPayment().setChannel("mock");
        long product = draftWithPhoto(); products.submit(seller, product); flush();
        assertThat(status(product)).isEqualTo("PENDING_REVIEW");
        assertThat(source(product)).isEqualTo(ExperienceProductSource.SELLER_SIMULATION);
        assertThat(me.latestApplication(seller).channelStatus()).isEqualTo("PENDING");
    }

    @Test void realModeStillAllowsDraftButRequiresChannelQualificationToPublish() throws Exception {
        properties.setExperienceListingEnabled(false);
        var capability = me.latestApplication(seller);
        assertThat(capability.publishingMode()).isEqualTo("REAL_PAYMENT");
        assertThat(capability.canPublish()).isFalse();
        long product = draftWithPhoto();
        assertThat(source(product)).isNull();
        assertThatThrownBy(() -> products.submit(seller, product)).isInstanceOf(BizException.class).hasMessageContaining("收款渠道核验");
        assertThatThrownBy(() -> products.create(seller, form(true))).isInstanceOf(BizException.class);
        assertThat(status(product)).isEqualTo("DRAFT");
        assertThat(source(product)).isNull();
    }

    @Test void realQualifiedSellerKeepsRealProductProvenance() throws Exception {
        properties.setExperienceListingEnabled(false);
        db.update("UPDATE seller_applications SET channel_status='QUALIFIED' WHERE id=?",application);
        long product = draftWithPhoto(); products.submit(seller, product); flush();
        login(admin, "SUPER_ADMIN"); reviews.review(admin, product, true, "审核通过"); flush();
        assertThat(source(product)).isNull();
        assertThat(status(product)).isEqualTo("ON_SALE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "SUPPLEMENT", "SUSPENDED"})
    void simulationCannotBypassLatestApplicationStatus(String applicationStatus) {
        db.update("UPDATE seller_applications SET status=? WHERE id=?",applicationStatus,application);
        assertThat(me.latestApplication(seller).canPublish()).isFalse();
        assertThatThrownBy(() -> products.create(seller, form(false))).isInstanceOf(BizException.class);
        assertThat(count("SELECT COUNT(*) FROM products WHERE seller_id=?",seller)).isZero();
    }

    @Test void disabledAccountAndRemovedSellerRoleCannotPublish() throws Exception {
        long product = draftWithPhoto();
        db.update("DELETE FROM user_roles WHERE user_id=? AND role='SELLER'",seller);
        assertThat(me.latestApplication(seller).canPublish()).isFalse();
        assertThatThrownBy(() -> products.submit(seller, product)).isInstanceOf(BizException.class);
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'SELLER')",seller);
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",seller);
        assertThat(me.latestApplication(seller).canPublish()).isFalse();
        assertThatThrownBy(() -> products.create(seller, form(false))).isInstanceOf(BizException.class);
    }

    @Test void newerSuspensionOverridesOlderApprovalEvenWithSameTimestamp() {
        db.update("INSERT INTO seller_applications(user_id,status,channel_status,created_at) SELECT user_id,'SUSPENDED','PENDING',created_at FROM seller_applications WHERE id=?",application);
        assertThat(me.latestApplication(seller).status()).isEqualTo("SUSPENDED");
        assertThat(me.latestApplication(seller).canPublish()).isFalse();
        assertThatThrownBy(() -> products.create(seller, form(false))).isInstanceOf(BizException.class);
    }

    @Test void administratorMustRecheckApprovalAtPublicationTime() throws Exception {
        long product = draftWithPhoto(); products.submit(seller, product); flush();
        db.update("UPDATE seller_applications SET status='SUSPENDED' WHERE id=?",application);
        login(admin, "SUPER_ADMIN");
        assertThatThrownBy(() -> reviews.review(admin, product, true, "试图通过")).isInstanceOf(BizException.class);
        assertThat(status(product)).isEqualTo("PENDING_REVIEW");
    }

    @ParameterizedTest
    @ValueSource(strings = {"maimai-experience-045", "maimai-experience-catalog-021", "maimai-seller-simulation-v1"})
    void eachExplicitSimulationSourceCanCheckoutWithoutRewritingItsOrigin(String originalSource) throws Exception {
        long product = draftWithPhoto();
        db.update("UPDATE products SET experience_source=?,supply_note='原始供货说明' WHERE id=?",originalSource,product); flush();
        products.submit(seller, product); flush();
        login(admin, "SUPER_ADMIN"); reviews.review(admin, product, true, "审核通过"); flush();
        login(buyer, "USER");
        var order = checkout.checkoutExperience(buyer, request(product)).orders().getFirst();
        assertThat(order.experienceSource()).isEqualTo(Order.INTERACTIVE_EXPERIENCE);
        assertThat(source(product)).isEqualTo(originalSource);
        assertThat(db.queryForObject("SELECT supply_note FROM products WHERE id=?",String.class,product)).isEqualTo("原始供货说明");
        assertThatThrownBy(() -> checkout.checkout(buyer, request(product))).isInstanceOf(BizException.class);
    }

    @Test void arbitraryImportedSourceCannotBecomeInteractiveExperience() throws Exception {
        long product = draftWithPhoto();
        db.update("UPDATE products SET experience_source='unrelated-import',status='ON_SALE' WHERE id=?",product); flush();
        login(buyer, "USER");
        assertThatThrownBy(() -> checkout.checkoutExperience(buyer,request(product))).isInstanceOf(BizException.class).hasMessageContaining("只能选择体验商品");
        assertThat(count("SELECT COUNT(*) FROM orders WHERE buyer_id=?",buyer)).isZero();
    }

    @Test void anUnsubmittedNullDraftCanOptIntoSimulationWhenEnabled() throws Exception {
        properties.setExperienceListingEnabled(false);
        long product = draftWithPhoto(); assertThat(source(product)).isNull();
        properties.setExperienceListingEnabled(true);
        products.submit(seller, product); flush();
        assertThat(source(product)).isEqualTo(ExperienceProductSource.SELLER_SIMULATION);
        assertThat(status(product)).isEqualTo("PENDING_REVIEW");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFF_SHELF", "REJECTED", "PENDING_REVIEW"})
    void enablingSimulationNeverConvertsPreviouslySubmittedRealProducts(String previousStatus) throws Exception {
        properties.setExperienceListingEnabled(false);
        long product = draftWithPhoto();
        db.update("UPDATE products SET status=? WHERE id=?",previousStatus,product); flush();
        properties.setExperienceListingEnabled(true);
        if (previousStatus.equals("PENDING_REVIEW")) {
            login(admin,"SUPER_ADMIN");
            assertThatThrownBy(() -> reviews.review(admin,product,true,"试图通过")).isInstanceOf(BizException.class).hasMessageContaining("正式交易");
        } else {
            assertThatThrownBy(() -> products.submit(seller,product)).isInstanceOf(BizException.class).hasMessageContaining("正式交易");
        }
        assertThat(source(product)).isNull();
        assertThat(status(product)).isEqualTo(previousStatus);
    }

    @Test void draftWithPreviousPublicationHistoryCannotBeSilentlyConverted() throws Exception {
        properties.setExperienceListingEnabled(false);
        long product = draftWithPhoto();
        db.update("UPDATE product_revisions SET action='OFF_SHELF' WHERE product_id=?",product);
        properties.setExperienceListingEnabled(true);
        assertThatThrownBy(() -> products.submit(seller,product)).isInstanceOf(BizException.class).hasMessageContaining("正式交易");
        assertThat(source(product)).isNull();
    }

    @Test void draftLinkedToAnExistingOrderCannotBeSilentlyConverted() throws Exception {
        properties.setExperienceListingEnabled(false);
        long product = draftWithPhoto();
        long batch=insert("INSERT INTO checkout_batches(batch_no,user_id,idempotency_key) VALUES(?,?,?)",
                "SB"+UUID.randomUUID().toString().replace("-", "").substring(0,20),buyer,UUID.randomUUID().toString());
        long order=insert("INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,goods_amount_cents,freight_cents,total_cents,platform_fee_cents,fulfillment_status,pay_status,refund_status,expires_at) VALUES(?,?,?,?,'MEETUP',2900,0,2900,1,'PENDING_PAYMENT','UNPAID','NONE',DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY))",
                "SO"+UUID.randomUUID().toString().replace("-", "").substring(0,20),batch,buyer,seller);
        db.update("INSERT INTO order_items(order_id,product_id,title,item_condition,price_cents,quantity) VALUES(?,?,'既有交易快照','GOOD',2900,1)",order,product);
        properties.setExperienceListingEnabled(true);
        assertThatThrownBy(() -> products.submit(seller,product)).isInstanceOf(BizException.class).hasMessageContaining("正式交易");
        assertThat(source(product)).isNull();
        assertThat(count("SELECT COUNT(*) FROM order_items WHERE product_id=?",product)).isEqualTo(1);
    }

    @Test void simulatedPublicationStillRequiresProductPhotos() {
        long product=products.create(seller,form(false)); flush();
        assertThatThrownBy(() -> products.submit(seller,product)).isInstanceOf(BizException.class).hasMessageContaining("至少 1 张");
        assertThat(status(product)).isEqualTo("DRAFT");
    }

    private long draftWithPhoto() throws Exception {
        long product = products.create(seller, form(false));
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40,40,BufferedImage.TYPE_INT_RGB),"jpeg",bytes);
        var image = media.upload(seller,"DRAFT",new MockMultipartFile("file","item.jpg","image/jpeg",bytes.toByteArray()));
        attachments.attach(seller,product,List.of(image.id())); flush();
        return product;
    }

    private ProductCreateRequest form(boolean submit) {
        return new ProductCreateRequest("已说明外观的二手台灯",category,"白色台灯，灯罩边缘有一处磨痕，功能需现场核对。",
                "GOOD","灯罩边缘磨痕",2900L,2,"上海市",List.of("MEETUP"),0L,"按约交付",submit,List.of(),null,null,null);
    }

    private CheckoutRequest request(long product) {
        return new CheckoutRequest(UUID.randomUUID().toString(),List.of(new CheckoutItem(product,1,"MEETUP",null)),
                null,"上海市公共交付点",Instant.now().plusSeconds(3600),null);
    }
    private String source(long product) { return db.queryForObject("SELECT experience_source FROM products WHERE id=?",String.class,product); }
    private String status(long product) { return db.queryForObject("SELECT status FROM products WHERE id=?",String.class,product); }
    private long count(String sql,Object... values) { return db.queryForObject(sql,Long.class,values); }
    private void flush() { entities.flush(); entities.clear(); }
    private long user(String role) {
        long id=insert("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'unused','发布流程测试','ACTIVE')",UUID.randomUUID()+"@example.invalid");
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)",id,role); return id;
    }
    private long insert(String sql,Object... values) {
        var keys = new GeneratedKeyHolder();
        db.update(connection -> {var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},keys);
        return keys.getKey().longValue();
    }
    private void login(long user,String... roles) {
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(user,"test@example.invalid","测试",Set.of(roles)),null,List.of()));
        SecurityContextHolder.setContext(context);
    }
}
