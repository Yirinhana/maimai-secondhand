package com.maimai.catalog.assistant;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Draft assistance is available to signed-in users; it never grants seller approval or publishes a product. */
@RestController
@RequestMapping("/api/v1/me/listing-assistant")
public class ListingAssistantController {
    private final ListingAssistantService assistant;
    private final ListingPriceComparisonService comparisons;

    public ListingAssistantController(ListingAssistantService assistant, ListingPriceComparisonService comparisons) {
        this.assistant = assistant; this.comparisons = comparisons;
    }

    public record Recognize(Long ownerId, List<String> imageIds, String requestKey, Boolean cacheOnly) {}

    @GetMapping("/availability")
    public ListingAssistantService.Availability availability() {
        return assistant.availability(SecurityUtils.currentUserId());
    }

    @GetMapping("/runs/{id}")
    public ListingAssistantService.Result result(@PathVariable long id) {
        long owner = SecurityUtils.currentUserId();
        if (id <= 0) throw BizException.notFound("这次识图记录不存在");
        return assistant.result(owner, id);
    }

    @PostMapping("/recognize")
    public ListingAssistantService.Result recognize(@RequestBody Recognize request) {
        long owner = SecurityUtils.currentUserId();
        if (request == null || request.ownerId() == null || request.ownerId() <= 0)
            throw BizException.badRequest("LISTING_AI_OWNER", "请刷新页面后重新识图");
        if (request.ownerId() != owner)
            throw BizException.conflict("LISTING_AI_ACCOUNT_CHANGED", "账号已切换，请刷新页面后继续识图，原草稿不会被覆盖");
        return assistant.recognize(owner, request.imageIds(), request.requestKey(), Boolean.TRUE.equals(request.cacheOnly()));
    }

    @GetMapping("/comparables")
    public ListingPriceComparisonService.Comparison compare(@RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long excludeProductId) {
        return comparisons.compare(SecurityUtils.currentUserId(), query, categoryId, excludeProductId);
    }
}
