package com.maimai.admin.service;

import com.maimai.admin.dto.AdminDtos;
import com.maimai.identity.repo.UserRepository;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台总览统计。 */
@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminStatsService(UserRepository userRepository,
                             OrderRepository orderRepository,
                             JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminDtos.StatsOverview overview() {
        long userCount = userRepository.count();
        long orderCount = orderRepository.count();
        long productOnSaleCount = count("SELECT COUNT(*) FROM products WHERE status = 'ON_SALE'");
        long paidOrderCount = count("SELECT COUNT(*) FROM orders WHERE pay_status = 'PAID'");
        long refundSuccessCount = count("SELECT COUNT(*) FROM refunds WHERE status = 'SUCCESS'");
        Long feeSum = jdbcTemplate.queryForObject(
                "SELECT (SELECT COALESCE(SUM(platform_fee_cents),0) FROM orders WHERE pay_status='PAID')"
                        + " - (SELECT COALESCE(SUM(platform_fee_refund_cents),0) FROM refunds WHERE status='SUCCESS')",
                Long.class);
        return new AdminDtos.StatsOverview(userCount, productOnSaleCount, orderCount,
                paidOrderCount, refundSuccessCount, feeSum == null ? 0 : feeSum);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
