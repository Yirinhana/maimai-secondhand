package com.maimai.admin.controller;

import com.maimai.admin.service.AdminOperationsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {
    private final AdminOperationsService service;
    public AdminOperationsController(AdminOperationsService service){this.service=service;}
    @GetMapping("/operations/dashboard") public AdminOperationsService.Dashboard dashboard(){return service.dashboard();}
    @GetMapping("/orders") public AdminOperationsService.OrderPage orders(@RequestParam(required=false) String source,@RequestParam(required=false) String orderNo,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.orders(source,orderNo,page,size);}
    @GetMapping("/orders/{orderNo}") public AdminOperationsService.OrderDetail order(@PathVariable String orderNo){return service.order(orderNo);}
}
