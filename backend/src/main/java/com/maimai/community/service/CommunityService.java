package com.maimai.community.service;

import com.maimai.community.dto.CommunityDtos.*;
import org.springframework.stereotype.Service;

/** Stable module facade. Transactions belong to the individual domain services. */
@Service
public class CommunityService {
    private final CollectionService collections;
    private final DemandService demands;
    private final ReportService reports;
    private final RatingService ratings;
    public CommunityService(CollectionService collections, DemandService demands, ReportService reports, RatingService ratings) {
        this.collections = collections; this.demands = demands; this.reports = reports; this.ratings = ratings;
    }
    public void addFavorite(Long user, Long product) { collections.addFavorite(user, product); }
    public void removeFavorite(Long user, Long product) { collections.removeFavorite(user, product); }
    public PageResult<FavoriteItem> listMyFavorites(Long user, Integer page, Integer size) { return collections.favorites(user, page, size); }
    public void followSeller(Long user, Long seller) { collections.follow(user, seller); }
    public void unfollowSeller(Long user, Long seller) { collections.unfollow(user, seller); }
    public PageResult<FollowItem> listMyFollows(Long user, Integer page, Integer size) { return collections.follows(user, page, size); }
    public void setFootprintEnabled(Long user, Boolean enabled) { collections.setEnabled(user, enabled); }
    public boolean isFootprintEnabled(Long user) { return collections.enabled(user); }
    public void recordFootprint(Long user, Long product) { collections.record(user, product); }
    public void clearFootprint(Long user, Long product) { collections.clear(user, product); }
    public void clearMyFootprints(Long user) { collections.clear(user, null); }
    public PageResult<FootprintItem> listMyFootprints(Long user, Integer page, Integer size) { return collections.footprints(user, page, size); }
    public void cleanupExpiredFootprints() { collections.cleanupExpiredFootprints(); }
    public DemandItem createDemand(Long actor, DemandCreateRequest request, String ignoredNickname) { return demands.create(actor, request); }
    public DemandItem updateDemand(Long actor, Long id, DemandPatchRequest request, String ignoredNickname) { return demands.update(actor, id, request); }
    public void closeDemand(Long actor, Long id) { demands.close(actor, id); }
    public DemandItem publicDemand(Long id) { return demands.publicDetail(id); }
    public PageResult<DemandItem> listPublicDemands(Integer page, Integer size) { return demands.publicList(page, size); }
    public PageResult<DemandItem> listMyDemands(Long actor, Integer page, Integer size) { return demands.myList(actor, page, size); }
    public DemandReplyItem createDemandReply(Long actor, Long id, DemandReplyRequest request) { return demands.createReply(actor, id, request); }
    public DemandReplyItem updateDemandReply(Long actor, Long id, DemandReplyRequest request) { return demands.updateReply(actor, id, request); }
    public void deleteDemandReply(Long actor, Long id) { demands.deleteReply(actor, id); }
    public PageResult<DemandReplyItem> listDemandReplies(Long id, Integer page, Integer size) { return demands.publicReplies(id, page, size); }
    public PageResult<DemandReplyItem> listMyDemandReplies(Long actor, Integer page, Integer size) { return demands.myReplies(actor, page, size); }
    public void reviewDemand(Long actor, Long id, boolean approve, String reason) { demands.review(actor, id, approve, reason); }
    public void reviewDemandReply(Long actor, Long id, boolean approve, String reason) { demands.reviewReply(actor, id, approve, reason); }
    public PageResult<DemandItem> listAdminDemands(Long actor, String status, Integer page, Integer size) { return demands.adminList(actor, status, page, size); }
    public PageResult<DemandReplyItem> listAdminDemandReplies(Long actor, String status, Integer page, Integer size) { return demands.adminReplies(actor, status, page, size); }
    public ReportItem createReport(Long actor, ReportCreateRequest request, String ignoredNickname) { return reports.create(actor, request); }
    public PageResult<ReportItem> listMyReports(Long actor, Integer page, Integer size) { return reports.mine(actor, page, size); }
    public PageResult<ReportItem> listPendingReports(String status, Integer page, Integer size) { return reports.admin(status, page, size); }
    public void processReport(Long actor, Long id, ReportAction action, String note) { reports.process(actor, id, action, note); }
    public RatingItem createOrderRating(Long actor, Long id, RatingRequest request, String ignoredNickname) { return ratings.create(actor, id, request); }
    public PageResult<RatingItem> listOrderRatings(Long id, Integer page, Integer size) { return ratings.orderRatings(id, page, size); }
    public PageResult<RatingItem> listMyRatings(Long actor, Integer page, Integer size) { return ratings.mine(actor, page, size); }
    public PageResult<PublicRatingItem> listReceivedRatings(Long user, Integer page, Integer size) { return ratings.received(user, page, size); }
}
