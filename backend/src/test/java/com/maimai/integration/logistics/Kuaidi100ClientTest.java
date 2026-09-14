package com.maimai.integration.logistics;

import com.maimai.common.BizException;
import com.maimai.integration.logistics.Kuaidi100Client.Kuaidi100TraceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Kuaidi100Client}. The transport is replaced by a scripted
 * in-memory fake so no network calls are made. Each test asserts the post body that
 * the client constructed, the signature it computed, and the decoded result.
 */
class Kuaidi100ClientTest {

    private static final String CUSTOMER = "testcustomer";
    private static final String KEY = "testkey";

    private ScriptedTransport transport;
    private Kuaidi100Client client;

    @BeforeEach
    void setUp() {
        transport = new ScriptedTransport();
        Kuaidi100Properties props = new Kuaidi100Properties(CUSTOMER, KEY);
        client = new Kuaidi100Client(props, transport, new ObjectMapper());
    }

    @Test
    @DisplayName("sign() matches a independently computed fixed vector")
    void signsFixedVector() {
        // Independently computed with .NET MD5.HashData over the documented byte concatenation.
        String param = "{\"com\":\"yuantong\",\"num\":\"YT123456789\",\"show\":\"0\",\"order\":\"desc\"}";
        String expected = "F77C1F44B9AABCB5DDA057DC866FF170";
        String actual = Kuaidi100Client.sign(param, KEY, CUSTOMER);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("signed param string round-trips identically through URL form encoding")
    void signsParamWithChinese() {
        String param = client.buildParamJson("shunfeng", "SF1234567890CN", "13800138000");
        // The encoded form body must URL-encode the JSON verbatim — no second serialisation.
        transport.nextResponse("{\"status\":\"200\",\"com\":\"shunfeng\",\"nu\":\"SF1234567890CN\",\"state\":\"0\",\"data\":[]}");
        Kuaidi100TraceResult result = client.query(
                new Kuaidi100Client.QueryRequest("SHUNFENG", "SF1234567890CN", "13800138000"));
        assertNotNull(result);
        assertEquals("shunfeng", result.carrier());
        assertEquals("SF1234567890CN", result.number());
        String signed = Kuaidi100Client.sign(param, KEY, CUSTOMER);
        assertTrue(transport.lastBody().contains("sign=" + signed));
        assertTrue(transport.lastBody().contains("param=" + java.net.URLEncoder.encode(param, java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(transport.lastBody().contains("customer=" + java.net.URLEncoder.encode(CUSTOMER, java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("no outbound request is issued when credentials are missing")
    void missingCredentialsThrows() {
        Kuaidi100Client unconfigured = new Kuaidi100Client(
                new Kuaidi100Properties("", ""), transport, new ObjectMapper());
        BizException ex = assertThrows(BizException.class, () -> unconfigured.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null)));
        assertEquals("LOGISTICS_NOT_CONFIGURED", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals(0, transport.callCount());
    }

    @Test void rejectsInvalidRequestsBeforeTransport() {
        for (var request : List.of(
                new Kuaidi100Client.QueryRequest("yuantong", null, null),
                new Kuaidi100Client.QueryRequest("yuantong", "short", null),
                new Kuaidi100Client.QueryRequest("https://evil.invalid", "YT123456789", null),
                new Kuaidi100Client.QueryRequest("shunfeng", "SF123456789", null),
                new Kuaidi100Client.QueryRequest("zhongtong", "ZT123456789", "unsafe&phone"))) {
            assertThrows(BizException.class, () -> client.query(request));
        }
        assertEquals(0, transport.callCount());
    }

    @Test void missingStateOrTraceArrayIsNotInvented() {
        for (String body : List.of(
                "{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\",\"data\":[]}",
                "{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\",\"state\":\"0\"}")) {
            transport.nextResponse(body);
            assertThrows(BizException.class, () -> client.query(new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null)));
        }
    }

    @Test
    @DisplayName("in-transit (state=0) is parsed and traces are returned in order")
    void successInTransit() {
        transport.nextResponse("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\","
                + "\"state\":\"0\","
                + "\"data\":[{\"time\":\"2024-05-01 10:00\",\"context\":\"揽收\"},"
                + "{\"time\":\"2024-05-02 12:00\",\"context\":\"到达广州中转站\"}]}");
        Kuaidi100TraceResult r = client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null));
        assertEquals(Kuaidi100Client.NormalizedState.IN_TRANSIT, r.normalizedState());
        assertFalse(r.signed());
        assertFalse(r.exception());
        assertEquals(2, r.traces().size());
        assertEquals("2024-05-01 10:00", r.traces().get(0).time());
    }

    @Test
    @DisplayName("state=3 means SIGNED but NOT a user-confirmed receipt")
    void successSigned() {
        transport.nextResponse("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\","
                + "\"state\":\"3\",\"data\":[{\"time\":\"2024-05-03 09:00\",\"context\":\"签收\"}]}");
        Kuaidi100TraceResult r = client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null));
        assertEquals(Kuaidi100Client.NormalizedState.SIGNED, r.normalizedState());
        assertTrue(r.signed());
        assertFalse(r.exception());
        assertEquals("3", r.rawState());
    }

    @Test
    @DisplayName("state=2 (疑难) is mapped to EXCEPTION")
    void successException() {
        transport.nextResponse("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\","
                + "\"state\":\"2\",\"data\":[]}");
        Kuaidi100TraceResult r = client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null));
        assertEquals(Kuaidi100Client.NormalizedState.EXCEPTION, r.normalizedState());
        assertTrue(r.exception());
    }

