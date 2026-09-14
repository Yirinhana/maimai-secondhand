package com.maimai.integration.maps;

import com.maimai.common.BizException;
import com.maimai.integration.maps.MapsDtos.Coordinate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 高德地图客户端：负责参数校验、HTTP 调用与响应解析。 */
@Component
public class MapsClient {

    static final int MAX_ADDRESS_LEN = 120;
    static final int MAX_KEYWORD_LEN = 120;
    static final int MAX_CITY_LEN = 60;
    static final int MAX_PAGE_SIZE = 20;
    static final int MIN_PAGE = 1;
    static final double LNG_MIN = -180.0;
    static final double LNG_MAX = 180.0;
    static final double LAT_MIN = -90.0;
    static final double LAT_MAX = 90.0;

    private final MapsProperties props;
    private final MapsTransport transport;
    private final ObjectMapper mapper;

    public MapsClient(MapsProperties props, MapsTransport transport, ObjectMapper mapper) {
        this.props = props;
        this.transport = transport;
        this.mapper = mapper;
    }

    /** 地理编码：地址 -> 经纬度。 */
    public MapsDtos.GeoCodedLocation geocode(String address, String city) {
        validateText("address", address, MAX_ADDRESS_LEN);
        if (city != null && !city.isEmpty()) {
            validateText("city", city, MAX_CITY_LEN);
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("address", address);
        if (city != null && !city.isEmpty()) {
            params.put("city", city);
        }
        JsonNode root = call("/v3/geocode/geo", params);
        JsonNode geocodes = root.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            throw BizException.notFound("没有找到匹配地址");
        }
        JsonNode first = geocodes.get(0);
        String formatted = textOrNull(first, "formatted_address");
        Coordinate c = parseLocation(first.path("location").asText());
        if (formatted == null || c == null) {
            throw badResponse();
        }
        return new MapsDtos.GeoCodedLocation(formatted, c);
    }

    /** 逆地理编码：经纬度 -> 地址。 */
    public MapsDtos.ReverseGeoResult reverseGeocode(double longitude, double latitude) {
        validateCoordinate(longitude, latitude);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("location", longitude + "," + latitude);
        params.put("extensions", "base");
        JsonNode root = call("/v3/geocode/regeo", params);
        JsonNode regeo = root.path("regeocode");
        String formatted = textOrNull(regeo, "formatted_address");
        if (formatted == null) {
            throw BizException.notFound("没有找到对应地址");
        }
        return new MapsDtos.ReverseGeoResult(formatted, new Coordinate(longitude, latitude));
    }

    /** 周边搜索：用于查找面交点（POI）。 */
    public MapsDtos.AroundSearchResult aroundSearch(double longitude, double latitude,
                                                    String keyword, int radiusMeters,
                                                    Integer page, Integer offset) {
        validateCoordinate(longitude, latitude);
        validateText("keywords", keyword, MAX_KEYWORD_LEN);
        if (radiusMeters <= 0 || radiusMeters > 50_000) {
            throw BizException.badRequest("INVALID_RADIUS", "radius 必须在 (0,50000]");
        }
        int pageNo = page == null ? 1 : page;
        int pageSize = offset == null ? MAX_PAGE_SIZE : offset;
        if (pageNo != MIN_PAGE) {
            throw BizException.badRequest("INVALID_PAGE", "周边地点仅支持第一页");
        }
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw BizException.badRequest("INVALID_OFFSET", "offset 必须在 (0,20]");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("location", longitude + "," + latitude);
        params.put("keywords", keyword);
        params.put("radius", Integer.toString(radiusMeters));
        params.put("offset", Integer.toString(pageSize));
        params.put("page", Integer.toString(pageNo));
        params.put("extensions", "base");

        JsonNode root = call("/v3/place/around", params);
        JsonNode pois = root.path("pois");
        List<MapsDtos.Poi> list = new ArrayList<>();
        if (!pois.isArray()) throw badResponse();
        if (pois.isArray()) {
            for (JsonNode p : pois) {
                if(list.size()>=pageSize) break;
                String id = textOrNull(p, "id");
                String name = textOrNull(p, "name");
                String address = textOrNull(p, "address");
                Coordinate c = parseLocation(p.path("location").asText());
                if (id != null && name != null && c != null) {
                    list.add(new MapsDtos.Poi(id, name, address, c));
                }
            }
        }
        return new MapsDtos.AroundSearchResult(List.copyOf(list));
    }

