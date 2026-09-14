package com.maimai.messaging;

import com.maimai.common.BizException;
import com.maimai.messaging.dto.MessageDtos.*;
import com.maimai.messaging.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MessageConcurrencyTest {
    @Autowired MessageService messages;
    @Autowired JdbcTemplate db;
    private long sender,recipient,conversation;

    @Test
    void senderQuotaRemainsThirtyAcrossConcurrentTransactions() throws Exception {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("maimai_test");
        sender=createUser();recipient=createUser();
        conversation=messages.open(sender,new OpenConversation(recipient,null));
        try(ExecutorService workers=Executors.newFixedThreadPool(8)) {
            CountDownLatch start=new CountDownLatch(1);
            List<Future<String>> results=new ArrayList<>();
            for(int i=0;i<40;i++) results.add(workers.submit(()->{
                start.await();
                try {messages.send(sender,conversation,new SendMessage(UUID.randomUUID(),"并发消息",null));return "SENT";}
                catch(BizException ex) {return ex.getCode();}
            }));
            start.countDown();
            List<String> statuses=new ArrayList<>();
            for(Future<String> result:results) statuses.add(result.get(45,TimeUnit.SECONDS));
            assertThat(statuses.stream().filter("SENT"::equals).count()).isEqualTo(30);
            assertThat(statuses.stream().filter("RATE_LIMITED"::equals).count()).isEqualTo(10);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM direct_messages WHERE conversation_id=?",Long.class,conversation)).isEqualTo(30);
        }
    }

    @AfterEach
    void removeOnlyThisTestsCommittedRows() {
        if(conversation>0) {
            db.update("DELETE FROM direct_messages WHERE conversation_id=?",conversation);
            db.update("DELETE FROM message_read_positions WHERE conversation_id=?",conversation);
            db.update("DELETE FROM message_conversations WHERE id=?",conversation);
        }
        if(sender>0) db.update("DELETE FROM message_user_blocks WHERE blocker_id=? OR blocked_id=?",sender,sender);
        if(sender>0) db.update("DELETE FROM users WHERE id=?",sender);
        if(recipient>0) db.update("DELETE FROM users WHERE id=?",recipient);
    }
    private long createUser() {
        String email="msg-concurrent-"+UUID.randomUUID()+"@example.test";
        db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-only','并发测试','ACTIVE')",email);
        return db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }

    @Test void committedBlockAppliesToConcurrentSendsAndCannotBeDuplicated() throws Exception {
        sender=createUser();recipient=createUser();
        conversation=messages.open(sender,new OpenConversation(recipient,null));
        try(var workers=Executors.newFixedThreadPool(8)) {
            var blocks=new ArrayList<Future<?>>();
            for(int i=0;i<8;i++) blocks.add(workers.submit(()->messages.setBlock(recipient,sender,true)));
            for(var future:blocks) future.get(20,TimeUnit.SECONDS);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM message_user_blocks WHERE blocker_id=? AND blocked_id=?",Long.class,recipient,sender)).isEqualTo(1);
            var sends=new ArrayList<Future<String>>();
            for(int i=0;i<8;i++) sends.add(workers.submit(()->{
                try {messages.send(sender,conversation,new SendMessage(UUID.randomUUID(),"屏蔽后的新消息",null));return "SENT";}
                catch(BizException error){return error.getCode();}
            }));
            for(var future:sends) assertThat(future.get(20,TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM direct_messages WHERE conversation_id=?",Long.class,conversation)).isZero();
        }
    }
}
