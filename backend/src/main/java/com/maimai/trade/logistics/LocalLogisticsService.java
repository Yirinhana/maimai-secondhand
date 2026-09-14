package com.maimai.trade.logistics;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** 本地/测试环境物流查询：返回明确标注「模拟轨迹」的固定轨迹，不调用外部服务。 */
@Service
@Profile({"local", "test"})
public class LocalLogisticsService implements LogisticsService {

    @Override
    public LogisticsTrace queryTrace(String carrier, String trackingNo, String phone) {
        return new LogisticsTrace("IN_TRANSIT",
                "模拟轨迹（本地环境，非真实物流）：快件已被" + carrier + "揽收，正在运输途中",
                Instant.now());
    }
}
