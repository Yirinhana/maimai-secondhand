package com.maimai.catalog.controller;
import com.maimai.catalog.service.ListingDraftService;
import com.maimai.common.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
@RestController
@RequestMapping("/api/v1/me/listing-drafts")
public class ListingDraftController {
    private final ListingDraftService service;
    public ListingDraftController(ListingDraftService service){this.service=service;}
    public record Save(long version,JsonNode payload,Long ownerId){}
    private long owner(Long expected){long actor=SecurityUtils.currentUserId();if(expected!=null&&expected!=actor)throw com.maimai.common.BizException.conflict("DRAFT_ACCOUNT_CHANGED","账号已切换，请刷新页面后继续填写");return actor;}
    @GetMapping("/{key}") public ListingDraftService.Draft get(@PathVariable String key,@RequestParam(required=false) Long ownerId){return service.get(owner(ownerId),key);}
    @PutMapping("/{key}") public ListingDraftService.Draft save(@PathVariable String key,@RequestBody Save input){if(input.ownerId()==null)throw com.maimai.common.BizException.badRequest("DRAFT_OWNER","请刷新后重新保存草稿");return service.save(owner(input.ownerId()),key,input.version(),input.payload());}
}
