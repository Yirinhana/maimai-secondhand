package com.maimai.integration.maps;

import java.io.IOException;
import java.util.Map;

/**
 * 窄传输接口：仅负责按 GET 表单参数请求高德 Web 服务并返回原始 JSON 字符串。
 * 设计为便于在单元测试中注入伪造响应；不暴露 URL/密钥给上游。
 */
public interface MapsTransport {

    /**
     * 发起一次 GET 请求。
     *
     * @param path 相对路径，如 "/v3/geocode/geo"，主机固定为高德官方端点。
     * @param params 不含 key 的查询参数；key 由传输层在调用方已注入的前提下补齐。
     * @return 高德返回的原始 JSON 字符串（UTF-8）
     * @throws IOException 网络或超时错误
     */
    String get(String path, Map<String, String> params) throws IOException;
}
