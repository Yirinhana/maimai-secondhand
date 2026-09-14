package com.maimai.admin.controller;

import com.maimai.trade.service.TradeReminderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/trade-todos")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
public class AdminTradeTodoController {
    private final TradeReminderService reminders;
    public AdminTradeTodoController(TradeReminderService reminders){this.reminders=reminders;}
    @GetMapping public List<TradeReminderService.Todo> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return reminders.todos(page,size);
    }
}
