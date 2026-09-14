package com.maimai.admin.service;

import com.maimai.admin.dto.AdminDtos;
import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.domain.SellerApplication;
import com.maimai.identity.domain.User;
import com.maimai.identity.domain.UserRole;
import com.maimai.identity.repo.SellerApplicationRepository;
import com.maimai.identity.repo.UserRepository;
import com.maimai.identity.repo.UserRoleRepository;
import com.maimai.notification.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 后台卖家申请审核。 */
@Service
@Transactional
public class AdminSellerApplicationService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;
    private final AdminAuditService adminAuditService;
    private final org.springframework.core.env.Environment environment;

    public AdminSellerApplicationService(SellerApplicationRepository sellerApplicationRepository,
                                         UserRepository userRepository,
                                         UserRoleRepository userRoleRepository,
                                         NotificationService notificationService,
                                         AdminAuditService adminAuditService, org.springframework.core.env.Environment environment) {
        this.sellerApplicationRepository = sellerApplicationRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.notificationService = notificationService;
        this.adminAuditService = adminAuditService;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResult<AdminDtos.SellerApplicationItem> list(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SellerApplication> result;
        if (status == null || status.isBlank()) {
            result = sellerApplicationRepository.findAll(pageable);
        } else {
            result = sellerApplicationRepository.findByStatus(parseStatus(status), pageable);
        }
        Map<Long, User> users = userRepository.findAllById(
                result.getContent().stream().map(SellerApplication::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<AdminDtos.SellerApplicationItem> content = result.getContent().stream()
                .map(a -> {
                    User user = users.get(a.getUserId());
                    return new AdminDtos.SellerApplicationItem(a.getId(), a.getUserId(),
                            user == null ? null : user.getEmail(),
                            user == null ? null : user.getNickname(),
                            a.getStatus().name(), a.getChannelStatus().name(), a.getIntro(), a.getReason(),
                            a.getReviewedBy(), a.getReviewedAt(), a.getCreatedAt());
                })
                .toList();
        return new AdminDtos.PageResult<>(content, result.getTotalElements(),
                result.getNumber(), result.getSize());
    }

    /** 审核：APPROVE 授予 SELLER 角色（已有跳过），channelQualified=true 时渠道资质置 QUALIFIED。 */
    public AdminDtos.SellerApplicationItem review(Long id, AdminDtos.ReviewApplicationRequest request) {
        if (Boolean.TRUE.equals(request.channelQualified())
                && !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "test"))) {
            throw new BizException("CHANNEL_QUALIFICATION_UNVERIFIED", "真实渠道资质必须由渠道核验，不能以后台勾选代替",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
        SellerApplication application = sellerApplicationRepository.lockById(id)
                .orElseThrow(() -> BizException.notFound("卖家申请不存在"));
        String before = application.getStatus() + "/" + application.getChannelStatus();
        Long adminId = SecurityUtils.currentUserId();

        switch (request.action()) {
            case "APPROVE" -> {
                application.setStatus(SellerApplication.Status.APPROVED);
                if (Boolean.TRUE.equals(request.channelQualified())) {
                    application.setChannelStatus(SellerApplication.ChannelStatus.QUALIFIED);
                }
                if (!userRoleRepository.existsByUserIdAndRole(
                        application.getUserId(), UserRole.Role.SELLER)) {
                    UserRole role = new UserRole();
                    role.setUserId(application.getUserId());
                    role.setRole(UserRole.Role.SELLER);
                    userRoleRepository.save(role);
                }
            }
            case "REJECT" -> application.setStatus(SellerApplication.Status.REJECTED);
            case "SUPPLEMENT" -> application.setStatus(SellerApplication.Status.SUPPLEMENT);
            case "SUSPEND" -> application.setStatus(SellerApplication.Status.SUSPENDED);
            default -> throw BizException.badRequest("REVIEW_ACTION_INVALID", "非法的审核动作");
        }
        application.setReason(request.reason());
        application.setReviewedBy(adminId);
        application.setReviewedAt(Instant.now());
        application.setUpdatedAt(Instant.now());
        sellerApplicationRepository.save(application);

        String after = application.getStatus() + "/" + application.getChannelStatus();
        adminAuditService.record("SELLER_APPLICATION_" + request.action(), "SELLER_APPLICATION",
                application.getId(), request.reason(), before, after);
        notificationService.notify(application.getUserId(), "SELLER_APPLICATION",
                "卖家申请审核结果",
                "您的卖家申请审核结果：" + application.getStatus().name() + "。理由：" + request.reason());
        return new AdminDtos.SellerApplicationItem(application.getId(), application.getUserId(),
                null, null, application.getStatus().name(), application.getChannelStatus().name(),
                application.getIntro(), application.getReason(), application.getReviewedBy(),
                application.getReviewedAt(), application.getCreatedAt());
    }

    private SellerApplication.Status parseStatus(String status) {
        try {
            return SellerApplication.Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("APPLICATION_STATUS_INVALID", "非法的申请状态筛选");
        }
    }
}
