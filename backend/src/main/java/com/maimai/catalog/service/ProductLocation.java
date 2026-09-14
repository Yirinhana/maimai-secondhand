package com.maimai.catalog.service;

import com.maimai.common.BizException;
import java.math.BigDecimal;

/** GCJ02 points selected by the user; geographic distance is not a courier price. */
public final class ProductLocation {
    private ProductLocation() {}
    public static void validate(BigDecimal latitude,BigDecimal longitude) {
        if((latitude==null)!=(longitude==null))throw invalid();
        if(latitude!=null && (latitude.abs().compareTo(BigDecimal.valueOf(90))>0||longitude.abs().compareTo(BigDecimal.valueOf(180))>0))throw invalid();
    }
    public static Double distance(BigDecimal latitude,BigDecimal longitude,Double originLatitude,Double originLongitude) {
        if(latitude==null || longitude==null || originLatitude==null || originLongitude==null)return null;
        double a=Math.toRadians(latitude.doubleValue()),b=Math.toRadians(originLatitude);
        double cosine=Math.sin(a)*Math.sin(b)+Math.cos(a)*Math.cos(b)*Math.cos(Math.toRadians(longitude.doubleValue()-originLongitude));
        return Math.acos(Math.max(-1,Math.min(1,cosine)))*6371008.8;
    }
    public static void validateOrigin(Double latitude,Double longitude,boolean required) {
        if((latitude==null)!=(longitude==null) || (required&&latitude==null))throw invalid();
        if(latitude!=null && (!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180))throw invalid();
    }
    private static BizException invalid(){return BizException.badRequest("LOCATION_INVALID","请提供有效且成对的经纬度；附近排序需要主动选择搜索位置");}
}
