package com.maimai.integration.maps;

import com.maimai.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapsClientTest {

    private FakeTransport transport;
    private MapsClient client;

    @BeforeEach
    void setUp() {
        transport = new FakeTransport();
        transport.queue("{\"status\":\"1\",\"infocode\":\"10000\",\"geocodes\":[{\"formatted_address\":\"北京市海淀区中关村大街1号\",\"location\":\"116.310003,39.991957\"}]}");
        client = new MapsClient(new MapsProperties("test-key"), transport, new ObjectMapper());
    }

    @Test
    void geocode_success() {
        MapsDtos.GeoCodedLocation r = client.geocode("中关村大街1号", "北京");
        assertNotNull(r);
        assertEquals("北京市海淀区中关村大街1号", r.formattedAddress());
        assertEquals(116.310003, r.location().longitude(), 1e-6);
        assertEquals(39.991957, r.location().latitude(), 1e-6);
        assertEquals("GCJ-02",r.location().coordinateSystem());
    }

    @Test
    void geocode_empty_result_returns_not_found() {
        transport.queue("{\"status\":\"1\",\"infocode\":\"10000\",\"geocodes\":[]}");
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(BizException.class,()->client.geocode("不存在的地址XYZ", null)).getStatus());
    }

    @Test
    void geocode_blank_address_rejected() {
        assertThrows(BizException.class, () -> client.geocode("", null));
    }

    @Test
    void regeo_success() {
        transport.queue("{\"status\":\"1\",\"infocode\":\"10000\",\"regeocode\":{\"formatted_address\":\"北京市朝阳区\"}}");
        MapsDtos.ReverseGeoResult r = client.reverseGeocode(116.482, 39.921);
        assertNotNull(r);
        assertEquals("北京市朝阳区", r.formattedAddress());
        assertEquals(116.482, r.location().longitude(), 1e-6);
    }

    @Test
    void regeo_invalid_coordinate() {
        assertThrows(BizException.class, () -> client.reverseGeocode(Double.NaN, 0));
        assertThrows(BizException.class, () -> client.reverseGeocode(0, Double.POSITIVE_INFINITY));
        assertThrows(BizException.class, () -> client.reverseGeocode(200, 0));
        assertThrows(BizException.class, () -> client.reverseGeocode(0, -100));
    }

    @Test
    void aroundSearch_success_capped_by_max() {
        StringBuilder sb = new StringBuilder("{\"status\":\"1\",\"infocode\":\"10000\",\"pois\":[");
        for (int i = 0; i < 25; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"P").append(i).append("\",\"name\":\"POI").append(i)
              .append("\",\"address\":\"A")
              .append(i).append("\",\"location\":\"116.3").append(i).append(",39.9").append(i).append("\"}");
        }
        sb.append("]}");
        transport.queue(sb.toString());
        MapsDtos.AroundSearchResult r = client.aroundSearch(116.3, 39.9, "咖啡", 2000, 1, 20);
        assertEquals(20, r.pois().size());
    }

    @Test
    void aroundSearch_offset_over_20_rejected() {
        assertThrows(BizException.class,
                () -> client.aroundSearch(116.3, 39.9, "咖啡", 2000, 1, 21));
    }

    @Test
    void aroundSearch_skips_entries_with_missing_fields() {
        transport.queue("{\"status\":\"1\",\"infocode\":\"10000\",\"pois\":["
                + "{\"id\":\"P1\",\"name\":\"OK\",\"location\":\"116.3,39.9\"},"
                + "{\"id\":\"P2\",\"name\":\"BAD\",\"location\":\"not-a-pair\"},"
                + "{\"name\":\"NOID\",\"location\":\"116.3,39.9\"}"
                + "]}");
        MapsDtos.AroundSearchResult r = client.aroundSearch(116.3, 39.9, "咖啡", 2000, 1, 20);
        assertEquals(1, r.pois().size());
        assertEquals("P1", r.pois().get(0).id());
    }

    @Test
    void driving_success() {
        transport.queue("{\"status\":\"1\",\"infocode\":\"10000\",\"route\":{\"paths\":[{\"distance\":12345,\"cost\":{\"duration\":987}}]}}");
        MapsDtos.DrivingRoute r = client.drivingRoute(116.3, 39.9, 116.5, 39.95);
        assertEquals(12345L, r.distanceMeters());
        assertEquals(987L, r.durationSeconds());
    }

    @Test
    void driving_no_path_is_not_found() {
        transport.queue("{\"status\":\"1\",\"infocode\":\"10000\",\"route\":{\"paths\":[]}}");
        BizException ex = assertThrows(BizException.class,
                () -> client.drivingRoute(116.3, 39.9, 116.5, 39.95));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void driving_zero_distance_is_not_found() {
        transport.queue("{\"status\":\"1\",\"infocode\":\"10000\",\"route\":{\"paths\":[{\"distance\":0,\"cost\":{\"duration\":1}}]}}");
        assertThrows(BizException.class,
                () -> client.drivingRoute(116.3, 39.9, 116.5, 39.95));
    }

    @Test
    void provider_error_status_returns_502_and_no_url_leak() {
        transport.queue("{\"status\":\"0\",\"infocode\":\"20001\",\"info\":\"FAIL\"}");
        BizException ex = assertThrows(BizException.class,
                () -> client.geocode("中关村大街1号", null));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertTrue(!ex.getMessage().contains("test-key"));
        assertTrue(!ex.getMessage().contains("amap.com"));
    }

    @Test
    void provider_invalid_key_returns_503_without_logging_user_out() {
        transport.queue("{\"status\":\"0\",\"infocode\":\"10001\"}");
        BizException ex = assertThrows(BizException.class,
                () -> client.geocode("中关村大街1号", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void malformed_json_returns_502() {
        transport.queue("not-json-at-all");
        BizException ex = assertThrows(BizException.class,
                () -> client.geocode("中关村大街1号", null));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void missing_status_field_returns_502() {
        transport.queue("{\"infocode\":\"10000\"}");
        BizException ex = assertThrows(BizException.class,
                () -> client.geocode("中关村大街1号", null));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void missing_key_throws_503_and_does_not_call_transport() {
        MapsClient noKey = new MapsClient(new MapsProperties(""), transport, new ObjectMapper());
        int before = transport.calls.get();
        BizException ex = assertThrows(BizException.class,
                () -> noKey.geocode("中关村大街1号", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals(before, transport.calls.get());
    }

    @Test
    void ioexception_returns_502() {
        transport.queueError(new IOException("boom"));
        BizException ex = assertThrows(BizException.class,
                () -> client.geocode("中关村大街1号", null));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertTrue(!ex.getMessage().contains("boom"));
    }

    @Test
    void keyword_too_long_rejected() {
        String tooLong = "x".repeat(MapsClient.MAX_KEYWORD_LEN + 1);
        assertThrows(BizException.class,
                () -> client.aroundSearch(116.3, 39.9, tooLong, 1000, 1, 10));
    }

    /** 仅记录被调用的相对路径与参数个数，不记录实际查询串/密钥，避免日志外泄。 */
    static class FakeTransport implements MapsTransport {
        final java.util.Deque<String> responses = new java.util.ArrayDeque<>();
        final java.util.Deque<IOException> errors = new java.util.ArrayDeque<>();
        final AtomicInteger calls = new AtomicInteger();
        volatile String lastUrlHint;
        volatile String lastPath;

        void queue(String body) {
            responses.clear();
            responses.add(body);
        }

        void queueError(IOException e) {
            errors.add(e);
        }

        @Override
        public String get(String path, Map<String, String> params) throws IOException {
            calls.incrementAndGet();
            lastPath = path;
            lastUrlHint = "key=test-key&" + (params == null ? 0 : params.size()) + " params";
            if (!errors.isEmpty()) {
                throw errors.poll();
            }
            if (responses.isEmpty()) {
                throw new IOException("no fixture");
            }
            return responses.poll();
        }
    }
}
