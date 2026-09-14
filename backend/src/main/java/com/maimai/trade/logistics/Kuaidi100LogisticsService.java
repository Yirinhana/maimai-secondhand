package com.maimai.trade.logistics;

import com.maimai.integration.logistics.Kuaidi100Client;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 真实快递100适配器；授权和持久化查询间隔由上层负责。 */
@Service
@Profile("!local & !test")
public class Kuaidi100LogisticsService implements LogisticsService {

    private final Kuaidi100Client client;

    public Kuaidi100LogisticsService(Kuaidi100Client client) { this.client = client; }

    @Override
    public LogisticsTrace queryTrace(String carrier, String trackingNo, String phone) {
        var result = client.query(new Kuaidi100Client.QueryRequest(carrier, trackingNo, phone));
        String status = result.signed() ? "DELIVERED" : result.normalizedState().name();
        String text = result.traces().stream().map(t -> t.time() + "  " + t.context())
                .collect(java.util.stream.Collectors.joining("\n"));
        return new LogisticsTrace(status, text, result.fetchedAt());
    }
}
