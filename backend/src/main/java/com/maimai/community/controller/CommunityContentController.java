package com.maimai.community.controller;

import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.service.DemandService;
import com.maimai.community.service.RatingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/community")
public class CommunityContentController {
    private final DemandService demands;
    private final RatingService ratings;
    public CommunityContentController(DemandService demands,RatingService ratings){this.demands=demands;this.ratings=ratings;}
    @GetMapping("/demands/{id}/replies/{replyId}") public DemandReplyItem reply(@PathVariable long id,@PathVariable long replyId){return demands.publicReply(id,replyId);}
    @GetMapping("/users/{id}/ratings/{ratingId}") public PublicRatingItem rating(@PathVariable long id,@PathVariable long ratingId){return ratings.publicDetail(id,ratingId);}
}
