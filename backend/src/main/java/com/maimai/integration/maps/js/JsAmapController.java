package com.maimai.integration.maps.js;

import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;

@RestController
public class JsAmapController {
    private final JsAmapService service;
    private final SimpleRateLimiter limiter;
    public JsAmapController(JsAmapService service, SimpleRateLimiter limiter) {this.service=service;this.limiter=limiter;}

    @GetMapping("/api/v1/maps/js-config")
    public ResponseEntity<JsAmapService.JsConfig> config() {
        SecurityUtils.currentUserId();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.getConfig());
    }

    @GetMapping("/_AMapService/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        long user = SecurityUtils.currentUserId();
        limiter.require("amap-js:" + user, 60, 60, "地图查询过于频繁，请稍后再试");
        limiter.require("amap-js:global", 600, 60, "地图查询繁忙，请稍后再试");
        String path = request.getRequestURI().substring((request.getContextPath() + "/_AMapService").length());
        Map<String, List<String>> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> query.put(name, Arrays.asList(values)));
        var result = service.proxy(path, query);
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .header("Content-Type", result.contentType()).body(result.body());
    }
}
