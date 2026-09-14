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
        assertThatThrownBy(()->service.conversation(outsider,conversation)).isInstanceOf(BizException.class);
        assertThat(service.inboxOverview(outsider)).isEqualTo(new InboxOverview(0,0,0));
    }

    @Test void productCardKeepsSnapshotAndRetryAndRejectsAnotherSeller() {
        String title="咨询卡片-"+UUID.randomUUID();
        db.update("INSERT INTO categories(name) VALUES(?)",title);
        long category=db.queryForObject("SELECT id FROM categories WHERE name=?",Long.class,title);
        db.update("INSERT INTO products(seller_id,category_id,title,item_condition,price_cents,region,delivery_methods,status) VALUES(?,?,?,'GOOD',18000,'上海','EXPRESS','ON_SALE')",bob,category,title);
        long product=db.queryForObject("SELECT id FROM products WHERE title=?",Long.class,title);
        var request=new SendMessage(UUID.randomUUID(),null,null,product);
        var first=service.send(alice,conversation,request);
        assertThat(first.product().title()).isEqualTo(title);
        assertThat(service.conversations(bob,0,10).getFirst().lastMessage()).contains(title);
        db.update("UPDATE products SET title='修改后的标题',status='OFF_SHELF' WHERE id=?",product);
        assertThat(service.send(alice,conversation,request).id()).isEqualTo(first.id());
        assertThat(service.history(bob,conversation,null,10).items().getFirst().product().title()).isEqualTo(title);
        assertThatThrownBy(()->service.send(alice,conversation,new SendMessage(UUID.randomUUID(),null,null,product))).isInstanceOf(BizException.class);
        db.update("UPDATE products SET status='ON_SALE',seller_id=? WHERE id=?",outsider,product);
        assertThatThrownBy(()->service.send(alice,conversation,new SendMessage(UUID.randomUUID(),null,null,product))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.send(alice,conversation,new SendMessage(request.clientId(),null,null,product+1))).isInstanceOf(BizException.class);
    }

    @Test void inboxFiltersBeforePaginationAndScopesSearchToMember() {
        long first=user("目标商家一"),second=user("目标商家二"),unrelated=user("普通商家");
        long c1=service.open(alice,new OpenConversation(first,null));
        long c2=service.open(alice,new OpenConversation(second,null));
        service.open(alice,new OpenConversation(unrelated,null));
        assertThat(service.conversations(alice,0,1,"目标商家",false)).extracting(Conversation::id).containsExactly(c2);
        assertThat(service.conversations(alice,1,1,"目标商家",false)).extracting(Conversation::id).containsExactly(c1);
        assertThat(service.conversations(outsider,0,20,"目标商家",false)).isEmpty();
        assertThat(service.conversations(alice,0,20,"%",false)).isEmpty();
        assertThatThrownBy(()->service.conversations(alice,0,20,"a".repeat(101),false)).isInstanceOf(BizException.class);
    }

    @Test void inboxOverviewCountsIncomingUnreadAndTracksReadWithoutDeletingHistory() {
        var first=service.send(bob,conversation,new SendMessage(UUID.randomUUID(),"咨询一",null));
        var second=service.send(bob,conversation,new SendMessage(UUID.randomUUID(),"咨询二",null));
        service.send(alice,conversation,new SendMessage(UUID.randomUUID(),"回复",null));
        assertThat(service.inboxOverview(alice)).isEqualTo(new InboxOverview(1,1,2));
        assertThat(service.inboxOverview(bob)).isEqualTo(new InboxOverview(1,1,1));
        assertThat(service.conversations(alice,0,20,"",true)).extracting(Conversation::id).containsExactly(conversation);
        service.read(alice,conversation,first.id());
        assertThat(service.inboxOverview(alice).unreadMessages()).isEqualTo(1);
        service.read(alice,conversation,second.id());
        assertThat(service.inboxOverview(alice)).isEqualTo(new InboxOverview(1,0,0));
        assertThat(service.conversations(alice,0,20,"",true)).isEmpty();
        assertThat(service.history(alice,conversation,null,20).items()).hasSize(3);
    }

    @Test void conversationSummaryReturnsOnlyTheOtherMembersPublicIdentity() {
        String avatar=UUID.randomUUID()+".jpg";
        db.update("INSERT INTO user_avatars(user_id,filename,byte_size) VALUES(?,?,12)",bob,avatar);
        Conversation summary=service.conversation(alice,conversation);
        assertThat(summary.otherUserId()).isEqualTo(bob);
        assertThat(summary.otherNickname()).isEqualTo("乙");
        assertThat(summary.otherAvatarUrl()).isEqualTo("/api/v1/avatars/"+avatar);
        assertThat(service.conversation(bob,conversation).otherUserId()).isEqualTo(alice);
        assertThatThrownBy(()->service.conversation(alice,Long.MAX_VALUE)).isInstanceOf(BizException.class);
    }

    @Test void productSearchFindsAssociatedConversationWithoutExposingItToOthers() {
        String categoryName="私信分类-"+UUID.randomUUID(),title="复古相机-"+UUID.randomUUID();
        db.update("INSERT INTO categories(name) VALUES(?)",categoryName);
        long category=db.queryForObject("SELECT id FROM categories WHERE name=?",Long.class,categoryName);
        db.update("""
            INSERT INTO products(seller_id,category_id,title,item_condition,price_cents,region,delivery_methods,status)
            VALUES(?,?,?,'GOOD',18000,'上海','EXPRESS','ON_SALE')
            """,bob,category,title);
        long product=db.queryForObject("SELECT id FROM products WHERE title=?",Long.class,title);
        db.update("UPDATE message_conversations SET product_id=? WHERE id=?",product,conversation);
        assertThat(service.conversations(alice,0,20,"复古相机",false)).extracting(Conversation::id).containsExactly(conversation);
        assertThat(service.conversation(alice,conversation).productTitle()).isEqualTo(title);
        assertThat(service.conversations(outsider,0,20,"复古相机",false)).isEmpty();
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
