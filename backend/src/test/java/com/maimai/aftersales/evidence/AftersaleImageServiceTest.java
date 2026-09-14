package com.maimai.aftersales.evidence;

import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AftersaleImageServiceTest {
    static final Path DIRECTORY = Path.of(".local", "evidence-test-"+UUID.randomUUID()).toAbsolutePath().normalize();
    @DynamicPropertySource static void config(DynamicPropertyRegistry properties) {
        properties.add("MAIMAI_AFTERSALE_IMAGE_DIR", DIRECTORY::toString);
    }
    @Autowired JdbcTemplate db;
    @Autowired AftersaleImageService images;
    long buyer,seller,stranger,batch,order,aftersale;

    @BeforeEach void fixtures() {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("maimai_test");
        buyer=user();seller=user();stranger=user();
        String id=UUID.randomUUID().toString().replace("-", "").substring(0,24);
        db.update("INSERT INTO checkout_batches(batch_no,user_id,idempotency_key) VALUES(?,?,?)",id,buyer,id);
        batch=db.queryForObject("SELECT id FROM checkout_batches WHERE batch_no=?",Long.class,id);
        db.update("INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,goods_amount_cents,total_cents,expires_at) VALUES(?,?,?,?,'EXPRESS',10000,10000,DATE_ADD(NOW(),INTERVAL 1 HOUR))",id,batch,buyer,seller);
        order=db.queryForObject("SELECT id FROM orders WHERE order_no=?",Long.class,id);
        db.update("INSERT INTO aftersales(aftersale_no,order_id,buyer_id,type,reason,status) VALUES(?,?,?,'REFUND_ONLY','证据权限测试','PENDING_SELLER')",id,order,buyer);
        aftersale=db.queryForObject("SELECT id FROM aftersales WHERE aftersale_no=?",Long.class,id);
        as(buyer,"USER");
    }

    @AfterEach void cleanupOnlyOwnRecords() throws IOException {
        SecurityContextHolder.clearContext();
        for (String filename : db.queryForList("SELECT storage_name FROM aftersale_images WHERE aftersale_id=?",String.class,aftersale)) {
            Path path=DIRECTORY.resolve(filename).normalize();
            assertThat(path.getParent()).isEqualTo(DIRECTORY);
            Files.deleteIfExists(path);
        }
        db.update("DELETE FROM aftersale_images WHERE aftersale_id=?",aftersale);
        db.update("DELETE FROM aftersales WHERE id=?",aftersale);
        db.update("DELETE FROM orders WHERE id=?",order);
        db.update("DELETE FROM checkout_batches WHERE id=?",batch);
        for(long id:new long[]{buyer,seller,stranger}) db.update("DELETE FROM users WHERE id=?",id);
        Files.deleteIfExists(DIRECTORY);
    }

    @Test void imageIsReencodedAndVisibleOnlyToParticipantsOrCaseStaff() throws Exception {
        var uploaded=images.upload(aftersale,png());
        byte[] encoded=Files.readAllBytes(images.readable(uploaded.id()));
        assertThat(encoded[0]&255).isEqualTo(255);
        assertThat(encoded[1]&255).isEqualTo(216);
        assertThat(ImageIO.read(new ByteArrayInputStream(encoded)).getWidth()).isEqualTo(8);
        as(seller,"USER");assertThat(images.list(aftersale)).hasSize(1);assertThat(images.readable(uploaded.id())).exists();
        as(stranger,"SUPPORT");assertThat(images.readable(uploaded.id())).exists();
        as(stranger,"SUPER_ADMIN");assertThat(images.list(aftersale)).hasSize(1);
        for(String role:List.of("USER","OPERATOR")) {
            as(stranger,role);
            assertThatThrownBy(()->images.list(aftersale)).isInstanceOf(BizException.class);
            assertThatThrownBy(()->images.readable(uploaded.id())).isInstanceOf(BizException.class);
        }
    }

    @Test void rejectsNonImagesAndClosedCaseWithoutCreatingAttachment() throws Exception {
        assertThatThrownBy(()->images.upload(aftersale,new MockMultipartFile("file","fake.png","image/png","<svg onload='alert(1)'/>".getBytes())))
                .isInstanceOf(BizException.class);
        db.update("UPDATE aftersales SET status='RESOLVED' WHERE id=?",aftersale);
        assertThatThrownBy(()->images.upload(aftersale,png())).isInstanceOfSatisfying(BizException.class,
                error->assertThat(error.getCode()).isEqualTo("AFTERSALE_CLOSED"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM aftersale_images WHERE aftersale_id=?",Long.class,aftersale)).isZero();
    }

    @Test void staffMayReviewButCannotImpersonateEvidenceUploader() {
        as(stranger,"SUPPORT");
        assertThatThrownBy(()->images.upload(aftersale,png())).isInstanceOfSatisfying(BizException.class,
                error->assertThat(error.getStatus().value()).isEqualTo(403));
        assertThat(images.list(aftersale)).isEmpty();
    }

    @Test void enforcesTwelveImagesPerCase() throws Exception {
        for(int i=0;i<12;i++) images.upload(aftersale,png());
        assertThat(images.list(aftersale)).hasSize(12);
        assertThatThrownBy(()->images.upload(aftersale,png())).isInstanceOfSatisfying(BizException.class,
                error->assertThat(error.getCode()).isEqualTo("AFTERSALE_IMAGE_LIMIT"));
    }

    private long user() {
        String email="evidence-"+UUID.randomUUID()+"@example.test";
        db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-only','证据测试','ACTIVE')",email);
        return db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }
    private void as(long user,String role) {
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(user,"test@example.test","测试",Set.of(role)),null,List.of()));
        SecurityContextHolder.setContext(context);
    }
    private MockMultipartFile png() throws IOException {
        var image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);image.flush();
        return new MockMultipartFile("file","original.png","image/png",bytes.toByteArray());
    }
}
