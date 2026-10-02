package com.maimai.catalog.controller;
import com.maimai.catalog.service.SearchSubscriptionService;
import com.maimai.common.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/me/searches")
public class SearchSubscriptionController {
    private final SearchSubscriptionService service;
    public SearchSubscriptionController(SearchSubscriptionService service){this.service=service;}
    public record Toggle(boolean enabled){}
    @GetMapping public List<SearchSubscriptionService.Watch> mine(){return service.mine(SecurityUtils.currentUserId());}
    @PostMapping public List<SearchSubscriptionService.Watch> create(@RequestBody SearchSubscriptionService.Input input){return service.create(SecurityUtils.currentUserId(),input);}
    @PatchMapping("/{id}") public void toggle(@PathVariable long id,@RequestBody Toggle input){service.enabled(SecurityUtils.currentUserId(),id,input.enabled());}
    @DeleteMapping("/{id}") public void remove(@PathVariable long id){service.remove(SecurityUtils.currentUserId(),id);}
}
