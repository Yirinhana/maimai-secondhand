package com.maimai.integration.maps.js;

import com.maimai.common.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** JSAPI的同源代理。浏览器Key可公开，security code只在服务端追加。 */
@Service
public class JsAmapService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsAmapService.class);
    private static final Set<String> PATHS = Set.of(
            "/v3/geocode/geo", "/v3/geocode/regeo", "/v3/place/text", "/v3/place/around",
            "/v3/assistant/inputtips", "/v3/direction/driving", "/v3/direction/walking",
            "/v4/direction/bicycling", "/v5/direction/driving", "/v3/ip");
    private static final Pattern CALLBACK = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private final String key;
    private final String securityCode;
    private final ObjectMapper mapper;
    private final ConnectionFactory connections;

    @Autowired
    public JsAmapService(@Value("${MAIMAI_AMAP_JS_KEY:}") String key,
                         @Value("${MAIMAI_AMAP_SECURITY_CODE:}") String securityCode,
                         ObjectMapper mapper) {
        this(key, securityCode, mapper, url -> (HttpURLConnection) url.openConnection());
    }

    JsAmapService(String key, String securityCode, ObjectMapper mapper, ConnectionFactory connections) {
        this.key = key == null ? "" : key.strip();
        this.securityCode = securityCode == null ? "" : securityCode.strip();
        this.mapper = mapper;
        this.connections = connections;
    }

    public JsConfig getConfig() {
        boolean enabled = !key.isBlank() && !securityCode.isBlank();
        return new JsConfig(enabled, enabled ? key : "", "/_AMapService", "GCJ-02");
    }

    public ProxyResult proxy(String path, Map<String, List<String>> query) {
        if (!PATHS.contains(path)) throw invalid("不支持此地图请求路径");
        if (!getConfig().enabled()) throw new BizException("MAPS_NOT_CONFIGURED", "网页地图尚未配置", HttpStatus.SERVICE_UNAVAILABLE);
        Map<String, String> params = normalize(query);
        String callback = params.remove("callback");
        String jsonp = params.remove("jsonp");
        if (callback != null && jsonp != null && !callback.equals(jsonp)) throw invalid("地图回调参数不一致");
        if (callback == null) callback = jsonp;
        if (callback != null && (callback.length() > 128 || !CALLBACK.matcher(callback).matches())) throw invalid("地图回调参数无效");
        params.put("key", key);
        params.put("jscode", securityCode);
        // 上游只请求JSON，避免执行其返回的任意脚本；验证后再生成SDK需要的JSONP封装。
        params.put("output", "JSON");
        String encoded = params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        byte[] raw = fetch("https://restapi.amap.com" + path + "?" + encoded);
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.contains(securityCode)) {
            log.warn("AMap JS response rejected: credential isolation, path={}", path);
            throw unavailable();
        }
        String canonical;
        try {
            var tree = mapper.readTree(text);
            if (tree == null || !tree.isObject()) {
                log.warn("AMap JS response rejected: expected JSON object, path={}", path);
                throw unavailable();
            }
            canonical = mapper.writeValueAsString(tree);
        } catch (JacksonException error) {
            log.warn("AMap JS response rejected: invalid JSON, path={}", path);
            throw unavailable();
        }
        String result = callback == null ? canonical : callback + "(" + canonical + ");";
        return new ProxyResult(result.getBytes(StandardCharsets.UTF_8),
                callback == null ? "application/json;charset=UTF-8" : "application/javascript;charset=UTF-8");
    }

    private Map<String, String> normalize(Map<String, List<String>> query) {
        if (query == null || query.size() > 30) throw invalid("地图请求参数过多");
        Map<String, String> values = new LinkedHashMap<>();
        long size = 0;
        for (var entry : query.entrySet()) {
            String name = entry.getKey();
            if (name == null || !name.matches("[A-Za-z0-9_]{1,32}") || entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().size() > 2)
                throw invalid("地图请求参数无效");
            List<String> supplied = entry.getValue();
            for (String part : supplied) {
                if (part == null) throw invalid("地图请求参数无效");
                size += name.length() + part.length();
                if (size > 4096) throw invalid("地图请求过长");
            }
            // 逆地理编码 SDK 也会重复 key。所有客户端凭据都直接丢弃，
            // 不参加上游请求；proxy 只追加本站配置的一组 key/jscode。
            if (Set.of("key", "jscode", "sig").contains(name.toLowerCase(Locale.ROOT))) continue;
            String value = supplied.get(0);
            // JS SDK 的 POI 请求实际会发送两次相同的 s。只折叠这一已观察到的形式，
            // 不让回调或互相冲突的参数通过重复值校验。
            if (supplied.size() != 1 && !(name.equals("s") && supplied.size() == 2 && value.equals(supplied.get(1))))
                throw invalid("地图请求参数无效");
            values.put(name, value);
        }
        return values;
    }

    private byte[] fetch(String endpoint) {
        HttpURLConnection connection = null;
        // Actual JSAPI connection setup on the local network can take about 12 seconds.
        // Keep bounded socket reads while allowing time for a successful cold connection.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        try {
            connection = connections.open(URI.create(endpoint).toURL());
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                log.warn("AMap JS upstream HTTP status={}", status);
                throw unavailable();
            }
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                while (true) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        log.warn("AMap JS upstream exceeded total request deadline");
                        throw unavailable();
                    }
                    connection.setReadTimeout((int) Math.max(1, Math.min(8000, TimeUnit.NANOSECONDS.toMillis(remaining))));
                    int count = stream.read(buffer);
                    if (count == -1) break;
                    if (bytes.size() + count > 1024 * 1024) {
                        log.warn("AMap JS upstream exceeded response size limit");
                        throw unavailable();
                    }
                    bytes.write(buffer, 0, count);
                }
                return bytes.toByteArray();
            }
        } catch (IOException error) {
            // Exception messages may embed a credential-bearing URL. Keep only the error class.
            log.warn("AMap JS upstream transport failure: {}", error.getClass().getSimpleName());
            throw unavailable();
        }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static String encode(String value) {return URLEncoder.encode(value, StandardCharsets.UTF_8);}
    private static BizException invalid(String message) {return BizException.badRequest("MAPS_INVALID_REQUEST", message);}
    private static BizException unavailable() {return new BizException("MAPS_UPSTREAM_ERROR", "地图服务暂时不可用", HttpStatus.BAD_GATEWAY);}
    @FunctionalInterface interface ConnectionFactory {HttpURLConnection open(URL url) throws IOException;}
    public record JsConfig(boolean enabled, String key, String serviceHost, String coordinateSystem) {}
    public record ProxyResult(byte[] body, String contentType) {}
}
