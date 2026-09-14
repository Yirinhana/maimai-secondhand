package com.maimai.trade.logistics;

import java.time.Instant;

/** 物流轨迹快照。status 对应 Shipment.Status 枚举名；tracesText 为轨迹文本。 */
public record LogisticsTrace(String status, String tracesText, Instant updatedAt) {
}
