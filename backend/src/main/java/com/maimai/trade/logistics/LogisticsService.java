package com.maimai.trade.logistics;

/** 物流查询窄接口：trade 仅依赖本接口，渠道实现可替换。 */
public interface LogisticsService {

    /**
     * 查询运单轨迹。
     *
     * @param carrier    承运商
     * @param trackingNo 运单号
     * @return 轨迹快照
     * @throws com.maimai.common.BizException 查询不可用/失败时抛出，调用方保留旧数据
     */
    LogisticsTrace queryTrace(String carrier, String trackingNo, String phone);
}
