package com.maimai.common.mail;

import com.maimai.common.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpMailServiceTest {
    @Test void buildsUtf8MessageWithOnlyIntendedRecipient() {
        var transport = mock(JavaMailSender.class);
        new SmtpMailService(transport, "sender@example.test", true).send("buyer@example.test", "麦麦二手验证码", "验证码：123456");
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(transport).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("buyer@example.test");
        assertThat(captor.getValue().getFrom()).isEqualTo("sender@example.test");
        assertThat(captor.getValue().getText()).isEqualTo("验证码：123456");
        assertThat(captor.getValue().getBcc()).isNull();
    }
    @Test void unconfiguredOrHeaderInjectionDoesNotSend() {
        var transport = mock(JavaMailSender.class);
        assertThatThrownBy(() -> new SmtpMailService(transport, "sender@example.test", false).send("buyer@example.test", "test", "test"))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getCode()).isEqualTo("MAIL_NOT_CONFIGURED"));
        var service = new SmtpMailService(transport, "sender@example.test", true);
        assertThatThrownBy(() -> service.send("buyer@example.test\r\nBcc:other@example.test", "test", "test")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.send("buyer@example.test", "test\r\nBcc:other@example.test", "test")).isInstanceOf(BizException.class);
        verifyNoInteractions(transport);
    }
    @Test void providerFailureIsSanitizedAndNeverPretendsDeliverySucceeded() {
        var transport = mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("sensitive-password-and-address")).when(transport).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new SmtpMailService(transport, "sender@example.test", true).send("buyer@example.test", "test", "test"))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MAIL_SEND_FAILED");
                    assertThat(error.getMessage()).doesNotContain("sensitive-password-and-address");
                });
    }
    @Test void sslAndStartTlsBothRequireEncryptedVerifiedConnectionsWithFiniteTimeouts() {
        for (boolean ssl : new boolean[]{true, false}) {
            var sender = SmtpMailService.createSender("smtp.example.test", ssl ? 465 : 587, "fixture", "test-only", ssl);
            var props = sender.getJavaMailProperties();
            assertThat(props.getProperty("mail.smtp.ssl.enable")).isEqualTo(Boolean.toString(ssl));
            assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo(Boolean.toString(!ssl));
            assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
            assertThat(props.getProperty("mail.smtp.connectiontimeout")).isEqualTo("3000");
            assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("5000");
            assertThat(props.getProperty("mail.smtp.writetimeout")).isEqualTo("5000");
        }
    }
}
