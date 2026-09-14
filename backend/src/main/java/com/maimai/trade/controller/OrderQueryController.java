package com.maimai.trade.controller;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.trade.api.TradeOrderOps;
import com.maimai.trade.domain.Order;
import com.maimai.trade.dto.TradeDtos.OrderDto;
import com.maimai.trade.dto.TradeDtos.PageResponse;
import com.maimai.trade.repo.OrderQueryRepository;
import com.maimai.trade.repo.OrderRepository;
import com.maimai.trade.service.FulfillmentService;
import com.maimai.trade.service.OrderDtoMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 订单查询与买家取消。 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderQueryController {

    private static final int MAX_PAGE_SIZE = 50;

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderDtoMapper orderDtoMapper;
    private final FulfillmentService fulfillmentService;
    private final TradeOrderOps tradeOrderOps;

    public OrderQueryController(OrderRepository orderRepository,
                                OrderQueryRepository orderQueryRepository,
                                OrderDtoMapper orderDtoMapper,
                                FulfillmentService fulfillmentService,
                                TradeOrderOps tradeOrderOps) {
        this.orderRepository = orderRepository;
        this.orderQueryRepository = orderQueryRepository;
        this.orderDtoMapper = orderDtoMapper;
        this.fulfillmentService = fulfillmentService;
        this.tradeOrderOps = tradeOrderOps;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public PageResponse<OrderDto> list(@RequestParam(defaultValue = "buyer") String role,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.currentUserId();
        Order.FulfillmentStatus filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = Order.FulfillmentStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BizException.badRequest("INVALID_STATUS", "未知的订单状态");
            }
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<Order> result = switch (role) {
            case "buyer" -> filter == null
                    ? orderRepository.findByBuyerIdOrderByCreatedAtDesc(userId, pageable)
                    : orderQueryRepository.findByBuyerIdAndFulfillmentStatusOrderByCreatedAtDesc(userId, filter, pageable);
            case "seller" -> filter == null
                    ? orderRepository.findBySellerIdOrderByCreatedAtDesc(userId, pageable)
                    : orderQueryRepository.findBySellerIdAndFulfillmentStatusOrderByCreatedAtDesc(userId, filter, pageable);
            default -> throw BizException.badRequest("INVALID_ROLE", "role 仅支持 buyer|seller");
        };
        return new PageResponse<>(result.getContent().stream().map(orderDtoMapper::toDto).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    @GetMapping("/{orderNo}")
    public OrderDto detail(@PathVariable String orderNo) {
        Order order = fulfillmentService.requireOrder(orderNo);
        fulfillmentService.requireVisible(order, SecurityUtils.currentUserId());
        return orderDtoMapper.toDto(order);
    }

    @PostMapping("/{orderNo}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String orderNo) {
        Order order = fulfillmentService.requireOrder(orderNo);
        SecurityUtils.requireOwner(order.getBuyerId());
        if (order.getFulfillmentStatus() != Order.FulfillmentStatus.PENDING_PAYMENT) {
            throw BizException.conflict("ORDER_STATE", "仅待付款订单可以取消");
        }
        tradeOrderOps.closeUnpaid(order.getId(), "买家取消");
        return ResponseEntity.noContent().build();
    }
}
