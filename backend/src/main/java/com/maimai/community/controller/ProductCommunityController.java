package com.maimai.community.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.service.ProductDiscussionService;
import com.maimai.community.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/{productId}")
public class ProductCommunityController {
    private final ProductDiscussionService comments;private final RatingService ratings;
    public ProductCommunityController(ProductDiscussionService comments,RatingService ratings){this.comments=comments;this.ratings=ratings;}
    @GetMapping("/comments") public PageResult<ProductDiscussionService.Comment> list(@PathVariable long productId,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size){return comments.list(productId,page,size);}
    @PostMapping("/comments") public ProductDiscussionService.Comment create(@PathVariable long productId,@RequestBody @Valid ProductDiscussionService.CreateComment body){return comments.create(SecurityUtils.currentUserId(),productId,body);}
    @DeleteMapping("/comments/{id}") public ResponseEntity<Void> delete(@PathVariable long productId,@PathVariable long id){comments.delete(SecurityUtils.currentUserId(),productId,id);return ResponseEntity.noContent().build();}
    @GetMapping("/ratings") public PageResult<PublicRatingItem> ratings(@PathVariable long productId,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size){return ratings.product(productId,page,size);}
}
