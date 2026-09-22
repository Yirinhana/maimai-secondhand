package com.maimai.support.ai;

import com.maimai.common.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Fixed connection timeout, bounded body subscriber and a deadline covering the complete response body. */
@Component
public class HermesHttpTransport implements HermesTransport {
    record Response(int status,byte[] body) { }
    interface Sender { CompletableFuture<Response> send(HttpRequest request); }
    private final Sender sender;
    private final Duration timeout;
    @Autowired
    public HermesHttpTransport() {
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        this.sender=request->{
            var wire=client.sendAsync(request,info->new LimitedBody());
            var result=wire.thenApply(r->new Response(r.statusCode(),r.body()));
            result.whenComplete((value,error)->{if(result.isCancelled()) wire.cancel(true);});
            return result;
        };
        this.timeout=Duration.ofSeconds(60);
    }
    HermesHttpTransport(Sender sender,Duration timeout) { this.sender=sender; this.timeout=timeout; }
    @Override
    public String post(URI endpoint,String token,String body) {
        var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Authorization","Bearer "+token)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        var future=sender.send(request);
        try {
            var response=future.get(timeout.toNanos(),TimeUnit.NANOSECONDS);
            if(response.status()<200||response.status()>=300) throw new BizException("AI_UNAVAILABLE","智能客服暂不可用，请联系人工客服",HttpStatus.SERVICE_UNAVAILABLE);
            return new String(response.body(),StandardCharsets.UTF_8);
        } catch(TimeoutException ex) {
            future.cancel(true); throw new BizException("AI_TIMEOUT","智能客服响应超时，请联系人工客服",HttpStatus.GATEWAY_TIMEOUT);
        } catch(InterruptedException ex) {
            future.cancel(true); Thread.currentThread().interrupt(); throw unavailable();
        } catch(ExecutionException ex) {
            if(ex.getCause() instanceof HttpTimeoutException) throw new BizException("AI_TIMEOUT","智能客服响应超时，请联系人工客服",HttpStatus.GATEWAY_TIMEOUT);
            throw unavailable();
        }
    }
    private static BizException unavailable() { return new BizException("AI_UNAVAILABLE","智能客服连接失败，请联系人工客服",HttpStatus.SERVICE_UNAVAILABLE); }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if(buffer.remaining()>65536-output.size()) { subscription.cancel(); result.completeExceptionally(new IllegalStateException("response exceeds limit")); return; }
                byte[] part=new byte[buffer.remaining()]; buffer.get(part); output.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(output.toByteArray()); }
    }
}
