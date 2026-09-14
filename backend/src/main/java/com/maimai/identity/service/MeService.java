package com.maimai.identity.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.domain.Address;
import com.maimai.identity.domain.SellerApplication;
import com.maimai.identity.domain.SellerApplication.ChannelStatus;
import com.maimai.identity.domain.SellerApplication.Status;
import com.maimai.identity.domain.User;
import com.maimai.identity.dto.MeDtos.AddressRequest;
import com.maimai.identity.dto.MeDtos.AddressView;
import com.maimai.identity.dto.MeDtos.SellerApplicationRequest;
import com.maimai.identity.dto.MeDtos.SellerApplicationView;
import com.maimai.identity.repo.AddressRepository;
import com.maimai.identity.repo.SellerApplicationRepository;
import com.maimai.identity.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 个人中心服务：资料修改、收货地址 CRUD、卖家申请。 */
@Service
@Transactional
public class MeService {

    private static final Logger log = LoggerFactory.getLogger(MeService.class);

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final SellerApplicationRepository sellerApplicationRepository;

    public MeService(UserRepository userRepository, AddressRepository addressRepository,
                     SellerApplicationRepository sellerApplicationRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.sellerApplicationRepository = sellerApplicationRepository;
    }

    /** 修改昵称，返回更新后的用户（控制器据此刷新会话主体）。 */
    public User updateProfile(Long userId, String nickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BizException.unauthorized("请先登录"));
        user.setNickname(nickname);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<AddressView> listAddresses(Long userId) {
        return addressRepository.findByUserId(userId).stream().map(MeService::toView).toList();
    }

    /** 新增地址：首个地址自动设为默认；显式指定默认时清除其他默认。 */
    public AddressView addAddress(Long userId, AddressRequest request) {
        List<Address> existing = addressRepository.findByUserId(userId);
        Address address = new Address();
        address.setUserId(userId);
        apply(address, request);
        boolean makeDefault = existing.isEmpty() || request.isDefault();
        if (makeDefault) {
            clearDefault(existing);
        }
        address.setDefault(makeDefault);
        return toView(addressRepository.save(address));
    }

    /** 修改地址：仅本人；指定默认时清除其他默认。 */
    public AddressView updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> BizException.notFound("地址不存在"));
        SecurityUtils.requireOwner(address.getUserId());
        apply(address, request);
        address.setDefault(request.isDefault());
        if (request.isDefault()) {
            clearDefault(addressRepository.findByUserId(userId), addressId);
        }
        return toView(addressRepository.save(address));
    }

    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> BizException.notFound("地址不存在"));
        SecurityUtils.requireOwner(address.getUserId());
        addressRepository.delete(address);
    }

    /** 提交卖家申请：已有待审核申请时拒绝重复提交。 */
    public SellerApplicationView applySeller(Long userId, SellerApplicationRequest request) {
        sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(latest -> latest.getStatus() == Status.PENDING)
                .ifPresent(latest -> {
                    throw BizException.conflict("APPLICATION_PENDING", "已有待审核的卖家申请，请耐心等待审核");
                });

        SellerApplication application = new SellerApplication();
        application.setUserId(userId);
        application.setStatus(Status.PENDING);
        application.setChannelStatus(ChannelStatus.PENDING);
        application.setIntro(request.intro());
        SellerApplication saved = sellerApplicationRepository.save(application);
        log.info("卖家申请已提交 userId={} applicationId={}", userId, saved.getId());
        return toView(saved);
    }

    /** 最新一条卖家申请；从未申请时返回 NONE 占位视图。 */
    @Transactional(readOnly = true)
    public SellerApplicationView latestApplication(Long userId) {
        return sellerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(MeService::toView)
                .orElse(new SellerApplicationView(null, "NONE", null, null, null, null));
    }

    private void apply(Address address, AddressRequest request) {
        address.setReceiver(request.receiver());
        address.setPhone(request.phone());
        address.setRegion(request.region());
        address.setDetail(request.detail());
    }

    private void clearDefault(List<Address> addresses) {
        clearDefault(addresses, null);
    }

    private void clearDefault(List<Address> addresses, Long exceptId) {
        boolean changed = false;
        for (Address item : addresses) {
            if (item.isDefault() && (exceptId == null || !item.getId().equals(exceptId))) {
                item.setDefault(false);
                changed = true;
            }
        }
        if (changed) {
            addressRepository.saveAll(addresses);
        }
    }

    private static AddressView toView(Address address) {
        return new AddressView(address.getId(), address.getReceiver(), address.getPhone(),
                address.getRegion(), address.getDetail(), address.isDefault(), address.getCreatedAt());
    }

    private static SellerApplicationView toView(SellerApplication application) {
        return new SellerApplicationView(application.getId(), application.getStatus().name(),
                application.getChannelStatus().name(), application.getIntro(), application.getReason(),
                application.getCreatedAt());
    }
}
