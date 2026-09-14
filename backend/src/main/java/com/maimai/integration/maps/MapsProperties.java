package com.maimai.integration.maps;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 高德 Web 服务配置。密钥缺失由调用方决定如何处理（地图客户端抛 BizException）。 */
@Component
public class MapsProperties {

    private final String key;

    public MapsProperties(@Value("${maimai.amap.key:${MAIMAI_AMAP_KEY:}}") String key) {
        this.key = key == null ? "" : key.trim();
    }

    public String getKey() {
        return key;
    }

    public boolean hasKey() {
        return !key.isEmpty();
    }
}
