package com.maimai.catalog.controller;
import com.maimai.catalog.service.DraftImageAttachmentService;
import com.maimai.common.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class DraftImageAttachmentController {
    private final DraftImageAttachmentService service;
    public DraftImageAttachmentController(DraftImageAttachmentService service){this.service=service;}
    public record Images(List<String> ids){}
    @PostMapping("/api/v1/seller/products/{id}/draft-images") public void attach(@PathVariable long id,@RequestBody Images images){service.attach(SecurityUtils.currentUserId(),id,images.ids());}
}
