package com.maimai.community.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.common.BizException;
import com.maimai.community.dto.CommunityDtos;
import com.maimai.community.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/community")
public class CommunityController {

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    // ---------- 收藏 ----------
    @PostMapping("/favorites/{productId}")
    public ResponseEntity<Void> addFavorite(@PathVariable Long productId) {
        service.addFavorite(SecurityUtils.currentUserId(), productId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/favorites/{productId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long productId) {
        service.removeFavorite(SecurityUtils.currentUserId(), productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/favorites")
    public CommunityDtos.PageResult<CommunityDtos.FavoriteItem> listFavorites(@RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        return service.listMyFavorites(SecurityUtils.currentUserId(), page, size);
    }

    // ---------- 关注 ----------
    @PostMapping("/follows/{sellerId}")
    public ResponseEntity<Void> followSeller(@PathVariable Long sellerId) {
        service.followSeller(SecurityUtils.currentUserId(), sellerId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/follows/{sellerId}")
    public ResponseEntity<Void> unfollowSeller(@PathVariable Long sellerId) {
        service.unfollowSeller(SecurityUtils.currentUserId(), sellerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/follows")
    public CommunityDtos.PageResult<CommunityDtos.FollowItem> listFollows(@RequestParam(required = false) Integer page,
                                                                        @RequestParam(required = false) Integer size) {
        return service.listMyFollows(SecurityUtils.currentUserId(), page, size);
    }

    // ---------- 足迹 ----------
    @PostMapping("/footprints/{productId}")
    public ResponseEntity<Void> recordFootprint(@PathVariable Long productId) {
        service.recordFootprint(SecurityUtils.currentUserId(), productId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/footprints")
    public CommunityDtos.PageResult<CommunityDtos.FootprintItem> listFootprints(@RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer size) {
        return service.listMyFootprints(SecurityUtils.currentUserId(), page, size);
    }

    @PatchMapping("/footprints/setting")
    public ResponseEntity<Void> setFootprintEnabled(@RequestBody @Valid CommunityDtos.FootprintSettingRequest request) {
        service.setFootprintEnabled(SecurityUtils.currentUserId(), request.enabled());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/footprints/setting")
    public Map<String, Boolean> getFootprintEnabled() {
        return Map.of("enabled", service.isFootprintEnabled(SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/footprints")
    public ResponseEntity<Void> clearFootprints() {
        service.clearMyFootprints(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/footprints/{productId}")
    public ResponseEntity<Void> clearFootprint(@PathVariable Long productId) {
        service.clearFootprint(SecurityUtils.currentUserId(), productId);
        return ResponseEntity.noContent().build();
    }

    // ---------- 求购 ----------
    @PostMapping("/demands")
    public CommunityDtos.DemandItem createDemand(@RequestBody @Valid CommunityDtos.DemandCreateRequest request) {
        Long actorId = SecurityUtils.currentUserId();
        String actorNick = SecurityUtils.current().nickname();
        return service.createDemand(actorId, request, actorNick);
    }

    @PutMapping("/demands/{demandId}")
    public CommunityDtos.DemandItem updateDemand(@PathVariable Long demandId,
                                                @RequestBody @Valid CommunityDtos.DemandPatchRequest request) {
        Long actorId = SecurityUtils.currentUserId();
        String actorNick = SecurityUtils.current().nickname();
        return service.updateDemand(actorId, demandId, request, actorNick);
    }

    @PostMapping("/demands/{demandId}/close")
    public ResponseEntity<Void> closeDemand(@PathVariable Long demandId) {
        service.closeDemand(SecurityUtils.currentUserId(), demandId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/demands")
    public CommunityDtos.PageResult<CommunityDtos.DemandItem> listPublicDemands(@RequestParam(required = false) Integer page,
                                                                             @RequestParam(required = false) Integer size) {
        return service.listPublicDemands(page, size);
    }

    @GetMapping("/demands/me")
    public CommunityDtos.PageResult<CommunityDtos.DemandItem> listMyDemands(@RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        return service.listMyDemands(SecurityUtils.currentUserId(), page, size);
    }

    @GetMapping("/demands/{demandId}")
    public CommunityDtos.DemandItem publicDemand(@PathVariable Long demandId) {
        return service.publicDemand(demandId);
    }

    @GetMapping("/replies/me")
    public CommunityDtos.PageResult<CommunityDtos.DemandReplyItem> myReplies(@RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        return service.listMyDemandReplies(SecurityUtils.currentUserId(), page, size);
    }

    @GetMapping("/admin/demands")
    public CommunityDtos.PageResult<CommunityDtos.DemandItem> adminDemands(@RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) Integer page,
                                                                         @RequestParam(required = false) Integer size) {
        return service.listAdminDemands(SecurityUtils.currentUserId(), status, page, size);
    }

    @GetMapping("/admin/demand-replies")
    public CommunityDtos.PageResult<CommunityDtos.DemandReplyItem> adminReplies(@RequestParam(required = false) String status,
                                                                             @RequestParam(required = false) Integer page,
                                                                             @RequestParam(required = false) Integer size) {
        return service.listAdminDemandReplies(SecurityUtils.currentUserId(), status, page, size);
    }

    @PostMapping("/demands/{demandId}/replies")
    public CommunityDtos.DemandReplyItem createDemandReply(@PathVariable Long demandId,
                                                          @RequestBody @Valid CommunityDtos.DemandReplyRequest request) {
        return service.createDemandReply(SecurityUtils.currentUserId(), demandId, request);
    }

    @GetMapping("/demands/{demandId}/replies")
    public CommunityDtos.PageResult<CommunityDtos.DemandReplyItem> listDemandReplies(@PathVariable Long demandId,
                                                                                   @RequestParam(required = false) Integer page,
                                                                                   @RequestParam(required = false) Integer size) {
        return service.listDemandReplies(demandId, page, size);
    }

    @PutMapping("/replies/{replyId}")
    public CommunityDtos.DemandReplyItem updateDemandReply(@PathVariable Long replyId,
                                                           @RequestBody @Valid CommunityDtos.DemandReplyRequest request) {
        return service.updateDemandReply(SecurityUtils.currentUserId(), replyId, request);
    }

    @DeleteMapping("/replies/{replyId}")
    public ResponseEntity<Void> deleteDemandReply(@PathVariable Long replyId) {
        service.deleteDemandReply(SecurityUtils.currentUserId(), replyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/demands/{demandId}/review")
    public ResponseEntity<Void> reviewDemand(@PathVariable Long demandId,
                                            @RequestBody @Valid CommunityDtos.ModerationDecisionRequest request) {
        service.reviewDemand(SecurityUtils.currentUserId(), demandId, request.approve(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/demand-replies/{replyId}/review")
    public ResponseEntity<Void> reviewDemandReply(@PathVariable Long replyId,
                                                  @RequestBody @Valid CommunityDtos.ModerationDecisionRequest request) {
        service.reviewDemandReply(SecurityUtils.currentUserId(), replyId, request.approve(), request.reason());
        return ResponseEntity.noContent().build();
    }

    // ---------- 举报 ----------
    @PostMapping("/reports")
    public CommunityDtos.ReportItem createReport(@RequestBody @Valid CommunityDtos.ReportCreateRequest request) {
        Long actorId = SecurityUtils.currentUserId();
        String actorNick = SecurityUtils.current().nickname();
        return service.createReport(actorId, request, actorNick);
    }

    @GetMapping("/reports/me")
    public CommunityDtos.PageResult<CommunityDtos.ReportItem> listMyReports(@RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        return service.listMyReports(SecurityUtils.currentUserId(), page, size);
    }

    @GetMapping("/admin/reports")
    public CommunityDtos.PageResult<CommunityDtos.ReportItem> listReports(@RequestParam(required = false) String status,
                                                                        @RequestParam(required = false) Integer page,
                                                                        @RequestParam(required = false) Integer size) {
        return service.listPendingReports(status, page, size);
    }

    @PostMapping("/admin/reports/{reportId}")
    public ResponseEntity<Void> processReport(@PathVariable Long reportId,
                                             @RequestBody @Valid CommunityDtos.ReportProcessRequest request) {
        service.processReport(SecurityUtils.currentUserId(), reportId,
                parseReportAction(request.action()), request.note());
        return ResponseEntity.noContent().build();
    }

    // ---------- 评价 ----------
    @PostMapping("/orders/{orderId}/ratings")
    public CommunityDtos.RatingItem createRating(@PathVariable Long orderId,
                                                @RequestBody @Valid CommunityDtos.RatingRequest request) {
        return service.createOrderRating(SecurityUtils.currentUserId(), orderId, request, SecurityUtils.current().nickname());
    }

    @GetMapping("/orders/{orderId}/ratings")
    public CommunityDtos.PageResult<CommunityDtos.RatingItem> listOrderRatings(@PathVariable Long orderId,
                                                                              @RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer size) {
        return service.listOrderRatings(orderId, page, size);
    }

    @GetMapping("/ratings/me")
    public CommunityDtos.PageResult<CommunityDtos.RatingItem> listMyRatings(@RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        return service.listMyRatings(SecurityUtils.currentUserId(), page, size);
    }

    @GetMapping("/users/{userId}/ratings")
    public CommunityDtos.PageResult<CommunityDtos.PublicRatingItem> receivedRatings(@PathVariable Long userId,
                                                                                   @RequestParam(required = false) Integer page,
                                                                                   @RequestParam(required = false) Integer size) {
        return service.listReceivedRatings(userId, page, size);
    }

    private CommunityDtos.ReportAction parseReportAction(String action) {
        try {
            return CommunityDtos.ReportAction.valueOf(action.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw BizException.badRequest("REPORT_ACTION_INVALID", "非法的处理动作");
        }
    }
}
