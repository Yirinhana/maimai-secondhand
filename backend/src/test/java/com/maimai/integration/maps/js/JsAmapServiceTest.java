package com.maimai.integration.maps.js;

import com.maimai.common.BizException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class JsAmapServiceTest {
    private static final String CODE = "test-only-private-code";
    @Test void overridesSdkKeyAddsPrivateCodeAndWrapsValidatedJsonp() {
        var requested = new AtomicReference<URL>();
        var connection = new Stub(200, "{\"status\":\"1\",\"result\":\"上海\"}");
        var service = new JsAmapService("public-test-key", CODE, new ObjectMapper(), url -> {requested.set(url);return connection;});
        var response = service.proxy("/v3/geocode/geo", Map.of("address", List.of("上海 人民广场"),
                "callback", List.of("AMap._jsonp.callback1"), "key", List.of("attacker-key"), "jscode", List.of("attacker-code")));
        Map<String, String> query = new HashMap<>();
        for (String item : requested.get().getQuery().split("&")) {
            String[] parts = item.split("=", 2);
            query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        assertThat(requested.get().getHost()).isEqualTo("restapi.amap.com");
        assertThat(query).containsEntry("key", "public-test-key").containsEntry("jscode", CODE)
                .containsEntry("address", "上海 人民广场").containsEntry("output", "JSON");
        assertThat(query).doesNotContainKey("callback");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("AMap._jsonp.callback1({\"status\":\"1\",\"result\":\"上海\"});")
                .doesNotContain(CODE);
        assertThat(response.contentType()).startsWith("application/javascript");
        assertThat(connection.closed).isTrue();
        assertThat(connection.disconnected).isTrue();
        assertThat(connection.getConnectTimeout()).isEqualTo(3000);
        assertThat(connection.getReadTimeout()).isBetween(1, 8000);
        assertThat(connection.getInstanceFollowRedirects()).isFalse();
    }

    @Test void rejectsPathCallbackDuplicatesAndUnconfiguredBeforeNetwork() {
        var service = new JsAmapService("public-test-key", CODE, new ObjectMapper(), url -> {throw new AssertionError("must not connect");});
        for (String path : List.of("https://evil.invalid", "/v3/../ip", "/v3/batch", "/v3/ip/extra"))
            assertThatThrownBy(() -> service.proxy(path, Map.of())).isInstanceOf(BizException.class);
        for (String callback : List.of("alert(1)", "foo;bar", "1callback", "foo.1bar", "foo\nbar", "x".repeat(129)))
            assertThatThrownBy(() -> service.proxy("/v3/ip", Map.of("callback", List.of(callback)))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.proxy("/v3/ip", Map.of("callback", List.of("one", "two")))).isInstanceOf(BizException.class);
        var disabled = new JsAmapService("public-test-key", "", new ObjectMapper(), url -> {throw new AssertionError("must not connect");});
        assertThat(disabled.getConfig().enabled()).isFalse();
        assertThat(disabled.getConfig().key()).isEmpty();
        assertThatThrownBy(() -> disabled.proxy("/v3/ip", Map.of())).isInstanceOf(BizException.class);
    }

    @Test void acceptsSimpleCallbackAndRejectsUntrustedBodiesOrLeakedCode() {
        var good = new Stub(200, "{\"status\":\"0\",\"info\":\"INVALID_USER_SCODE\"}");
        var service = new JsAmapService("public-test-key", CODE, new ObjectMapper(), url -> good);
        assertThat(new String(service.proxy("/v3/ip", Map.of("callback", List.of("jsonp_1"))).body(), StandardCharsets.UTF_8))
                .startsWith("jsonp_1(").contains("INVALID_USER_SCODE");
        for (String body : List.of("<html>upstream error</html>", "alert(1)", "[]", "{\"secret\":\""+CODE+"\"}", "x".repeat(1024*1024+1))) {
            var connection = new Stub(200, body);
            var bad = new JsAmapService("public-test-key", CODE, new ObjectMapper(), url -> connection);
            assertThatThrownBy(() -> bad.proxy("/v3/ip", Map.of())).isInstanceOf(BizException.class);
            assertThat(connection.disconnected).isTrue();
            assertThat(connection.closed).isTrue();
        }
    }

    @Test void rejectsRedirectsAndHttpErrors() {
        for (int status : new int[]{302, 401, 500}) {
            var connection = new Stub(status, "ignored");
            var service = new JsAmapService("public-test-key", CODE, new ObjectMapper(), url -> connection);
            assertThatThrownBy(() -> service.proxy("/v3/ip", Map.of())).isInstanceOfSatisfying(BizException.class,
                    error -> assertThat(error.getStatus().value()).isEqualTo(502));
            assertThat(connection.disconnected).isTrue();
        }
    }

    @Test void canonicalizesOnlyTheSdkIdenticalDuplicateSParameter() {
        var requested = new AtomicReference<URL>();
        var service = new JsAmapService("public-test-key", CODE, new ObjectMapper(), url -> {
            requested.set(url);
            return new Stub(200, "{\"status\":\"1\",\"pois\":[]}");
        });
        service.proxy("/v3/place/text", Map.of("s", List.of("rsv3", "rsv3"), "keywords", List.of("上海人民广场")));
        assertThat(Arrays.stream(requested.get().getQuery().split("&")).filter(item -> item.startsWith("s=")))
                .containsExactly("s=rsv3");
        var noNetwork = new JsAmapService("public-test-key", CODE, new ObjectMapper(), url -> { throw new AssertionError("must not connect"); });
        for (var query : List.of(Map.of("s", List.of("one", "two")), Map.of("s", List.of("one", "one", "one")),
                Map.of("callback", List.of("cb", "cb")), Map.of("key", List.of("one", "one", "one")))) {
            assertThatThrownBy(() -> noNetwork.proxy("/v3/place/text", query)).isInstanceOf(BizException.class);
        }
    }

    @Test void discardsDuplicatedAndCaseVariantClientCredentialsBeforeUsingServerConfiguration() {
        var requested = new AtomicReference<URL>();
        var service = new JsAmapService("public-test-key", CODE, new ObjectMapper(), url -> {
            requested.set(url);
            return new Stub(200, "{\"status\":\"1\"}");
        });
        service.proxy("/v3/geocode/regeo", Map.of("key", List.of("sdk-key", "different-client-key"),
                "KEY", List.of("case-variant-key"), "jscode", List.of("one", "two"), "SIG", List.of("one", "two"),
                "s", List.of("rsv3", "rsv3"), "location", List.of("121.4737,31.2304")));
        var query = Arrays.asList(requested.get().getQuery().split("&"));
        assertThat(query.stream().filter(item -> item.startsWith("key="))).containsExactly("key=public-test-key");
        assertThat(query.stream().filter(item -> item.startsWith("jscode="))).containsExactly("jscode=" + CODE);
        assertThat(requested.get().getQuery()).doesNotContain("sdk-key", "different-client-key", "case-variant-key", "KEY=", "SIG=", "sig=");
    }

    private static class Stub extends HttpURLConnection {
        final int status; final byte[] bytes; boolean closed,disconnected;
        Stub(int status, String body) {super(url());this.status=status;this.bytes=body.getBytes(StandardCharsets.UTF_8);}
        private static URL url() {try{return URI.create("https://restapi.amap.com/v3/ip").toURL();}catch(Exception e){throw new AssertionError(e);}}
        @Override public int getResponseCode() {return status;}
        @Override public InputStream getInputStream() {return new ByteArrayInputStream(bytes){@Override public void close(){closed=true;}};}
        @Override public void disconnect() {disconnected=true;}
        @Override public boolean usingProxy() {return false;}
        @Override public void connect() {}
    }
}
