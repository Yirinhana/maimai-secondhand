package com.maimai.identity.controller;

import com.maimai.identity.service.AvatarService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AvatarController {
    private final AvatarService avatars;
    public AvatarController(AvatarService avatars) {this.avatars=avatars;}

    @PostMapping(value="/me/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarService.AvatarView upload(@RequestPart("file") MultipartFile file) {return avatars.upload(file);}

    @GetMapping("/avatars/{filename}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String filename) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=avatar.jpg")
                .body(new FileSystemResource(avatars.readable(filename)));
    }
}