    @Test
    @DisplayName("state=4 (退签) is EXCEPTION")
    void successReturned() {
        transport.nextResponse("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\",\"state\":\"4\",\"data\":[]}");
        Kuaidi100TraceResult r = client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null));
        assertTrue(r.exception());
        assertEquals(Kuaidi100Client.NormalizedState.EXCEPTION, r.normalizedState());
    }

    @Test
    @DisplayName("state=14 (拒签) is EXCEPTION")
    void successRejected() {
        transport.nextResponse("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\",\"state\":\"14\",\"data\":[]}");
        Kuaidi100TraceResult r = client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null));
        assertTrue(r.exception());
    }

    @Test
    @DisplayName("unknown raw state is mapped to UNKNOWN, never auto-completed")
    void unknownState() {
        transport.nextResponse("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\",\"state\":\"9999\",\"data\":[]}");
        Kuaidi100TraceResult r = client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null));
        assertEquals(Kuaidi100Client.NormalizedState.UNKNOWN, r.normalizedState());
        assertFalse(r.signed());
        assertFalse(r.exception());
    }

    @Test
    @DisplayName("empty data array is a legal no-trace response")
    void noTracesYet() {
        transport.nextResponse("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\",\"state\":\"0\",\"data\":[]}");
        Kuaidi100TraceResult r = client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null));
        assertEquals(Kuaidi100Client.NormalizedState.IN_TRANSIT, r.normalizedState());
        assertEquals(0, r.traces().size());
    }

    @Test
    @DisplayName("response number that does not match the request is rejected")
    void responseNumberMismatch() {
        transport.nextResponse("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"DIFFERENT\",\"state\":\"0\",\"data\":[]}");
        BizException ex = assertThrows(BizException.class, () -> client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null)));
        assertEquals("LOGISTICS_RESPONSE_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("non-2xx is reported as upstream failure, not as success")
    void httpFailure() {
        transport.nextTransportFailure(new java.io.IOException("Kuaidi100 HTTP 500"));
        BizException ex = assertThrows(BizException.class, () -> client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null)));
        assertEquals("LOGISTICS_TRANSPORT_ERROR", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    @DisplayName("upstream status!=200 is reported as upstream error")
    void upstreamError() {
        transport.nextResponse("{\"status\":\"400\",\"returnCode\":\"501\",\"message\":\"bad sign\"}");
        BizException ex = assertThrows(BizException.class, () -> client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null)));
        assertEquals("LOGISTICS_UPSTREAM_ERROR", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    @DisplayName("malformed JSON response is reported as bad gateway")
    void malformedJson() {
        transport.nextResponse("{not-json");
        BizException ex = assertThrows(BizException.class, () -> client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null)));
        assertEquals("LOGISTICS_BAD_RESPONSE", ex.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    @DisplayName("response missing required fields is rejected")
    void missingFields() {
        transport.nextResponse("{\"status\":\"200\",\"state\":\"0\",\"data\":[]}");
        BizException ex = assertThrows(BizException.class, () -> client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null)));
        assertEquals("LOGISTICS_BAD_RESPONSE", ex.getCode());
    }

    @Test
    @DisplayName("trace list is capped at 100 entries")
    void tracesCappedAt100() {
        StringBuilder sb = new StringBuilder("{\"status\":\"200\",\"com\":\"yuantong\",\"nu\":\"YT123456789\",\"state\":\"0\",\"data\":[");
        for (int i = 0; i < 150; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"time\":\"2024-05-01 10:00\",\"context\":\"step").append(i).append("\"}");
        }
        sb.append("]}");
        transport.nextResponse(sb.toString());
        Kuaidi100TraceResult r = client.query(
                new Kuaidi100Client.QueryRequest("yuantong", "YT123456789", null));
        assertEquals(100, r.traces().size());
    }

    /** Minimal deterministic transport stub for unit tests. */
    private static final class ScriptedTransport implements Kuaidi100Transport {
        private final List<String> responses = new ArrayList<>();
        private final List<java.io.IOException> failures = new ArrayList<>();
        private final AtomicReference<String> lastBody = new AtomicReference<>();
        private int callCount;

        void nextResponse(String body) {
            responses.add(body);
        }

        void nextTransportFailure(java.io.IOException ex) {
            failures.add(ex);
        }

        String lastBody() {
            return lastBody.get();
        }

        int callCount() {
            return callCount;
        }

        @Override
        public String post(String formBody) throws java.io.IOException {
            callCount++;
            lastBody.set(formBody);
            if (!failures.isEmpty()) {
                throw failures.remove(0);
            }
            if (responses.isEmpty()) {
                throw new java.io.IOException("no scripted response");
            }
            return responses.remove(0);
        }
    }
}
