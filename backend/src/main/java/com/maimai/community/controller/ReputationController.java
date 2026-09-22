package com.maimai.community.controller;

import com.maimai.community.service.ReputationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/community/users")
public class ReputationController {
    private final ReputationService service;
    public ReputationController(ReputationService service){this.service=service;}
    @GetMapping("/{id}/reputation") public ReputationService.Profile profile(@PathVariable long id){return service.profile(id);}
}
