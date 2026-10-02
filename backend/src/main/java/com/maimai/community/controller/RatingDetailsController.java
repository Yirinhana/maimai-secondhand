package com.maimai.community.controller;
import com.maimai.community.service.RatingDetailsService;
import com.maimai.common.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/community/ratings/{id}")
public class RatingDetailsController {
    private final RatingDetailsService service;
    public RatingDetailsController(RatingDetailsService service){this.service=service;}
    public record Followup(String text){}
    @GetMapping("/details") public RatingDetailsService.Details detail(@PathVariable long id){return service.detail(id);}
    @PostMapping("/followup") public RatingDetailsService.Details followup(@PathVariable long id,@RequestBody Followup input){return service.followup(SecurityUtils.currentUserId(),id,input.text());}
}
