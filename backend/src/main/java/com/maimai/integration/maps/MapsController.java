package com.maimai.integration.maps;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.common.SimpleRateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 地图 REST 控制器。所有端点要求已登录用户，限流 30/60s。 */
@RestController
@RequestMapping("/api/v1/maps")
public class MapsController {

    private final MapsClient client;
    private final SimpleRateLimiter rateLimiter;

    public MapsController(MapsClient client, SimpleRateLimiter rateLimiter) {
        this.client = client;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/geocode")
    public MapsDtos.GeoCodedLocation geocode(@RequestParam("address") @NotBlank String address,
                                             @RequestParam(value = "city", required = false) String city) {
        Long uid = currentUserOrThrow();
        rateLimiter.require("maps:" + uid, 30, 60, "地图查询过于频繁");
        return client.geocode(address, city);
    }

    @GetMapping("/regeo")
    public MapsDtos.ReverseGeoResult regeo(
            @RequestParam("longitude") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @RequestParam("latitude") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude) {
        Long uid = currentUserOrThrow();
        rateLimiter.require("maps:" + uid, 30, 60, "地图查询过于频繁");
        return client.reverseGeocode(longitude, latitude);
    }

    @GetMapping("/around")
    public MapsDtos.AroundSearchResult around(
            @RequestParam("longitude") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @RequestParam("latitude") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @RequestParam("keyword") @NotBlank String keyword,
            @RequestParam(value = "radius", defaultValue = "2000") @Positive @Max(50_000) Integer radius,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
            @RequestParam(value = "offset", defaultValue = "20") @Min(1) @Max(20) Integer offset) {
        Long uid = currentUserOrThrow();
        rateLimiter.require("maps:" + uid, 30, 60, "地图查询过于频繁");
        return client.aroundSearch(longitude, latitude, keyword, radius, page, offset);
    }

    @GetMapping("/driving")
    public MapsDtos.DrivingRoute driving(
            @RequestParam("originLng") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double originLng,
            @RequestParam("originLat") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double originLat,
            @RequestParam("destLng") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double destLng,
            @RequestParam("destLat") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double destLat) {
        Long uid = currentUserOrThrow();
        rateLimiter.require("maps:" + uid, 30, 60, "地图查询过于频繁");
        return client.drivingRoute(originLng, originLat, destLng, destLat);
    }

    private static Long currentUserOrThrow() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw BizException.unauthorized("未登录");
        }
        return uid;
    }
}
