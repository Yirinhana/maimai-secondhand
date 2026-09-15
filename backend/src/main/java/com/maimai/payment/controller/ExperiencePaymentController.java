package com.maimai.payment.controller;

import com.maimai.payment.dto.ExperiencePaymentDtos.*;
import com.maimai.payment.service.ExperiencePaymentService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Authentication, CSRF, and active-account checks are supplied by the normal API security chain. */
@RestController
@RequestMapping("/api/v1")
public class ExperiencePaymentController {
    private final ExperiencePaymentService service;
    public ExperiencePaymentController(ExperiencePaymentService service) { this.service = service; }
    @PostMapping("/orders/{orderNo}/experience-pay")
    public ResponseEntity<Session> create(@PathVariable String orderNo) { return json(service.create(orderNo)); }
    @GetMapping("/experience-pay/{token}")
    public ResponseEntity<Session> get(@PathVariable String token) { return json(service.get(token)); }
    @PostMapping("/experience-pay/{token}/result")
    public ResponseEntity<Session> finish(@PathVariable String token, @RequestBody @Valid ResultRequest request) {
        return json(service.finish(token, request.result()));
    }
    @GetMapping(value="/experience-pay/{token}/qr", produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(service.qr(token));
    }
    private ResponseEntity<Session> json(Session value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
