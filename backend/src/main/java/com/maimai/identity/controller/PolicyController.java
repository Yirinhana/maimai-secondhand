package com.maimai.identity.controller;

import com.maimai.identity.service.PolicyConsentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {
    private final PolicyConsentService policies;
    public PolicyController(PolicyConsentService policies){this.policies=policies;}
    @GetMapping("/current")
    public PolicyConsentService.CurrentPolicy current(){return policies.current();}
}
