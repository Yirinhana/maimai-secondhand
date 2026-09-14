package com.maimai.messaging;

import com.maimai.common.BizException;
import com.maimai.messaging.dto.MessageDtos.*;
import com.maimai.messaging.service.MessageService;
import com.maimai.messaging.service.MessageImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageServiceIntegrationTest {
    @TempDir static Path images;
    @DynamicPropertySource
    static void imageDirectory(DynamicPropertyRegistry registry) {registry.add("maimai.messaging-image-dir",()->images.toString());}
    @Autowired MessageService service;
    @Autowired MessageImageService imageService;
    @Autowired JdbcTemplate db;
    long alice,bob,outsider,conversation;

    @BeforeEach
    void setUp() {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("maimai_test");
        alice=user("甲");bob=user("乙");outsider=user("丙");
        conversation=service.open(alice,new OpenConversation(bob,null));
    }

    @Test
    void conversationAndMessageReadsRejectNonMembers() {
        assertThat(service.open(bob,new OpenConversation(alice,null))).isEqualTo(conversation);
        assertThatThrownBy(()->service.open(alice,new OpenConversation(alice,null))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.history(outsider,conversation,null,20)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.send(outsider,conversation,new SendMessage(UUID.randomUUID(),"越权",null))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.read(outsider,conversation,0)).isInstanceOf(BizException.class);
        assertThat(service.conversations(outsider,0,20)).isEmpty();
    }

    @Test
    void retryHasOneMessageAndCannotChangeContentOrConversation() {
        UUID retryId=UUID.randomUUID();
        SendMessage request=new SendMessage(retryId,"请问还在吗？",null);
        Message first=service.send(alice,conversation,request);
        assertThat(service.send(alice,conversation,request).id()).isEqualTo(first.id());
        assertThat(service.history(bob,conversation,null,20).items()).hasSize(1);
        assertThatThrownBy(()->service.send(alice,conversation,new SendMessage(retryId,"改过的内容",null))).isInstanceOf(BizException.class);
        long another=service.open(alice,new OpenConversation(outsider,null));
        assertThatThrownBy(()->service.send(alice,another,request)).isInstanceOf(BizException.class);
    }

    @Test
    void historyUsesStableCursorAndReadPositionOnlyAdvances() {
        Message first=service.send(alice,conversation,new SendMessage(UUID.randomUUID(),"第一条",null));
        Message second=service.send(alice,conversation,new SendMessage(UUID.randomUUID(),"第二条",null));
        assertThat(service.conversations(bob,0,20).getFirst().unread()).isEqualTo(2);
        History page=service.history(bob,conversation,null,1);
        assertThat(page.items().getFirst().id()).isEqualTo(second.id());
        assertThat(page.hasMore()).isTrue();
        assertThat(service.history(bob,conversation,page.nextBeforeId(),1).items().getFirst().id()).isEqualTo(first.id());
        service.read(bob,conversation,second.id());service.read(bob,conversation,first.id());
        assertThat(service.conversations(bob,0,20).getFirst().unread()).isZero();
        long another=service.open(alice,new OpenConversation(outsider,null));
        Message foreign=service.send(alice,another,new SendMessage(UUID.randomUUID(),"其他会话",null));
        assertThatThrownBy(()->service.read(bob,conversation,foreign.id())).isInstanceOf(BizException.class);
    }

    @Test
    void imageStaysPrivateUntilSentAndCannotBeReusedByAnotherSender() throws Exception {
        BufferedImage original=new BufferedImage(20,20,BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();ImageIO.write(original,"png",encoded);
        UploadedImage image=imageService.upload(alice,conversation,new MockMultipartFile("file","photo.png","image/png",encoded.toByteArray()));
        assertThat(imageService.readable(alice,image.id()).getFileName().toString()).endsWith(".jpg");
        assertThatThrownBy(()->imageService.readable(bob,image.id())).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.send(bob,conversation,new SendMessage(UUID.randomUUID(),null,image.id()))).isInstanceOf(BizException.class);
        service.send(alice,conversation,new SendMessage(UUID.randomUUID(),null,image.id()));
        assertThat(imageService.readable(bob,image.id())).isRegularFile();
        assertThatThrownBy(()->imageService.readable(outsider,image.id())).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.send(alice,conversation,new SendMessage(UUID.randomUUID(),null,image.id()))).isInstanceOf(BizException.class);
    }

    @Test
    void nonImageAndOversizedPageAreRejected() {
        assertThatThrownBy(()->imageService.upload(alice,conversation,new MockMultipartFile("file","test.png","image/png","<script>bad</script>".getBytes())))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.conversations(alice,Integer.MAX_VALUE,50)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.send(alice,conversation,new SendMessage(UUID.randomUUID(),"   ",null))).isInstanceOf(BizException.class);
    }

    private long user(String nickname) {
        String email="msg-"+UUID.randomUUID()+"@example.test";
        db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-only',?,'ACTIVE')",email,nickname);
        return db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }

    @Test void blockStopsBothDirectionsButKeepsHistoryAndUnblockingIsOwned() throws Exception {
        var before=service.send(alice,conversation,new SendMessage(UUID.randomUUID(),"保留交易协商历史",null));
        assertThat(service.setBlock(alice,bob,true).blockedByMe()).isTrue();
        assertThat(service.blockState(bob,alice).blockedByMe()).isFalse();
        assertThat(service.setBlock(bob,alice,false).blockedEitherDirection()).isTrue();
        assertThatThrownBy(()->service.open(bob,new OpenConversation(alice,null))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.send(alice,conversation,new SendMessage(UUID.randomUUID(),"阻止发送",null))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.send(bob,conversation,new SendMessage(UUID.randomUUID(),"阻止接收",null))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->imageService.upload(bob,conversation,new MockMultipartFile("file","photo.png","image/png",new byte[]{1}))).isInstanceOf(BizException.class);
        assertThat(service.history(bob,conversation,null,20).items()).extracting(Message::id).contains(before.id());
        service.read(bob,conversation,before.id());
        assertThat(service.conversations(bob,0,20)).hasSize(1);
        assertThat(service.setBlock(alice,bob,false).blockedEitherDirection()).isFalse();
        assertThat(service.send(bob,conversation,new SendMessage(UUID.randomUUID(),"恢复协商",null)).body()).isEqualTo("恢复协商");
    }

    @Test void blockDoesNotHideAlreadySentPrivateImagesOrOrderNotifications() throws Exception {
        var original=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        var bytes=new ByteArrayOutputStream();ImageIO.write(original,"png",bytes);original.flush();
        var uploaded=imageService.upload(alice,conversation,new MockMultipartFile("file","proof.png","image/png",bytes.toByteArray()));
        service.send(alice,conversation,new SendMessage(UUID.randomUUID(),null,uploaded.id()));
        db.update("INSERT INTO notifications(user_id,type,title,content) VALUES(?,'ORDER','订单仍可查询','屏蔽不能移除订单通知')",bob);
        service.setBlock(bob,alice,true);
        assertThat(imageService.readable(bob,uploaded.id())).isRegularFile();
        assertThatThrownBy(()->imageService.readable(outsider,uploaded.id())).isInstanceOf(BizException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=?",Long.class,bob)).isEqualTo(1);
    }
}
