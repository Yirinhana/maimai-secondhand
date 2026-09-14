package com.maimai.catalog.service;

import com.maimai.common.BizException;
import java.util.*;

/** Province coverage is explicit; city-only addresses are never guessed for a restricted seller. */
public final class ProductShipping {
    public static final List<String> PROVINCES=List.of("北京市","天津市","河北省","山西省","内蒙古自治区","辽宁省","吉林省","黑龙江省","上海市","江苏省","浙江省","安徽省","福建省","江西省","山东省","河南省","湖北省","湖南省","广东省","广西壮族自治区","海南省","重庆市","四川省","贵州省","云南省","西藏自治区","陕西省","甘肃省","青海省","宁夏回族自治区","新疆维吾尔自治区","台湾省","香港特别行政区","澳门特别行政区");
    private ProductShipping() {}
    public static List<String> normalize(List<String> values) {
        if(values==null)return List.of();
        if(values.size()>34)throw BizException.badRequest("SHIPPING_PROVINCES_INVALID","配送省份最多34项");
        var result=new TreeSet<String>();
        for(String value:values) {
            if(value==null || !PROVINCES.contains(value.trim()))throw BizException.badRequest("SHIPPING_PROVINCES_INVALID","请从标准省份列表选择配送范围");
            result.add(value.trim());
        }
        return List.copyOf(result);
    }
    public static List<String> split(String value){return value==null||value.isBlank()?List.of():List.of(value.split(","));}
    public static void requireCovered(String configured,String addressRegion) {
        var allowed=split(configured);if(allowed.isEmpty())return;
        String input=addressRegion==null?"":addressRegion.strip();
        String province=PROVINCES.stream().filter(p->input.startsWith(p)||input.startsWith(shortName(p))).findFirst().orElse(null);
        if(province==null)throw BizException.badRequest("ADDRESS_PROVINCE_REQUIRED","该商品限制配送省份，请在收货地区开头填写省份，例如“上海市 浦东新区”");
        if(!allowed.contains(province))throw BizException.badRequest("DELIVERY_REGION_UNSUPPORTED","该商品暂不配送至所选收货省份，请更换地址或选择其他商品");
    }
    private static String shortName(String name) {
        return name.replace("壮族自治区","").replace("回族自治区","").replace("维吾尔自治区","").replace("自治区","").replace("特别行政区","").replace("省","").replace("市","");
    }
}
