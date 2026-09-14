package com.maimai.trade.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.trade.dto.TradeDtos.MeetupRequest;
import com.maimai.trade.dto.TradeDtos.ShipRequest;
import com.maimai.trade.dto.TradeDtos.ShipmentDto;
import com.maimai.trade.service.FulfillmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 订单履约端点：发货、轨迹、确认收货、面交约定。 */
@RestController
@RequestMapping("/api/v1/orders")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @PostMapping("/{orderNo}/ship")
    public ShipmentDto ship(@PathVariable String orderNo, @RequestBody @Valid ShipRequest request) {
        return fulfillmentService.ship(SecurityUtils.currentUserId(), orderNo,
                request.carrier(), request.trackingNo());
    }

    @GetMapping("/{orderNo}/shipment")
    public ShipmentDto shipment(@PathVariable String orderNo) {
        return fulfillmentService.shipment(SecurityUtils.currentUserId(), orderNo);
    }

    @PostMapping("/{orderNo}/confirm-receipt")
    public ResponseEntity<Void> confirmReceipt(@PathVariable String orderNo) {
        fulfillmentService.confirmReceipt(SecurityUtils.currentUserId(), orderNo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderNo}/meetup")
    public ResponseEntity<Void> arrangeMeetup(@PathVariable String orderNo,
                                              @RequestBody @Valid MeetupRequest request) {
        fulfillmentService.arrangeMeetup(SecurityUtils.currentUserId(), orderNo,
                request.location(), request.scheduledAt());
        return ResponseEntity.noContent().build();
    }
}
