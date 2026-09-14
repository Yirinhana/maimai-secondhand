package com.maimai.integration.logistics;

import com.maimai.common.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Kuaidi100 polling client.
 *
 * <p>This class is intentionally transport-only: it knows how to build the form payload,
 * sign it according to the official rule {@code MD5(param + key + customer)}, decode the
 * response and normalise the state into the small set of values consumed by the order
 * service. Rate limiting, caching, persistence and ordering of polls for the same
 * tracking number are responsibilities of the caller — Kuaidi100 may lock a tracking
 * number if polled more frequently than once every 30 minutes.
 */
@Component
public class Kuaidi100Client {

    private static final int MAX_TRACES = 100;
    private static final Pattern NUM_PATTERN = Pattern.compile("^[A-Za-z0-9\\-]{6,32}$");

    private final Kuaidi100Properties properties;
    private final Kuaidi100Transport transport;
    private final ObjectMapper objectMapper;

    @Autowired
    public Kuaidi100Client(Kuaidi100Properties properties,
                           Kuaidi100Transport transport,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    /** Constructor used by tests that want full control over collaborators. */
    public Kuaidi100Client(Kuaidi100Properties properties,
                           Kuaidi100Transport transport) {
        this(properties, transport, new ObjectMapper());
    }

    public Kuaidi100TraceResult query(QueryRequest request) {
        if (request == null) {
            throw BizException.badRequest("LOGISTICS_INVALID_REQUEST", "query request is required");
        }
        String carrier = normalizeCarrier(request.carrier());
        String number = request.number();
        if (number == null || !NUM_PATTERN.matcher(number).matches()) {
            throw BizException.badRequest("LOGISTICS_INVALID_NUMBER", "tracking number length must be 6-32");
        }
        if (!properties.isConfigured()) {
            throw new BizException("LOGISTICS_NOT_CONFIGURED",
                    "Kuaidi100 credentials are not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (Set.of("shunfeng", "shunfengkuaiyun", "zhongtong").contains(carrier)
                && (request.phone() == null || request.phone().isBlank())) {
            throw BizException.badRequest("LOGISTICS_PHONE_REQUIRED", "此承运商查询需要订单联系电话");
        }
        if (request.phone() != null && !request.phone().isBlank()
                && !request.phone().matches("[0-9+\\-]{4,20}")) {
            throw BizException.badRequest("LOGISTICS_INVALID_PHONE", "物流查询联系电话格式错误");
        }

        String paramJson = buildParamJson(carrier, number, request.phone());
        String sign = sign(paramJson, properties.getKey(), properties.getCustomer());

        String formBody = "customer=" + urlEncode(properties.getCustomer())
                + "&sign=" + urlEncode(sign)
                + "&param=" + urlEncode(paramJson);

        String body;
        try {
            body = transport.post(formBody);
        } catch (IOException | RuntimeException e) {
            throw mapTransportFailure(e);
        }

        return parseResponse(body, carrier, number);
    }

    /** Build the {@code param} JSON exactly once, in the order required by the API. */
    String buildParamJson(String carrier, String number, String phone) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("com", carrier);
        map.put("num", number);
        if (phone != null && !phone.isBlank()) {
            map.put("phone", phone);
        }
        map.put("show", "0");
        map.put("order", "desc");
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JacksonException e) {
            throw BizException.badRequest("LOGISTICS_PARAM_ENCODE", "failed to serialise param");
        }
    }

    /** MD5(param + key + customer), UTF-8 bytes, 32 character upper-case hex. */
    static String sign(String param, String key, String customer) {
        byte[] data = (param + key + customer).getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    private Kuaidi100TraceResult parseResponse(String body, String requestedCarrier, String requestedNumber) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new BizException("LOGISTICS_BAD_RESPONSE",
                    "Kuaidi100 returned invalid JSON", HttpStatus.BAD_GATEWAY);
        }
        if (root == null || !root.isObject()) {
            throw new BizException("LOGISTICS_BAD_RESPONSE",
                    "Kuaidi100 returned non-object payload", HttpStatus.BAD_GATEWAY);
        }

        String status = textOrNull(root.get("status"));
        if (!"200".equals(status)) {
            String code = textOrNull(root.get("returnCode"));
            // Upstream credential problems are reported as 502/503 so that a failed
            // integration never logs the user out of the storefront.
            HttpStatus http = upstreamCredentialProblem(code)
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY;
            throw new BizException("LOGISTICS_UPSTREAM_ERROR",
                    "物流服务暂时不可用，请稍后查看",
                    http);
        }