    /** 驾车路线距离与时长。 */
    public MapsDtos.DrivingRoute drivingRoute(double originLng, double originLat,
                                              double destLng, double destLat) {
        validateCoordinate(originLng, originLat);
        validateCoordinate(destLng, destLat);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("origin", originLng + "," + originLat);
        params.put("destination", destLng + "," + destLat);
        params.put("show_fields", "cost");

        JsonNode root = call("/v5/direction/driving", params);
        JsonNode paths = root.path("route").path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            throw BizException.notFound("未找到驾车路线");
        }
        JsonNode first = paths.get(0);
        long distance = positiveNumber(first.path("distance"));
        long duration = positiveNumber(first.path("cost").path("duration"));
        return new MapsDtos.DrivingRoute(
                distance,
                duration,
                new Coordinate(originLng, originLat),
                new Coordinate(destLng, destLat));
    }

    // ---- internals ----

    private JsonNode call(String path, Map<String, String> params) {
        if (!props.hasKey()) {
            throw new BizException("MAPS_UNAVAILABLE", "地图服务未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String body;
        try {
            body = transport.get(path, params);
        } catch (IOException e) {
            throw new BizException("MAPS_IO_ERROR", "地图服务暂不可用", HttpStatus.BAD_GATEWAY);
        }
        if (body == null || body.isEmpty()) {
            throw new BizException("MAPS_BAD_RESPONSE", "地图服务返回为空", HttpStatus.BAD_GATEWAY);
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (tools.jackson.core.JacksonException e) {
            throw new BizException("MAPS_BAD_RESPONSE", "地图服务响应格式错误", HttpStatus.BAD_GATEWAY);
        }
        if(root==null || !root.isObject()) throw badResponse();
        String status = root.path("status").asText("");
        String infocode = root.path("infocode").asText("");
        if (!"1".equals(status)) {
            HttpStatus httpStatus = "10001".equals(infocode) || "10003".equals(infocode)
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY;
            throw new BizException("MAPS_PROVIDER_ERROR", "地图服务暂不可用", httpStatus);
        }
        if (!"10000".equals(infocode)) {
            throw new BizException("MAPS_PROVIDER_ERROR", "地图服务暂不可用", HttpStatus.BAD_GATEWAY);
        }
        return root;
    }

    private static void validateText(String name, String value, int maxLen) {
        if (value == null || value.isBlank()) {
            throw BizException.badRequest("INVALID_PARAM", name + " 不能为空");
        }
        if (value.length() > maxLen) {
            throw BizException.badRequest("INVALID_PARAM", name + " 长度超过 " + maxLen);
        }
    }

    private static void validateCoordinate(double lng, double lat) {
        if (Double.isNaN(lng) || Double.isInfinite(lng) || Double.isNaN(lat) || Double.isInfinite(lat)) {
            throw BizException.badRequest("INVALID_COORD", "经纬度必须是有限数值");
        }
        if (lng < LNG_MIN || lng > LNG_MAX || lat < LAT_MIN || lat > LAT_MAX) {
            throw BizException.badRequest("INVALID_COORD", "经纬度超出有效范围");
        }
    }

    private static Coordinate parseLocation(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        int idx = s.indexOf(',');
        if (idx <= 0) {
            return null;
        }
        try {
            double lng = Double.parseDouble(s.substring(0, idx).trim());
            double lat = Double.parseDouble(s.substring(idx + 1).trim());
            if (Double.isNaN(lng) || Double.isInfinite(lng) || Double.isNaN(lat) || Double.isInfinite(lat)) {
                return null;
            }
            if (lng < LNG_MIN || lng > LNG_MAX || lat < LAT_MIN || lat > LAT_MAX) {
                return null;
            }
            return new Coordinate(lng, lat);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (!v.isTextual()) {
            return null;
        }
        String s = v.asText();
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static long positiveNumber(JsonNode value) {
        try {
            String text=value.asText("");
            if(!text.matches("[0-9]{1,12}")) throw badResponse();
            long number=Long.parseLong(text);
            if(number<=0) throw badResponse();
            return number;
        } catch(NumberFormatException ex) {throw badResponse();}
    }
    private static BizException badResponse() {
        return new BizException("MAPS_BAD_RESPONSE","地图服务响应字段不完整",HttpStatus.BAD_GATEWAY);
    }
}
