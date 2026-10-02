package com.maimai.common;
import com.maimai.common.security.SecurityUtils;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
public class PersonalMediaController {
    private final PersonalMediaService service;
    public PersonalMediaController(PersonalMediaService service){this.service=service;}
    @PostMapping("/api/v1/me/media") public PersonalMediaService.Media upload(@RequestParam String purpose,@RequestParam long ownerId,@RequestParam MultipartFile file){long actor=SecurityUtils.currentUserId();if(actor!=ownerId)throw BizException.conflict("MEDIA_ACCOUNT_CHANGED","账号已切换，请刷新页面后上传图片");return service.upload(actor,purpose,file);}
    @GetMapping("/api/v1/me/media/{id}") public ResponseEntity<byte[]> mine(@PathVariable String id){return response(service.mine(SecurityUtils.currentUserId(),id));}
    @GetMapping("/api/v1/community/ratings/{rating}/images/{id}") public ResponseEntity<byte[]> review(@PathVariable long rating,@PathVariable String id){return response(service.review(rating,id));}
    private ResponseEntity<byte[]> response(byte[] bytes){return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(bytes);}
}