        String carrier = textOrNull(root.get("com"));
        String number = textOrNull(root.get("nu"));
        if (carrier == null || number == null) {
            throw new BizException("LOGISTICS_BAD_RESPONSE",
                    "Kuaidi100 response missing com/nu", HttpStatus.BAD_GATEWAY);
        }
        if (!carrier.equalsIgnoreCase(requestedCarrier) || !number.equals(requestedNumber)) {
            throw new BizException("LOGISTICS_RESPONSE_MISMATCH",
                    "Kuaidi100 response did not match request", HttpStatus.BAD_GATEWAY);
        }

        String state = textOrNull(root.get("state"));
        if (state == null) {
            throw new BizException("LOGISTICS_BAD_RESPONSE", "物流服务返回缺少状态", HttpStatus.BAD_GATEWAY);
        }
        NormalizedState normalized = mapState(state);
        boolean signed = normalized == NormalizedState.SIGNED;
        boolean exception = normalized == NormalizedState.EXCEPTION;
        List<TraceEntry> traces = readTraces(root.get("data"));

        return new Kuaidi100TraceResult(
                carrier,
                number,
                state,
                normalized,
                signed,
                exception,
                traces,
                Instant.now());
    }

    private static NormalizedState mapState(String state) {
        if (state == null) return NormalizedState.UNKNOWN;
        switch (state) {
            case "0": return NormalizedState.IN_TRANSIT;
            case "1": return NormalizedState.IN_TRANSIT; // pickup accepted, still moving
            case "3": return NormalizedState.SIGNED;
            case "5": return NormalizedState.IN_TRANSIT; // out for delivery
            case "8": return NormalizedState.IN_TRANSIT; // customs cleared
            case "2":
            case "4":
            case "14":
                return NormalizedState.EXCEPTION;
            default:
                return NormalizedState.UNKNOWN;
        }
    }

    private static boolean upstreamCredentialProblem(String code) {
        if (code == null) return false;
        return code.startsWith("5") || code.equals("501") || code.equals("502")
                || code.equalsIgnoreCase("AUTH") || code.equalsIgnoreCase("SIGN");
    }

    private static List<TraceEntry> readTraces(JsonNode data) {
        if (data == null || !data.isArray()) {
            throw new BizException("LOGISTICS_BAD_RESPONSE",
                    "Kuaidi100 data must be an array", HttpStatus.BAD_GATEWAY);
        }
        List<TraceEntry> out = new ArrayList<>();
        int count = 0;
        for (JsonNode entry : data) {
            if (!entry.isObject()) {
                throw new BizException("LOGISTICS_BAD_RESPONSE",
                        "Kuaidi100 trace entry must be an object", HttpStatus.BAD_GATEWAY);
            }
            String time = textOrNull(entry.get("time"));
            String context = textOrNull(entry.get("context"));
            if (time == null || context == null) {
                throw new BizException("LOGISTICS_BAD_RESPONSE",
                        "Kuaidi100 trace missing time/context", HttpStatus.BAD_GATEWAY);
            }
            out.add(new TraceEntry(time, context));
            count++;
            if (count >= MAX_TRACES) break;
        }
        return List.copyOf(out);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual() && !node.isNumber()) return null;
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String normalizeCarrier(String carrier) {
        if (carrier == null) {
            throw BizException.badRequest("LOGISTICS_INVALID_CARRIER", "carrier is required");
        }
        String lower = carrier.trim().toLowerCase(Locale.ROOT);
        if (!lower.matches("[a-z0-9_]{2,40}")) {
            throw BizException.badRequest("LOGISTICS_INVALID_CARRIER", "carrier is required");
        }
        return lower;
    }

    private static BizException mapTransportFailure(Exception ex) {
        if (ex instanceof BizException biz) return biz;
        return new BizException("LOGISTICS_TRANSPORT_ERROR",
                "Kuaidi100 transport failure", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public record QueryRequest(String carrier, String number, String phone) { }

    public enum NormalizedState {
        IN_TRANSIT, SIGNED, EXCEPTION, UNKNOWN
    }

    public record TraceEntry(String time, String context) { }

    public record Kuaidi100TraceResult(
            String carrier,
            String number,
            String rawState,
            NormalizedState normalizedState,
            boolean signed,
            boolean exception,
            List<TraceEntry> traces,
            Instant fetchedAt) { }
}
